package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What a Puppet Manipulation player is looking at: one of their puppets, or any living target. Line of sight required. */
public final class PuppetTargeting {
    /** How far beside the crosshair (blocks) something still counts as looked at. */
    private static final double TOLERANCE = 0.3;

    private PuppetTargeting() {}

    /** The owner's own puppet under the crosshair within {@code range} (possessed ones excluded). */
    public static Optional<PuppetEntity> lookedAtPuppet(ServerPlayer owner, double range) {
        return look(owner, PuppetEntity.class, range, p -> p.isAlive() && !p.isPossessed() && p.isOwnedBy(owner));
    }

    /** The owner's puppet under the crosshair, else their nearest one, within {@code range}. */
    public static Optional<PuppetEntity> lookedAtOrNearest(ServerPlayer owner, double range) {
        Optional<PuppetEntity> looked = lookedAtPuppet(owner, range);
        if (looked.isPresent()) return looked;
        List<PuppetEntity> near = Puppets.near(owner, range);
        return near.isEmpty() ? Optional.empty() : Optional.of(near.get(0));
    }

    /** Any living thing under the crosshair (a player, mob or puppet; never the caster). */
    public static Optional<LivingEntity> lookedAtLiving(ServerPlayer owner, double range) {
        return look(owner, LivingEntity.class, range, e -> e != owner && e.isAlive() && !e.isSpectator()
                && !(e instanceof PuppetEntity p && p.isPossessed()) && !(e instanceof PuppetShellEntity));
    }

    /** What a string can attach to under the crosshair: {@link #lookedAtLiving}, but not armor stands. */
    public static Optional<LivingEntity> lookedAtStringTarget(ServerPlayer owner, double range) {
        return look(owner, LivingEntity.class, range, e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && !(e instanceof PuppetEntity p && p.isPossessed()) && !(e instanceof PuppetShellEntity));
    }

    /** Where the crosshair meets a block within {@code range}, if anywhere. */
    public static Optional<BlockHitResult> lookedAtBlock(ServerPlayer owner, double range) {
        Vec3 eye = owner.getEyePosition();
        BlockHitResult hit = owner.level().clip(new ClipContext(eye, eye.add(owner.getViewVector(1f).scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? Optional.empty() : Optional.of(hit);
    }

    private static <T extends Entity> Optional<T> look(ServerPlayer caster, Class<T> type, double range, Predicate<? super T> valid) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        BlockHitResult block = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = caster.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0 + TOLERANCE);
        for (T other : caster.serverLevel().getEntitiesOfClass(type, search, valid::test)) {
            AABB box = other.getBoundingBox().inflate(TOLERANCE);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return Optional.ofNullable(best);
    }
}
