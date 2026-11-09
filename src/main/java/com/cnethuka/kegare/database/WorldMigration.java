package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Moving an existing server on and off Postgres (DESIGN §3.33): {@code /kegare db migrate} copies the world into the database and {@code /kegare db export}
 * copies the database back into the world. A migration is safe to run twice, as the database's own rows always win, and it never changes a world file.
 */
public final class WorldMigration {
    /** What a world gives a migration or an export. */
    public record Target(Path playerFiles, Predicate<UUID> online, Supplier<UniqueTraitSavedData> worldTraits, Supplier<Long> gameTime, Supplier<BlockPos> spawn) {
        public static Target of(MinecraftServer server) {
            return new Target(server.getWorldPath(LevelResource.PLAYER_DATA_DIR), player -> server.getPlayerList().getPlayer(player) != null, () -> UniqueTraitSavedData.inWorld(server),
                    () -> server.overworld().getGameTime(), () -> server.overworld().getSharedSpawnPos());
        }
    }

    /**
     * What a migration did, or with a dry run what it would do.
     *
     * @param conflicts bearers the world has that the database has someone else for: the database's stand
     */
    public record Migrated(boolean dry, int files, int imported, int rows, int alreadyThere, int noData, List<String> failures, int bearers, int pending, int revoked, int seen, int conflicts) {}

    /**
     * What an export did.
     *
     * @param created players who'd never been on this world: a file at its spawn, so they don't get a fresh roll with storage off
     * @param online  left alone: their next save writes their live data
     */
    public record Exported(int players, int written, int created, int online, List<String> failures, int bearers, int pending, int revoked) {}

    /** What the world's trait record holds, taken on the server thread. */
    private record WorldTraits(Map<ResourceLocation, UUID> bearers, Map<ResourceLocation, UniqueTraitSavedData.Pending> pending, Map<UUID, Set<ResourceLocation>> revoked, Map<UUID, Long> seen, long gameTime) {
        static WorldTraits of(UniqueTraitSavedData world, long gameTime) {
            Map<UUID, Set<ResourceLocation>> revoked = new HashMap<>();
            world.revokedView().forEach((player, traits) -> revoked.put(player, Set.copyOf(traits)));
            return new WorldTraits(Map.copyOf(world.bearers()), new LinkedHashMap<>(world.pendingView()), revoked, Map.copyOf(world.lastSeenView()), gameTime);
        }
    }

    private WorldMigration() {}

    // ---------------------------------------------------------------- the world into the database

    /** Call it on the server thread: it reads the world's trait record there, and everything else on the storage's own threads. */
    public static CompletableFuture<Migrated> migrate(KegareDatabase database, Target target, boolean dry) {
        WorldTraits traits = WorldTraits.of(target.worldTraits().get(), target.gameTime().get());
        return CompletableFuture.supplyAsync(() -> {
            try {
                database.awaitReady();
                Migrated result = run(database, target.playerFiles(), traits, dry);
                if (!dry) database.pollNetworkSafely(); // the memory has what just went in
                return result;
            } catch (SQLException | IOException e) {
                throw new CompletionException(new SQLException(DatabaseLog.describe(e, database.config)));
            }
        }, database.io);
    }

    private static Migrated run(KegareDatabase database, Path playerFiles, WorldTraits traits, boolean dry) throws SQLException, IOException {
        int files = 0, imported = 0, rowCount = 0, alreadyThere = 0, noData = 0;
        List<String> failures = new ArrayList<>();
        for (Path file : playerFilesIn(playerFiles)) {
            UUID player = playerOf(file);
            if (player == null) continue;
            files++;
            Map<String, String> rows;
            try {
                rows = PlayerDataCodec.fromPlayerFile(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
            } catch (IOException | RuntimeException e) {
                failures.add(file.getFileName() + " (" + e.getClass().getSimpleName() + ")");
                continue;
            }
            if (rows.isEmpty()) {
                noData++;
                continue;
            }
            // A player who has any rows at all is left as they are: whatever they've done since is in those rows, and a row they no longer have was deleted by play.
            int added = database.pool.transaction(connection -> {
                if (!PlayerStore.load(connection, player).isEmpty()) return -1;
                return dry ? rows.size() : PlayerStore.writeMissing(connection, player, rows);
            });
            if (added < 0) {
                alreadyThere++;
            } else {
                imported++;
                rowCount += added;
            }
        }
        int[] written = database.pool.transaction(connection -> writeTraits(connection, traits, dry));
        return new Migrated(dry, files, imported, rowCount, alreadyThere, noData, List.copyOf(failures), written[0], written[1], written[2], written[3], written[4]);
    }

    private static List<Path> playerFilesIn(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".dat")).sorted().toList();
        }
    }

    @Nullable
    private static UUID playerOf(Path file) {
        String name = file.getFileName().toString();
        try {
            return UUID.fromString(name.substring(0, name.length() - ".dat".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Only what isn't there: what the database already has stands. Returns bearers, pending, revoked, seen, conflicts. */
    private static int[] writeTraits(Connection connection, WorldTraits world, boolean dry) throws SQLException {
        int bearers = 0, pending = 0, revoked = 0, seen = 0, conflicts = 0;
        for (Map.Entry<ResourceLocation, UUID> entry : world.bearers().entrySet()) {
            UUID there = bearerOf(connection, entry.getKey().toString());
            if (there != null) {
                if (!there.equals(entry.getValue())) conflicts++;
            } else if (hasPending(connection, entry.getKey().toString())) {
                conflicts++;
            } else {
                bearers++;
                if (!dry) execute(connection, "INSERT INTO kegare.unique_trait (trait, bearer) VALUES (?, ?) ON CONFLICT (trait) DO NOTHING", entry.getKey().toString(), entry.getValue());
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<ResourceLocation, UniqueTraitSavedData.Pending> entry : world.pending().entrySet()) {
            String trait = entry.getKey().toString();
            if (hasPending(connection, trait) || bearerOf(connection, trait) != null) continue;
            pending++;
            if (dry) continue;
            long left = Math.max(0, entry.getValue().dueTick() - world.gameTime()); // in the world it's game time, and on the network the wall clock
            execute(connection, "INSERT INTO kegare.trait_pending (trait, previous, killer, due_at) VALUES (?, ?, ?, ?) ON CONFLICT (trait) DO NOTHING",
                    trait, entry.getValue().previousBearer(), entry.getValue().killer().orElse(null), new Timestamp(now + left * 50));
        }
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : world.revoked().entrySet()) {
            for (ResourceLocation trait : entry.getValue()) {
                try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM kegare.trait_revoked WHERE player = ? AND trait = ?")) {
                    exists.setObject(1, entry.getKey());
                    exists.setString(2, trait.toString());
                    try (ResultSet result = exists.executeQuery()) {
                        if (result.next()) continue;
                    }
                }
                revoked++;
                if (!dry) execute(connection, "INSERT INTO kegare.trait_revoked (player, trait) VALUES (?, ?) ON CONFLICT DO NOTHING", entry.getKey(), trait.toString());
            }
        }
        for (Map.Entry<UUID, Long> entry : world.seen().entrySet()) {
            try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM kegare.player_seen WHERE player = ?")) {
                exists.setObject(1, entry.getKey());
                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) continue;
                }
            }
            seen++;
            if (!dry) execute(connection, "INSERT INTO kegare.player_seen (player, seen_at) VALUES (?, ?) ON CONFLICT (player) DO NOTHING", entry.getKey(), new Timestamp(entry.getValue()));
        }
        return new int[] {bearers, pending, revoked, seen, conflicts};
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    @Nullable
    private static UUID bearerOf(Connection connection, String trait) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT bearer FROM kegare.unique_trait WHERE trait = ?")) {
            select.setString(1, trait);
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? result.getObject(1, UUID.class) : null;
            }
        }
    }

    private static boolean hasPending(Connection connection, String trait) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM kegare.trait_pending WHERE trait = ?")) {
            select.setString(1, trait);
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    // ---------------------------------------------------------------- the database into the world

    /**
     * Copies everything back, for switching storage off. The database is read off the server thread once anything queued has gone in, and the files are written on it, one
     * player at a time, as the game's own saves are: that way a save can't land in the middle of one. From here until the server stops, player files keep Kegare's keys too.
     */
    public static CompletableFuture<Exported> export(KegareDatabase database, Target target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                database.awaitReady();
                database.awaitQueue();
                Map<UUID, Map<String, String>> players = database.pool.transaction(WorldMigration::everyPlayer);
                return new DatabaseCopy(players, database.traits.read(database.pool));
            } catch (SQLException e) {
                throw new CompletionException(new SQLException(DatabaseLog.describe(e, database.config)));
            }
        }, database.io).thenCompose(copy -> toWorld(database, target, copy));
    }

    private record DatabaseCopy(Map<UUID, Map<String, String>> players, NetworkTraits.Snapshot traits) {}

    private static CompletableFuture<Exported> toWorld(KegareDatabase database, Target target, DatabaseCopy copy) {
        CompletableFuture<Exported> done = new CompletableFuture<>();
        AtomicInteger written = new AtomicInteger(), created = new AtomicInteger(), online = new AtomicInteger();
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        KegareDatabase.Host host = database.host();
        host.onServerThread(() -> {
            database.exported();
            writeWorldTraits(target, copy.traits());
        });
        for (Map.Entry<UUID, Map<String, String>> player : copy.players().entrySet()) {
            host.onServerThread(() -> {
                if (target.online().test(player.getKey())) {
                    online.incrementAndGet();
                    return;
                }
                try {
                    if (toPlayerFile(target, player.getKey(), player.getValue())) created.incrementAndGet();
                    else written.incrementAndGet();
                } catch (IOException | RuntimeException e) {
                    failures.add(player.getKey() + " (" + e.getClass().getSimpleName() + ")");
                }
            });
        }
        host.onServerThread(() -> done.complete(new Exported(copy.players().size(), written.get(), created.get(), online.get(), List.copyOf(failures),
                copy.traits().bearers().size(), copy.traits().pending().size(), copy.traits().revoked().size())));
        return done;
    }

    private static Map<UUID, Map<String, String>> everyPlayer(Connection connection) throws SQLException {
        Map<UUID, Map<String, String>> players = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT player, attachment, data::text FROM kegare.player_data ORDER BY player"); ResultSet result = select.executeQuery()) {
            while (result.next()) players.computeIfAbsent(result.getObject(1, UUID.class), p -> new LinkedHashMap<>()).put(result.getString(2), result.getString(3));
        }
        return players;
    }

    /** The world's trait record becomes the database's, with its wall-clock times back in game time. */
    private static void writeWorldTraits(Target target, NetworkTraits.Snapshot traits) {
        long gameTime = target.gameTime().get(), nowTicks = System.currentTimeMillis() / 50;
        Map<ResourceLocation, UniqueTraitSavedData.Pending> pending = new LinkedHashMap<>();
        traits.pending().forEach((trait, entry) -> pending.put(trait, new UniqueTraitSavedData.Pending(entry.previousBearer(), entry.killer(), gameTime + Math.max(0, entry.dueTick() - nowTicks))));
        UniqueTraitSavedData world = target.worldTraits().get();
        world.replaceAll(traits.bearers(), pending, traits.revoked(), traits.seen(), Set.of());
        world.setDirty();
    }

    /** Returns whether it had to make the file. */
    private static boolean toPlayerFile(Target target, UUID player, Map<String, String> rows) throws IOException {
        Path file = target.playerFiles().resolve(player + ".dat");
        boolean existed = Files.exists(file);
        CompoundTag tag = existed ? NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()) : newPlayerFile(target.spawn().get());
        PlayerDataCodec.toPlayerFile(tag, rows);
        Files.createDirectories(target.playerFiles());
        Path temporary = target.playerFiles().resolve(player + ".kegare-export.tmp");
        NbtIo.writeCompressed(tag, temporary);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        Kegare.LOGGER.debug("Exported {}'s Kegare data to their world file", player);
        return !existed;
    }

    /** A player file for someone this world hasn't seen, who'd otherwise come in at 0, 0, 0. */
    private static CompoundTag newPlayerFile(BlockPos spawn) {
        CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(spawn.getX() + 0.5));
        position.add(DoubleTag.valueOf(spawn.getY() + 1));
        position.add(DoubleTag.valueOf(spawn.getZ() + 0.5));
        tag.put("Pos", position);
        return tag;
    }
}
