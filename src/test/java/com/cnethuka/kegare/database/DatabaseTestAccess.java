package com.cnethuka.kegare.database;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** For tests in other packages. */
public final class DatabaseTestAccess {
    private DatabaseTestAccess() {}

    public static void assumeAvailable() {
        TestDb.assumeAvailable();
    }

    public static void wipe() throws Exception {
        TestDb.wipe();
    }

    public static KegareDatabase started(MinecraftServer server, Path dir) {
        KegareDatabase database = new KegareDatabase(TestDb.config("a"), dir, new TestHost(server, dir));
        database.start();
        return database;
    }

    public static KegareDatabase unreachable(MinecraftServer server, Path dir) {
        KegareDatabase database = new KegareDatabase(new DatabaseConfig(true, "jdbc:postgresql://127.0.0.1:1/none", "", "", 2, "a", 1, 1, 1, 1, 2, 2, 1, 1), dir, new TestHost(server, dir));
        database.start();
        return database;
    }

    public static void join(KegareDatabase database, ServerPlayer player) throws Exception {
        SavingIT.join(database, player);
    }

    public static void awaitQueue(KegareDatabase database) {
        SavingIT.await("the queue", () -> database.queueSize() == 0);
    }

    public static Map<String, String> rows(KegareDatabase database, UUID player) throws Exception {
        return SavingIT.rows(database.pool, player);
    }

    public static void corrupt(KegareDatabase database, UUID player, String attachment, String json) throws Exception {
        database.pool.transaction(c -> {
            try (PreparedStatement update = c.prepareStatement("UPDATE kegare.player_data SET data = ?::jsonb WHERE player = ? AND attachment = ?")) {
                update.setString(1, json);
                update.setObject(2, player);
                update.setString(3, attachment);
                update.executeUpdate();
            }
            return null;
        });
    }
}
