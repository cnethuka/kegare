package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Path's ice, on the client: pixel ice on the blocks' own texels (block marks {@code marks/path_ice}, {@code path_ice_b}) laid
 * under the feet of every player sliding, read off the server's own trail loop on them, so every client shows it while the slide
 * really runs. Laid the way the server lays its trail ({@code IcePaths}: on ground within 1.5 blocks under the feet, a patch every
 * half a block or so), each patch forms from the middle out in a few ticks, holds for the trail's time and melts from the edge in
 * about when the server's trail there expires. No block is touched.
 */
public final class PathClient {
    /** Two patches of the same ice, so the band doesn't repeat. */
    static final ResourceLocation ICE = Kegare.id("path_ice"), ICE_B = Kegare.id("path_ice_b");
    /** A new patch once the feet are this far from the last one (the server merges its points closer than 0.4). */
    static final double SPACING = 0.45;
    /** Ice only on ground at most this far under the feet, as the server's trail. */
    static final double MAX_HEIGHT = 1.5;
    /** A patch forms over this, then (holding for the rest of the trail's time) melts from the edge in over this, centred on its end. */
    static final int SPREAD = 3, MELT = 24;
    /** How much of each patch shows, and how far off the feet it sits: a little different every time, so the band's edges wander. */
    static final float REACH_MIN = 0.82f;
    static final double WANDER = 0.1;
    /** The four ways a patch is laid that keep its shine on the same diagonal, so the whole band shines one way. */
    static final int[] TURNS = {0, 3, 4, 7};
    private static final Map<Integer, Vec3> LAST = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int laid;

    private PathClient() {}

    /** How long the server's trail lasts, in ticks. */
    static int trailTicks() {
        try {
            return RimeConfig.ticks(RimeConfig.PATH_TRAIL_SECONDS);
        } catch (IllegalStateException notLoaded) {
            return 80;
        }
    }

    /** A patch's timing for a trail of this many ticks: its edge is gone {@code MELT / 2} before the trail's end, its middle as long after. */
    static BlockMarks.Timing timing(int trailTicks, float reach) {
        return new BlockMarks.Timing(reach, 0, SPREAD, Math.max(1, trailTicks - SPREAD - MELT / 2), MELT);
    }

    /** Patch {@code n} of the trail: which of the two, and turned which of the four ways. */
    static int turn(int n) {
        return TURNS[(n >> 1) & 3];
    }

    static void tick(ClientLevel level) {
        Set<Integer> sliding = new HashSet<>();
        for (Player slider : level.players()) {
            if (ClientVisuals.loopTime(slider, RimeIds.ICE_PATH_TRAIL_KEY, 0f) == null) continue;
            sliding.add(slider.getId());
            Vec3 at = ground(level, slider);
            if (at == null) continue;
            Vec3 last = LAST.get(slider.getId());
            if (last != null && last.distanceToSqr(at) < SPACING * SPACING) continue;
            LAST.put(slider.getId(), at);
            lay(level, at);
        }
        LAST.keySet().retainAll(sliding);
    }

    private static void lay(ClientLevel level, Vec3 at) {
        BlockMarks.Pattern a = BlockMarks.pattern(ICE), b = BlockMarks.pattern(ICE_B);
        if (a == null) return;
        int n = laid++;
        BlockMarks.Pattern patch = b != null && (n & 1) == 1 ? b : a;
        Vec3 placed = at.add((RANDOM.nextDouble() - 0.5) * 2 * WANDER, 0, (RANDOM.nextDouble() - 0.5) * 2 * WANDER);
        float reach = REACH_MIN + RANDOM.nextFloat() * (1f - REACH_MIN);
        BlockMarks.stamp(level, patch, placed, Direction.UP, turn(n), timing(trailTicks(), reach));
    }

    /** The top of the ground under the feet, as the server finds it for its trail; null if there's none close enough. */
    private static @Nullable Vec3 ground(ClientLevel level, Player slider) {
        Vec3 feet = slider.position().add(0, 0.2, 0);
        BlockHitResult hit = level.clip(new ClipContext(feet, feet.subtract(0, MAX_HEIGHT, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, slider));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) return null;
        return hit.getLocation();
    }

    static void clear() {
        LAST.clear();
    }
}
