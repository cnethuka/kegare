package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.melee.client.MeleeClient;
import com.cnethuka.kegare.severed.BlurPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Drives Blur on its owner's client (DESIGN §3.27), the way a dash is driven: the client owns its player's movement, so the velocity
 * is set here before each tick's move and the game's own collision stops it (no clipping). Momentum: the velocity turns toward where
 * they steer at no more than {@code turnDegrees} a tick, so a sharp turn overshoots, and letting go skids to a stop. Anything that
 * slows movement (Slowness, a Decelerate hit, a Stasis Field) slows it the same share, and a hit's knockback becomes the momentum it
 * steers on from. No Blur speed in water or lava, on a mount, gliding or flying, or while a dash or a Closing Line is driving.
 */
public final class BlurDrive {
    static BlurPayload state = BlurPayload.OFF;
    private static Vec3 velocity = Vec3.ZERO;
    private static Vec3 last;

    private BlurDrive() {}

    static void set(BlurPayload payload) {
        if (!payload.on()) velocity = Vec3.ZERO;
        state = payload;
    }

    public static boolean on() {
        return state.on();
    }

    /** Horizontal speed it's driving at (blocks/tick), for the feel. */
    static double speed() {
        return velocity.horizontalDistance();
    }

    static Vec3 velocity() {
        return velocity;
    }

    static void reset() {
        state = BlurPayload.OFF;
        velocity = Vec3.ZERO;
        last = null;
    }

    /** Before the player moves this tick. */
    static void tick(LocalPlayer player) {
        Vec3 at = player.position();
        Vec3 moved = last == null ? Vec3.ZERO : new Vec3(at.x - last.x, 0, at.z - last.z);
        last = at;
        if (!state.on()) {
            velocity = Vec3.ZERO;
            return;
        }
        if (moved.lengthSqr() > 16) velocity = Vec3.ZERO; // moved by something else: a teleport, a swap
        else if (player.horizontalCollision && moved.horizontalDistance() < velocity.horizontalDistance()) velocity = moved; // a wall took it
        // A hit's knockback lands on the momentum instead of being driven over: the push is the new velocity, steered from there.
        if (player.hurtTime > 0 && player.hurtTime >= player.hurtDuration - 1) {
            Vec3 pushed = horizontal(player.getDeltaMovement());
            if (pushed.subtract(velocity).length() > 0.2) velocity = pushed;
        }
        boolean recovering = player.level().getGameTime() < state.recoveryUntil(); // after a Closing Line: no Blur speed
        if (MeleeClient.dashing() || RushDrive.active() || recovering || player.isInWater() || player.isInLava() || player.isPassenger()
                || player.isFallFlying() || player.getAbilities().flying) {
            velocity = horizontal(player.getDeltaMovement());
            return;
        }
        float forward = player.input.forwardImpulse, left = player.input.leftImpulse;
        double top = state.speed() * movementFactor(player);
        double speed = velocity.horizontalDistance();
        if (Math.abs(forward) + Math.abs(left) > 0.01f) {
            Vec3 want = direction(player.getYRot(), forward, left);
            Vec3 heading = speed < 0.05 ? want : turn(velocity.normalize(), want, state.turnDegrees() * Mth.DEG_TO_RAD);
            speed = speed > top ? Math.max(top, speed - state.skid()) : Math.min(top, speed + state.acceleration());
            velocity = heading.scale(speed);
        } else {
            speed = Math.max(0, speed - state.skid());
            velocity = speed < 1.0e-3 ? Vec3.ZERO : velocity.normalize().scale(speed);
        }
        if (velocity.lengthSqr() > 0) {
            // The game adds the movement keys' own push on top of what's set here; set that much less, so the move is the velocity.
            Vec3 push = walkingPush(player);
            player.setDeltaMovement(velocity.x - push.x, player.getDeltaMovement().y, velocity.z - push.z);
        }
    }

    /**
     * The push vanilla's own move adds from the movement keys ({@code LivingEntity.travel}: the keys at 0.98, at the ground's
     * friction-adjusted speed, or the air's), on top of any velocity set before it. Left in, Blur runs at its top speed plus a walk
     * (16.5 blocks a second at 0.7 a tick). Read from this tick's keys as they stand before the move.
     */
    static Vec3 walkingPush(LocalPlayer player) {
        float amount;
        if (player.onGround()) {
            net.minecraft.core.BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();
            float friction = player.level().getBlockState(below).getFriction(player.level(), below, player);
            amount = player.getSpeed() * (0.21600002F / (friction * friction * friction));
        } else {
            amount = player.isSprinting() ? 0.025999999F : 0.02F;
        }
        return inputVector(player.input.leftImpulse * 0.98F, player.input.forwardImpulse * 0.98F, amount, player.getYRot());
    }

    /** Vanilla's {@code Entity.getInputVector} for the keys alone (no up or down), as it works it out. */
    static Vec3 inputVector(float left, float forward, float amount, float yaw) {
        Vec3 relative = new Vec3(left, 0, forward);
        double sq = relative.lengthSqr();
        if (sq < 1.0E-7) return Vec3.ZERO;
        Vec3 v = (sq > 1.0 ? relative.normalize() : relative).scale(amount);
        float sin = Mth.sin(yaw * (float) (Math.PI / 180.0)), cos = Mth.cos(yaw * (float) (Math.PI / 180.0));
        return new Vec3(v.x * cos - v.z * sin, v.y, v.z * cos + v.x * sin);
    }

    /** What their movement speed is now against what it normally is (a sprint's own boost aside): Slowness 0.85, a Stasis Field 0.25. */
    static double movementFactor(LocalPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null || attribute.getBaseValue() <= 0) return 1;
        double value = attribute.getValue() / (player.isSprinting() ? 1.3 : 1.0);
        return Mth.clamp(value / attribute.getBaseValue(), 0.0, 1.5);
    }

    /** The world direction for movement keys at {@code yaw}. */
    static Vec3 direction(float yaw, float forward, float left) {
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD), cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        Vec3 d = new Vec3(left * cos - forward * sin, 0, forward * cos + left * sin);
        return d.lengthSqr() < 1.0e-6 ? Vec3.ZERO : d.normalize();
    }

    /** {@code from} turned toward {@code to} by at most {@code max} radians, in the horizontal plane. */
    static Vec3 turn(Vec3 from, Vec3 to, double max) {
        double a = Math.atan2(from.z, from.x), b = Math.atan2(to.z, to.x);
        double diff = Mth.wrapDegrees(Math.toDegrees(b - a)) * Mth.DEG_TO_RAD;
        double step = Mth.clamp(diff, -max, max);
        return new Vec3(Math.cos(a + step), 0, Math.sin(a + step));
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }
}
