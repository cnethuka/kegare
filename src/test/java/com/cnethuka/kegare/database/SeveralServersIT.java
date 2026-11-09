package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.rmt.BrainDamage;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Two servers sharing one real database (DESIGN §3.33). Skipped without {@code KEGARE_TEST_DB_URL}. */
@ExtendWith(EphemeralTestServerProvider.class)
class SeveralServersIT {
    private static final ResourceLocation EYES = ResourceLocation.parse("kegare:six_eyes");

    @TempDir
    Path dirA;
    @TempDir
    Path dirB;

    @BeforeEach
    void wipe() throws Exception {
        TestDb.assumeAvailable();
        TestDb.wipe();
    }

    /** Heartbeats far apart, so a test can make a lease stale without one refreshing it. */
    private static DatabaseConfig quiet(String id, int leaseWait, int stale) {
        return new DatabaseConfig(true, TestDb.URL, "", "", 4, id, 2, 5, 5, 5, leaseWait, stale, 600, 1);
    }

    private record Server(KegareDatabase database, TestHost host) {}

    private Server server(MinecraftServer minecraft, DatabaseConfig config, Path dir) {
        TestHost host = new TestHost(minecraft, null);
        KegareDatabase database = new KegareDatabase(config, dir, host);
        database.start();
        return new Server(database, host);
    }

    interface Scene {
        void run() throws Exception;
    }

    private static void onServer(MinecraftServer server, Scene scene) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                scene.run();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                KegareDatabase.setCurrent(null);
                TestServer.removePlayers(server);
            }
        });
    }

    private static void sql(KegareDatabase database, String statement) throws SQLException {
        database.pool.transaction(c -> {
            try (Statement s = c.createStatement()) {
                s.execute(statement);
            }
            return null;
        });
    }

    private static int count(KegareDatabase database, String select) throws SQLException {
        return database.pool.transaction(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(select)) {
                r.next();
                return r.getInt(1);
            }
        });
    }

    private static void pump(Server server) {
        server.host().runPosted();
    }

    private static void awaitPumping(String what, java.util.function.BooleanSupplier condition, Server... servers) {
        SavingIT.await(what, () -> {
            for (Server s : servers) pump(s);
            return condition.getAsBoolean();
        });
    }

    @Test
    void aSecondServerIsRefusedWhileTheFirstHoldsThemAndGetsThemOnceItLetsGo(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA), b = server(minecraft, quiet("b", 2, 600), dirB);
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "lease_one");
                SavingIT.join(a.database(), player);
                BirthService.rebirth(player, OptionalInt.of(3210));
                a.database().snapshot(player);

                KegareDatabase.LoginResult refused = b.database().login(player.getUUID()).get();
                assertFalse(refused.admitted(), "on one server at a time");
                assertEquals(KegareDatabase.HELD, refused.refusal(), "with a clear message");

                a.database().logout(player);
                SavingIT.await("the save and the release", () -> a.database().queueSize() == 0);
                KegareDatabase.LoginResult moved = b.database().login(player.getUUID()).get();
                assertTrue(moved.admitted(), "once it's let go");
                assertTrue(moved.rows().get("kegare:malice").contains("3210"), "and their data has moved with them");
                assertNull(moved.preRolledBirth(), "no birth for someone with data");
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void theNewServerWaitsForTheOldOneToFinishSaving(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 10, 600), dirA), b = server(minecraft, quiet("b", 10, 600), dirB);
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "lease_wait");
                SavingIT.join(a.database(), player);
                BirthService.rebirth(player, OptionalInt.of(111));
                a.database().snapshot(player);
                SavingIT.await("the first save", () -> a.database().queueSize() == 0);
                var waiting = b.database().login(player.getUUID()); // while A still has them
                Thread.sleep(800);
                assertFalse(waiting.isDone(), "it waits");
                MaliceService.adminBirth(player, 222);
                a.database().logout(player);
                assertTrue(waiting.get().admitted(), "and comes in when A lets go");
                assertTrue(waiting.get().rows().get("kegare:malice").contains("222"), "with what A saved last");
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void aLeaseWithNoHeartbeatGoesStaleSoACrashedServerDoesntLockAnyoneOut(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 1, 2), dirA), b = server(minecraft, quiet("b", 6, 2), dirB);
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "lease_stale");
                SavingIT.join(a.database(), player);
                // A "crashes": it never checks in again (its heartbeats are ten minutes apart).
                KegareDatabase.LoginResult taken = b.database().login(player.getUUID()).get();
                assertTrue(taken.admitted(), "taken over once two seconds have gone by with no heartbeat");
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void aStaleTokensWriteDoesNothingAndItsPlayerIsSentAway(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 1, 600), dirA), b = server(minecraft, quiet("b", 1, 600), dirB);
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "lease_token");
                SavingIT.join(a.database(), player);
                BirthService.rebirth(player, OptionalInt.of(1000));
                BrainDamage.set(player, 10.0);
                a.database().snapshot(player);
                SavingIT.await("A's save", () -> a.database().queueSize() == 0);

                sql(b.database(), "UPDATE kegare.player_lease SET heartbeat = now() - interval '1 hour'");
                KegareDatabase.LoginResult taken = b.database().login(player.getUUID()).get();
                assertTrue(taken.admitted(), "B took over from a server that looked dead");
                sql(b.database(), "UPDATE kegare.player_data SET data = '{\"meter\":77.0}'::jsonb WHERE attachment = 'kegare:brain'"); // what B has done since

                BrainDamage.set(player, 55.0); // A isn't dead after all, and doesn't know
                a.database().snapshot(player);
                SavingIT.await("A's stale write", () -> a.database().refusedWrites.get() == 1);
                assertEquals(1, count(b.database(), "SELECT count(*) FROM kegare.player_data WHERE attachment = 'kegare:brain' AND (data->>'meter')::float = 77.0"),
                        "B's newer data is untouched");
                awaitPumping("A sending them away", () -> !a.host().kicked.isEmpty(), a);
                assertTrue(a.host().kicked.get(0).contains(KegareDatabase.LEASE_LOST));
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void aHeartbeatNoticesALostLease(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, new DatabaseConfig(true, TestDb.URL, "", "", 4, "a", 2, 5, 5, 5, 1, 600, 1, 1), dirA);
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "lease_beat");
                SavingIT.join(a.database(), player);
                sql(a.database(), "UPDATE kegare.player_lease SET token = gen_random_uuid(), server_id = 'b'");
                awaitPumping("the heartbeat to notice", () -> !a.host().kicked.isEmpty(), a);
            } finally {
                a.database().stop();
            }
        });
    }

    @Test
    void theDatabaseItselfRefusesASecondBearer(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA);
            try {
                a.database().login(UUID.randomUUID()).get();
                sql(a.database(), "INSERT INTO kegare.unique_trait (trait, bearer) VALUES ('kegare:six_eyes', gen_random_uuid())");
                SQLException refused = assertThrows(SQLException.class,
                        () -> sql(a.database(), "INSERT INTO kegare.unique_trait (trait, bearer) VALUES ('kegare:six_eyes', gen_random_uuid())"));
                assertEquals("23505", refused.getSQLState(), "the primary key says no");
            } finally {
                a.database().stop();
            }
        });
    }

    @Test
    void twoServersRacingOnAClaimEndWithOneBearer(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA), b = server(minecraft, quiet("b", 2, 600), dirB);
            try {
                a.database().login(UUID.randomUUID()).get();
                b.database().login(UUID.randomUUID()).get();
                UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
                assertTrue(a.database().traitRecord().tryClaim(EYES, p1), "A thinks it's free");
                assertTrue(b.database().traitRecord().tryClaim(EYES, p2), "and so does B");
                awaitPumping("the loser being told", () -> a.host().events.stream().anyMatch(e -> e.startsWith("refused")) || b.host().events.stream().anyMatch(e -> e.startsWith("refused")), a, b);
                assertEquals(1, count(a.database(), "SELECT count(*) FROM kegare.unique_trait WHERE trait = 'kegare:six_eyes'"), "one row");
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void newPlayersRacingOnABirthRollCantBothWin(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            double before = KegareServerConfig.SIX_EYES_BIRTH_CHANCE.get();
            KegareServerConfig.SIX_EYES_BIRTH_CHANCE.set(1.0);
            Server a = server(minecraft, quiet("a", 2, 600), dirA), b = server(minecraft, quiet("b", 2, 600), dirB);
            try {
                var one = a.database().login(UUID.randomUUID());
                var two = b.database().login(UUID.randomUUID());
                int won = (one.get().preRolledBirth().contains(EYES) ? 1 : 0) + (two.get().preRolledBirth().contains(EYES) ? 1 : 0);
                assertEquals(1, won, "the database said yes to one, before either came in");
            } finally {
                KegareServerConfig.SIX_EYES_BIRTH_CHANCE.set(before);
                a.database().stop();
                b.database().stop();
            }
        });
    }

    private static void malice(KegareDatabase database, UUID player, String json) throws SQLException {
        database.pool.transaction(c -> {
            PlayerStore.write(c, player, Map.of("kegare:malice", json));
            return null;
        });
    }

    @Test
    void successionPicksFromTheWholeNetworkOnceAndTheWordNamesNoOne(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA), b = server(minecraft, quiet("b", 2, 600), dirB);
            try {
                UUID previous = UUID.randomUUID(), severed = UUID.randomUUID(), onlyHeir = UUID.randomUUID();
                assertTrue(a.database().login(previous).get().admitted());
                assertTrue(a.database().login(severed).get().admitted());
                assertTrue(b.database().login(onlyHeir).get().admitted(), "the heir's on the other server");
                malice(a.database(), severed, "{\"born\":1,\"severed\":1,\"capacity\":0}");
                malice(a.database(), onlyHeir, "{\"born\":1,\"capacity\":500}");
                awaitPumping("B's record", () -> true, b);
                a.database().traitRecord().startSuccession(EYES, new UniqueTraitSavedData.Pending(previous, Optional.empty(), System.currentTimeMillis() / 50 - 1));

                awaitPumping("the grant on both servers", () -> a.host().events.stream().anyMatch(e -> e.startsWith("grant")) && b.host().events.stream().anyMatch(e -> e.startsWith("grant")), a, b);
                awaitPumping("the word on both servers", () -> a.host().events.stream().anyMatch(e -> e.startsWith("broadcast")) && b.host().events.stream().anyMatch(e -> e.startsWith("broadcast")), a, b);
                Thread.sleep(1200);
                pump(a);
                pump(b);
                assertEquals(1, a.host().events.stream().filter(e -> e.startsWith("grant")).count(), "once, for all that both servers run it");
                assertTrue(a.host().events.stream().filter(e -> e.startsWith("grant")).findFirst().orElseThrow().contains(onlyHeir.toString()), "the only eligible one, on the other server");
                assertTrue(a.host().events.stream().filter(e -> e.startsWith("broadcast")).findFirst().orElseThrow().endsWith(" null"), "the word names no one");
                assertEquals(0, count(a.database(), "SELECT count(*) FROM kegare.trait_pending"), "no longer pending");
                assertEquals(1, count(a.database(), "SELECT count(*) FROM kegare.unique_trait WHERE trait = 'kegare:six_eyes'"), "one bearer");
            } finally {
                a.database().stop();
                b.database().stop();
            }
        });
    }

    @Test
    void pendingTimesAreWallClockSoServersAgree(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA);
            KegareDatabase.setCurrent(a.database());
            try {
                a.database().login(UUID.randomUUID()).get();
                long due = TraitSuccession.now(minecraft) + 20 * 3600;
                assertTrue(due > 30_000_000_000L, "counted from the epoch, not from this world's game time");
                a.database().traitRecord().startSuccession(EYES, new UniqueTraitSavedData.Pending(UUID.randomUUID(), Optional.empty(), due));
                awaitPumping("it in the table", () -> {
                    try {
                        return count(a.database(), "SELECT count(*) FROM kegare.trait_pending WHERE due_at BETWEEN now() + interval '59 minutes' AND now() + interval '61 minutes'") == 1;
                    } catch (SQLException e) {
                        throw new AssertionError(e);
                    }
                }, a);
            } finally {
                KegareDatabase.setCurrent(null);
                a.database().stop();
            }
        });
    }

    @Test
    void theGamesOwnTraitCodeGoesThroughTheNetworkRecord(MinecraftServer minecraft) throws Exception {
        onServer(minecraft, () -> {
            Server a = server(minecraft, quiet("a", 2, 600), dirA);
            KegareDatabase.setCurrent(a.database());
            try {
                ServerPlayer player = TestServer.damageable(minecraft, "net_trait");
                SavingIT.join(a.database(), player);
                BirthService.rebirth(player, OptionalInt.of(800));
                assertEquals(TraitService.AddResult.ADDED, TraitService.add(player, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
                awaitPumping("the bearer in the table", () -> {
                    try {
                        return count(a.database(), "SELECT count(*) FROM kegare.unique_trait WHERE trait = 'kegare:six_eyes'") == 1;
                    } catch (SQLException e) {
                        throw new AssertionError(e);
                    }
                }, a);
                assertTrue(UniqueTraitSavedData.get(minecraft).isNetworked(), "the game's asking the network's record");
                assertTrue(UniqueTraitSavedData.inWorld(minecraft).bearer(EYES).isEmpty(), "and the world's own copy isn't touched");
                TraitService.remove(player, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
                awaitPumping("it gone", () -> {
                    try {
                        return count(a.database(), "SELECT count(*) FROM kegare.unique_trait") == 0;
                    } catch (SQLException e) {
                        throw new AssertionError(e);
                    }
                }, a);
            } finally {
                KegareDatabase.setCurrent(null);
                a.database().stop();
            }
        });
    }
}
