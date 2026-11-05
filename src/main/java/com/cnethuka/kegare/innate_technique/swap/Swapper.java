package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The moving part of Swap. Every position swap goes through here: both move at once (a player or a mob), both fall
 * distances are reset (only Momentum Exchange keeps fall distance, on purpose, and it never moves anyone), and both
 * ends get their effects and the sound. Something bigger than the space it lands in is nudged up out of the blocks.
 * Callers fire {@link SwapTargetEvent} first.
 */
public final class Swapper {
    private Swapper() {}

    /** Swaps the caster's position with a player's or a mob's, and their facing too if asked. */
    public static void swap(ServerPlayer caster, LivingEntity target, boolean facing) {
        Vec3 posA = caster.position();
        Vec3 posB = target.position();
        float yawA = caster.getYRot(), pitchA = caster.getXRot();
        float yawB = target.getYRot(), pitchB = target.getXRot();
        depart(caster);
        depart(target);
        SwapLink.play(caster.serverLevel(), bodyPos(caster), bodyPos(target));
        moveTo(caster, posB, facing ? yawB : yawA, facing ? pitchB : pitchA);
        moveTo(target, posA, facing ? yawA : yawB, facing ? pitchA : pitchB);
        arrive(caster);
        arrive(target);
    }

    /** Moves one entity (keeping its own facing), resetting its fall distance. */
    public static void moveTo(Entity entity, Vec3 pos) {
        moveTo(entity, pos, entity.getYRot(), entity.getXRot());
    }

    public static void moveTo(Entity entity, Vec3 pos, float yaw, float pitch) {
        Vec3 spot = clearOfBlocks(entity, pos);
        if (entity instanceof ServerPlayer player && !(entity instanceof FakePlayer)) {
            if (player.isSleeping()) player.stopSleepInBed(true, true);
            player.teleportTo(player.serverLevel(), spot.x, spot.y, spot.z, yaw, pitch); // also dismounts
        } else if (entity instanceof FakePlayer) { // no client to tell, and its connection ignores teleports
            entity.stopRiding();
            entity.moveTo(spot.x, spot.y, spot.z, yaw, pitch);
        } else {
            entity.stopRiding();
            entity.teleportTo((ServerLevel) entity.level(), spot.x, spot.y, spot.z, Set.of(), yaw, pitch);
            if (entity instanceof LivingEntity living) living.yBodyRot = yaw;
            if (entity instanceof Mob mob) mob.getNavigation().stop();
        }
        entity.setYHeadRot(yaw);
        entity.resetFallDistance();
    }

    /** The spot itself, or up to two blocks above it if the entity wouldn't fit there (a big mob in a small space). */
    static Vec3 clearOfBlocks(Entity entity, Vec3 pos) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos);
        Vec3 spot = pos;
        for (int i = 0; i < 8 && !entity.level().noCollision(entity, box); i++) {
            spot = spot.add(0, 0.25, 0);
            box = box.move(0, 0.25, 0);
        }
        return entity.level().noCollision(entity, box) ? spot : pos;
    }

    /** Where someone leaves: a teal implosion and the sound. */
    public static void depart(Entity entity) {
        effect(entity, SwapIds.DEPART, 0.9f);
    }

    /** Where someone arrives. */
    public static void arrive(Entity entity) {
        effect(entity, SwapIds.ARRIVE, 1.15f);
    }

    private static void effect(Entity entity, Visual visual, float pitch) {
        ServerLevel level = (ServerLevel) entity.level();
        Vec3 at = bodyPos(entity);
        VisualService.playAt(level, at, visual);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, pitch);
    }

    public static Vec3 bodyPos(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
    }
}
