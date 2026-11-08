package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.mixin.ChunkMapAccessor;
import com.cnethuka.kegare.mixin.ServerEntityAccessor;
import com.cnethuka.kegare.mixin.TrackedEntityAccessor;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The moving part of Swap. Every position swap goes through here: both move at once (a player or a mob), both fall
 * distances are reset (only Momentum Exchange keeps fall distance, on purpose, and it never moves anyone), and both
 * ends get their effects and the clap. Something bigger than the space it lands in is nudged up out of the blocks.
 * Callers fire {@link SwapTargetEvent} first.
 * <p>
 * Everyone watching gets the new position straight away (not at the end of the tick, as the tracker would send it), so both
 * moves land in the same frame for them; with {@code SwapClient}'s snap, nobody is seen sliding there.
 */
public final class Swapper {
    private Swapper() {}

    /** Swaps the caster's position with a player's or a mob's, and their facing too if asked, with a clap at both ends. */
    public static void swap(ServerPlayer caster, LivingEntity target, boolean facing, SwapSounds.Clap clap) {
        landed(caster, target, 0, 0, 0);
        Vec3 posA = caster.position();
        Vec3 posB = target.position();
        float yawA = caster.getYRot(), pitchA = caster.getXRot();
        float yawB = target.getYRot(), pitchB = target.getXRot();
        clap.at(caster);
        clap.at(target);
        depart(caster);
        depart(target);
        moveTo(caster, posB, facing ? yawB : yawA, facing ? pitchB : pitchA);
        moveTo(target, posA, facing ? yawA : yawB, facing ? pitchA : pitchB);
        arrive(caster);
        arrive(target);
    }

    /**
     * A Swap move landed on {@code target} (DESIGN §3.23): it goes through the Impact System like any hit, as a technique, but never
     * breaks the world (it's a clean magic trick). What it did is all it's worth: a plain swap is a light one.
     *
     * @param healthShare the shares of health it moved (Total Swap), else 0
     * @param motion      the speed it handed the target (Momentum Exchange), else 0
     * @param charge      1 for the ultimate, else 0
     */
    static void landed(ServerPlayer caster, LivingEntity target, double healthShare, double motion, double charge) {
        com.cnethuka.kegare.impact.Impacts.hit(caster, target, com.cnethuka.kegare.impact.ImpactKind.TECHNIQUE, healthShare, motion, charge, false);
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
        if (!(entity instanceof FakePlayer)) {
            // Watchers now, not when the tracker gets round to it (the moved player was told by teleportTo). The tracker is told
            // too: its next small step is sent relative to where it thinks watchers have the entity, and that is now here.
            ServerLevel level = (ServerLevel) entity.level();
            level.getChunkSource().broadcast(entity, new ClientboundTeleportEntityPacket(entity));
            if (((ChunkMapAccessor) level.getChunkSource().chunkMap).kegare$entityMap().get(entity.getId())
                    instanceof TrackedEntityAccessor tracked) {
                ((ServerEntityAccessor) tracked.kegare$serverEntity()).kegare$positionCodec().setBase(entity.trackingPosition());
            }
        }
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

    /**
     * Where someone leaves: a teal flicker and their afterimage. The payload names the entity, so each client draws the afterimage
     * from it (as that client sees it: a disguise included) before the move reaches them.
     */
    public static void depart(Entity entity) {
        effect(entity, SwapIds.DEPART);
    }

    /** Where someone arrives: the flicker again. */
    public static void arrive(Entity entity) {
        effect(entity, SwapIds.ARRIVE);
    }

    private static void effect(Entity entity, Visual visual) {
        VisualService.playAt((ServerLevel) entity.level(), bodyPos(entity), visual, entity.getId());
    }

    public static Vec3 bodyPos(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
    }
}
