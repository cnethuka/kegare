package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.rmt.BrainDamage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Saving, loading and outages against a real database (DESIGN §3.33). Skipped without {@code KEGARE_TEST_DB_URL}. */
@ExtendWith(EphemeralTestServerProvider.class)
class SavingIT {
    @TempDir
    Path config;

    @BeforeEach
    void wipe() throws Exception {
        TestDb.assumeAvailable();
        TestDb.wipe();
    }

    static void await(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out waiting for " + what);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private KegareDatabase started(MinecraftServer server, DatabaseConfig settings, TestHost host) throws Exception {
        KegareDatabase database = new KegareDatabase(settings, config, host);
        database.start();
        return database;
    }

    /** Runs a player through the login a real one takes. */
    static void join(KegareDatabase database, ServerPlayer player) throws Exception {
        KegareDatabase.LoginResult result = database.login(player.getUUID()).get();
        assertTrue(result.admitted(), "let in");
        database.loginLoaded(player.getUUID(), result);
        database.applyOnJoin(player);
        assertTrue(database.admit(player), "with their data on them");
    }

    static Map<String, String> rows(ConnectionPool pool, UUID player) throws Exception {
        return pool.transaction(c -> PlayerStore.load(c, player));
    }

    private static int capacityInDatabase(ConnectionPool pool, UUID player) throws Exception {
        return pool.transaction(c -> {
            try (PreparedStatement select = c.prepareStatement("SELECT (data->>'capacity')::int FROM kegare.player_data WHERE player = ? AND attachment = 'kegare:malice'")) {
                select.setObject(1, player);
                try (ResultSet r = select.executeQuery()) {
                    return r.next() ? r.getInt(1) : -1;
                }
            }
        });
    }

    private void onServer(MinecraftServer server, ThrowingRunnable task) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void savedDataComesBackOnTheNextLogin(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            KegareDatabase database = started(server, TestDb.config("a"), new TestHost(server, null));
            try {
                ServerPlayer player = TestServer.damageable(server, "save_one");
                join(database, player);
                BirthService.rebirth(player, OptionalInt.of(2222));
                BrainDamage.set(player, 33.0);
                database.logout(player);
                await("the logout's save", () -> database.queueSize() == 0);

                Map<String, String> saved = rows(database.pool, player.getUUID());
                assertTrue(saved.containsKey(Kegare.MODID + ":malice"), "Malice is in the table");
                TestServer.removePlayers(server);

                ServerPlayer again = TestServer.damageable(server, "save_one");
                join(database, again);
                assertEquals(2222, MaliceService.get(again).capacity(), "the same capacity");
                assertTrue(MaliceService.get(again).isBorn(), "and born: nothing rolls");
                assertEquals(33.0, BrainDamage.get(again), 1e-6, "the brain meter");
            } finally {
                database.stop();
            }
        });
    }

    @Test
    void hashingSkipsWhatHasntChanged(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            KegareDatabase database = started(server, TestDb.config("a"), new TestHost(server, null));
            try {
                ServerPlayer player = TestServer.damageable(server, "save_hash");
                join(database, player);
                BirthService.rebirth(player, OptionalInt.of(500));
                database.snapshot(player);
                await("the first save", () -> database.queueSize() == 0 && database.writes.get() >= 1);
                long after = database.writes.get();

                for (int i = 0; i < 5; i++) database.snapshot(player);
                Thread.sleep(300);
                assertEquals(after, database.writes.get(), "nothing changed, nothing written");

                BrainDamage.set(player, 12.0);
                database.snapshot(player);
                await("the change", () -> database.writes.get() == after + 1);
                assertEquals(1, database.writes.get() - after, "one transaction");

                // A rejoin isn't a change either.
                database.logout(player);
                await("the logout", () -> database.queueSize() == 0);
                TestServer.removePlayers(server);
                ServerPlayer again = TestServer.damageable(server, "save_hash");
                join(database, again);
                long rejoined = database.writes.get();
                database.snapshot(again);
                Thread.sleep(300);
                assertEquals(rejoined, database.writes.get(), "what came from the database doesn't go straight back");
            } finally {
                database.stop();
            }
        });
    }

    @Test
    void anAttachmentThatsGoneLosesItsRow(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            KegareDatabase database = started(server, TestDb.config("a"), new TestHost(server, null));
            try {
                ServerPlayer player = TestServer.damageable(server, "save_gone");
                join(database, player);
                BirthService.rebirth(player, OptionalInt.of(500));
                Burnout.apply(player, 200);
                database.snapshot(player);
                await("the save", () -> database.queueSize() == 0 && rows(database, player).containsKey(Kegare.MODID + ":burnout"));
                player.removeData(com.cnethuka.kegare.registry.KegareAttachments.BURNOUT.get());
                database.snapshot(player);
                await("the row going", () -> database.queueSize() == 0 && !rows(database, player).containsKey(Kegare.MODID + ":burnout"));
            } finally {
                database.stop();
            }
        });
    }

    private static Map<String, String> rows(KegareDatabase database, ServerPlayer player) {
        try {
            return rows(database.pool, player.getUUID());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aLongOutageCatchesUpAsSoonAsItsBack(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            try (ToggleProxy proxy = new ToggleProxy()) {
                KegareDatabase database = started(server, TestDb.quickStop("a", TestDb.urlVia(proxy.port())), new TestHost(server, null));
                try {
                    ServerPlayer player = TestServer.damageable(server, "save_long_out");
                    join(database, player);
                    BirthService.rebirth(player, OptionalInt.of(100));
                    database.snapshot(player);
                    await("the first save", () -> database.queueSize() == 0 && database.writes.get() >= 1);

                    proxy.down();
                    MaliceService.adminBirth(player, 321);
                    database.snapshot(player);
                    Thread.sleep(16_000); // the writer's backoff is up to 16 s by now
                    proxy.up();
                    long back = System.nanoTime();
                    await("it catching up", () -> database.queueSize() == 0 && !database.isDown());
                    long took = (System.nanoTime() - back) / 1_000_000;
                    assertTrue(took < 6_000, "the writer doesn't sleep out its backoff once the database is back: " + took + " ms");
                    try (ConnectionPool direct = new ConnectionPool(TestDb.config("check"))) {
                        assertEquals(321, capacityInDatabase(direct, player.getUUID()));
                    }
                } finally {
                    proxy.up();
                    database.stop();
                }
            }
        });
    }

    @Test
    void anOutageQueuesKeepsTheNewestWarnsOnceAndCatchesUp(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            try (ToggleProxy proxy = new ToggleProxy()) {
                TestHost host = new TestHost(server, null);
                KegareDatabase database = started(server, TestDb.quickStop("a", TestDb.urlVia(proxy.port())), host);
                try {
                    ServerPlayer player = TestServer.damageable(server, "save_out");
                    join(database, player);
                    BirthService.rebirth(player, OptionalInt.of(100));
                    database.snapshot(player);
                    await("the first save", () -> database.queueSize() == 0 && database.writes.get() >= 1);

                    proxy.down();
                    long start = System.nanoTime();
                    MaliceService.adminBirth(player, 200);
                    database.snapshot(player);
                    assertTrue(System.nanoTime() - start < 50_000_000L, "the server thread doesn't wait on a dead database");
                    await("the failure to show", database::isDown);
                    assertEquals(1, database.queueSize(), "it's waiting");
                    MaliceService.adminBirth(player, 300);
                    database.snapshot(player);
                    MaliceService.adminBirth(player, 400);
                    database.snapshot(player);
                    assertEquals(1, database.queueSize(), "one player's waiting, however many snapshots");
                    Thread.sleep(2500); // long enough to fail again and again
                    assertEquals(1, host.told.stream().filter("message.kegare.db.down"::equals).count(), "ops are told once, not flooded");

                    proxy.up();
                    await("it catching up", () -> database.queueSize() == 0 && !database.isDown());
                    assertEquals(1, host.told.stream().filter("message.kegare.db.up"::equals).count(), "and once when it's back");
                    try (ConnectionPool direct = new ConnectionPool(TestDb.config("check"))) {
                        assertEquals(400, capacityInDatabase(direct, player.getUUID()), "the newest wins");
                    }
                } finally {
                    proxy.up();
                    database.stop();
                }
            }
        });
    }

    @Test
    void stoppingDuringAnOutageSpillsToAFileAndTheNextStartReplaysItFirst(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            UUID id;
            try (ToggleProxy proxy = new ToggleProxy()) {
                KegareDatabase database = started(server, TestDb.quickStop("a", TestDb.urlVia(proxy.port())), new TestHost(server, null));
                ServerPlayer player = TestServer.damageable(server, "save_spill");
                id = player.getUUID();
                join(database, player);
                BirthService.rebirth(player, OptionalInt.of(100));
                database.snapshot(player);
                await("the first save", () -> database.queueSize() == 0 && database.writes.get() >= 1);

                proxy.down();
                MaliceService.adminBirth(player, 987);
                database.logout(player);
                database.stop();

                Path spill = config.resolve(SpillFile.FILE_NAME);
                assertTrue(Files.exists(spill), "what wouldn't go is on disk");
                String text = Files.readString(spill, StandardCharsets.UTF_8);
                assertTrue(text.contains(id.toString()), "theirs");
                assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("password"), "and no password in it");
            }
            TestServer.removePlayers(server);

            KegareDatabase next = started(server, TestDb.config("a"), new TestHost(server, null));
            try {
                ServerPlayer back = TestServer.damageable(server, "save_spill");
                join(next, back); // a login waits for the replay, so this can't see old data
                assertEquals(987, MaliceService.get(back).capacity(), "the spilled save was in before they came in");
                assertFalse(Files.exists(config.resolve(SpillFile.FILE_NAME)), "and the file's gone");
            } finally {
                next.stop();
            }
        });
    }

    @Test
    void offAndOnSharingOneQueueNeverWritesWithoutAdmission(MinecraftServer server) throws Exception {
        onServer(server, () -> {
            KegareDatabase database = started(server, TestDb.config("a"), new TestHost(server, null));
            try {
                ServerPlayer stranger = TestServer.damageable(server, "save_stranger");
                database.snapshot(stranger);
                database.logout(stranger);
                Thread.sleep(200);
                assertEquals(0, database.queueSize(), "nobody who wasn't let in is ever saved");
                assertTrue(rows(database.pool, stranger.getUUID()).isEmpty());
            } finally {
                database.stop();
            }
        });
    }
}
