package com.cnethuka.kegare.progression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The live fights: at most one per pair of players, ended by {@value #TICK_INTERVAL} tick checks for the timeout, or at
 * once by a death or a logout. Runtime only (a fight never outlives a logout or a restart). Damage to yourself, to your
 * own puppets or shell, and damage with no attacker never reach here ({@link Attribution}).
 */
public final class FightTracker {
    /** Told about every fight that ends, once per player. */
    @FunctionalInterface
    public interface Listener {
        void onFightEnded(FightReport report);
    }

    /** Told when a fight starts, with each player in turn as {@code player}. */
    @FunctionalInterface
    public interface StartListener {
        void onFightStarted(ServerPlayer player, ServerPlayer opponent);
    }

    private static final int TICK_INTERVAL = 20;

    private record Pair(UUID low, UUID high) {
        static Pair of(UUID a, UUID b) {
            return a.compareTo(b) < 0 ? new Pair(a, b) : new Pair(b, a);
        }

        boolean involves(UUID player) {
            return low.equals(player) || high.equals(player);
        }
    }

    private record Live(Fight fight, ServerPlayer a, ServerPlayer b) {
        ServerPlayer other(UUID player) {
            return a.getUUID().equals(player) ? b : a;
        }
    }

    private record Spend(long tick, double baseMalice) {}

    private record OpeningMove(UUID player, String move, long tick) {}

    private static final Map<Pair, Live> FIGHTS = new HashMap<>();
    /** Malice spent while not in a fight, which the fight it opens picks up. */
    private static final Map<UUID, List<Spend>> OPENING = new HashMap<>();
    /** Which fight a cast with no target of its own goes to: the one last active. */
    private static final Map<UUID, Pair> LAST_ACTIVE = new HashMap<>();
    /** Melee moves against someone before a fight with them is on, which the fight picks up. */
    private static final Map<Pair, List<OpeningMove>> OPENING_MOVES = new HashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<StartListener> START_LISTENERS = new CopyOnWriteArrayList<>();

    private FightTracker() {}

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void addStartListener(StartListener listener) {
        START_LISTENERS.add(listener);
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** A player damaged another player. Starts their fight if there isn't one. */
    public static void damage(ServerPlayer attacker, ServerPlayer victim, double amount) {
        if (attacker == victim || amount <= 0) return;
        Live live = open(attacker, victim);
        live.fight.damage(attacker.getUUID(), amount, now(attacker.server));
        LAST_ACTIVE.put(attacker.getUUID(), Pair.of(attacker.getUUID(), victim.getUUID()));
        LAST_ACTIVE.put(victim.getUUID(), Pair.of(attacker.getUUID(), victim.getUUID()));
    }

    /** One of {@code caster}'s abilities landed on {@code target}. A landing with no damage (a swap) opens a fight too. */
    public static void landed(ServerPlayer caster, ServerPlayer target, String ability, boolean special) {
        if (caster == target || !target.isAlive() || target.hasDisconnected()) return;
        Live live = open(caster, target);
        live.fight.landed(caster.getUUID(), ability, special, now(caster.server));
        LAST_ACTIVE.put(caster.getUUID(), Pair.of(caster.getUUID(), target.getUUID()));
    }

    /** A plain hit, recorded as the melee move it was. Only a Severed player's Assessment reads these. */
    public static void melee(ServerPlayer attacker, ServerPlayer victim, String move) {
        if (attacker == victim) return;
        Live live = FIGHTS.get(Pair.of(attacker.getUUID(), victim.getUUID()));
        if (live != null) live.fight.melee(attacker.getUUID(), move, now(attacker.server));
    }

    /**
     * A melee move that isn't a hit, made against {@code opponent}: a perfect parry or a clean dodge. It can come first, with no fight on
     * yet, so like opening Malice it waits for the fight. It never opens one: only damage and landings do, so it changes nothing about
     * what a fight is or which ones qualify. Only a Severed player's Assessment reads these.
     */
    public static void meleeMove(ServerPlayer player, ServerPlayer opponent, String move) {
        if (player == opponent) return;
        Pair key = Pair.of(player.getUUID(), opponent.getUUID());
        long now = now(player.server);
        Live live = FIGHTS.get(key);
        if (live != null) {
            live.fight.melee(player.getUUID(), move, now);
            return;
        }
        List<OpeningMove> pending = OPENING_MOVES.computeIfAbsent(key, k -> new ArrayList<>());
        long grace = ProgressionConfig.ticks(ProgressionConfig.OPENING_CAST_SECONDS);
        pending.removeIf(m -> now - m.tick > grace);
        pending.add(new OpeningMove(player.getUUID(), move, now));
    }

    /** Malice spent on a technique cast, at base cost. Goes to the fight last active, or waits for the next one. */
    public static void spend(ServerPlayer player, double baseMalice) {
        if (baseMalice <= 0) return;
        Pair last = LAST_ACTIVE.get(player.getUUID());
        Live live = last == null ? null : FIGHTS.get(last);
        if (live != null) {
            live.fight.spend(player.getUUID(), baseMalice);
            return;
        }
        long now = now(player.server);
        List<Spend> pending = OPENING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        long grace = ProgressionConfig.ticks(ProgressionConfig.OPENING_CAST_SECONDS);
        pending.removeIf(s -> now - s.tick > grace);
        pending.add(new Spend(now, baseMalice));
    }

    private static Live open(ServerPlayer a, ServerPlayer b) {
        Pair key = Pair.of(a.getUUID(), b.getUUID());
        Live live = FIGHTS.get(key);
        if (live != null) return live;
        long now = now(a.server);
        Fight fight = new Fight(a.getUUID(), b.getUUID(), now);
        live = new Live(fight, a, b);
        FIGHTS.put(key, live);
        pickUpOpeningSpend(fight, a, now);
        pickUpOpeningSpend(fight, b, now);
        pickUpOpeningMoves(fight, key, now);
        LAST_ACTIVE.putIfAbsent(a.getUUID(), key);
        LAST_ACTIVE.putIfAbsent(b.getUUID(), key);
        for (StartListener listener : START_LISTENERS) {
            listener.onFightStarted(a, b);
            listener.onFightStarted(b, a);
        }
        return live;
    }

    private static void pickUpOpeningSpend(Fight fight, ServerPlayer player, long now) {
        List<Spend> pending = OPENING.remove(player.getUUID());
        if (pending == null) return;
        long grace = ProgressionConfig.ticks(ProgressionConfig.OPENING_CAST_SECONDS);
        for (Spend spend : pending) {
            if (now - spend.tick <= grace) fight.spend(player.getUUID(), spend.baseMalice);
        }
    }

    private static void pickUpOpeningMoves(Fight fight, Pair key, long now) {
        List<OpeningMove> pending = OPENING_MOVES.remove(key);
        if (pending == null) return;
        long grace = ProgressionConfig.ticks(ProgressionConfig.OPENING_CAST_SECONDS);
        for (OpeningMove move : pending) {
            if (now - move.tick <= grace) fight.melee(move.player, move.move, move.tick);
        }
    }

    /** The fights this player is in right now. */
    public static List<Fight> of(ServerPlayer player) {
        List<Fight> out = new ArrayList<>();
        for (Live live : FIGHTS.values()) if (live.fight.involves(player.getUUID())) out.add(live.fight);
        return out;
    }

    /** Their fight, if they have one. */
    @Nullable
    public static Fight between(ServerPlayer a, ServerPlayer b) {
        Live live = FIGHTS.get(Pair.of(a.getUUID(), b.getUUID()));
        return live == null ? null : live.fight;
    }

    /** Every second: fights with no damage for the timeout are over. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK_INTERVAL == 0) endExpired(server);
    }

    public static void endExpired(MinecraftServer server) {
        if (FIGHTS.isEmpty()) return;
        long now = now(server);
        long timeout = ProgressionConfig.ticks(ProgressionConfig.FIGHT_TIMEOUT);
        for (Map.Entry<Pair, Live> e : List.copyOf(FIGHTS.entrySet())) {
            if (!e.getValue().fight.expired(now, timeout)) continue;
            FIGHTS.remove(e.getKey());
            report(e.getValue(), FightOutcome.SURVIVED, FightOutcome.SURVIVED);
        }
    }

    /** {@code dead} died: every fight of theirs ends now. Runs on the dying entity, before its data is copied. */
    public static void onDeath(ServerPlayer dead) {
        UUID id = dead.getUUID();
        // What they took from players across ALL their fights: the share a win is measured against.
        double takenFromPlayers = 0;
        for (Live live : FIGHTS.values()) {
            if (live.fight.involves(id)) takenFromPlayers += live.fight.opponentOf(id).dealt();
        }
        double winShare = ProgressionConfig.WIN_SHARE.get();
        for (Map.Entry<Pair, Live> e : List.copyOf(FIGHTS.entrySet())) {
            Live live = e.getValue();
            if (!live.fight.involves(id)) continue;
            FIGHTS.remove(e.getKey());
            FightOutcome other = Fight.outcomeOnOpponentDeath(live.fight.opponentOf(id).dealt(), takenFromPlayers, winShare);
            report(live, id, FightOutcome.LOSS, other);
        }
    }

    /** {@code gone} logged out: every fight of theirs ends now. */
    public static void onLogout(ServerPlayer gone) {
        UUID id = gone.getUUID();
        for (Map.Entry<Pair, Live> e : List.copyOf(FIGHTS.entrySet())) {
            Live live = e.getValue();
            if (!live.fight.involves(id)) continue;
            FIGHTS.remove(e.getKey());
            report(live, id, FightOutcome.LEFT, FightOutcome.SURVIVED);
        }
        OPENING.remove(id);
        OPENING_MOVES.keySet().removeIf(pair -> pair.involves(id));
        LAST_ACTIVE.remove(id);
    }

    private static void report(Live live, FightOutcome forA, FightOutcome forB) {
        report(live, live.a.getUUID(), forA, forB);
    }

    private static void report(Live live, UUID first, FightOutcome forFirst, FightOutcome forOther) {
        ServerPlayer one = live.a.getUUID().equals(first) ? live.a : live.b;
        ServerPlayer two = live.other(first);
        FightReport a = new FightReport(one, two, live.fight, forFirst);
        FightReport b = new FightReport(two, one, live.fight, forOther);
        for (Listener listener : LISTENERS) {
            listener.onFightEnded(a);
            listener.onFightEnded(b);
        }
    }

    public static void clear() {
        FIGHTS.clear();
        OPENING.clear();
        OPENING_MOVES.clear();
        LAST_ACTIVE.clear();
    }
}
