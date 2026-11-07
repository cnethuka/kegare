package com.cnethuka.kegare.melee.client;

/** The numbers behind how a dash feels from inside it. No game state, so they can be tested. */
final class DashKick {
    /** Ticks from the start of a dash until it's gone. A dash itself lasts four to six. */
    static final float DURATION = 11f;
    /** Ticks to reach its peak: quickly, or it reads as a zoom and not a jolt. */
    static final float RISE = 2f;
    /** How much wider the view opens at the peak, with every setting at full. */
    static final float FOV_PEAK = 0.16f;

    private DashKick() {}

    /** From 0 up to 1 within {@link #RISE} ticks, then easing back to 0 by {@link #DURATION}. */
    static float envelope(float age) {
        if (age <= 0f || age >= DURATION) return 0f;
        if (age < RISE) return age / RISE;
        float left = 1f - (age - RISE) / (DURATION - RISE);
        return left * left;
    }

    /** @param strength 1 at full, 0 for no kick at all */
    static float fovMultiplier(float age, float strength) {
        return 1f + FOV_PEAK * strength * envelope(age);
    }

    /** How visible a streak is at this distance from the screen's middle (0 the crosshair, 1 the screen's edge): never over the view. */
    static float edgeFade(float radius) {
        float t = (radius - CLEAR_RADIUS) / (FULL_RADIUS - CLEAR_RADIUS);
        return t <= 0f ? 0f : t >= 1f ? 1f : t * t * (3f - 2f * t);
    }

    /** Nothing is ever drawn inside this much of the screen. */
    static final float CLEAR_RADIUS = 0.6f;
    private static final float FULL_RADIUS = 0.9f;
}
