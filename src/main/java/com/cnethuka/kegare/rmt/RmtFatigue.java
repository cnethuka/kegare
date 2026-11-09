package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.progression.FightTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Injury fatigue (DESIGN §3.17): the more damage you take in a fight, the weaker your Reverse Malice Technique heals, as injuries
 * piling up drag a user's output down in canon. It comes from damage TAKEN (what came off your health, from any source), never from
 * healing, and healing doesn't take it away. fatigue = {@code fatigueWeight} x (damage taken / max health), at most {@code maxFatigue},
 * and output is multiplied by (1 - fatigue), so it never stops RMT outright. Reset when their last fight ends (or, with no fight
 * between players, when their combat tag runs out), on death and on logout. Everyone has it. Later systems lift or pause it:
 * {@link #restore}, {@link #ignore} (Black Spark's zone).
 */
public final class RmtFatigue {
    private RmtFatigue() {}

    /** How much of their output injuries take away right now, 0 to {@code maxFatigue}. */
    public static double of(ServerPlayer player) {
        return Math.min(RmtConfig.MAX_FATIGUE.get(), GreenOrbService.state(player).fatigue);
    }

    /** Health came off them: more fatigue, unless something has them ignoring it. */
    static void onDamageTaken(ServerPlayer player, float amount) {
        if (amount <= 0f || player.getMaxHealth() <= 0f) return;
        RmtState state = GreenOrbService.state(player);
        if (player.level().getGameTime() < state.fatigueIgnoredUntil) return;
        state.fatigue = Math.min(RmtConfig.MAX_FATIGUE.get(), state.fatigue + RmtConfig.FATIGUE_WEIGHT.get() * amount / player.getMaxHealth());
        sync(player);
    }

    /** For later systems (Black Spark's zone): takes {@code amount} of fatigue away (0 to 1, as {@link #of} reads). */
    public static void restore(ServerPlayer player, double amount) {
        RmtState state = GreenOrbService.state(player);
        state.fatigue = Math.max(0.0, state.fatigue - Math.max(0.0, amount));
        sync(player);
    }

    /** For later systems (Black Spark's zone): damage taken in the next {@code ticks} ticks adds none. */
    public static void ignore(ServerPlayer player, int ticks) {
        RmtState state = GreenOrbService.state(player);
        state.fatigueIgnoredUntil = Math.max(state.fatigueIgnoredUntil, player.level().getGameTime() + Math.max(0, ticks));
    }

    /** Admin: set it outright (0 to 1). */
    public static void set(ServerPlayer player, double fatigue) {
        GreenOrbService.state(player).fatigue = Math.max(0.0, Math.min(1.0, fatigue));
        sync(player);
    }

    static void reset(ServerPlayer player) {
        RmtState state = GreenOrbService.state(player);
        state.fatigue = 0.0;
        state.fatigueIgnoredUntil = Long.MIN_VALUE;
        sync(player);
    }

    /** Their fight ended: if it was their last, the injuries of it are behind them. */
    static void onFightEnded(ServerPlayer player) {
        if (FightTracker.of(player).isEmpty()) reset(player);
    }

    /** Every second: hurt with no fight between players (mobs, a fall), it lasts until the combat tag runs out. */
    static void tick(ServerPlayer player) {
        RmtState state = GreenOrbService.state(player);
        if (player.level().getGameTime() >= state.survivedLowAt) { // rmt_survived_low: healed out of 3 hearts, and still alive
            state.survivedLowAt = Long.MAX_VALUE;
            if (player.isAlive()) com.cnethuka.kegare.progression.InsightHooks.rmtSurvivedLow(player);
        }
        if (player.tickCount % 20 != 0) return;
        if (GreenOrbService.state(player).fatigue > 0 && FightTracker.of(player).isEmpty() && !CombatHelper.isInCombat(player)) reset(player);
        else sync(player);
    }

    /** Tells the owner their output and fatigue, if either changed (the Sorcerer screen's RMT details). */
    static void sync(ServerPlayer player) {
        if (player.connection == null || player.isFakePlayer()) return;
        RmtState state = GreenOrbService.state(player);
        RmtOutputPayload payload = new RmtOutputPayload((float) RmtOutput.of(player), (float) of(player), RmtOnAnother.knows(player));
        if (payload.equals(state.sentOutput)) return;
        state.sentOutput = payload;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
