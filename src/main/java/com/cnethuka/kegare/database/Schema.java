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
            """,
            """
            CREATE TABLE kegare.player_lease (
                player uuid PRIMARY KEY,
                server_id text NOT NULL,
                token uuid NOT NULL,
                heartbeat timestamptz NOT NULL DEFAULT now()
            );
            CREATE INDEX player_lease_server ON kegare.player_lease (server_id);
            CREATE TABLE kegare.unique_trait (
                trait text PRIMARY KEY,
                bearer uuid NOT NULL
            );
            CREATE TABLE kegare.trait_pending (
                trait text PRIMARY KEY,
                previous uuid NOT NULL,
                killer uuid,
                due_at timestamptz NOT NULL
            );
            CREATE TABLE kegare.trait_revoked (
                player uuid NOT NULL,
                trait text NOT NULL,
                PRIMARY KEY (player, trait)
            );
            CREATE TABLE kegare.player_seen (
                player uuid PRIMARY KEY,
                seen_at timestamptz NOT NULL
            );
            CREATE TABLE kegare.network_event (
                id bigserial PRIMARY KEY,
                kind text NOT NULL,
                trait text NOT NULL,
                player uuid,
                created_at timestamptz NOT NULL DEFAULT now()
            );
            """,
            """
            CREATE TABLE kegare.player_name (
                player uuid PRIMARY KEY,
                name text NOT NULL,
                updated_at timestamptz NOT NULL DEFAULT now()
            );
            CREATE TABLE kegare.stat_event (
                id bigserial PRIMARY KEY,
                at timestamptz NOT NULL,
                server_id text NOT NULL,
                player uuid NOT NULL,
                kind text NOT NULL,
                amount double precision NOT NULL,
                detail text,
                announced boolean NOT NULL DEFAULT false
            );
            CREATE INDEX stat_event_player_kind ON kegare.stat_event (player, kind);
            CREATE SCHEMA kegare_public;
            CREATE VIEW kegare_public.player_stats AS
                SELECT n.name,
                    count(*) FILTER (WHERE e.kind = 'pvp_kill') AS pvp_kills,
                    count(*) FILTER (WHERE e.kind = 'death') AS deaths,
                    count(*) FILTER (WHERE e.kind = 'fight') AS fights,
                    coalesce(sum(e.amount) FILTER (WHERE e.kind = 'damage_dealt'), 0) AS damage_dealt,
                    count(*) FILTER (WHERE e.kind = 'black_spark') AS black_sparks,
                    coalesce(max(e.amount) FILTER (WHERE e.kind = 'black_spark'), 0)::bigint AS longest_chain
                FROM kegare.player_name n JOIN kegare.stat_event e ON e.player = n.player
                GROUP BY n.player, n.name;
            CREATE VIEW kegare_public.announced_advancements AS
                SELECT n.name, e.detail AS advancement, e.at AS earned_at
                FROM kegare.stat_event e JOIN kegare.player_name n ON n.player = e.player
                WHERE e.kind = 'advancement' AND e.announced;
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
