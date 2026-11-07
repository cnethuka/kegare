package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Which chunks of Glacial Lance show at a charge, and how each one snaps in. The chunks are the objects in
 * {@code models/fx/glacial_lance.obj}, made by {@code tools/blockbench/lance_shapes.py}, in the order they arrive: the stub in the
 * fist, the two halves of the javelin, then the head, the split tail and, at full charge only, the spurs. The javelin is complete
 * the moment a throw is allowed, so what an opponent sees in the hand is what can come at them: a stub can't be thrown, a javelin
 * can, and spurs mean full damage.
 */
public final class LanceShape {
    public static final List<String> PIECES = List.of("grip", "fore", "aft", "head", "tail", "spurs");
    public static final int SHARDS = 4;

    /** Where each chunk arrives, as how far the charge has got from the minimum (0) to full (1). The stub is there from the start. */
    private static final float[] PAST_MINIMUM = {Float.NEGATIVE_INFINITY, 0f, 0.19f, 0.44f, 0.69f, 1f};
    /** The minimum charge as a fraction of a full one, if the server's numbers can't be read. */
    private static final float DEFAULT_MINIMUM = 0.2f;
    /** A chunk snaps in over this many ticks: it shoots out of the fist a little past its place and settles back. */
    static final float SNAP_TICKS = 3f;
    private static final float SNAP_FROM = 0.5f;
    private static final float SNAP_OVER = 1.12f;

    private LanceShape() {}

    /**
     * The charge (a fraction of full) at which chunk {@code piece} arrives.
     *
     * @param minimum the least charge that throws, as a fraction of full
     */
    static float arrival(int piece, float minimum) {
        if (piece == 0) return 0f;
        return minimum + (1f - minimum) * PAST_MINIMUM[piece];
    }

    /** Is chunk {@code piece} part of a lance thrown at this charge? */
    public static boolean shown(int piece, float charge, float minimum) {
        return charge + 1.0e-4f >= arrival(piece, minimum);
    }

    /**
     * How big chunk {@code piece} is drawn in the hand: 0 until it arrives, then out past its full size and back over a few ticks.
     *
     * @param ticks     how long the charge has run
     * @param fullTicks how long a full charge takes
     */
    public static float held(int piece, float ticks, float fullTicks, float minimum) {
        float since = ticks - arrival(piece, minimum) * fullTicks;
        if (since < 0f) return 0f;
        return snap(since / SNAP_TICKS);
    }

    /** 0 to 1 over a snap: a quick shot out past full size, then settling back to it. */
    static float snap(float t) {
        if (t >= 1f) return 1f;
        if (t < 0.5f) return Mth.lerp(t / 0.5f, SNAP_FROM, SNAP_OVER);
        return Mth.lerp((t - 0.5f) / 0.5f, SNAP_OVER, 1f);
    }

    /** How many chunks a charge has reached (1 = only the stub). */
    public static int count(float charge, float minimum) {
        int n = 0;
        for (int i = 0; i < PIECES.size(); i++) if (shown(i, charge, minimum)) n = i + 1;
        return n;
    }

    /** The minimum charge as a fraction of a full one, from the server's numbers (synced to every client). */
    public static float minimum() {
        try {
            double max = RimeConfig.LANCE_MAX_CHARGE.get();
            return max <= 0 ? DEFAULT_MINIMUM : (float) Mth.clamp(RimeConfig.LANCE_MIN_CHARGE.get() / max, 0.0, 1.0);
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MINIMUM;
        }
    }
}
