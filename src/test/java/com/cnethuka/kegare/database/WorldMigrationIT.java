package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** {@code /kegare db migrate} and {@code export} (DESIGN §3.33), against a real database. Skipped without {@code KEGARE_TEST_DB_URL}. */
@ExtendWith(EphemeralTestServerProvider.class)
class WorldMigrationIT {
    private static final ResourceLocation EYES = ResourceLocation.parse("kegare:six_eyes");
    private static final ResourceLocation OTHER = ResourceLocation.parse("kegare:other_trait");
    private static final long GAME_TIME = 1000;
    /** An attachment that play will have deleted by the second run. */
    private static final String GONE_LATER = "kegare:gone_later";
    private static final BlockPos SPAWN = new BlockPos(7, 64, -3);

    @TempDir
    Path dir;

    @BeforeEach
    void wipe() throws Exception {
        TestDb.assumeAvailable();
        TestDb.wipe();
    }

    private Path files() throws Exception {
        return Files.createDirectories(dir.resolve("playerdata"));
    }

    private Path writePlayerFile(ServerPlayer player, int capacity) throws Exception {
        MaliceService.adminBirth(player, capacity);
        CompoundTag tag = player.saveWithoutId(new CompoundTag());
        CompoundTag attachments = tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        attachments.put(GONE_LATER, new CompoundTag());
        tag.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        Path file = files().resolve(player.getUUID() + ".dat");
        NbtIo.writeCompressed(tag, file);
        return file;
    }

    private static <T> T done(TestHost host, CompletableFuture<T> future) throws Exception {
        SavingIT.await("it to finish", () -> {
            host.runPosted();
            return future.isDone();
        });
        return future.get();
    }

    private static int capacityIn(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get("capacity").getAsInt();
    }

    @Test
    void migrateTwiceThenExport(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                ServerPlayer one = TestServer.damageable(server, "migrate_one"), two = TestServer.damageable(server, "migrate_two");
                UUID newToThisWorld = UUID.randomUUID(), onlineNow = UUID.randomUUID();
                Path fileOne = writePlayerFile(one, 1234), fileTwo = writePlayerFile(two, 2345);
                CompoundTag noKegare = one.saveWithoutId(new CompoundTag());
                PlayerDataCodec.stripFromPlayerFile(noKegare);
                NbtIo.writeCompressed(noKegare, files().resolve(UUID.randomUUID() + ".dat"));
                Path broken = files().resolve(UUID.randomUUID() + ".dat");
                Files.writeString(broken, "this isn't a player file");
                Files.writeString(files().resolve("not-a-player.dat"), "nor is this");
                byte[] oneBefore = Files.readAllBytes(fileOne);

                UniqueTraitSavedData world = new UniqueTraitSavedData();
                assertTrue(world.tryClaim(EYES, one.getUUID()), "setup");
                world.startSuccession(OTHER, new UniqueTraitSavedData.Pending(two.getUUID(), Optional.empty(), GAME_TIME + 200));
                world.revoke(two.getUUID(), EYES);
                world.markSeen(one.getUUID(), 5000L);

                Set<UUID> online = new HashSet<>();
                WorldMigration.Target target = new WorldMigration.Target(files(), online::contains, () -> world, () -> GAME_TIME, () -> SPAWN);
                TestHost host = new TestHost(server, files());
                KegareDatabase database = new KegareDatabase(TestDb.config("a"), dir, host);
                database.start();
                try {
                    // A dry run says what it would do, and writes nothing.
                    WorldMigration.Migrated dry = done(host, WorldMigration.migrate(database, target, true));
                    assertTrue(dry.dry());
                    assertEquals(4, dry.files(), "the file that isn't named after a player doesn't count");
                    assertEquals(2, dry.imported());
                    assertEquals(0, dry.alreadyThere());
                    assertEquals(1, dry.noData());
                    assertEquals(1, dry.failures().size());
                    assertTrue(dry.failures().get(0).startsWith(broken.getFileName().toString()), dry.failures().toString());
                    assertEquals(1, dry.bearers());
                    assertEquals(1, dry.pending());
                    assertEquals(1, dry.revoked());
                    assertEquals(1, dry.seen());
                    assertTrue(SavingIT.rows(database.pool, one.getUUID()).isEmpty(), "a dry run writes nothing");
                    assertFalse(database.traits.read(database.pool).bearers().containsKey(EYES), "not a trait either");

                    // The real one.
                    WorldMigration.Migrated first = done(host, WorldMigration.migrate(database, target, false));
                    assertEquals(2, first.imported());
                    assertEquals(dry.rows(), first.rows(), "the dry run told the truth");
                    assertEquals(1, first.failures().size());
                    assertEquals(1234, capacityIn(SavingIT.rows(database.pool, one.getUUID()).get("kegare:malice")));
                    assertEquals(2345, capacityIn(SavingIT.rows(database.pool, two.getUUID()).get("kegare:malice")));
                    NetworkTraits.Snapshot traits = database.traits.read(database.pool);
                    assertEquals(one.getUUID(), traits.bearers().get(EYES));
                    long dueIn = traits.pending().get(OTHER).dueTick() * 50 - System.currentTimeMillis();
                    assertTrue(dueIn > 5_000 && dueIn <= 10_000, "200 ticks of game time left is 10 seconds of wall clock: " + dueIn);
                    assertEquals(Set.of(EYES), traits.revoked().get(two.getUUID()));
                    assertArrayEquals(oneBefore, Files.readAllBytes(fileOne), "the world's copy is untouched");

                    // Twice changes nothing, including anything that's happened since.
                    String newer = SavingIT.rows(database.pool, one.getUUID()).get("kegare:malice").replace("1234", "9999");
                    database.pool.transaction(c -> {
                        PlayerStore.write(c, one.getUUID(), Map.of("kegare:malice", newer));
                        return null;
                    });
                    database.pool.transaction(c -> {
                        Map<String, String> delete = new java.util.HashMap<>();
                        delete.put(GONE_LATER, null);
                        PlayerStore.write(c, one.getUUID(), delete);
                        return null;
                    });
                    WorldMigration.Migrated second = done(host, WorldMigration.migrate(database, target, false));
                    assertEquals(0, second.imported());
                    assertEquals(0, second.rows());
                    assertEquals(2, second.alreadyThere());
                    assertEquals(0, second.bearers() + second.pending() + second.revoked() + second.seen());
                    assertEquals(0, second.conflicts());
                    assertEquals(9999, capacityIn(SavingIT.rows(database.pool, one.getUUID()).get("kegare:malice")), "newer data stands");
                    assertFalse(SavingIT.rows(database.pool, one.getUUID()).containsKey(GONE_LATER), "and something play deleted doesn't come back");

                    // A world whose bearer isn't the database's: the database's stands, and it says so.
                    UniqueTraitSavedData otherWorld = new UniqueTraitSavedData();
                    otherWorld.tryClaim(EYES, two.getUUID());
                    WorldMigration.Migrated conflict = done(host, WorldMigration.migrate(database, new WorldMigration.Target(files(), online::contains, () -> otherWorld, () -> GAME_TIME, () -> SPAWN), false));
                    assertEquals(1, conflict.conflicts());
                    assertEquals(one.getUUID(), database.traits.read(database.pool).bearers().get(EYES));

                    // Two more people the world's files don't have: one new here, and one online.
                    database.pool.transaction(c -> {
                        PlayerStore.write(c, newToThisWorld, Map.of("kegare:malice", newer));
                        PlayerStore.write(c, onlineNow, Map.of("kegare:malice", newer));
                        return null;
                    });
                    online.add(onlineNow);
                    CompoundTag vanillaBefore = NbtIo.readCompressed(fileOne, NbtAccounter.unlimitedHeap());

                    // Export: everything goes back.
                    CompoundTag saved = new CompoundTag();
                    saved.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, new CompoundTag());
                    saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).putString("kegare:malice", "kept");
                    KegareDatabase.setCurrent(database);
                    try {
                        KegareDatabase.beforePlayerFileWritten(saved);
                        assertTrue(saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).isEmpty(), "setup: with storage on the keys come out");

                        WorldMigration.Exported exported = done(host, WorldMigration.export(database, target));
                        assertEquals(4, exported.players());
                        assertEquals(2, exported.written());
                        assertEquals(1, exported.created());
                        assertEquals(1, exported.online());
                        assertTrue(exported.failures().isEmpty(), exported.failures().toString());
                        assertEquals(1, exported.bearers());

                        // From here on a save keeps them.
                        saved.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, new CompoundTag());
                        saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).putString("kegare:malice", "kept");
                        KegareDatabase.beforePlayerFileWritten(saved);
                        assertEquals("kept", saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getString("kegare:malice"), "after an export, player files keep Kegare's keys");
                    } finally {
                        KegareDatabase.setCurrent(null);
                    }

                    CompoundTag afterOne = NbtIo.readCompressed(fileOne, NbtAccounter.unlimitedHeap());
                    Map<String, String> inFile = PlayerDataCodec.fromPlayerFile(afterOne);
                    Map<String, String> inDatabase = SavingIT.rows(database.pool, one.getUUID());
                    assertEquals(inDatabase.keySet(), inFile.keySet(), "everything, and what play deleted stays deleted");
                    for (String key : inDatabase.keySet()) {
                        assertEquals(JsonParser.parseString(inDatabase.get(key)), JsonParser.parseString(inFile.get(key)), key);
                    }
                    assertEquals(9999, capacityIn(inFile.get("kegare:malice")), "the newest data");
                    for (String key : vanillaBefore.getAllKeys()) {
                        if (!key.equals(AttachmentHolder.ATTACHMENTS_NBT_KEY)) assertEquals(vanillaBefore.get(key), afterOne.get(key), "vanilla's own " + key + " is as it was");
                    }

                    // What Minecraft will really read: the file on a player.
                    ServerPlayer reader = TestServer.damageable(server, "migrate_reader");
                    reader.load(afterOne);
                    assertEquals(9999, MaliceService.get(reader).capacity(), "and a player loads it");

                    Path created = files().resolve(newToThisWorld + ".dat");
                    CompoundTag fresh = NbtIo.readCompressed(created, NbtAccounter.unlimitedHeap());
                    assertEquals(9999, capacityIn(PlayerDataCodec.fromPlayerFile(fresh).get("kegare:malice")));
                    assertTrue(fresh.contains("DataVersion"), "or Minecraft would try to upgrade it from nothing");
                    ServerPlayer newcomer = TestServer.damageable(server, "migrate_newcomer");
                    newcomer.load(fresh);
                    assertEquals(new BlockPos(7, 65, -3), newcomer.blockPosition(), "they come in at the world's spawn, not 0, 0, 0");
                    assertFalse(Files.exists(files().resolve(onlineNow + ".dat")), "someone online goes in at their next save");
                    assertEquals(Files.size(broken), "this isn't a player file".length(), "and what it couldn't read it left alone");

                    assertEquals(one.getUUID(), world.bearer(EYES).orElseThrow(), "the world's record has the bearer");
                    UniqueTraitSavedData.Pending pending = world.pendingView().get(OTHER);
                    assertTrue(pending.dueTick() > GAME_TIME && pending.dueTick() <= GAME_TIME + 200, "and pending is game time again: " + pending.dueTick());
                    assertEquals(Set.of(EYES), world.revokedView().get(two.getUUID()));
                    assertTrue(world.isDirty(), "so it gets written");
                } finally {
                    database.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void statusSaysHowItIsAndCanBeCut(MinecraftServer server) throws Exception {
        try (ToggleProxy proxy = new ToggleProxy()) {
            TestHost host = new TestHost(server, dir);
            KegareDatabase database = new KegareDatabase(TestDb.quickStop("status", TestDb.urlVia(proxy.port())), dir, host);
            database.start();
            try {
                UUID player = UUID.randomUUID();
                assertTrue(database.login(player).get().admitted(), "setup");
                KegareDatabase.Status status = database.status().get();
                assertTrue(status.connected());
                assertEquals(Schema.current(), status.schemaVersion());
                assertEquals("status", status.serverId());
                assertEquals(1, status.leasesHere());
                assertEquals(1, status.leasesEverywhere());
                proxy.down();
                KegareDatabase.Status down = database.status().get();
                assertFalse(down.connected(), "it really asks");
                assertEquals(1, down.leasesHere(), "and still knows what it holds");
                proxy.up();
            } finally {
                database.stop();
            }
        }
    }
}
