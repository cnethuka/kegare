package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Insight: what a player has learned by living through things. Kept on reroll and death. It lowers the Refinement that
 * matching {@link Unlocks} ask for; it never changes when a technique's own abilities unlock.
 */
public final class Insight {
    private Insight() {}

    private static InsightData data(Player player) {
        return player.getData(KegareAttachments.INSIGHT);
    }

    /**
     * Pays {@code amount} into {@code category} the first time this player ever does {@code firstKey}, and never again.
     *
     * @return whether it paid
     */
    public static boolean recordFirst(ServerPlayer player, String firstKey, InsightCategory category, int amount) {
        if (!data(player).firsts.add(firstKey)) return false;
        add(player, category, amount);
        return true;
    }

    public static boolean hasFirst(Player player, String firstKey) {
        return data(player).firsts.contains(firstKey);
    }

    public static int get(Player player, InsightCategory category) {
        return data(player).value(category);
    }

    /** Admin and {@link #recordFirst}. It never goes below 0. */
    public static void add(ServerPlayer player, InsightCategory category, int amount) {
        if (amount == 0) return;
        InsightData data = data(player);
        data.values.put(category, Math.max(0, data.value(category) + amount));
        PlayerSync.markDirty(player);
    }

    /** Admin: back to nothing, and every first can pay again. */
    public static void reset(ServerPlayer player) {
        InsightData data = data(player);
        data.values.clear();
        data.firsts.clear();
        PlayerSync.markDirty(player);
    }
}
