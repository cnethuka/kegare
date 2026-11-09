package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.severed.RushPayload;
import javax.annotation.Nullable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Carries out a Closing Line the server has allowed (DESIGN §3.27), like a dash: the velocity set before each tick's move along a
 * straight line in three dimensions (a leap up to a ledge, or down off one), measured against how far it's really got, the game's
 * own collision stopping it (a wall is a crash, which the server judges). Anything slowing movement slows it the same share. A lip
 * no higher than a step is stepped over, as a run would; the ground under a line that dips, or a ceiling over one that rises, only
 * holds them, and they're drawn back onto the line once past it. It ends where the server said, keeping a little speed for the skid,
 * and gravity takes over from there.
 */
final class RushDrive {
    private static final double END_KEEP = 0.12;
    private static final int SPARE_TICKS = 4;
    /** The most of a tick's travel spent drawing them back onto the line, as a share of its speed. */
    private static final double PULL = 0.25;
    /** How finely a tick's travel is checked for a lip to step over, in blocks. */
    private static final double STEP_SAMPLE = 0.5;
    @Nullable private static Vec3 direction;
    @Nullable private static Vec3 start;
    private static double speed, distance, current;
    private static int ticksLeft;
    /** The server stopped it: the end is applied before their next move, so none of the rush's speed carries on. */
    private static boolean stopping;

    private RushDrive() {}

    static void start(RushPayload payload) {
        if (payload.distance() <= 0) {
            if (direction != null) stopping = true;
            return;
        }
        Vec3 d = new Vec3(payload.dirX(), payload.dirY(), payload.dirZ());
        if (d.lengthSqr() < 1.0e-6) return;
        direction = d.normalize();
        start = null;
        stopping = false;
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
        stopping = false;
    }

    /** Before the player moves this tick. */
    static void tick(LocalPlayer player) {
        if (direction == null) return;
        if (stopping) {
            end(player);
            return;
        }
        boolean first = start == null;
        if (first) start = player.position();
        Vec3 at = player.position();
        Vec3 rel = at.subtract(start);
        double along = rel.dot(direction);
        double factor = BlurDrive.movementFactor(player);
        double step = Math.min(speed * factor, distance - along);
        boolean blocked = !first && player.horizontalCollision && player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4;
        if (step <= 0.02 || blocked || --ticksLeft < 0 || rel.lengthSqr() > (distance + 3) * (distance + 3)) {
            end(player);
            return;
        }
        current = step;
        Vec3 velocity = direction.scale(step);
        // Back onto the line, if the ground, a ceiling or a step put them off it.
        Vec3 pull = start.add(direction.scale(along)).subtract(at);
        double most = speed * factor * PULL;
        if (pull.lengthSqr() > most * most) pull = pull.normalize().scale(most);
        player.setDeltaMovement(stepOver(player, velocity.add(pull)));
    }

    /**
     * Done: a little of its speed kept for the skid, none of it upward, and gravity has them from here (its first tick at once, so on
     * level ground they're on it again and skid with its friction, not the air's).
     */
    private static void end(LocalPlayer player) {
        if (direction != null) {
            Vec3 keep = direction.scale(END_KEEP * speed);
            player.setDeltaMovement(keep.x, Math.min(0, keep.y) - player.getGravity(), keep.z);
        }
        direction = null;
        stopping = false;
        current = 0;
    }

    /**
     * {@code velocity}, lifted by as little as clears a lip in its way no higher than their step height (a slab, a stair, the edge of
     * a ledge the line just grazes). The game only steps a player up on the ground, and most of a rush is in the air. A wall, too
     * high to step over, is left for the game's collision to stop them at.
     */
    static Vec3 stepOver(LocalPlayer player, Vec3 velocity) {
        AABB box = player.getBoundingBox().deflate(1.0e-3); // resting on the ground isn't in it
        double most = player.maxUpStep(), lift = 0;
        int samples = Math.max(1, (int) Math.ceil(velocity.length() / STEP_SAMPLE));
        for (int i = 1; i <= samples; i++) {
            Vec3 part = velocity.scale(i / (double) samples);
            if (player.level().noCollision(player, box.move(part.x, part.y + lift, part.z))) continue;
            double up = lift;
            boolean cleared = false;
            while (!cleared && (up += 0.125) <= most + 1.0e-6) cleared = player.level().noCollision(player, box.move(part.x, part.y + up, part.z));
            if (!cleared) break;
            lift = up;
        }
        return lift > 0 ? velocity.add(0, lift, 0) : velocity;
    }
}
