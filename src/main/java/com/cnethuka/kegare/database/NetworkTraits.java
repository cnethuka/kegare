package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TransferRules;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Unique traits across a network (DESIGN §3.33). The game keeps asking {@link UniqueTraitSavedData}, which answers from memory and never waits; what
 * changes goes to Postgres in order on the writer thread, and every {@code pollSeconds} what's there comes back. The trait is the primary key, so the
 * database itself refuses a second bearer, even if two servers race. Succession runs on one server at a time, under an advisory lock, and picks its
 * heir from everyone online across the network; whichever server holds them applies it, and the broadcast reaches every server and names no one.
 */
public final class NetworkTraits implements UniqueTraitSavedData.Backend {
    private static final long SUCCESSION_LOCK = 0x6B65676172650002L;
    private static final int EVENT_HOURS = 1;

    /** One change to the record, in order. */
    record Op(String kind, @Nullable String trait, @Nullable UUID player, @Nullable UUID killer, long dueMillis) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("kind", kind);
            if (trait != null) json.addProperty("trait", trait);
            if (player != null) json.addProperty("player", player.toString());
            if (killer != null) json.addProperty("killer", killer.toString());
            json.addProperty("due", dueMillis);
            return json;
        }

        static Op fromJson(JsonObject json) {
            return new Op(json.get("kind").getAsString(), json.has("trait") ? json.get("trait").getAsString() : null,
                    json.has("player") ? UUID.fromString(json.get("player").getAsString()) : null,
                    json.has("killer") ? UUID.fromString(json.get("killer").getAsString()) : null, json.get("due").getAsLong());
        }
    }

    /** What the database holds, read off the server thread and applied on it. */
    record Snapshot(Map<ResourceLocation, UUID> bearers, Map<ResourceLocation, UniqueTraitSavedData.Pending> pending, Map<UUID, Set<ResourceLocation>> revoked,
                    Map<UUID, Long> seen, Set<UUID> online) {}

    /** What happened on the network, in order. */
    record Event(long id, String kind, ResourceLocation trait, @Nullable UUID player) {}

    /** What the storage does with what comes back: on the server thread. */
    interface Applier {
        /** A claim the database refused: someone else has it. */
        void refused(ResourceLocation trait, UUID player);

        void event(Event event);

        /** The record's been refreshed: anyone with a revocation waiting? */
        void refreshed();
    }

    private final UniqueTraitSavedData view = new UniqueTraitSavedData(this);
    private final ConcurrentLinkedQueue<Op> ops = new ConcurrentLinkedQueue<>();
    /** Bumped on every change made here, so a refresh read before it isn't laid over it. */
    private final AtomicLong changes = new AtomicLong();
    private final int staleSeconds;
    private long lastEvent = -1;

    NetworkTraits(int staleSeconds) {
        this.staleSeconds = staleSeconds;
    }

    public UniqueTraitSavedData view() {
        return view;
    }

    // ---------------------------------------------------------------- what the game changes (server thread)

    private void add(Op op) {
        changes.incrementAndGet();
        ops.add(op);
    }

    @Override
    public void claimed(ResourceLocation trait, UUID player, boolean force) {
        add(new Op(force ? "force_claim" : "claim", trait.toString(), player, null, 0));
    }

    @Override
    public void released(ResourceLocation trait, UUID player) {
        add(new Op("release", trait.toString(), player, null, 0));
    }

    @Override
    public void succession(ResourceLocation trait, UniqueTraitSavedData.Pending pending) {
        add(new Op("succession", trait.toString(), pending.previousBearer(), pending.killer().orElse(null), pending.dueTick() * 50));
    }

    @Override
    public void revoked(UUID player, ResourceLocation trait) {
        add(new Op("revoke", trait.toString(), player, null, 0));
    }

    @Override
    public void revocationsTaken(UUID player) {
        add(new Op("revocations_taken", null, player, null, 0));
    }

    @Override
    public void seen(UUID player, long epochMillis) {
        add(new Op("seen", null, player, null, epochMillis));
    }

    boolean hasOps() {
        return !ops.isEmpty();
    }

    List<Op> opsCopy() {
        return new ArrayList<>(ops);
    }

    void addAll(List<Op> replay) {
        ops.addAll(replay);
    }

    // ---------------------------------------------------------------- writing them (the writer thread)

    /** Writes what's waiting, oldest first; what fails stays at the head. Returns false if there's nothing to do. */
    void flush(ConnectionPool pool, Applier applier) throws SQLException {
        Op op;
        while ((op = ops.peek()) != null) {
            Op next = op;
            Optional<UUID> refused = pool.transaction(connection -> apply(connection, next));
            ops.poll();
            refused.ifPresent(player -> applier.refused(ResourceLocation.parse(next.trait()), player));
        }
    }

    /** One change. If the database refuses a claim because someone else has the trait, says who was refused. */
    private static Optional<UUID> apply(Connection connection, Op op) throws SQLException {
        switch (op.kind()) {
            case "claim", "force_claim" -> {
                if (op.kind().equals("force_claim")) deletePending(connection, op.trait());
                else if (pendingExists(connection, op.trait())) return Optional.of(op.player());
                return insertBearer(connection, op.trait(), op.player()) || op.player().equals(bearerOf(connection, op.trait())) ? Optional.empty() : Optional.of(op.player());
            }
            case "release" -> {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.unique_trait WHERE trait = ? AND bearer = ?")) {
                    delete.setString(1, op.trait());
                    delete.setObject(2, op.player());
                    delete.executeUpdate();
                }
            }
            case "succession" -> {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO kegare.trait_pending (trait, previous, killer, due_at) VALUES (?, ?, ?, ?) ON CONFLICT (trait) DO NOTHING""")) {
                    insert.setString(1, op.trait());
                    insert.setObject(2, op.player());
                    insert.setObject(3, op.killer());
                    insert.setTimestamp(4, new Timestamp(op.dueMillis()));
                    insert.executeUpdate();
                }
            }
            case "revoke" -> {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kegare.trait_revoked (player, trait) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                    insert.setObject(1, op.player());
                    insert.setString(2, op.trait());
                    insert.executeUpdate();
                }
            }
            case "revocations_taken" -> {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.trait_revoked WHERE player = ?")) {
                    delete.setObject(1, op.player());
                    delete.executeUpdate();
                }
            }
            case "seen" -> {
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO kegare.player_seen (player, seen_at) VALUES (?, ?)
                        ON CONFLICT (player) DO UPDATE SET seen_at = GREATEST(kegare.player_seen.seen_at, EXCLUDED.seen_at)""")) {
                    upsert.setObject(1, op.player());
                    upsert.setTimestamp(2, new Timestamp(op.dueMillis()));
                    upsert.executeUpdate();
                }
            }
            default -> throw new SQLException("Unknown trait change: " + op.kind());
        }
        return Optional.empty();
    }

    private static boolean insertBearer(Connection connection, String trait, UUID player) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kegare.unique_trait (trait, bearer) VALUES (?, ?) ON CONFLICT (trait) DO NOTHING")) {
            insert.setString(1, trait);
            insert.setObject(2, player);
            return insert.executeUpdate() == 1;
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

    private static boolean pendingExists(Connection connection, String trait) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM kegare.trait_pending WHERE trait = ?")) {
            select.setString(1, trait);
            try (ResultSet result = select.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void deletePending(Connection connection, String trait) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.trait_pending WHERE trait = ?")) {
            delete.setString(1, trait);
            delete.executeUpdate();
        }
    }

    /**
     * A new player's unique traits are rolled here, off the server thread and before they enter, so the database has said yes before anything shows them
     * they won. A trait waiting for an heir can't be won. Returns what they won.
     */
    Set<ResourceLocation> preRollBirth(Connection connection, UUID player) throws SQLException {
        Set<ResourceLocation> won = new HashSet<>();
        for (Trait trait : KegareRegistries.TRAIT) {
            if (!trait.isUnique() || trait.birthChance() <= 0 || ThreadLocalRandom.current().nextDouble() >= trait.birthChance()) continue;
            if (pendingExists(connection, trait.id().toString())) continue;
            if (insertBearer(connection, trait.id().toString(), player)) won.add(trait.id());
        }
        return won;
    }

    // ---------------------------------------------------------------- the network's turn (its own thread)

    /** Succession, for whichever server gets the lock. */
    void runSuccession(ConnectionPool pool) throws SQLException {
        pool.transaction(connection -> {
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
                lock.setLong(1, SUCCESSION_LOCK);
                try (ResultSet result = lock.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) return null; // another server's doing it
                }
            }
            for (DuePending due : duePending(connection)) {
                Trait trait = KegareRegistries.TRAIT.get(due.trait());
                if (trait == null) continue;
                List<TransferRules.Candidate> online = online(connection, trait.requiresMalice());
                Optional<UUID> heir = TransferRules.pickHeir(trait.successionMode(), online, due.previous(), due.killer(), ThreadLocalRandom.current()::nextInt);
                if (heir.isEmpty()) continue; // nobody eligible yet: the next time someone is
                deletePending(connection, due.trait().toString());
                if (!insertBearer(connection, due.trait().toString(), heir.get())) continue;
                event(connection, "grant", due.trait(), heir.get());
                event(connection, "broadcast", due.trait(), null);
                Kegare.LOGGER.info("{} passed on across the network", due.trait());
            }
            return null;
        });
    }

    private record DuePending(ResourceLocation trait, UUID previous, @Nullable UUID killer) {}

    private static List<DuePending> duePending(Connection connection) throws SQLException {
        List<DuePending> out = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT trait, previous, killer FROM kegare.trait_pending WHERE due_at <= now()");
             ResultSet result = select.executeQuery()) {
            while (result.next()) {
                ResourceLocation trait = ResourceLocation.tryParse(result.getString(1));
                if (trait != null) out.add(new DuePending(trait, result.getObject(2, UUID.class), result.getObject(3, UUID.class)));
            }
        }
        return out;
    }

    /** Everyone online across the network: whoever holds a lease that's alive. */
    private List<TransferRules.Candidate> online(Connection connection, boolean needsMalice) throws SQLException {
        List<TransferRules.Candidate> out = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT l.player, coalesce((d.data->>'severed')::boolean, false)
                FROM kegare.player_lease l
                LEFT JOIN kegare.player_data d ON d.player = l.player AND d.attachment = 'kegare:malice'
                WHERE l.heartbeat > now() - make_interval(secs => ?)""")) {
            select.setInt(1, staleSeconds);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) out.add(new TransferRules.Candidate(result.getObject(1, UUID.class), needsMalice && result.getBoolean(2)));
            }
        }
        return out;
    }

    private static void event(Connection connection, String kind, ResourceLocation trait, @Nullable UUID player) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kegare.network_event (kind, trait, player) VALUES (?, ?, ?)")) {
            insert.setString(1, kind);
            insert.setString(2, trait.toString());
            insert.setObject(3, player);
            insert.executeUpdate();
        }
    }

    /** What's happened since we last looked. Starts from now: what happened before this server came up isn't its to act on. */
    List<Event> newEvents(ConnectionPool pool) throws SQLException {
        return pool.transaction(connection -> {
            if (lastEvent < 0) {
                try (PreparedStatement select = connection.prepareStatement("SELECT coalesce(max(id), 0) FROM kegare.network_event");
                     ResultSet result = select.executeQuery()) {
                    result.next();
                    lastEvent = result.getLong(1);
                }
                return List.of();
            }
            List<Event> out = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT id, kind, trait, player FROM kegare.network_event WHERE id > ? ORDER BY id")) {
                select.setLong(1, lastEvent);
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        ResourceLocation trait = ResourceLocation.tryParse(result.getString(3));
                        lastEvent = result.getLong(1);
                        if (trait != null) out.add(new Event(lastEvent, result.getString(2), trait, result.getObject(4, UUID.class)));
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM kegare.network_event WHERE created_at < now() - make_interval(hours => ?)")) {
                delete.setInt(1, EVENT_HOURS);
                delete.executeUpdate();
            }
            return out;
        });
    }

    long changeCount() {
        return changes.get();
    }

    Snapshot read(ConnectionPool pool) throws SQLException {
        return pool.transaction(connection -> {
            Map<ResourceLocation, UUID> bearers = new HashMap<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT trait, bearer FROM kegare.unique_trait"); ResultSet r = select.executeQuery()) {
                while (r.next()) {
                    ResourceLocation trait = ResourceLocation.tryParse(r.getString(1));
                    if (trait != null) bearers.put(trait, r.getObject(2, UUID.class));
                }
            }
            Map<ResourceLocation, UniqueTraitSavedData.Pending> pending = new LinkedHashMap<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT trait, previous, killer, due_at FROM kegare.trait_pending"); ResultSet r = select.executeQuery()) {
                while (r.next()) {
                    ResourceLocation trait = ResourceLocation.tryParse(r.getString(1));
                    if (trait != null) pending.put(trait, new UniqueTraitSavedData.Pending(r.getObject(2, UUID.class), Optional.ofNullable(r.getObject(3, UUID.class)), r.getTimestamp(4).getTime() / 50));
                }
            }
            Map<UUID, Set<ResourceLocation>> revoked = new HashMap<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT player, trait FROM kegare.trait_revoked"); ResultSet r = select.executeQuery()) {
                while (r.next()) {
                    ResourceLocation trait = ResourceLocation.tryParse(r.getString(2));
                    if (trait != null) revoked.computeIfAbsent(r.getObject(1, UUID.class), u -> new HashSet<>()).add(trait);
                }
            }
            Map<UUID, Long> seen = new HashMap<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT s.player, s.seen_at FROM kegare.player_seen s JOIN kegare.unique_trait t ON t.bearer = s.player"); ResultSet r = select.executeQuery()) {
                while (r.next()) seen.put(r.getObject(1, UUID.class), r.getTimestamp(2).getTime());
            }
            Set<UUID> online = new HashSet<>();
            try (PreparedStatement select = connection.prepareStatement("SELECT player FROM kegare.player_lease WHERE heartbeat > now() - make_interval(secs => ?)")) {
                select.setInt(1, staleSeconds);
                try (ResultSet r = select.executeQuery()) {
                    while (r.next()) online.add(r.getObject(1, UUID.class));
                }
            }
            return new Snapshot(bearers, pending, revoked, seen, online);
        });
    }

    /** Lays what the database holds over the memory, unless something's changed here since it was read. Server thread. */
    boolean applyIfUnchanged(Snapshot snapshot, long changesWhenRead) {
        if (changes.get() != changesWhenRead || hasOps()) return false;
        view.replaceAll(snapshot.bearers(), snapshot.pending(), snapshot.revoked(), snapshot.seen(), snapshot.online());
        return true;
    }
}
