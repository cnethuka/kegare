package com.cnethuka.kegare.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * {@code player_lease}'s SQL (DESIGN §3.33): a player is owned by one server at a time. Login takes the lease; logout saves, then lets go; every write
 * carries the token and does nothing if it no longer owns the player. Ages come from the database's own clock, so servers needn't agree on the time.
 */
final class LeaseStore {
    private LeaseStore() {}

    /** Takes the player if they're free, already ours, or held by a server that's stopped checking in. Null if another server still holds them. */
    @Nullable
    static UUID acquire(Connection connection, UUID player, String serverId, int staleSeconds) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO kegare.player_lease (player, server_id, token) VALUES (?, ?, ?)
                ON CONFLICT (player) DO NOTHING""")) {
            UUID token = UUID.randomUUID();
            insert.setObject(1, player);
            insert.setString(2, serverId);
            insert.setObject(3, token);
            if (insert.executeUpdate() == 1) return token;
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT server_id, extract(epoch FROM now() - heartbeat) FROM kegare.player_lease WHERE player = ? FOR UPDATE""")) {
            select.setObject(1, player);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) return null; // let go a moment ago: the next try gets it
                if (!result.getString(1).equals(serverId) && result.getDouble(2) <= staleSeconds) return null;
            }
        }
        UUID token = UUID.randomUUID();
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE kegare.player_lease SET server_id = ?, token = ?, heartbeat = now() WHERE player = ?""")) {
            update.setString(1, serverId);
            update.setObject(2, token);
            update.setObject(3, player);
            update.executeUpdate();
        }
        return token;
    }

    /** Do we still own them? Locks the row until the transaction ends, so nobody can take them while we write. */
    static boolean owns(Connection connection, UUID player, UUID token) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT token FROM kegare.player_lease WHERE player = ? FOR UPDATE")) {
            select.setObject(1, player);
            try (ResultSet result = select.executeQuery()) {
                return result.next() && token.equals(result.getObject(1));
            }
        }
    }

    static void release(Connection connection, UUID player, UUID token) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.player_lease WHERE player = ? AND token = ?")) {
            delete.setObject(1, player);
            delete.setObject(2, token);
            delete.executeUpdate();
        }
    }

    /** Everything this server holds is still alive. Returns what it really still holds: player to token. */
    static Map<UUID, UUID> heartbeat(Connection connection, String serverId) throws SQLException {
        Map<UUID, UUID> held = new HashMap<>();
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE kegare.player_lease SET heartbeat = now() WHERE server_id = ? RETURNING player, token""")) {
            update.setString(1, serverId);
            try (ResultSet result = update.executeQuery()) {
                while (result.next()) held.put(result.getObject(1, UUID.class), result.getObject(2, UUID.class));
            }
        }
        return held;
    }

    /** Nobody's on a server that's just started: whatever it held last run, it holds no longer. */
    static void releaseAll(Connection connection, String serverId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.player_lease WHERE server_id = ?")) {
            delete.setString(1, serverId);
            delete.executeUpdate();
        }
    }
}
