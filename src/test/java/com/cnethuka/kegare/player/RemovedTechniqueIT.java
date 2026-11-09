package com.cnethuka.kegare.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.database.DatabaseTestAccess;
import com.cnethuka.kegare.database.KegareDatabase;
import com.cnethuka.kegare.database.PlayerDataCodec;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** The same old save, with Postgres storage on (DESIGN §3.33): its rows in the database, the same free reroll. Skipped without {@code KEGARE_TEST_DB_URL}. */
@ExtendWith(EphemeralTestServerProvider.class)
class RemovedTechniqueIT {
    @TempDir
    Path dir;

    @BeforeEach
    void wipe() throws Exception {
        DatabaseTestAccess.assumeAvailable();
        DatabaseTestAccess.wipe();
    }

    @Test
    void anOldSaveInTheDatabaseGetsTheSameFreeReroll(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            KegareDatabase database = DatabaseTestAccess.started(server, dir);
            KegareDatabase.setCurrent(database);
            try {
                Map<String, String> before = PlayerDataCodec.fromPlayerFile(RemovedTechniqueTest.oldSave());
                ServerPlayer player = TestServer.playerWithAdvancements(server, "old_save_db");
                DatabaseTestAccess.put(database, player.getUUID(), before);

                DatabaseTestAccess.join(database, player); // the real login: rows loaded and put on them before anything reads them
                assertEquals("kegare:test_apex", InnateTechniqueService.get(player).innateTechniqueId().orElseThrow().toString(), "it loaded as it was");
                PlayerEvents.handleLogin(player);
                RemovedTechniqueTest.checkRerolled(player, before);

                database.logout(player);
                DatabaseTestAccess.awaitQueue(database);
                Map<String, String> saved = DatabaseTestAccess.rows(database, player.getUUID());
                String technique = InnateTechniqueService.current(player).orElseThrow().id().toString();
                assertEquals(technique, RemovedTechniqueTest.compact(saved.get("kegare:innate_technique")).getAsJsonObject().get("innate_technique").getAsString(),
                        "and the new technique is what's in the database");
                assertEquals(RemovedTechniqueTest.compact(before.get("kegare:roll")), RemovedTechniqueTest.compact(saved.get("kegare:roll")), "the roll counts too");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                KegareDatabase.setCurrent(null);
                database.stop();
                TestServer.removePlayers(server);
            }
        });
    }
}
