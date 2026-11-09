package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.rmt.RmtFatigue;
import com.cnethuka.kegare.rmt.RmtOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The zone (DESIGN §3.28): after a Black Spark the sorcerer is at 120% of their potential for {@code zoneSeconds}, where the next one comes
 * easier. Their damage (every kind) and their RMT healing are ×1.2, costs unchanged; their RMT ignores injury fatigue, and each Black Spark
 * in the zone takes some of it away for the rest of the fight; the window widens ({@code zoneWindow}). Each Black Spark in it starts it
 * over. A chain counts Black Sparks landed while the zone from the one before is still up, and ends with the zone.
 */
public final class Zone {
    static final class State {
        long until;
        int ticks;
        int chain;
        /** Of the chain, the Black Sparks on players (the advancements count only those). */
        int playerChain;
        /** The tick they came into it: the hit that put them there isn't one of the zone's. */
        long enteredAt = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Zone() {}

    public static void init() {
        // In the zone RMT ignores injury fatigue and heals x1.2.
        RmtOutput.addModifier((player, output) -> {
            if (!in(player)) return output;
            double fatigue = RmtFatigue.of(player);
            double unfatigued = fatigue < 0.999 ? output / (1.0 - fatigue) : RmtOutput.base(player);
            return unfatigued * FistsConfig.ZONE_RMT.get();
        });
        BlackSpark.listenFirst(Zone::onSpark);
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static boolean in(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && player.level().getGameTime() < state.until;
    }

    /** Black Sparks in the current chain: 1 for the one that started the zone, 0 out of it. */
    public static int chain(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null || !in(player) ? 0 : state.chain;
    }

    /** Ticks of the zone left. */
    public static int ticksLeft(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? 0 : (int) Math.max(0, state.until - player.level().getGameTime());
    }

    /** Black Sparks on players in the current chain: what the advancements count. */
    public static int playerChain(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null || !in(player) ? 0 : state.playerChain;
    }

    /** A Black Spark landed: into the zone, or the zone started over and the chain one longer, with fatigue taken away. */
    static void onSpark(ServerPlayer player, net.minecraft.world.entity.LivingEntity target) {
        boolean already = in(player);
        State state = state(player);
        state.chain = already ? state.chain + 1 : 1;
        int onPlayers = already ? state.playerChain : 0;
        state.playerChain = target instanceof ServerPlayer ? onPlayers + 1 : onPlayers;
        if (!already) state.enteredAt = player.level().getGameTime();
        if (already) RmtFatigue.restore(player, FistsConfig.RESTORE_PER_SPARK.get());
        start(player, (int) Math.round(FistsConfig.ZONE_SECONDS.get() * 20));
    }

    /** In the zone for {@code ticks} from now ({@code /kegare debug spark zone} too); 0 ends it. */
    public static void start(ServerPlayer player, int ticks) {
        State state = state(player);
        if (ticks <= 0) {
            end(player);
            return;
        }
        state.until = player.level().getGameTime() + ticks;
        state.ticks = ticks;
        RmtFatigue.ignore(player, ticks);
        sync(player);
    }

    static void end(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null) sync(player);
    }

    /** Every tick: the zone runs out, and the chain with it. */
    static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null && player.level().getGameTime() >= state.until) end(player);
    }

    /** All their damage, every kind, while they're in it: after everything else that adds to a hit. */
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity() && in(attacker)
                && STATES.get(attacker.getUUID()).enteredAt != attacker.level().getGameTime()) {
            event.setAmount(event.getAmount() * FistsConfig.ZONE_DAMAGE.get().floatValue());
        }
    }

    private static void sync(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        PacketDistributor.sendToPlayer(player, state == null ? new ZonePayload(0, 0) : new ZonePayload(state.until, state.ticks));
        FistsTell.sync(player);
    }

    static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void clear() {
        STATES.clear();
    }
}
