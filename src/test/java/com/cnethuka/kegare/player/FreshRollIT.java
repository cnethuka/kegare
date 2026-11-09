package com.cnethuka.kegare.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.database.DatabaseTestAccess;
import com.cnethuka.kegare.database.KegareDatabase;
import com.cnethuka.kegare.malice.MaliceService;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fresh Malice or technique roll over real data is the worst failure there is (DESIGN §3.33). Through the real login handling, against a real
 * database. Skipped without {@code KEGARE_TEST_DB_URL}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class FreshRollIT {
    @TempDir
    Path dir;

    @BeforeEach
    void wipe() throws Exception {
        DatabaseTestAccess.assumeAvailable();
        DatabaseTestAccess.wipe();
    }

    private void scene(MinecraftServer server, Scene scene) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            KegareDatabase database = DatabaseTestAccess.started(server, dir);
            KegareDatabase.setCurrent(database);
            try {
                scene.run(database);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                KegareDatabase.setCurrent(null);
                database.stop();
                TestServer.removePlayers(server);
            }
        });
    }

    interface Scene {
        void run(KegareDatabase database) throws Exception;
    }

    @Test
    void aBrandNewPlayerIsBornAndSavedOnce(MinecraftServer server) throws Exception {
        scene(server, database -> {
            ServerPlayer player = TestServer.damageable(server, "roll_new");
            DatabaseTestAccess.join(database, player);
            PlayerEvents.handleLogin(player);
            assertTrue(MaliceService.get(player).isBorn(), "no data anywhere: a real first join");
        });
    }

    @Test
    void someoneWithNothingLoadedIsNeverRolled(MinecraftServer server) throws Exception {
        scene(server, database -> {
            ServerPlayer player = TestServer.damageable(server, "roll_unloaded");
            PlayerEvents.handleLogin(player); // skipped the load, however that could happen
            assertFalse(MaliceService.get(player).isBorn(), "nothing rolled");
            database.logout(player);
            Thread.sleep(200);
            assertEquals(0, database.queueSize(), "and nothing saved over what's really theirs");
        });
    }

    @Test
    void realDataIsAppliedBeforeLoginHandlingSoNothingRolls(MinecraftServer server) throws Exception {
        scene(server, database -> {
            ServerPlayer first = TestServer.damageable(server, "roll_real");
            DatabaseTestAccess.join(database, first);
            BirthService.rebirth(first, OptionalInt.of(4321));
            database.logout(first);
            DatabaseTestAccess.awaitQueue(database);
            TestServer.removePlayers(server);

            ServerPlayer back = TestServer.damageable(server, "roll_real");
            DatabaseTestAccess.join(database, back);
            PlayerEvents.handleLogin(back);
            assertEquals(4321, MaliceService.get(back).capacity(), "their own Malice, not a new roll");
        });
    }

    @Test
    void aRowThatWontDecodeKeepsThemOutAndTouchesNothing(MinecraftServer server) throws Exception {
        scene(server, database -> {
            ServerPlayer first = TestServer.damageable(server, "roll_corrupt");
            DatabaseTestAccess.join(database, first);
            BirthService.rebirth(first, OptionalInt.of(1111));
            database.logout(first);
            DatabaseTestAccess.awaitQueue(database);
            TestServer.removePlayers(server);
            DatabaseTestAccess.corrupt(database, first.getUUID(), "kegare:malice", "{\"tier\":\"nonsense\",\"capacity\":\"nope\"}");
            Map<String, String> before = DatabaseTestAccess.rows(database, first.getUUID());

            ServerPlayer back = TestServer.damageable(server, "roll_corrupt");
            database.loginLoaded(back.getUUID(), before);
            database.applyOnJoin(back);
            PlayerEvents.handleLogin(back);
            assertFalse(MaliceService.get(back).isBorn(), "nothing rolled");
            database.logout(back);
            Thread.sleep(200);
            assertEquals(before, DatabaseTestAccess.rows(database, back.getUUID()), "and their rows are as they were");
        });
    }

    @Test
    void dataWithNoBirthInItKeepsThemOut(MinecraftServer server) throws Exception {
        scene(server, database -> {
            ServerPlayer player = TestServer.damageable(server, "roll_partial");
            database.loginLoaded(player.getUUID(), Map.of("kegare:brain", "{\"meter\":5.0}"));
            database.applyOnJoin(player);
            PlayerEvents.handleLogin(player);
            assertFalse(MaliceService.get(player).isBorn(), "rows but no birth: kept out, not rolled");
        });
    }

    @Test
    void noRowButDataInTheirWorldFileIsImportedNotRolled(MinecraftServer server) throws Exception {
        scene(server, database -> {
            // A world from before storage was switched on: their file has the data, the database doesn't.
            ServerPlayer old = TestServer.damageable(server, "roll_import");
            BirthService.rebirth(old, OptionalInt.of(5150));
            CompoundTag file = new CompoundTag();
            file.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, old.serializeAttachments(server.registryAccess()));
            NbtIo.writeCompressed(file, dir.resolve(old.getUUID() + ".dat"));
            TestServer.removePlayers(server);

            ServerPlayer back = TestServer.damageable(server, "roll_import");
            DatabaseTestAccess.join(database, back);
            PlayerEvents.handleLogin(back);
            assertEquals(5150, MaliceService.get(back).capacity(), "their own, from the file");
            assertTrue(DatabaseTestAccess.rows(database, back.getUUID()).containsKey("kegare:malice"), "and now in the database, before they entered");
        });
    }

    @Test
    void whenTheDatabaseCantBeReachedALoginIsRefused(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                KegareDatabase unreachable = DatabaseTestAccess.unreachable(server, dir);
                try {
                    assertFalse(unreachable.login(java.util.UUID.randomUUID()).get().admitted(), "kept out with the message, no roll");
                } finally {
                    unreachable.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
