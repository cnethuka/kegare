package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.fists.FistsAdvancements;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.Driver;

/** Stats, leaderboards and the public views (DESIGN §3.33), against a real database. Skipped without {@code KEGARE_TEST_DB_URL}. */
@ExtendWith(EphemeralTestServerProvider.class)
class StatsIT {
    private static final Path SCRIPT = Path.of(System.getProperty("kegare.projectDir", ".")).resolve("tools/db/public_reader.sql");

    @TempDir
    Path dir;

    @BeforeEach
    void wipe() throws Exception {
        TestDb.assumeAvailable();
        TestDb.wipe();
    }

    private KegareDatabase started(MinecraftServer server) throws Exception {
        KegareDatabase database = new KegareDatabase(TestDb.config("a"), dir, new TestHost(server, dir));
        database.start();
        database.login(UUID.randomUUID()).get(); // up and migrated
        return database;
    }

    private static void awaitStats(KegareDatabase database) {
        SavingIT.await("the stats to go in", () -> !database.statsWaiting());
    }

    private static void dropReader(KegareDatabase database) throws SQLException {
        database.pool.transaction(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = 'kegare_public_reader'")) {
                if (r.next()) {
                    try (Statement drop = c.createStatement()) {
                        drop.execute("DROP OWNED BY kegare_public_reader");
                        drop.execute("DROP ROLE kegare_public_reader");
                    }
                }
            }
            return null;
        });
    }

    private static List<String> everyPublicValue(KegareDatabase database) throws SQLException {
        return database.pool.transaction(c -> {
            List<String> values = new ArrayList<>();
            for (String view : List.of("player_stats", "announced_advancements")) {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM kegare_public." + view)) {
                    while (r.next()) for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) values.add(String.valueOf(r.getString(i)));
                }
            }
            return values;
        });
    }

    private static Set<String> columns(KegareDatabase database, String schema) throws SQLException {
        return database.pool.transaction(c -> {
            Set<String> out = new TreeSet<>();
            try (var p = c.prepareStatement("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ?")) {
                p.setString(1, schema);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(r.getString(1) + "." + r.getString(2));
                }
            }
            return out;
        });
    }

    @Test
    void theLeaderboardCountsWhatHappened(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                KegareDatabase database = started(server);
                try {
                    UUID one = UUID.randomUUID(), two = UUID.randomUUID();
                    database.login(one, "Alpha").get();
                    database.login(two, "Bravo").get();
                    for (int i = 0; i < 3; i++) database.recordStat(one, StatEvents.PVP_KILL, 1, null, false);
                    database.recordStat(two, StatEvents.PVP_KILL, 1, null, false);
                    database.recordStat(one, StatEvents.DEATH, 1, null, false);
                    database.recordStat(two, StatEvents.DAMAGE_DEALT, 7.5, null, false);
                    database.recordStat(two, StatEvents.DAMAGE_DEALT, 2.5, null, false);
                    database.recordStat(one, StatEvents.DAMAGE_TAKEN, 10, null, false);
                    database.recordStat(one, StatEvents.BLACK_SPARK, 1, null, false);
                    database.recordStat(one, StatEvents.BLACK_SPARK, 4, null, false);
                    database.recordStat(one, StatEvents.BLACK_SPARK, 2, null, false);
                    database.recordStat(two, StatEvents.FIGHT, 1, null, false);
                    awaitStats(database);

                    List<KegareDatabase.TopRow> kills = database.top(KegareDatabase.PublicStat.PVP_KILLS).get();
                    assertEquals(new KegareDatabase.TopRow("Alpha", 3), kills.get(0), "most first");
                    assertEquals(new KegareDatabase.TopRow("Bravo", 1), kills.get(1));
                    assertEquals(10, database.top(KegareDatabase.PublicStat.DAMAGE_DEALT).get().get(0).value(), 1e-9, "damage is summed");
                    assertEquals(3, database.top(KegareDatabase.PublicStat.BLACK_SPARKS).get().get(0).value(), "sparks are counted");
                    assertEquals(4, database.top(KegareDatabase.PublicStat.LONGEST_CHAIN).get().get(0).value(), "and the longest chain is the longest");
                    assertEquals(1, database.top(KegareDatabase.PublicStat.FIGHTS).get().size(), "only who's on the list");
                    assertEquals(1, database.top(KegareDatabase.PublicStat.DEATHS).get().size());
                } finally {
                    database.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void nothingPrivateIsInThePublicViews(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            KegareDatabase.setCurrent(null);
            try {
                KegareDatabase database = started(server);
                KegareDatabase.setCurrent(database);
                try {
                    // The views are exactly these columns, and nothing can come through them that isn't named here.
                    assertEquals(Set.of("announced_advancements.advancement", "announced_advancements.earned_at", "announced_advancements.name",
                            "player_stats.black_sparks", "player_stats.damage_dealt", "player_stats.deaths", "player_stats.fights", "player_stats.longest_chain", "player_stats.name", "player_stats.pvp_kills"),
                            columns(database, "kegare_public"));

                    ServerPlayer player = TestServer.damageable(server, "pub_player");
                    database.login(player.getUUID(), "PublicName").get();
                    var advancements = server.getAdvancements();
                    var silent = advancements.get(KegareAdvancements.CLEAR_SIGHT); // the Six Eyes tab's root: silent
                    var announced = advancements.get(FistsAdvancements.LOCKED_IN);  // hidden, and announced
                    assertTrue(silent != null && announced != null, "setup: both exist");
                    assertFalse(silent.value().display().orElseThrow().shouldAnnounceChat(), "setup: silent");
                    assertTrue(announced.value().display().orElseThrow().isHidden() && announced.value().display().orElseThrow().shouldAnnounceChat(), "setup: hidden and announced");

                    // FakePlayers aren't recorded, so through the database's own door.
                    database.recordStat(player.getUUID(), StatEvents.ADVANCEMENT, 1, silent.id().toString(), false);
                    database.recordStat(player.getUUID(), StatEvents.ADVANCEMENT, 1, announced.id().toString(), true);
                    database.recordStat(player.getUUID(), StatEvents.PVP_KILL, 1, null, false);
                    // Private data sits right beside it in the same database.
                    database.pool.transaction(c -> {
                        PlayerStore.write(c, player.getUUID(), java.util.Map.of("kegare:grade", "{\"grade\":\"SPECIAL_GRADE\"}", "kegare:refinement", "{\"level\":9}",
                                "kegare:insight", "{\"output\":99}", "kegare:traits", "{\"traits\":[\"kegare:six_eyes\"]}", "kegare:unlocks", "{\"unlocked\":[\"x\"]}"));
                        return null;
                    });
                    database.traitRecord().forceClaim(net.minecraft.resources.ResourceLocation.parse("kegare:six_eyes"), player.getUUID());
                    awaitStats(database);
                    SavingIT.await("the bearer's row", () -> !database.traits.hasOps());

                    String everything = String.join(" ", everyPublicValue(database)).toLowerCase(Locale.ROOT);
                    assertTrue(everything.contains("publicname"), "setup: the views have this player: " + everything);
                    assertTrue(everything.contains("locked_in"), "a hidden advancement that was announced shows");
                    assertFalse(everything.contains("clear_sight"), "a silent one never does");
                    for (String secret : List.of("six_eyes", "special_grade", "grade", "refinement", "insight", "unlock", "assessment", player.getUUID().toString())) {
                        assertFalse(everything.contains(secret.toLowerCase(Locale.ROOT)), secret + " came through a public view: " + everything);
                    }
                } finally {
                    KegareDatabase.setCurrent(null);
                    database.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void theDocumentedRoleCanReadThePublicViewsAndNothingElse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                KegareDatabase database = started(server);
                String password = "reader-" + UUID.randomUUID();
                try {
                    dropReader(database);
                    runScript(database, password);
                    Properties reader = new Properties();
                    reader.setProperty("user", "kegare_public_reader");
                    reader.setProperty("password", password);
                    String urlWithoutUser = TestDb.URL.substring(0, TestDb.URL.indexOf('?'));
                    try (Connection connection = new Driver().connect(urlWithoutUser, reader); Statement statement = connection.createStatement()) {
                        statement.executeQuery("SELECT * FROM kegare_public.player_stats").close();
                        statement.executeQuery("SELECT * FROM kegare_public.announced_advancements").close();
                        for (String table : List.of("kegare.player_data", "kegare.unique_trait", "kegare.player_lease", "kegare.stat_event", "kegare.player_name", "kegare.schema_version", "kegare.trait_pending")) {
                            SQLException refused = assertThrows(SQLException.class, () -> statement.executeQuery("SELECT * FROM " + table).close(), table + " should be out of reach");
                            assertEquals("42501", refused.getSQLState(), "permission denied for " + table);
                        }
                        assertThrows(SQLException.class, () -> statement.execute("CREATE TABLE kegare_public.mine (x int)"), "and it can't write");
                    }
                } finally {
                    dropReader(database);
                    database.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** The real script from tools/db, with psql's one variable filled in. */
    private static void runScript(KegareDatabase database, String password) throws Exception {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(SCRIPT)) if (!line.startsWith("--")) sql.append(line).append('\n');
        String script = sql.toString().replace(":'password'", "'" + password + "'");
        database.pool.transaction(c -> {
            try (Statement s = c.createStatement()) {
                for (String statement : script.split(";")) if (!statement.isBlank()) s.execute(statement);
            }
            return null;
        });
    }
}
