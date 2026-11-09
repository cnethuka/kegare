package com.cnethuka.kegare.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Kegare's tables (DESIGN §3.33), in their own schema, built by versioned migrations of our own: applied on startup in one transaction and
 * recorded in {@code kegare.schema_version}. A database newer than this mod refuses to start the storage rather than guessing.
 */
final class Schema {
    /** Thrown when the database has migrations this version of the mod doesn't know. */
    static final class TooNewException extends SQLException {
        TooNewException(int database, int mod) {
            super("The database is at schema version " + database + " and this version of Kegare only knows up to " + mod + ". Update Kegare on this server.");
        }
    }

    /** Taken while migrating, so two servers starting together can't both do it. */
    private static final long MIGRATION_LOCK = 0x6B65676172650001L;

    /** Each entry is one version; the list only ever grows. */
    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE kegare.player_data (
                player uuid NOT NULL,
                attachment text NOT NULL,
                data jsonb NOT NULL,
                updated_at timestamptz NOT NULL DEFAULT now(),
                PRIMARY KEY (player, attachment)
            );
            """);

    static int current() {
        return MIGRATIONS.size();
    }

    private Schema() {}

    /** Brings the database up to this mod's version. Returns the version it's now at. */
    static int migrate(ConnectionPool pool) throws SQLException {
        return pool.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                    lock.setLong(1, MIGRATION_LOCK);
                    lock.execute();
                }
                statement.execute("CREATE SCHEMA IF NOT EXISTS kegare");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS kegare.schema_version (
                            version int PRIMARY KEY,
                            applied_at timestamptz NOT NULL DEFAULT now()
                        )""");
            }
            int at = version(connection);
            if (at > current()) throw new TooNewException(at, current());
            for (int v = at + 1; v <= current(); v++) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(MIGRATIONS.get(v - 1));
                }
                try (PreparedStatement record = connection.prepareStatement("INSERT INTO kegare.schema_version (version) VALUES (?)")) {
                    record.setInt(1, v);
                    record.executeUpdate();
                }
            }
            return current();
        });
    }

    static int version(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT coalesce(max(version), 0) FROM kegare.schema_version")) {
            result.next();
            return result.getInt(1);
        }
    }
}
