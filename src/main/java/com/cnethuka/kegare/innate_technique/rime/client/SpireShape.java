package com.cnethuka.kegare.innate_technique.rime.client;

import java.util.List;
import net.minecraft.util.Mth;

/**
 * Glacial Spire's spike as the game draws it (the pieces are the objects in {@code models/fx/glacial_spire.obj}): which pieces a
 * charge shows, how big, and how each comes up out of the ground. All in ticks after the eruption. Matches the launch: the spike is
 * as tall as the charge makes the throw, and it comes up about as fast as it throws (a full charge launches at 1.2 blocks a tick).
 */
final class SpireShape {
    /** The pieces, in the order they're drawn. */
    static final List<String> PIECES = List.of("ring", "core", "crown", "heavy");
    /** Ticks after the eruption each piece starts up: the foot and the spire at once, the rest a beat behind (in chunks). */
    static final float[] DELAY = {0f, 0f, 1f, 1.5f};
    /** The charge each piece needs: the heavy group only over half. */
    static final float[] NEEDS = {0f, 0f, 0f, 0.5f};
    /** How tall each piece stands at full size, about (for how far below ground it starts). */
    static final float[] TOP = {0.9f, 3.4f, 1.9f, 2.3f};
    static final int CHUNKS = 4;
    /** Ticks a piece takes to come all the way up (then it overshoots a little and settles back). */
    static final float RISE_TICKS = 2.5f;
    static final float OVERSHOOT = 0.1f;
    /** After it's up, it shudders for this long. */
    static final int QUIVER_TICKS = 8;

    private SpireShape() {}

    /** How much taller than the pieces' own size the spike stands at this charge: about 2.1 blocks at the least, 3.4 at full. */
    static float height(float charge) {
        return Mth.lerp(Mth.clamp(charge, 0f, 1f), 0.62f, 1f);
    }

    /** How much wider: a full charge is heavier as well as taller. */
    static float width(float charge) {
        return Mth.lerp(Mth.clamp(charge, 0f, 1f), 0.8f, 1f);
    }

    static boolean shown(int piece, float charge) {
        return charge + 1e-4f >= NEEDS[piece];
    }

    /**
     * How far up a piece is, {@code ticks} after the eruption: 0 still under the ground, 1 all the way out. It shoots up (fastest
     * at the start: sudden), carries on a little past 1 and settles back.
     */
    static float rise(float ticks, int piece) {
        float u = (ticks - DELAY[piece]) / RISE_TICKS;
        if (u <= 0f) return 0f;
        if (u < 1f) {
            float out = 1f - (1f - u) * (1f - u) * (1f - u);
            return out * (1f + OVERSHOOT);
        }
        if (u < 2f) {
            float back = u - 1f;
            return 1f + OVERSHOOT * (1f - back * back * (3f - 2f * back));
        }
        return 1f;
    }

    /** How far below its standing place a piece is drawn (blocks, before the charge's height): it comes up out of the ground. */
    static float sunk(float ticks, int piece) {
        return (1f - Math.min(1f, rise(ticks, piece))) * TOP[piece];
    }

    /** A shudder once it's up, in degrees, dying away. */
    static float quiver(float ticks) {
        float t = ticks - RISE_TICKS;
        if (t <= 0f || t >= QUIVER_TICKS) return 0f;
        return 1.8f * (1f - t / QUIVER_TICKS) * Mth.sin(t * 2.3f);
    }
}
