package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Kegare's optional Postgres storage (DESIGN §3.33). Off by default: then this is never more than a null check, and no connection is ever opened.
 * On a client, even a singleplayer one, it's never on.
 * <p>
 * The server thread never waits on the database during play: it takes a snapshot of each player's saved attachments, hashes them, and queues what
 * changed; a thread of its own does the writing, and logins are held in the configuration phase while another loads their data. A player who
 * can't be loaded is kept out, never given a fresh roll.
 */
public final class KegareDatabase {
    /** What a server gives the storage. */
    public interface Host {
        /** Runs something on the server thread. */
        void onServerThread(Runnable task);

        /** Tells ops (and the log) something's happened. */
        void tellOps(String langKey);

        HolderLookup.Provider registries();

        /** Where this player's world file is, if it could be there. */
        @Nullable
        Path playerFile(UUID player);
    }

    /** How a login went: the player's rows, or that they're to be kept out. */
    public record LoginResult(boolean admitted, Map<String, String> rows) {
        static final LoginResult REFUSED = new LoginResult(false, Map.of());
    }

    public static final String UNREACHABLE = "disconnect.kegare.db.unreachable";
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    @Nullable
    private static volatile KegareDatabase current;

    final DatabaseConfig config;
    final ConnectionPool pool;
    final SaveQueue queue = new SaveQueue();
    private final Path configDir;
    private final Host host;
    private final ExecutorService io;
    private final Thread writer;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile boolean running = true;
    private volatile boolean down;
    private volatile boolean refusedStart;
    private volatile int schemaVersion;
    /** Transactions written, for the tests: an unchanged player must cost none. */
    final AtomicLong writes = new AtomicLong();

    // Server thread only, from here down.
    /** What's been loaded for players on their way in. */
    private final Map<UUID, Map<String, String>> loaded = new HashMap<>();
    /** Whose data is really on them: only these are ever saved. */
    private final Set<UUID> admitted = new HashSet<>();
    private final Map<UUID, Map<String, Long>> lastSaved = new HashMap<>();

    public KegareDatabase(DatabaseConfig config, Path configDir, Host host) {
        this.config = config;
        this.pool = new ConnectionPool(config);
        this.configDir = configDir;
        this.host = host;
        this.io = Executors.newFixedThreadPool(Math.max(2, config.poolSize()), runnable -> {
            Thread thread = new Thread(runnable, "Kegare database");
            thread.setDaemon(true);
            return thread;
        });
        this.writer = new Thread(this::writeLoop, "Kegare database writer");
        this.writer.setDaemon(true);
    }

    @Nullable
    public static KegareDatabase current() {
        return current;
    }

    public static boolean isOn() {
        return current != null;
    }

    /** For tests: makes this the storage the game's hooks talk to (null for none). */
    public static void setCurrent(@Nullable KegareDatabase database) {
        current = database;
    }

    // ---------------------------------------------------------------- starting and stopping

    /** Reads the settings, writing the file if it isn't there; starts the storage if it's switched on and this is a dedicated server. */
    public static void serverStarting(@Nullable MinecraftServer server) {
        if (!FMLEnvironment.dist.isDedicatedServer() || server == null) return;
        DatabaseConfig config;
        try {
            config = DatabaseConfig.load(FMLPaths.CONFIGDIR.get(), System::getenv);
        } catch (IOException | RuntimeException e) {
            Kegare.LOGGER.error("Couldn't read {}. Kegare's storage stays in the world.", DatabaseConfig.FILE_NAME);
            return;
        }
        if (!config.enabled()) return;
        KegareDatabase database = new KegareDatabase(config, FMLPaths.CONFIGDIR.get(), new ServerHost(server));
        current = database;
        database.start();
    }

    /** Every player is gone by now: flush, spill what wouldn't go, and let go. */
    public static void serverStopped() {
        KegareDatabase database = current;
        if (database == null) return;
        current = null;
        database.stop();
    }

    /** Connects and migrates, and replays any spill file, on a thread of its own: logins wait for it. */
    public void start() {
        writer.start();
        io.execute(this::startUp);
    }

    private void startUp() {
        long backoff = 1000;
        while (running) {
            try {
                schemaVersion = Schema.migrate(pool);
                Map<UUID, SaveQueue.Batch> spilled = SpillFile.read(configDir);
                for (var entry : spilled.entrySet()) writeBatch(entry.getKey(), entry.getValue());
                SpillFile.delete(configDir);
                if (!spilled.isEmpty()) Kegare.LOGGER.info("Kegare's storage replayed {} players' unsaved data from the last run", spilled.size());
                Kegare.LOGGER.info("Kegare's storage is on Postgres, as {}, schema version {}", config.serverId(), schemaVersion);
                markUp();
                ready.complete(null);
                return;
            } catch (Schema.TooNewException e) {
                refusedStart = true;
                Kegare.LOGGER.error("Kegare's storage won't start: {} Nobody can join until it's fixed.", e.getMessage());
                ready.complete(null);
                return;
            } catch (SQLException | IOException | RuntimeException e) {
                markDown(e);
                sleep(backoff);
                backoff = Math.min(MAX_BACKOFF_MILLIS, backoff * 2);
            }
        }
    }

    /** Waits for the queue, up to shutdownTimeout; what's left goes to the spill file. Server stop only. */
    public void stop() {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.shutdownTimeoutSeconds());
        while (queue.size() > 0 && System.currentTimeMillis() < deadline) sleep(50);
        running = false;
        writer.interrupt();
        try {
            writer.join(TimeUnit.SECONDS.toMillis(config.socketTimeoutSeconds()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<UUID, SaveQueue.Batch> left = queue.copy();
        if (!left.isEmpty()) {
            try {
                SpillFile.write(configDir, left);
                Kegare.LOGGER.warn("Kegare's database is unreachable: {} players' data is in {} and goes in on the next start", left.size(), SpillFile.FILE_NAME);
            } catch (IOException e) {
                Kegare.LOGGER.error("Kegare couldn't save {} players' data to {}: {}", left.size(), SpillFile.FILE_NAME, e.getClass().getSimpleName());
            }
        }
        io.shutdownNow();
        pool.close();
    }

    // ---------------------------------------------------------------- health

    private void markDown(Throwable failure) {
        Kegare.LOGGER.debug("Kegare's database: {}", DatabaseLog.describe(failure, config));
        if (down) return;
        down = true;
        Kegare.LOGGER.warn("Kegare can't reach its database. The game carries on and saves are queued. {}", DatabaseLog.describe(failure, config));
        host.tellOps("message.kegare.db.down");
    }

    private void markUp() {
        if (!down) return;
        down = false;
        Kegare.LOGGER.info("Kegare's database is back. Queued saves are going in.");
        host.tellOps("message.kegare.db.up");
    }

    public boolean isDown() {
        return down;
    }

    public int queueSize() {
        return queue.size();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    // ---------------------------------------------------------------- logging in

    /**
     * The first half of a login, off the server thread: wait for the storage and for anything of theirs still being written, then read their rows.
     * A player with no rows whose world file has Kegare's data gets it copied in first, in the same transaction: never a fresh roll.
     */
    public CompletableFuture<LoginResult> login(UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ready.get(config.leaseWaitSeconds(), TimeUnit.SECONDS);
                if (refusedStart) return LoginResult.REFUSED;
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.leaseWaitSeconds());
                while (queue.has(player)) {
                    if (System.currentTimeMillis() > deadline) return LoginResult.REFUSED;
                    Thread.sleep(50);
                }
                Map<String, String> rows = pool.transaction(connection -> {
                    Map<String, String> found = PlayerStore.load(connection, player);
                    if (!found.isEmpty()) return found;
                    Map<String, String> inFile = fromWorldFile(player);
                    if (!inFile.isEmpty()) {
                        Kegare.LOGGER.info("Bringing {}'s Kegare data in from their world file", player);
                        PlayerStore.writeMissing(connection, player, inFile);
                    }
                    return inFile;
                });
                markUp();
                return new LoginResult(true, rows);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LoginResult.REFUSED;
            } catch (Exception e) {
                markDown(e);
                return LoginResult.REFUSED;
            }
        }, io);
    }

    private Map<String, String> fromWorldFile(UUID player) throws SQLException {
        Path file = host.playerFile(player);
        if (file == null || !Files.exists(file)) return Map.of();
        try {
            return PlayerDataCodec.fromPlayerFile(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (IOException | RuntimeException e) {
            // Keep them out: their data may well be in there.
            throw new SQLException("Couldn't read a player file: " + e.getClass().getSimpleName());
        }
    }

    /** The second half, on the server thread, with the task's result: they may come in. */
    public void loginLoaded(UUID player, Map<String, String> rows) {
        loaded.put(player, rows);
    }

    /** Right after their world file was read, before anything can read their data: puts it on them. */
    public void applyOnJoin(ServerPlayer player) {
        Map<String, String> rows = loaded.remove(player.getUUID());
        if (rows == null) {
            Kegare.LOGGER.error("{} came in with nothing loaded for them", player.getGameProfile().getName());
            return;
        }
        try {
            PlayerDataCodec.apply(player, rows, host.registries());
        } catch (PlayerDataCodec.DecodeException e) {
            Kegare.LOGGER.error("Keeping {} out: {}", player.getGameProfile().getName(), e.getMessage());
            return;
        }
        if (!rows.isEmpty() && !MaliceService.get(player).isBorn()) {
            Kegare.LOGGER.error("Keeping {} out: they have data but it isn't a birth's", player.getGameProfile().getName());
            return;
        }
        admitted.add(player.getUUID());
        // Hashed as the next snapshot will see them, not as the database printed them, or a rejoin would count as a change.
        Map<String, Long> hashes = new HashMap<>();
        PlayerDataCodec.snapshot(player, host.registries()).forEach((attachment, json) -> hashes.put(attachment, hash(json)));
        lastSaved.put(player.getUUID(), hashes);
    }

    /**
     * Is this player's real data on them? If not they're sent away, and nothing may roll for them or save them: what's in the database is theirs,
     * and it's safe as long as nothing writes over it. Called before the login handling that would roll a birth.
     */
    public boolean admit(ServerPlayer player) {
        if (admitted.contains(player.getUUID())) return true;
        player.connection.disconnect(Component.translatable(UNREACHABLE));
        return false;
    }

    // ---------------------------------------------------------------- saving

    /** Takes a snapshot of one player's saved attachments, and queues what changed. Server thread. Only ever someone whose real data is on them. */
    public void snapshot(ServerPlayer player) {
        if (!admitted.contains(player.getUUID())) return;
        Map<String, String> now = PlayerDataCodec.snapshot(player, host.registries());
        Map<String, Long> hashes = lastSaved.computeIfAbsent(player.getUUID(), p -> new HashMap<>());
        Map<String, String> changes = new HashMap<>();
        for (Map.Entry<String, String> entry : now.entrySet()) {
            long hash = hash(entry.getValue());
            Long before = hashes.put(entry.getKey(), hash);
            if (before == null || before != hash) changes.put(entry.getKey(), entry.getValue());
        }
        for (String attachment : List.copyOf(hashes.keySet())) {
            if (!now.containsKey(attachment)) { // gone (a death clears some): its row goes too
                hashes.remove(attachment);
                changes.put(attachment, null);
            }
        }
        if (!changes.isEmpty()) queue.offer(player.getUUID(), changes, false);
    }

    /** They've left: everything goes in, and the server stops watching them. */
    public void logout(ServerPlayer player) {
        snapshot(player);
        if (admitted.remove(player.getUUID())) queue.offer(player.getUUID(), Map.of(), true);
        lastSaved.remove(player.getUUID());
        loaded.remove(player.getUUID());
    }

    /** Is it this player's turn to be checked this tick? Spread out, so a full server doesn't do everyone in one tick. */
    public boolean dueThisTick(UUID player, long tick) {
        long interval = config.saveIntervalSeconds() * 20L;
        return Math.floorMod(player.hashCode(), interval) == tick % interval;
    }

    private static long hash(String json) {
        long hash = 0xcbf29ce484222325L; // FNV-1a
        for (int i = 0; i < json.length(); i++) {
            hash ^= json.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private void writeLoop() {
        long backoff = 1000;
        while (running) {
            try {
                var next = queue.take();
                if (next == null) {
                    queue.awaitWork(1000);
                    continue;
                }
                try {
                    writeBatch(next.getKey(), next.getValue());
                    backoff = 1000;
                    markUp();
                } catch (SQLException | RuntimeException e) {
                    queue.putBack(next.getKey(), next.getValue());
                    markDown(e);
                    Thread.sleep(backoff);
                    backoff = Math.min(MAX_BACKOFF_MILLIS, backoff * 2);
                }
            } catch (InterruptedException e) {
                if (!running) return;
            }
        }
    }

    /** One player's batch is one transaction. */
    private void writeBatch(UUID player, SaveQueue.Batch batch) throws SQLException {
        pool.transaction(connection -> {
            PlayerStore.write(connection, player, batch.changes);
            return null;
        });
        writes.incrementAndGet();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- the world's files

    /** A player's file is about to be written. With storage on, Kegare's keys don't go in it. */
    public static void beforePlayerFileWritten(CompoundTag file) {
        if (isOn()) PlayerDataCodec.stripFromPlayerFile(file);
    }

    /** The real server behind the storage. */
    private record ServerHost(MinecraftServer server) implements Host {
        @Override
        public void onServerThread(Runnable task) {
            server.execute(task);
        }

        @Override
        public void tellOps(String langKey) {
            server.execute(() -> {
                Component message = Component.translatable(langKey).withStyle(ChatFormatting.YELLOW);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.hasPermissions(2)) player.sendSystemMessage(message);
                }
            });
        }

        @Override
        public HolderLookup.Provider registries() {
            return server.registryAccess();
        }

        @Override
        public Path playerFile(UUID player) {
            return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat");
        }
    }
}
