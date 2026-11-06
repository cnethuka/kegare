package com.cnethuka.kegare.rmt;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The route the green orb travels: a smooth curve through a few points. The server rolls it and checks looks against it; the
 * client is sent the same points only to draw it. {@link #route} is a random walk over the view, in fixed steps, so every leg
 * takes about the same time, and it always turns, so no two look alike.
 */
public final class OrbPath {
    private final List<Vec3> points;

    public OrbPath(List<Vec3> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("A path needs a point");
        this.points = List.copyOf(points);
    }

    public List<Vec3> points() {
        return points;
    }

    /** Where it is at {@code fraction} of the way along (0 = the start, 1 = the end). A Catmull-Rom curve through every point. */
    public Vec3 at(double fraction) {
        int last = points.size() - 1;
        if (last == 0 || fraction <= 0) return points.get(0);
        if (fraction >= 1) return points.get(last);
        double along = fraction * last;
        int i = Mth.floor(along);
        double t = along - i;
        return catmullRom(points.get(Math.max(i - 1, 0)), points.get(i), points.get(i + 1), points.get(Math.min(i + 2, last)), t);
    }

    private static Vec3 catmullRom(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        return new Vec3(catmullRom(a.x, b.x, c.x, d.x, t), catmullRom(a.y, b.y, c.y, d.y, t), catmullRom(a.z, b.z, c.z, d.z, t));
    }

    private static double catmullRom(double a, double b, double c, double d, double t) {
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (3 * b - a - 3 * c + d) * t * t * t);
    }

    /**
     * What a route looks like, in degrees.
     *
     * @param legs how many steps it takes
     * @param step how far each one goes
     * @param yawRange how far it may wander left and right of where the player was looking
     * @param pitchRange and up and down
     * @param maxTurn the sharpest turn between two legs; the gentlest is a third of this, so it never runs straight
     */
    public record Shape(int legs, float step, float yawRange, float pitchRange, float maxTurn) {}

    private static final int TRIES = 24;

    /**
     * A random route as offsets from where the player was looking: x is yaw, y is pitch. It stays in range, as long as
     * {@code start} is, so it can't lead them round behind themselves.
     */
    public static List<Vec2> route(RandomSource random, Vec2 start, Shape shape) {
        List<Vec2> out = new ArrayList<>(shape.legs + 1);
        out.add(start);
        Vec2 at = start;
        // Sets off roughly across the view.
        float heading = headingTo(at, Vec2.ZERO) + (random.nextFloat() - 0.5f) * shape.maxTurn;
        for (int leg = 0; leg < shape.legs; leg++) {
            Vec2 next = null;
            for (int attempt = 0; attempt < TRIES && next == null; attempt++) {
                float turn = leg == 0 ? 0 : shape.maxTurn / 3f + random.nextFloat() * shape.maxTurn * 2f / 3f;
                float candidate = heading + turn * (random.nextBoolean() ? 1 : -1);
                Vec2 to = step(at, candidate, shape.step);
                if (inRange(to, shape)) {
                    next = to;
                    heading = candidate;
                }
            }
            if (next == null) { // cornered: back toward the middle
                heading = headingTo(at, Vec2.ZERO);
                next = step(at, heading, shape.step);
            }
            out.add(next);
            at = next;
        }
        return out;
    }

    private static boolean inRange(Vec2 at, Shape shape) {
        return Math.abs(at.x) <= shape.yawRange && Math.abs(at.y) <= shape.pitchRange;
    }

    private static Vec2 step(Vec2 from, float heading, float length) {
        float radians = heading * Mth.DEG_TO_RAD;
        return new Vec2(from.x + Mth.cos(radians) * length, from.y + Mth.sin(radians) * length);
    }

    private static float headingTo(Vec2 from, Vec2 to) {
        return (float) Mth.atan2(to.y - from.y, to.x - from.x) * Mth.RAD_TO_DEG;
    }
}
