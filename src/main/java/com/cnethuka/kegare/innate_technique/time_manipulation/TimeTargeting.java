package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.combat.CombatHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What the caster is looking at: a player or mob (Rewind), or a block (Point Maker's mark, Stasis Field's centre). */
final class TimeTargeting {
    private TimeTargeting() {}

    /**
     * The nearest player or mob under the crosshair within {@code [targeting] range}, in line of sight (blocks in the way stop it).
     * Not armor stands. Bosses are found (so the caster is told they're immune) and refused by the caller.
     */
    static Optional<LivingEntity> lookedAtLiving(ServerPlayer caster) {
        double range = TimeConfig.RANGE.get(), tolerance = TimeConfig.AIM_TOLERANCE.get();
        Vec3 eye = caster.getEyePosition(), look = caster.getViewVector(1f);
        BlockHitResult block = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = caster.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0 + tolerance);
        for (LivingEntity other : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
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

    static boolean immune(LivingEntity entity) {
        return CombatHelper.isBoss(entity);
    }

    /** The block under the crosshair within {@code range}, if any (fluids don't count). */
    static Optional<BlockHitResult> lookedAtBlock(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition(), look = caster.getViewVector(1f);
        BlockHitResult hit = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit) : Optional.empty();
    }

    /** The top of a block: where something stands on it (its collision shape's top; a full block's is one up). */
    static Vec3 topOf(ServerPlayer caster, BlockPos pos) {
        var shape = caster.level().getBlockState(pos).getCollisionShape(caster.level(), pos);
        double top = shape.isEmpty() ? 0.0 : shape.max(net.minecraft.core.Direction.Axis.Y);
        return new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }
}
