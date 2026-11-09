package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * The real database the integration tests run against: {@code KEGARE_TEST_DB_URL} (with its user and password in the query). Without it they
 * skip, so the suite passes on any machine. Each test starts from an empty database.
 */
final class TestDb {
    static final String URL = System.getenv("KEGARE_TEST_DB_URL");

    private TestDb() {}

    static boolean available() {
        return URL != null && !URL.isEmpty();
    }

    static void assumeAvailable() {
        assumeTrue(available(), "KEGARE_TEST_DB_URL isn't set");
    }

    static DatabaseConfig config(String serverId) {
        return config(serverId, URL);
    }

    static DatabaseConfig config(String serverId, String url) {
        return new DatabaseConfig(true, url, "", "", 4, serverId, 2, 5, 5, 5, 3, 3, 1, 1);
    }

    /** An empty database, then Kegare's schema. */
    static void wipe() throws SQLException {
        try (ConnectionPool pool = new ConnectionPool(config("wipe"))) {
            pool.transaction(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA IF EXISTS kegare CASCADE");
                    statement.execute("DROP SCHEMA IF EXISTS kegare_public CASCADE");
                }
                return null;
            });
        }
    }
}
