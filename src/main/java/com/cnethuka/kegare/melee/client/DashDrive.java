package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.melee.MeleeDashPayload;
import javax.annotation.Nullable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Carries out a dash the server has allowed. The client owns its player's movement, so it goes here: velocity set before each
 * tick's move, measured against how far it's really got, so it covers the same ground every time. Vanilla collision applies, and
 * that's what stops it at a wall.
 */
final class DashDrive {
    /** Speed left when it ends, so it doesn't stop dead. */
    private static final double END_KEEP = 0.15;
    /** Ticks past what it should take, in case something (an entity, a slope) holds it up without blocking it. */
    private static final int SPARE_TICKS = 3;

    @Nullable private Vec3 direction;
    @Nullable private Vec3 start;
    private double speed;
    private double distance;
    private int ticksLeft;

    void start(MeleeDashPayload payload) {
        if (payload.distance() <= 0) {
            stop();
            return;
        }
        direction = new Vec3(payload.dirX(), 0, payload.dirZ());
        start = null; // measured from where they are on the next tick
        speed = payload.speed();
        distance = payload.distance();
        ticksLeft = (int) Math.ceil(distance / speed) + SPARE_TICKS;
    }

    void stop() {
        direction = null;
    }

    void tick(LocalPlayer player) {
        if (direction == null) return;
        boolean first = start == null;
        if (first) start = player.position();
        double step = DashMotion.speedAt(player.position().subtract(start).dot(direction), distance, speed);
        boolean blocked = !first && player.horizontalCollision && player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4;
        if (step <= 0 || blocked || --ticksLeft < 0) {
            set(player, END_KEEP * speed);
            direction = null;
            return;
        }
        set(player, step);
    }

    private void set(LocalPlayer player, double horizontal) {
        Vec3 velocity = direction.scale(horizontal);
        player.setDeltaMovement(velocity.x, player.getDeltaMovement().y, velocity.z);
    }
}
