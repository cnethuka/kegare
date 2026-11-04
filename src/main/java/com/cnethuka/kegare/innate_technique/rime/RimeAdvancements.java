package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rime's advancement tab (root {@code kegare:innate_technique/rime}, unlocked on first rolling Rime). Every one is
 * hidden and silent in chat (they would reveal who has Rime), uses minecraft:impossible and is awarded from code.
 * PvP ones count player targets only.
 */
public final class RimeAdvancements {
    public static final ResourceLocation ROOT = KegareAdvancements.innateTechniqueRoot(Kegare.id("rime"));
    public static final ResourceLocation SHATTER = id("shatter");
    public static final ResourceLocation COLD_OPEN = id("cold_open");
    public static final ResourceLocation BRAIN_FREEZE = id("brain_freeze");
    public static final ResourceLocation FULL_CHARGE = id("full_charge");
    public static final ResourceLocation THIN_ICE = id("thin_ice");
    public static final ResourceLocation COLD_BLOODED = id("cold_blooded");
    public static final ResourceLocation DEEP_FREEZE = id("deep_freeze");
    public static final ResourceLocation LONG_WAY_FROM_HOME = id("long_way_from_home");
    public static final ResourceLocation HOME_FIELD = id("home_field");

    /** Thin Ice: total damage Rime Shell has blocked (kept across sessions). */
    public static final double THIN_ICE_DAMAGE = 100.0;
    /** Deep Freeze: this many freezes of the same player... */
    public static final int DEEP_FREEZE_COUNT = 3;
    /** ...within this many ticks (60 seconds). */
    public static final long DEEP_FREEZE_WINDOW_TICKS = 60 * 20;

    private RimeAdvancements() {}

    private static ResourceLocation id(String name) {
        return Kegare.id("innate_technique/rime/" + name);
    }

    /** Only a player who currently holds Rime earns these (so the tab never opens for anyone else). */
    public static void award(ServerPlayer player, ResourceLocation id) {
        if (Rime.holds(player)) KegareAdvancements.award(player, id);
    }
}
