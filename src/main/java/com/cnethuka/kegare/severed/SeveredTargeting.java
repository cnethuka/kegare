package com.cnethuka.kegare.severed;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Who a Severed player is looking at (Closing Line, DESIGN §3.27): the nearest player or mob under the crosshair, in line of sight. */
final class SeveredTargeting {
    private SeveredTargeting() {}

    static Optional<LivingEntity> lookedAt(ServerPlayer player) {
        double range = SeveredConfig.RANGE.get(), tolerance = SeveredConfig.AIM_TOLERANCE.get();
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        BlockHitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0 + tolerance);
        for (LivingEntity other : player.serverLevel().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            AABB box = other.getBoundingBox().inflate(tolerance);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return Optional.ofNullable(best);
    }
}
