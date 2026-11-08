package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.severed.RushPayload;
import javax.annotation.Nullable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Carries out a Closing Line the server has allowed (DESIGN §3.27), like a dash: the velocity set before each tick's move, measured
 * against how far it's really got, the game's own collision stopping it (a wall is a crash, which the server judges). Anything
 * slowing movement slows it the same share. It ends where the server said, keeping a little speed for the skid.
 */
final class RushDrive {
    private static final double END_KEEP = 0.12;
    private static final int SPARE_TICKS = 4;
    @Nullable private static Vec3 direction;
    @Nullable private static Vec3 start;
    private static double speed, distance, current;
    private static int ticksLeft;

    private RushDrive() {}

    static void start(RushPayload payload) {
        if (payload.distance() <= 0) {
            direction = null;
            return;
        }
        direction = new Vec3(payload.dirX(), 0, payload.dirZ());
        start = null;
        speed = payload.speed();
        distance = payload.distance();
        ticksLeft = (int) Math.ceil(distance / speed) + SPARE_TICKS;
    }

    static boolean active() {
        return direction != null;
    }

    static double speed() {
        return direction == null ? 0 : current;
    }

    static Vec3 direction() {
        return direction == null ? Vec3.ZERO : direction;
    }

    static void reset() {
        direction = null;
    }

    /** Before the player moves this tick. */
    static void tick(LocalPlayer player) {
        if (direction == null) return;
        boolean first = start == null;
        if (first) start = player.position();
        Vec3 rel = player.position().subtract(start);
        double along = rel.x * direction.x + rel.z * direction.z;
        double step = Math.min(speed * BlurDrive.movementFactor(player), distance - along);
        boolean blocked = !first && player.horizontalCollision && player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4;
        if (step <= 0.02 || blocked || --ticksLeft < 0 || rel.horizontalDistanceSqr() > (distance + 3) * (distance + 3)) {
            set(player, END_KEEP * speed);
            direction = null;
            current = 0;
            return;
        }
        current = step;
        set(player, step);
    }

    private static void set(LocalPlayer player, double horizontal) {
        if (direction == null) return;
        Vec3 velocity = direction.scale(horizontal);
        player.setDeltaMovement(velocity.x, player.getDeltaMovement().y, velocity.z);
    }
}
