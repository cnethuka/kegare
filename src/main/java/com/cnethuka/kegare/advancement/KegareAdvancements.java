package com.cnethuka.kegare.advancement;

import com.cnethuka.kegare.Kegare;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancement IDs. All use {@code minecraft:impossible} triggers (see {@code KegareAdvancementGenerator})
 * and are awarded only from code via {@link #award}.
 */
public final class KegareAdvancements {
    public static final ResourceLocation AWAKENING = Kegare.id("awakening");
    public static final ResourceLocation REAWAKENING = Kegare.id("reawakening");
    public static final ResourceLocation CHOSEN_BY_FATE = Kegare.id("chosen_by_fate");
    public static final ResourceLocation BITTER_LUCK = Kegare.id("bitter_luck");
    public static final ResourceLocation SEVERED = Kegare.id("severed");
    // Lensing Eyes (all hidden)
    public static final ResourceLocation CLEAR_SIGHT = Kegare.id("clear_sight");
    public static final ResourceLocation THE_CYCLE_CONTINUES = Kegare.id("the_cycle_continues");
    public static final ResourceLocation EYES_CLOSED = Kegare.id("eyes_closed");
    public static final ResourceLocation BLINDED = Kegare.id("blinded");

    private KegareAdvancements() {}

    public static void award(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            Kegare.LOGGER.warn("Missing advancement {}", id);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
