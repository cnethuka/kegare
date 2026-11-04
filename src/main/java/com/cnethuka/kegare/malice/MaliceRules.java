package com.cnethuka.kegare.malice;

/** Pure Malice rules (no Minecraft types, unit-tested). */
public final class MaliceRules {
    private MaliceRules() {}

    /**
     * True when the reserve, as players see it (whole numbers, floored like the HUD), drops from 1+ to 0.
     * Used for Running on Empty.
     */
    public static boolean hitsEmpty(double before, double after) {
        return Math.floor(before) >= 1 && after < 1;
    }

    /** Overflowing: born in the Vast tier. */
    public static boolean isOverflowing(MaliceTier tier) {
        return tier == MaliceTier.VAST;
    }
}
