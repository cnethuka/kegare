package com.cnethuka.kegare.advancement;

import com.cnethuka.kegare.Kegare;
import java.util.Set;
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
    // Lensing Eyes
    public static final ResourceLocation CLEAR_SIGHT = Kegare.id("clear_sight");
    public static final ResourceLocation THE_CYCLE_CONTINUES = Kegare.id("the_cycle_continues");
    public static final ResourceLocation EYES_CLOSED = Kegare.id("eyes_closed");
    public static final ResourceLocation BLINDED = Kegare.id("blinded");
    // Shrine (FUTURE, reserved: not generated or awarded yet)
    public static final ResourceLocation VESSEL_OF_THE_KING = Kegare.id("vessel_of_the_king");
    public static final ResourceLocation UNWORTHY_HEIR = Kegare.id("unworthy_heir");
    public static final ResourceLocation UNSEALED = Kegare.id("unsealed");

    /**
     * Design rule: an advancement that would reveal a secret identity (who holds a unique trait or the Shrine)
     * is hidden and never announced to chat; only its earner sees the toast. The generator applies this to every
     * ID here automatically, and datagen fails if one ever isn't hidden and silent. Add future secret IDs here.
     */
    public static final Set<ResourceLocation> SECRET_IDENTITY = Set.of(
            CLEAR_SIGHT, THE_CYCLE_CONTINUES, EYES_CLOSED, BLINDED,
            VESSEL_OF_THE_KING, UNWORTHY_HEIR, UNSEALED);

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
