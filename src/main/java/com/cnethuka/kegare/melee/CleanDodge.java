package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.MeleeMoves;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Did a dash really dodge something? A dash leaves a "ghost": the player's box at the moment they left. For the next
 * {@code dodgeWindow} seconds, anything aimed at the ghost that isn't aimed at the player now counts, once:
 * <ul>
 *   <li>a <b>melee swing</b>, checked each tick: a swing just started whose reach reaches the ghost (a player's look ray; a mob's
 *       attack box) and not the player;</li>
 *   <li>a <b>projectile</b>, checked each tick: one about to cross the ghost and not the player;</li>
 *   <li>an <b>ability area</b>: area abilities report their area as they go off ({@link #areaWentOff}).</li>
 * </ul>
 * Any real hit during the window ends it: that wasn't clean. Your own swings, shots, areas and puppets don't count.
 */
public final class CleanDodge {
    /** How many ticks ahead a projectile is followed (the fastest move a couple of blocks a tick). */
    private static final double PROJECTILE_LOOKAHEAD = 2.0;
    private static final double SEARCH = 8.0;
    private static final double MARGIN = 0.1;
    /** The game's default melee reach for a mob, past its own box. */
    private static final double MOB_REACH = 0.83;

    private CleanDodge() {}

    static void start(ServerPlayer player, MeleeState state) {
        state.ghost = player.getBoundingBox();
        state.ghostUntil = player.level().getGameTime() + MeleeConfig.ticks(MeleeConfig.DODGE_WINDOW.get());
    }

    static void tick(ServerPlayer player, MeleeState state) {
        if (state.ghost == null) return;
        if (player.level().getGameTime() > state.ghostUntil) {
            state.ghost = null;
            return;
        }
        AABB ghost = state.ghost;
        AABB now = player.getBoundingBox();
        AABB search = ghost.inflate(SEARCH);
        for (LivingEntity attacker : player.level().getEntitiesOfClass(LivingEntity.class, search, e -> e != player && e.isAlive() && e.swinging && e.swingTime <= 0)) {
            if (!Attribution.isOwn(player, attacker) && swingReaches(attacker, ghost) && !swingReaches(attacker, now)) {
                dodged(player, state, ghost.getCenter(), attacker);
                return;
            }
        }
        for (Projectile shot : player.level().getEntitiesOfClass(Projectile.class, search, e -> e.isAlive() && e.getOwner() != player)) {
            if (!Attribution.isOwn(player, shot) && crosses(shot, ghost) && !crosses(shot, now)) {
                dodged(player, state, ghost.getCenter(), shot);
                return;
            }
        }
    }

    /** A hit landed on them: whatever else was aimed at them, this dash wasn't clean. */
    static void wasHit(MeleeState state) {
        state.ghost = null;
    }

    /**
     * An ability's area has just gone off. {@code covers} says whether it would catch a box, by the same test the ability uses
     * on its targets. Costs nothing unless someone nearby has just dashed.
     */
    public static void areaWentOff(ServerLevel level, @Nullable Entity owner, Predicate<AABB> covers) {
        for (ServerPlayer player : level.players()) {
            if (!player.hasData(MeleeLayer.ATTACHMENT)) continue;
            MeleeState state = MeleeLayer.state(player);
            if (state.ghost == null || level.getGameTime() > state.ghostUntil || player == owner) continue;
            if (owner != null && Attribution.isOwn(player, owner)) continue;
            if (covers.test(state.ghost) && !covers.test(player.getBoundingBox())) dodged(player, state, state.ghost.getCenter(), owner);
        }
    }

    /** {@link #areaWentOff}'s {@code covers} for a sphere, measured to a box's centre as the area abilities measure targets. */
    public static Predicate<AABB> sphere(Vec3 center, double radius) {
        return box -> box.getCenter().distanceTo(center) <= radius;
    }

    private static void dodged(ServerPlayer player, MeleeState state, Vec3 at, @Nullable Entity from) {
        state.ghost = null;
        MeleeImpact.CLEAN_DODGE.fire(player.serverLevel(), at, player);
        ReinforcedStrike.earn(player, state);
        Entity thrower = from instanceof Projectile shot ? shot.getOwner() : from;
        if (thrower instanceof ServerPlayer opponent && Attribution.isRealTarget(player, opponent)) FightTracker.meleeMove(player, opponent, MeleeMoves.CLEAN_DODGE);
    }

    private static boolean swingReaches(LivingEntity attacker, AABB box) {
        if (attacker instanceof Player player) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1f).scale(player.entityInteractionRange()));
            BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 reach = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
            return box.inflate(MARGIN).contains(eye) || box.inflate(MARGIN).clip(eye, reach).isPresent();
        }
        return attacker instanceof Mob && attacker.getBoundingBox().inflate(MOB_REACH, 0, MOB_REACH).intersects(box);
    }

    private static boolean crosses(Projectile shot, AABB box) {
        Vec3 velocity = shot.getDeltaMovement();
        if (velocity.lengthSqr() < 0.01) return false; // stuck in a wall, or just lying there
        Vec3 from = shot.position();
        Vec3 to = from.add(velocity.scale(PROJECTILE_LOOKAHEAD));
        BlockHitResult wall = shot.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        return box.inflate(0.3).clip(from, to).isPresent() || box.inflate(0.3).contains(from);
    }
}
