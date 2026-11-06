package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;

/** Animations ({@code player_animations/melee.json}, written by {@code tools/anims/melee.py}), effects and loop keys. */
public final class MeleeIds {
    public static final ResourceLocation ANIM_DASH_FORWARD = Kegare.id("dash_forward");
    public static final ResourceLocation ANIM_DASH_BACK = Kegare.id("dash_back");
    public static final ResourceLocation ANIM_DASH_LEFT = Kegare.id("dash_left");
    public static final ResourceLocation ANIM_DASH_RIGHT = Kegare.id("dash_right");
    public static final ResourceLocation ANIM_PARRY_GUARD = Kegare.id("parry_guard");
    public static final ResourceLocation ANIM_PARRY_PERFECT = Kegare.id("parry_perfect");
    public static final ResourceLocation ANIM_PARRY_RECOVERY = Kegare.id("parry_recovery");
    public static final ResourceLocation ANIM_STAGGER = Kegare.id("stagger");
    public static final ResourceLocation ANIM_REINFORCED_STRIKE = Kegare.id("reinforced_strike");

    public static final ResourceLocation FX_DASH_BURST = Kegare.id("melee/dash_burst");
    public static final ResourceLocation FX_DASH_TRAIL = Kegare.id("melee/dash_trail");
    /** What the dasher sees instead in first person (the client swaps them). */
    public static final ResourceLocation FX_DASH_TRAIL_SELF = Kegare.id("melee/dash_trail_self");
    public static final ResourceLocation FX_PARRY_SPARK = Kegare.id("melee/parry_spark");
    /** What the defender sees instead in first person (the client swaps them). */
    public static final ResourceLocation FX_PARRY_SPARK_SELF = Kegare.id("melee/parry_spark_self");
    public static final ResourceLocation FX_GUARD_HIT = Kegare.id("melee/guard_hit");
    public static final ResourceLocation FX_GUARD_HIT_SELF = Kegare.id("melee/guard_hit_self");
    public static final ResourceLocation FX_STRIKE_IMPACT = Kegare.id("melee/strike_impact");
    public static final ResourceLocation FX_STRIKE_READY = Kegare.id("melee/strike_ready");

    static final String GUARD_LOOP = "kegare:melee_guard";
    static final String STRIKE_LOOP = "kegare:melee_strike_ready";
    /** The perfect-parry reaction's length: the guard pose comes back after it if they're still holding. */
    public static final int PARRY_PERFECT_TICKS = 9;

    private MeleeIds() {}
}
