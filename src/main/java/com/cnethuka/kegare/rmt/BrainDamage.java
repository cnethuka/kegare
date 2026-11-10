package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The brain's limit (DESIGN §3.17): a saved meter per player, 0 to {@link #MAX} (attachment {@code kegare:brain}, kept on death). A full
 * repair gives the whole technique back, ultimate included, so every full repair costs the same whatever the Burnout's length: what a
 * full repair of {@code dominionBurnoutAssumed} costs. Removing a share of a Burnout, by repair or by Background RMT's shortening, costs
 * that share: {@code brainPerSecond} x dominionBurnoutAssumed / L per second for a Burnout of length L, times
 * (1 + brainEscalation x meter / 100), since the same tissue is destroyed again and each repair costs more than the last. Full, it
 * refuses: no more repair, and the Burnout runs its full length. Applies to every RMT user, Six Eyes included. It falls
 * {@code brainHealPerSecond} per second out of a fight; sleeping through a night clears it.
 */
public final class BrainDamage {
    public static final double MAX = 100.0;

    public static final class Data {
        public static final Codec<Data> CODEC = Codec.DOUBLE.xmap(Data::new, d -> d.meter);
        double meter;

        public Data() {}

        Data(double meter) {
            this.meter = Math.max(0, Math.min(MAX, meter));
        }
    }

    private BrainDamage() {}

    public static double get(Player player) {
        return player.hasData(KegareAttachments.BRAIN) ? player.getData(KegareAttachments.BRAIN).meter : 0.0;
    }

    public static boolean isFull(Player player) {
        return get(player) >= MAX - 1.0e-6;
    }

    /** Hook for Dominions (later): a full brain can't take what opening one would cost, so it refuses to open. */
    public static boolean refusesDominion(Player player) {
        return isFull(player);
    }

    /** Admin and tests. */
    public static void set(ServerPlayer player, double meter) {
        changeTo(player, player.getData(KegareAttachments.BRAIN), meter);
    }

    /** Sets it, and tells the owner's screen when the shown (rounded) value moves. */
    private static void changeTo(ServerPlayer player, Data data, double meter) {
        long before = Math.round(data.meter);
        data.meter = Math.max(0, Math.min(MAX, meter));
        if (Math.round(data.meter) != before) com.cnethuka.kegare.network.PlayerSync.markDirty(player);
    }

    /**
     * The meter's growth a second at an empty meter, for a Burnout {@code lengthSeconds} long: a second is 1/L of it, and the whole of any
     * Burnout costs what the whole of a {@code dominionBurnoutAssumed} one does.
     */
    static double rate(double lengthSeconds) {
        return RmtConfig.BRAIN_PER_SECOND.get() * RmtConfig.DOMINION_BURNOUT_ASSUMED.get() / Math.max(1.0e-3, lengthSeconds);
    }

    /**
     * {@code seconds} of a Burnout {@code lengthSeconds} long taken away: the meter grows, solving dm/ds = a (1 + e m / 100). Returns the
     * seconds it allowed: all of them, or only as many as fit before it's full (then it's full).
     */
    public static double take(ServerPlayer player, double seconds, double lengthSeconds) {
        if (seconds <= 0) return 0;
        Data data = player.getData(KegareAttachments.BRAIN);
        double[] after = grow(data.meter, seconds, lengthSeconds);
        changeTo(player, data, after[0]);
        return after[1];
    }

    /** The meter after taking {@code seconds} away from {@code meter}, and the seconds that fit: {meter, seconds}. */
    public static double[] grow(double meter, double seconds, double lengthSeconds) {
        double a = rate(lengthSeconds), e = RmtConfig.BRAIN_ESCALATION.get();
        if (meter >= MAX || a <= 0) return new double[] {Math.min(MAX, meter), a <= 0 ? seconds : 0};
        double end, fits;
        if (e <= 1.0e-9) {
            end = meter + a * seconds;
            fits = (MAX - meter) / a;
        } else {
            double k = MAX / e;
            end = (meter + k) * Math.exp(a * e * seconds / MAX) - k;
            fits = MAX / (a * e) * Math.log((MAX + k) / (meter + k));
        }
        if (end <= MAX) return new double[] {end, seconds};
        return new double[] {MAX, Math.max(0, fits)};
    }

    /** Every server tick: out of a fight, it heals a little. */
    static void tick(ServerPlayer player) {
        if (!player.hasData(KegareAttachments.BRAIN) || CombatHelper.isInCombat(player)) return;
        Data data = player.getData(KegareAttachments.BRAIN);
        if (data.meter > 0) changeTo(player, data, data.meter - RmtConfig.BRAIN_HEAL_PER_SECOND.get() / 20.0);
    }

    /** A night slept through: everyone who slept it wakes clear. */
    static void nightSlept(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping() && player.hasData(KegareAttachments.BRAIN)) changeTo(player, player.getData(KegareAttachments.BRAIN), 0);
        }
    }

    /** How many full repairs of a Burnout {@code lengthSeconds} long fit from an empty meter (the anchor DESIGN reports: the same for any length). */
    public static int repairsThatFit(double lengthSeconds) {
        double meter = 0;
        for (int n = 0; n < 10_000; n++) {
            double[] after = grow(meter, lengthSeconds, lengthSeconds);
            if (after[1] < lengthSeconds - 1.0e-9) return n;
            meter = after[0];
        }
        return 10_000;
    }
}
