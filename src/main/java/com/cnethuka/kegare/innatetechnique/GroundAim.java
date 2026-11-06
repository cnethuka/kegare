package com.cnethuka.kegare.innatetechnique;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * For abilities that aim at the ground: a crosshair on a living thing means the ground under it. A look along flat ground
 * passes over a target's head and meets no block in range, so without this the one obvious way to aim would fail. A pure
 * world query, so the client can use it for an aiming preview. It only finds a spot: nothing locks on to the target.
 */
public final class GroundAim {
    /** Forgives the target having moved a little between what the client saw and the server checks. */
    private static final float TOLERANCE = 0.2f;

    private GroundAim() {}

    /**
     * The ground under the living thing the crosshair is on. Empty if the crosshair isn't on one (walls hide what's behind
     * them, and invisible things don't count, or aiming would give them away), or there's no ground under it within {@code
     * range} of the eyes: the same limit as aiming at the ground itself.
     */
    public static Optional<Vec3> underLookedAtEntity(Player player, double range, Predicate<LivingEntity> counts) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, search,
                e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && living.isPickable()
                        && !living.isInvisibleTo(player) && counts.test(living),
                TOLERANCE);
        if (hit == null) return Optional.empty();

        // From just above the feet, so a low ceiling over them can't be taken for the floor.
        Vec3 from = hit.getEntity().position().add(0, 0.1, 0);
        BlockHitResult floor = level.clip(new ClipContext(from, from.subtract(0, range, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS || floor.getLocation().distanceTo(eye) > range) return Optional.empty();
        return Optional.of(floor.getLocation());
    }
}
