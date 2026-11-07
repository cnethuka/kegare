package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innate_technique.rime.SplinterShardEntity;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Frost Shard (and Splinter) breaking where it hits: a few solid chips of its own ice flung back off whatever it hit, falling and
 * bouncing (LanceAftermath's shards), and a small soft shatter (`frost_shard_impact`: a flash, glints, a breath of mist). The server
 * names the shard with the impact, so its heading and kind are read here; a Splinter's is smaller. Nothing if it hit you: the hurt
 * flash says so, and it would be in your eyes.
 */
final class ShardImpact {
    private static final RandomSource RANDOM = RandomSource.create();

    private ShardImpact() {}

    static void init() {
        ClientVisuals.onPlayAt(RimeIds.FX_FROST_SHARD_IMPACT, ShardImpact::onImpact);
    }

    /** How big the impact is for this kind of shard: a Splinter's is clearly the lighter. */
    static float size(Entity shard) {
        return shard instanceof SplinterShardEntity ? 0.6f : 1f;
    }

    /** How many chips it breaks into. */
    static int chips(float size) {
        return Math.round(2 + 3 * size);
    }

    static boolean onImpact(ClientLevel level, Vec3 pos, int entityId, Visual visual) {
        Entity e = entityId < 0 ? null : level.getEntity(entityId);
        float size = size(e);
        if (LanceAftermath.nearOwnCamera(pos, 1.6)) return true;
        Vec3 heading = e == null || e.getDeltaMovement().lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : e.getDeltaMovement().normalize();
        int n = chips(size);
        for (int i = 0; i < n; i++) {
            // Back the way it came and out to the sides, a little up: off the wall or the body it hit.
            Vec3 vel = heading.scale(-0.12 - RANDOM.nextFloat() * 0.1)
                    .add(RANDOM.nextGaussian() * 0.08, 0.08 + RANDOM.nextFloat() * 0.12, RANDOM.nextGaussian() * 0.08);
            Vec3 at = pos.subtract(heading.scale(0.15)).add(RANDOM.nextGaussian() * 0.05, RANDOM.nextGaussian() * 0.05, RANDOM.nextGaussian() * 0.05);
            LanceAftermath.chunk(level, at, vel, size * (0.8f + RANDOM.nextFloat() * 0.5f), LanceMesh.SHARD, "chip_" + RANDOM.nextInt(4),
                    16 + RANDOM.nextInt(10));
        }
        ClientVisuals.playAt(level, pos.subtract(heading.scale(0.1)), RimeIds.FX_FROST_SHARD_IMPACT, 0, size);
        return true;
    }
}
