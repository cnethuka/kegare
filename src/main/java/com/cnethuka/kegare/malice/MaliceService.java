package com.cnethuka.kegare.malice;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
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

    public static boolean canAfford(Player player, double amount) {
        return amount <= 0 || get(player).current() + EPSILON >= amount;
    }

    /** Spends {@code amount} if the reserve covers it. Returns false (and spends nothing) otherwise. */
    public static boolean trySpend(ServerPlayer player, double amount) {
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
        malice.setCurrent(clamped);
        if (visibleChange) PlayerSync.markDirty(player);
    }

    public static void refill(ServerPlayer player) {
        setCurrent(player, get(player).capacity());
    }

    /** Called every server tick: regenerates a % of capacity per second unless paused by recent combat. */
    public static void tickRegen(ServerPlayer player) {
        MaliceData malice = get(player);
        if (!malice.isBorn() || malice.isSevered() || malice.current() >= malice.capacity()) return;
        if (CombatHelper.isRegenPaused(player)) return;
        double perTick = malice.capacity() * KegareServerConfig.REGEN_PERCENT_PER_SECOND.get() / 100.0 / 20.0;
        setCurrent(player, malice.current() + perTick);
    }

    /** Birth roll. The ONLY normal way capacity is ever set. */
    public static MaliceRoller.Result rollBirth(ServerPlayer player) {
        MaliceRoller.Result result = MaliceRoller.roll(
                KegareServerConfig.tierWeights(),
                KegareServerConfig.tierBases(),
                KegareServerConfig.CAPACITY_VARIATION.get(),
                player.getRandom()::nextInt);
        get(player).setBirth(result.tier(), result.capacity());
        PlayerSync.markDirty(player);
        return result;
    }

    /**
     * Admin debug override (the only exception to "capacity never changes"). Re-labels the tier,
     * updates the Severed flag (capacity 0 = Severed) and refills the reserve.
     */
    public static void adminSetCapacity(ServerPlayer player, int capacity) {
        MaliceTier tier = MaliceRoller.closestTier(capacity, KegareServerConfig.tierBases());
        get(player).setBirth(tier, Math.max(0, capacity));
        PlayerSync.markDirty(player);
    }
}
