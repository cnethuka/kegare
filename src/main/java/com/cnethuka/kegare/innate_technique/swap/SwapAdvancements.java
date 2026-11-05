package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Swap's advancement tab (root {@code kegare:innate_technique/swap}, unlocked on first rolling Swap). Every one is
 * hidden and silent in chat (they would reveal who has Swap), uses minecraft:impossible and is awarded from code.
 * Targets count whether they are players or mobs (only the disguises and Total Swap need players).
 */
public final class SwapAdvancements {
    public static final ResourceLocation ROOT = KegareAdvancements.innateTechniqueRoot(Kegare.id("swap"));
    public static final ResourceLocation SWITCHEROO = id("switcheroo");
    public static final ResourceLocation WRONG_TARGET = id("wrong_target");
    public static final ResourceLocation FALL_GUY = id("fall_guy");
    public static final ResourceLocation IDENTITY_CRISIS = id("identity_crisis");
    public static final ResourceLocation NOT_ALLOWED = id("not_allowed");
    public static final ResourceLocation ROUND_TRIP = id("round_trip");
    public static final ResourceLocation TWO_OF_YOU = id("two_of_you");
    public static final ResourceLocation RETURN_TO_SENDER = id("special_delivery_swap");
    public static final ResourceLocation NEWTONS_THIRD = id("newtons_third");

    /** Fall Guy: the swapped player's fall death must come within this many ticks of the swap (5 s). */
    public static final long FALL_GUY_WINDOW_TICKS = 5 * 20;
    /** Newton's Third: the fall death must come within this many ticks of the Momentum Exchange (10 s). */
    public static final long NEWTONS_THIRD_WINDOW_TICKS = 10 * 20;
    /** Identity Crisis: Total Swap cast below this fraction of max health... */
    public static final float IDENTITY_CRISIS_HEALTH = 0.3f;
    /** ...and still alive this many ticks later (30 s). */
    public static final long IDENTITY_CRISIS_SURVIVE_TICKS = 30 * 20;

    private SwapAdvancements() {}

    private static ResourceLocation id(String name) {
        return Kegare.id("innate_technique/swap/" + name);
    }

    /** Only a player who currently holds Swap earns these (so the tab never opens for anyone else). */
    public static void award(ServerPlayer player, ResourceLocation id) {
        if (Swap.holds(player)) KegareAdvancements.award(player, id);
    }
}
