package com.cnethuka.kegare.malice;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.trait.TraitSuccession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** All reads and writes of Malice go through here (server-authoritative). */
public final class MaliceService {
    private static final double EPSILON = 1.0e-6;

    private MaliceService() {}

    public static MaliceData get(Player player) {
        return player.getData(KegareAttachments.MALICE);
    }

    public static boolean isSevered(Player player) {
        return get(player).isSevered();
    }

    /** Changes what a cost really takes from a player (Six Eyes' near-zero waste, DESIGN §3.32). */
    @FunctionalInterface
    public interface CostModifier {
        double modify(ServerPlayer player, double amount);
    }

    private static final java.util.List<CostModifier> COST_MODIFIERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addCostModifier(CostModifier modifier) {
        COST_MODIFIERS.add(modifier);
    }

    /** What a cost of {@code amount} really takes from this player. */
    public static double costOf(Player player, double amount) {
        if (amount <= 0 || !(player instanceof ServerPlayer server)) return amount;
        for (CostModifier modifier : COST_MODIFIERS) amount = modifier.modify(server, amount);
        return amount;
    }

    /** What a cast's cost really takes: every cast costs at least 1 Malice, however little it wastes (a cast that costs nothing stays free). */
    public static double castCostOf(Player player, double amount) {
        return amount <= 0 ? amount : Math.max(Math.min(1.0, amount), costOf(player, amount));
    }

    public static boolean canAfford(Player player, double amount) {
        return amount <= 0 || get(player).current() + EPSILON >= costOf(player, amount);
    }

    /** Can they pay a cast of this cost? */
    public static boolean canAffordCast(Player player, double amount) {
        return amount <= 0 || get(player).current() + EPSILON >= castCostOf(player, amount);
    }

    /** Spends {@code amount} (as {@link #costOf} says) if the reserve covers it. Returns false (and spends nothing) otherwise. */
    public static boolean trySpend(ServerPlayer player, double amount) {
        return spendExactly(player, costOf(player, amount));
    }

    /** Spends a cast's cost (as {@link #castCostOf} says): at least 1. */
    public static boolean trySpendCast(ServerPlayer player, double amount) {
        return spendExactly(player, castCostOf(player, amount));
    }

    private static boolean spendExactly(ServerPlayer player, double amount) {
        if (amount <= 0) return true;
        MaliceData malice = get(player);
        if (malice.current() + EPSILON < amount) return false;
        setCurrent(player, malice.current() - amount);
        return true;
    }

    /** Sets the reserve, clamped to {@code [0, capacity]}. */
    public static void setCurrent(ServerPlayer player, double value) {
        MaliceData malice = get(player);
        double clamped = Mth.clamp(value, 0.0, malice.capacity());
        // The client only displays whole numbers, so sync when the whole part changes.
        boolean visibleChange = Mth.floor(clamped) != Mth.floor(malice.current());
        boolean hitEmpty = MaliceRules.hitsEmpty(malice.current(), clamped);
        malice.setCurrent(clamped);
        if (visibleChange) PlayerSync.markDirty(player);
        if (hitEmpty && CombatHelper.isInCombat(player)) KegareAdvancements.award(player, KegareAdvancements.RUNNING_ON_EMPTY);
    }

    public static void refill(ServerPlayer player) {
        setCurrent(player, get(player).capacity());
    }

    /** Multipliers on natural regeneration (Background RMT slows it while it runs, DESIGN §3.31). */
    private static final java.util.List<java.util.function.ToDoubleFunction<ServerPlayer>> REGEN_MODIFIERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addRegenModifier(java.util.function.ToDoubleFunction<ServerPlayer> modifier) {
        REGEN_MODIFIERS.add(modifier);
    }

    /** Called every server tick: regenerates a % of capacity per second (by tier) unless paused by recent combat. */
    public static void tickRegen(ServerPlayer player) {
        MaliceData malice = get(player);
        if (!malice.isBorn() || malice.isSevered() || malice.current() >= malice.capacity()) return;
        if (CombatHelper.isRegenPaused(player)) return;
        double regen = MaliceRules.regenPerTick(malice.capacity(), KegareServerConfig.regenPercent(malice.tier()));
        for (java.util.function.ToDoubleFunction<ServerPlayer> modifier : REGEN_MODIFIERS) regen *= modifier.applyAsDouble(player);
        setCurrent(player, malice.current() + regen);
    }

    /** Birth roll. The ONLY normal way capacity is ever set. */
    public static MaliceRoller.Result rollBirth(ServerPlayer player) {
        MaliceRoller.Result result = MaliceRoller.roll(
                KegareServerConfig.tierWeights(),
                KegareServerConfig.tierBases(),
                KegareServerConfig.CAPACITY_VARIATION_PERCENT.get(),
                player.getRandom()::nextInt);
        applyCapacity(player, result.tier(), result.capacity());
        return result;
    }

    /** Admin debug: a "birth" with a forced capacity (tier labelled by the closest base). 0 = Severed. */
    public static MaliceRoller.Result adminBirth(ServerPlayer player, int capacity) {
        int clamped = Math.max(0, capacity);
        MaliceTier tier = MaliceRoller.closestTier(clamped, KegareServerConfig.tierBases());
        applyCapacity(player, tier, clamped);
        return new MaliceRoller.Result(tier, clamped);
    }

    /**
     * Admin debug override (the only exception to "capacity never changes"). Re-labels the tier,
     * updates the Severed flag (capacity 0 = Severed) and refills the reserve.
     */
    public static void adminSetCapacity(ServerPlayer player, int capacity) {
        MaliceTier tier = MaliceRoller.closestTier(capacity, KegareServerConfig.tierBases());
        applyCapacity(player, tier, Math.max(0, capacity));
    }

    /**
     * The single place capacity and the Severed flag change ({@link MaliceData}'s setters are package-private).
     * Any path that makes a player Severed (birth or rebirth roll, admin, or anything added later) ends up here,
     * so becoming Severed always strips Malice-based traits.
     */
    private static void applyCapacity(ServerPlayer player, MaliceTier tier, int capacity) {
        MaliceData data = get(player);
        boolean wasSevered = data.isBorn() && data.isSevered();
        data.setBirth(tier, capacity);
        PlayerSync.markDirty(player);
        if (!wasSevered && data.isSevered()) TraitSuccession.stripMaliceTraits(player);
    }
}
