package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.trait.TraitSuccession;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

        /** Sends someone away, if they're here. */
        void kick(UUID player, String langKey);

        /** The database refused a claim: someone else has the trait. */
        void claimRefused(ResourceLocation trait, UUID player);

        /** Something happened on the network: a trait passed on, or the word of it. */
        void networkEvent(String kind, ResourceLocation trait, @Nullable UUID player);

        /** The trait record's fresh: apply any revocations waiting for players here. */
        void traitsRefreshed();
    }

    /**
     * How a login went: the player's rows and their lease's token, or why they're to be kept out.
     *
     * @param preRolledBirth nothing was anywhere for them: this is a first join, and these unique traits were already rolled and won
     */
    public record LoginResult(boolean admitted, @Nullable String refusal, Map<String, String> rows, @Nullable UUID token, @Nullable Set<ResourceLocation> preRolledBirth) {
        static final LoginResult UNREACHABLE_NOW = new LoginResult(false, UNREACHABLE, Map.of(), null, null);
        static final LoginResult HELD_ELSEWHERE = new LoginResult(false, HELD, Map.of(), null, null);
    }

    public static final String UNREACHABLE = "disconnect.kegare.db.unreachable";
    public static final String HELD = "disconnect.kegare.db.held";
    public static final String LEASE_LOST = "disconnect.kegare.db.lease_lost";
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    @Nullable
    private static volatile KegareDatabase current;

    final DatabaseConfig config;
    final ConnectionPool pool;
    final SaveQueue queue = new SaveQueue();
    final NetworkTraits traits;
    private final Path configDir;
    private final Host host;
    private final ExecutorService io;
    private final ScheduledExecutorService scheduler;
    private final Thread writer;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private volatile boolean running = true;
    private volatile boolean down;
    private volatile boolean refusedStart;
    private volatile int schemaVersion;
    /** Transactions written, for the tests: an unchanged player must cost none. */
    final AtomicLong writes = new AtomicLong();
    /** Batches dropped because this server no longer owned the player. */
    final AtomicLong refusedWrites = new AtomicLong();

    // Server thread only, from here down.
    /** What's been loaded for players on their way in. */
    private final Map<UUID, Loaded> loaded = new HashMap<>();
    /** First joins whose unique traits were rolled before they came in. */
    private final Set<UUID> preRolled = new HashSet<>();
    /** Whose data is really on them: only these are ever saved. */
    private final Set<UUID> admitted = new HashSet<>();
    private final Map<UUID, Map<String, Long>> lastSaved = new HashMap<>();

    /** Everything this server holds a lease on: player to token. Any thread. */
    private final Map<UUID, UUID> leases = new ConcurrentHashMap<>();

    private record Loaded(Map<String, String> rows, UUID token, long at) {}

    /** A client gets this long between its data loading and its entering the world. */
    private static final long LOADED_EXPIRES_MILLIS = 60_000;

    public KegareDatabase(DatabaseConfig config, Path configDir, Host host) {
        this.config = config;
        this.traits = new NetworkTraits(config.leaseStaleSeconds());
        this.pool = new ConnectionPool(config);
        this.configDir = configDir;
        this.host = host;
        this.io = Executors.newFixedThreadPool(Math.max(2, config.poolSize()), runnable -> {
            Thread thread = new Thread(runnable, "Kegare database");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Kegare database network");
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
                SpillFile.Contents spilled = SpillFile.read(configDir);
                for (var entry : spilled.batches().entrySet()) writeBatch(entry.getKey(), entry.getValue());
                traits.addAll(spilled.traitOps());
                traits.flush(pool, applier());
                SpillFile.delete(configDir);
                if (!spilled.batches().isEmpty()) Kegare.LOGGER.info("Kegare's storage replayed {} players' unsaved data from the last run", spilled.batches().size());
                pool.transaction(connection -> {
                    LeaseStore.releaseAll(connection, config.serverId()); // nobody's on a server that's only now starting
                    return null;
                });
                pollNetwork();
                scheduler.scheduleWithFixedDelay(this::heartbeat, config.heartbeatSeconds(), config.heartbeatSeconds(), TimeUnit.SECONDS);
                scheduler.scheduleWithFixedDelay(this::pollNetworkSafely, config.pollSeconds(), config.pollSeconds(), TimeUnit.SECONDS);
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
        while ((queue.size() > 0 || traits.hasOps()) && System.currentTimeMillis() < deadline) sleep(50);
        running = false;
        scheduler.shutdownNow();
        writer.interrupt();
        try {
            writer.join(TimeUnit.SECONDS.toMillis(config.socketTimeoutSeconds()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<UUID, SaveQueue.Batch> left = queue.copy();
        List<NetworkTraits.Op> opsLeft = traits.opsCopy();
        if (!left.isEmpty() || !opsLeft.isEmpty()) {
            try {
                SpillFile.write(configDir, left, opsLeft);
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
                if (refusedStart) return LoginResult.UNREACHABLE_NOW;
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.leaseWaitSeconds());
                while (queue.has(player)) { // their last logout here is still going in
                    if (System.currentTimeMillis() > deadline) return LoginResult.UNREACHABLE_NOW;
                    Thread.sleep(50);
                }
                while (true) {
                    LoginResult result = pool.transaction(connection -> {
                        UUID token = LeaseStore.acquire(connection, player, config.serverId(), config.leaseStaleSeconds());
                        if (token == null) return LoginResult.HELD_ELSEWHERE;
                        Map<String, String> found = PlayerStore.load(connection, player);
                        Set<ResourceLocation> won = null;
                        if (found.isEmpty()) {
                            found = fromWorldFile(player);
                            if (!found.isEmpty()) {
                                Kegare.LOGGER.info("Bringing {}'s Kegare data in from their world file", player);
                                PlayerStore.writeMissing(connection, player, found);
                            } else {
                                won = traits.preRollBirth(connection, player);
                            }
                        }
                        return new LoginResult(true, null, found, token, won);
                    });
                    if (result.admitted()) {
                        leases.put(player, result.token());
                        pollNetworkSafely(); // their traits are as the network has them when they come in
                        markUp();
                        return result;
                    }
                    if (System.currentTimeMillis() > deadline) return result; // another server's still got them
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return LoginResult.UNREACHABLE_NOW;
            } catch (Exception e) {
                markDown(e);
                return LoginResult.UNREACHABLE_NOW;
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
    public void loginLoaded(UUID player, LoginResult result) {
        loaded.put(player, new Loaded(result.rows(), result.token(), System.currentTimeMillis()));
        if (result.preRolledBirth() != null) preRolled.add(player);
    }

    /** Was this first join's unique trait roll done before they came in? Then the birth doesn't roll them again. */
    public boolean preRolledBirth(UUID player) {
        return preRolled.contains(player);
    }

    public void birthFinished(UUID player) {
        preRolled.remove(player);
    }

    /** Right after their world file was read, before anything can read their data: puts it on them. */
    public void applyOnJoin(ServerPlayer player) {
        Loaded entry = loaded.remove(player.getUUID());
        if (entry == null) {
            Kegare.LOGGER.error("{} came in with nothing loaded for them", player.getGameProfile().getName());
            return;
        }
        Map<String, String> rows = entry.rows();
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
        UUID token = leases.get(player.getUUID());
        if (!changes.isEmpty() && token != null) queue.offer(player.getUUID(), token, changes, false);
    }

    /** They've left: everything goes in, and the server stops watching them. */
    public void logout(ServerPlayer player) {
        snapshot(player);
        UUID token = leases.get(player.getUUID());
        // A refused player's lease goes too, with nothing written: what's in the database is safe, and now they can try again.
        if (token != null) queue.offer(player.getUUID(), token, Map.of(), true);
        admitted.remove(player.getUUID());
        lastSaved.remove(player.getUUID());
        loaded.remove(player.getUUID());
        preRolled.remove(player.getUUID());
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
                try {
                    if (traits.hasOps()) {
                        traits.flush(pool, applier());
                        backoff = 1000;
                        markUp();
                    }
                } catch (SQLException | RuntimeException e) {
                    markDown(e);
                    Thread.sleep(backoff);
                    backoff = Math.min(MAX_BACKOFF_MILLIS, backoff * 2);
                    continue;
                }
                var next = queue.take();
                if (next == null) {
                    queue.awaitWork(250);
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

    /**
     * One player's batch is one transaction, and carries the token: if this server no longer owns them it does nothing, so a stale server can't write
     * over newer data.
     */
    private void writeBatch(UUID player, SaveQueue.Batch batch) throws SQLException {
        boolean written = pool.transaction(connection -> {
            if (!LeaseStore.owns(connection, player, batch.token)) return false;
            PlayerStore.write(connection, player, batch.changes);
            if (batch.release) LeaseStore.release(connection, player, batch.token);
            return true;
        });
        if (written) {
            writes.incrementAndGet();
        } else {
            refusedWrites.incrementAndGet();
            Kegare.LOGGER.warn("Dropped a save for {}: this server no longer owns them", player);
            leaseLost(player, batch.token);
        }
        if (batch.release) leases.remove(player, batch.token);
    }

    // ---------------------------------------------------------------- the network

    private void heartbeat() {
        try {
            Map<UUID, UUID> held = pool.transaction(connection -> LeaseStore.heartbeat(connection, config.serverId()));
            markUp();
            for (Map.Entry<UUID, UUID> lease : leases.entrySet()) {
                if (!lease.getValue().equals(held.get(lease.getKey()))) leaseLost(lease.getKey(), lease.getValue());
            }
        } catch (SQLException | RuntimeException e) {
            markDown(e);
        }
    }

    /** Another server has them now: this one must not keep playing them, since nothing it saves will count. */
    private void leaseLost(UUID player, UUID token) {
        if (!leases.remove(player, token)) return;
        host.onServerThread(() -> {
            admitted.remove(player);
            host.kick(player, LEASE_LOST);
        });
    }

    private void pollNetworkSafely() {
        try {
            pollNetwork();
            markUp();
        } catch (SQLException | RuntimeException e) {
            markDown(e);
        }
    }

    private synchronized void pollNetwork() throws SQLException {
        traits.runSuccession(pool);
        List<NetworkTraits.Event> events = traits.newEvents(pool);
        long changesWhenRead = traits.changeCount();
        NetworkTraits.Snapshot snapshot = traits.read(pool);
        NetworkTraits.Applier applier = applier();
        host.onServerThread(() -> {
            if (traits.applyIfUnchanged(snapshot, changesWhenRead)) applier.refreshed();
            events.forEach(applier::event);
        });
    }

    private NetworkTraits.Applier applier() {
        return new NetworkTraits.Applier() {
            @Override
            public void refused(ResourceLocation trait, UUID player) {
                host.onServerThread(() -> host.claimRefused(trait, player));
            }

            @Override
            public void event(NetworkTraits.Event event) {
                host.networkEvent(event.kind(), event.trait(), event.player());
            }

            @Override
            public void refreshed() {
                host.traitsRefreshed();
            }
        };
    }

    /** The trait record, read and changed through memory, with Postgres behind it. */
    public com.cnethuka.kegare.trait.UniqueTraitSavedData traitRecord() {
        return traits.view();
    }

    /** A login that loaded and then never came in: lets go of what it held. Server thread, now and then. */
    public void expireLoaded() {
        long now = System.currentTimeMillis();
        for (var iterator = loaded.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (now - entry.getValue().at() < LOADED_EXPIRES_MILLIS) continue;
            iterator.remove();
            preRolled.remove(entry.getKey());
            queue.offer(entry.getKey(), entry.getValue().token(), Map.of(), true);
        }
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

        @Override
        public void kick(UUID player, String langKey) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) online.connection.disconnect(Component.translatable(langKey));
        }

        @Override
        public void claimRefused(ResourceLocation trait, UUID player) {
            TraitSuccession.claimRefused(server, trait, player);
        }

        @Override
        public void networkEvent(String kind, ResourceLocation trait, @Nullable UUID player) {
            TraitSuccession.networkEvent(server, kind, trait, player);
        }

        @Override
        public void traitsRefreshed() {
            TraitSuccession.networkRefreshed(server);
        }
    }
}
