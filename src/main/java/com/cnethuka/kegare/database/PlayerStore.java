package com.cnethuka.kegare.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** {@code player_data}'s SQL (DESIGN §3.33). All of it inside whatever transaction it's given. */
final class PlayerStore {
    private PlayerStore() {}

    static Map<String, String> load(Connection connection, UUID player) throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT attachment, data::text FROM kegare.player_data WHERE player = ?")) {
            select.setObject(1, player);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) rows.put(result.getString(1), result.getString(2));
            }
        }
        return rows;
    }

    /** One player's changes: a null value deletes that attachment's row. */
    static void write(Connection connection, UUID player, Map<String, String> changes) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement("""
                     INSERT INTO kegare.player_data (player, attachment, data, updated_at) VALUES (?, ?, ?::jsonb, now())
                     ON CONFLICT (player, attachment) DO UPDATE SET data = EXCLUDED.data, updated_at = now()""");
             PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.player_data WHERE player = ? AND attachment = ?")) {
            for (Map.Entry<String, String> change : changes.entrySet()) {
                if (change.getValue() == null) {
                    delete.setObject(1, player);
                    delete.setString(2, change.getKey());
                    delete.addBatch();
                } else {
                    upsert.setObject(1, player);
                    upsert.setString(2, change.getKey());
                    upsert.setString(3, change.getValue());
                    upsert.addBatch();
                }
            }
            upsert.executeBatch();
            delete.executeBatch();
        }
    }

    /** Only what isn't there yet: what a migration uses, so running it twice changes nothing. Returns how many it added. */
    static int writeMissing(Connection connection, UUID player, Map<String, String> rows) throws SQLException {
        int added = 0;
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO kegare.player_data (player, attachment, data, updated_at) VALUES (?, ?, ?::jsonb, now())
                ON CONFLICT (player, attachment) DO NOTHING""")) {
            for (Map.Entry<String, String> row : rows.entrySet()) {
                insert.setObject(1, player);
                insert.setString(2, row.getKey());
                insert.setString(3, row.getValue());
                added += insert.executeUpdate();
            }
        }
        return added;
    }
}
