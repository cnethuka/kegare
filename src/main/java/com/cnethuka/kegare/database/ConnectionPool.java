package com.cnethuka.kegare.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.postgresql.Driver;

/**
 * A small pool of our own (DESIGN §3.33), so the jar carries no pool library. At most {@code size} connections are out at once; one that has
 * gone bad is thrown away and replaced. The driver is used directly, not through DriverManager, which can't see into a mod's module layer.
 */
final class ConnectionPool implements AutoCloseable {
    /** Connections opened since the game started, by anyone: what the "off opens nothing" test reads. */
    static final AtomicLong OPENED = new AtomicLong();

    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DatabaseConfig config;
    private final Semaphore permits;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private volatile boolean closed;

    ConnectionPool(DatabaseConfig config) {
        this.config = config;
        this.permits = new Semaphore(config.poolSize(), true);
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        if (!config.username().isEmpty()) properties.setProperty("user", config.username());
        if (!config.password().isEmpty()) properties.setProperty("password", config.password());
        properties.setProperty("connectTimeout", Integer.toString(config.connectTimeoutSeconds()));
        properties.setProperty("loginTimeout", Integer.toString(config.connectTimeoutSeconds()));
        properties.setProperty("socketTimeout", Integer.toString(config.socketTimeoutSeconds()));
        properties.setProperty("ApplicationName", "kegare-" + config.serverId());
        Connection connection = new Driver().connect(config.url(), properties);
        if (connection == null) throw new SQLException("The database url isn't a PostgreSQL one");
        OPENED.incrementAndGet();
        return connection;
    }

    /** Runs one piece of work on a connection, in one transaction: committed if it returns, rolled back if it throws. */
    <T> T transaction(Work<T> work) throws SQLException {
        if (closed) throw new SQLException("The pool is closed");
        try {
            if (!permits.tryAcquire(config.connectTimeoutSeconds() + config.socketTimeoutSeconds(), TimeUnit.SECONDS)) {
                throw new SQLException("No free database connection");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted waiting for a database connection");
        }
        Connection connection = null;
        boolean good = false;
        try {
            connection = take();
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            good = true;
            return result;
        } catch (SQLException | RuntimeException e) {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) connection.rollback();
                    good = isDataError(e); // the statement was refused; the connection itself is fine
                } catch (SQLException ignored) {
                    good = false;
                }
            }
            throw e;
        } finally {
            give(connection, good);
            permits.release();
        }
    }

    /** A class 23 state is the database refusing the data (a constraint), not a problem with the connection. */
    private static boolean isDataError(Exception e) {
        return e instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23");
    }

    private Connection take() throws SQLException {
        while (true) {
            Connection connection;
            synchronized (idle) {
                connection = idle.pollFirst();
            }
            if (connection == null) return open();
            try {
                if (connection.isValid(config.connectTimeoutSeconds())) return connection;
            } catch (SQLException ignored) {
                // dead: fall through and try the next
            }
            closeQuietly(connection);
        }
    }

    private void give(Connection connection, boolean good) {
        if (connection == null) return;
        if (!good || closed) {
            closeQuietly(connection);
            return;
        }
        synchronized (idle) {
            idle.addFirst(connection);
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // it's going away either way
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (idle) {
            idle.forEach(ConnectionPool::closeQuietly);
            idle.clear();
        }
    }
}
