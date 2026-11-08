package com.cnethuka.kegare.innate_technique.swap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor's throw leaves the right hand, where {@code anchor_throw} flicks it out (DESIGN §3.19), and still lands where a throw
 * from the eyes would: where the crosshair points, give or take a long throw's drop. The speed, the small random spread and
 * the thrower's own movement are worked out as always (a pearl's, from between the eyes). Then only the direction is turned a
 * little, so that the flight from the hand meets the old flight where it lands. If the hand is in a block or behind one, or no
 * such aim lands in the same place, the token leaves from the eyes as before.
 */
final class AnchorThrow {
    /**
     * The right hand where {@code anchor_throw} lets go (tools/anims/swap.py, its 0.13 s key: the arm raised 74°, out 32°), in
     * blocks from the eyes along the body: to the right, up, forward. Scaled with the player.
     */
    static final double HAND_RIGHT = 0.52, HAND_UP = -0.40, HAND_FORWARD = 0.50;
    /** How close the throw from the hand must land to the old landing to count as the same place. */
    static final double SAME_SPOT = 0.3;
    /** The longest flight followed (a token is never followed past its lifetime either). */
    private static final int MAX_TICKS = 1200;
    private static final int AIM_STEPS = 8;

    private AnchorThrow() {}

    /** Where a flight got to: where it met a block ({@code landed}), or where it was when it stopped being followed. */
    record Flight(Vec3 at, boolean landed, double ticks, double reach) {}

    /**
     * Moves a token that was just shot from the eyes ({@code shootFromRotation}) to the caster's throwing hand and turns its aim
     * to land in the same place, keeping its speed. Leaves it as it was if that can't be done cleanly.
     */
    static void fromHand(ServerPlayer caster, AnchorEntity token, int lifetimeTicks) {
        Vec3 fromEyes = token.position();
        Vec3 velocity = token.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0e-6) return;
        Vec3 hand = hand(caster);
        Vec3 from = hand.subtract(0, token.getBbHeight() / 2.0, 0); // the token's position is the bottom of its box
        Level level = caster.level();
        if (level.clip(new ClipContext(caster.getEyePosition(), hand, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                .getType() != HitResult.Type.MISS) return;
        AABB box = token.getType().getDimensions().makeBoundingBox(from);
        if (!level.noCollision(token, box)) return;

        int maxTicks = Math.min(MAX_TICKS, Math.max(1, lifetimeTicks));
        Flight old = fly(token, fromEyes, velocity, maxTicks);
        Vec3 aim = aim(token, from, velocity, old.at(), Math.min(maxTicks, (int) Math.ceil(old.ticks()) + 20));
        Flight now = fly(token, from, aim, maxTicks);
        if (now.landed() != old.landed() || now.at().distanceTo(old.at()) > SAME_SPOT) return;

        token.moveTo(from.x, from.y, from.z, token.getYRot(), token.getXRot());
        token.shoot(aim.x, aim.y, aim.z, (float) speed, 0f); // no spread: it was already in the velocity
    }

    /** The throwing hand, in the world: the right hand at the release, placed by the body's facing (the arm hangs from it). */
    static Vec3 hand(ServerPlayer caster) {
        float yaw = caster.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double scale = caster.getScale();
        return caster.getEyePosition()
                .add(right.scale(HAND_RIGHT * scale))
                .add(forward.scale(HAND_FORWARD * scale))
                .add(0, HAND_UP * scale, 0);
    }

    /**
     * A token's flight from {@code from} at {@code velocity}, tick by tick the way a thrown token moves (ThrowableProjectile:
     * it moves, then slows and falls), until it meets a block or {@code maxTicks} run out. It stops being followed where the
     * world isn't loaded, as the token itself would.
     */
    static Flight fly(Entity token, Vec3 from, Vec3 velocity, int maxTicks) {
        Level level = token.level();
        double gravity = token.getGravity();
        Vec3 p = from, v = velocity;
        double reach = 0, carry = 1;
        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3 next = p.add(v);
            if (!level.isLoaded(BlockPos.containing(next))) return new Flight(p, false, tick, reach);
            HitResult hit = level.clip(new ClipContext(p, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, token));
            if (hit.getType() != HitResult.Type.MISS) {
                double part = p.distanceTo(hit.getLocation()) / Math.max(1.0e-6, v.length());
                return new Flight(hit.getLocation(), true, tick + part, reach + carry * part);
            }
            double drag = drag(level, p);
            p = next;
            reach += carry;
            carry *= drag;
            v = v.scale(drag).add(0, -gravity, 0);
        }
        return new Flight(p, false, maxTicks, reach);
    }

    /**
     * The direction from {@code from}, at {@code velocity}'s speed, whose flight passes through {@code goal}: each step finds where
     * the flight comes nearest the goal and turns the aim by the miss over how far a change of aim has carried it by then.
     */
    static Vec3 aim(Entity token, Vec3 from, Vec3 velocity, Vec3 goal, int maxTicks) {
        double speed = velocity.length();
        Vec3 aim = velocity;
        for (int i = 0; i < AIM_STEPS; i++) {
            Flight nearest = nearest(token, from, aim, goal, maxTicks);
            Vec3 miss = goal.subtract(nearest.at());
            if (miss.lengthSqr() < 1.0e-6) break;
            aim = aim.add(miss.scale(1.0 / Math.max(1.0, nearest.reach()))).normalize().scale(speed);
        }
        return aim;
    }

    /** The point of a flight, blocks ignored, that comes nearest {@code goal}. */
    private static Flight nearest(Entity token, Vec3 from, Vec3 velocity, Vec3 goal, int maxTicks) {
        Level level = token.level();
        double gravity = token.getGravity();
        Vec3 p = from, v = velocity;
        double reach = 0, carry = 1;
        Flight best = new Flight(from, false, 0, 0);
        double bestDistance = from.distanceToSqr(goal);
        for (int tick = 0; tick < maxTicks; tick++) {
            double t = v.lengthSqr() < 1.0e-12 ? 0 : Mth.clamp(goal.subtract(p).dot(v) / v.lengthSqr(), 0, 1);
            Vec3 at = p.add(v.scale(t));
            double distance = at.distanceToSqr(goal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Flight(at, false, tick + t, reach + carry * t);
            }
            double drag = drag(level, p);
            p = p.add(v);
            reach += carry;
            carry *= drag;
            v = v.scale(drag).add(0, -gravity, 0);
        }
        return best;
    }

    /** A thrown token keeps 0.99 of its speed a tick in the air, 0.8 in water. */
    private static double drag(Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER) ? 0.8 : 0.99;
    }
}
