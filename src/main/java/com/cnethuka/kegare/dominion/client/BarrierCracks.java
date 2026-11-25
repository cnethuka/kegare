package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.dominion.DominionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A barrier's cracks (DESIGN §3.35): white and glowing on its black shell, spreading out from where it was hit. Each hit lands on a
 * star of cracks: a new one where nothing has cracked yet, or the one already there, which reaches further. Every star reaches further
 * as the whole barrier weakens, so a barrier near breaking is cracked all over. A star's shape comes from where it is (the same on every
 * client), and it grows into its reach over a few ticks rather than at once. Only geometry: no texture, so the lines stay sharp up close
 * and far off.
 */
public final class BarrierCracks {
    /** At most this many stars on one barrier; a hit past that adds to the nearest. */
    static final int MAX_STARS = 24;
    /** A hit this close (radians round the sphere) to a star adds to it instead of starting another. */
    static final double MERGE = 0.14;
    /** The furthest a crack can run from its star, in radians round the sphere. */
    static final float MAX_REACH = 1.35f;
    private static final float CORE = 0.055f, HALO = 0.2f;
    /** As the barrier weakens, new fractures open across it: one for every this much of its strength gone. */
    static final float SPREAD_EVERY = 0.12f;

    /** One crack: points round the sphere (unit vectors from its centre), how far along the crack each is (radians), and its width. */
    record Line(Vec3[] points, float[] at, float width) {
        float length() {
            return at[at.length - 1];
        }
    }

    /** Where it was hit, everything that has cracked there, and how far its cracks reach now and are growing toward. */
    public static final class Star {
        final Vec3 dir;
        final List<Line> lines;
        float energy;
        float shown;
        /** Opened by the barrier weakening, not by a hit. */
        boolean spread;

        Star(Vec3 dir, float energy) {
            this.dir = dir;
            this.energy = energy;
            this.lines = lines(dir, seed(dir));
        }

        /** How far its cracks reach now (radians), as it grows into its reach. */
        public float shown() {
            return shown;
        }
    }

    private BarrierCracks() {}

    @SuppressWarnings("unchecked")
    static List<Star> stars(DominionEntity d) {
        DominionEntity.ClientCache cache = d.clientCache();
        if (cache.cracks == null) cache.cracks = new ArrayList<Star>();
        return (List<Star>) cache.cracks;
    }

    /** A hit on the barrier at {@code at}, taking {@code share} of its whole strength. */
    public static void hit(DominionEntity d, Vec3 at, float share) {
        Vec3 dir = at.subtract(d.centre());
        if (dir.lengthSqr() < 1.0e-6) return;
        add(stars(d), dir.normalize(), Math.max(0.01f, share));
    }

    static void add(List<Star> stars, Vec3 dir, float share) {
        Star nearest = null;
        double best = Double.MAX_VALUE;
        for (Star s : stars) {
            double angle = Math.acos(Mth.clamp(s.dir.dot(dir), -1, 1));
            if (angle < best) {
                best = angle;
                nearest = s;
            }
        }
        if (nearest != null && (best < MERGE || stars.size() >= MAX_STARS)) nearest.energy += share;
        else stars.add(new Star(dir, share));
    }

    /** How far a star's cracks reach (radians): further for what it has taken itself, and for how weak the whole barrier is now. */
    static float reach(float energy, float damage) {
        return Mth.clamp(0.1f + 2.4f * energy + 1.0f * damage, 0f, MAX_REACH);
    }

    /**
     * Every client tick: stars grow toward their reach (most of the way in about a third of a second). A barrier damaged with no stars
     * yet (seen first after it was hit, or one that closes already cracked) gets some where its damage says, the same for everyone.
     */
    static void tick(DominionEntity d, float damage) {
        List<Star> stars = stars(d);
        // New fractures across it as it weakens: where, the same for everyone (by where it stands), however it was hit.
        int wanted = (int) (damage / SPREAD_EVERY), have = 0;
        for (Star s : stars) if (s.spread) have++;
        if (have < wanted) {
            RandomSource rng = RandomSource.create(Mth.floor(d.centre().x) * 7919L ^ Mth.floor(d.centre().z) * 104729L ^ Mth.floor(d.centre().y));
            for (int i = 0; i < wanted; i++) {
                double yaw = rng.nextDouble() * Mth.TWO_PI, up = Math.asin(0.15 + rng.nextDouble() * 0.8);
                if (i < have) continue; // already open
                Star s = new Star(new Vec3(Math.cos(yaw) * Math.cos(up), Math.sin(up), Math.sin(yaw) * Math.cos(up)), 0f);
                s.spread = true;
                stars.add(s);
            }
        }
        for (Star s : stars) s.shown += (reach(s.energy, damage) - s.shown) * 0.3f;
    }

    // ---------------------------------------------------------------------------------------------- shape

    static long seed(Vec3 dir) {
        return Math.round(dir.x * 1000) * 73856093L ^ Math.round(dir.y * 1000) * 19349663L ^ Math.round(dir.z * 1000) * 83492791L;
    }

    /**
     * A star's cracks: five to seven running out from it in straight runs with sharp kinks, now and then a branch off one. Worked out in
     * the flat (radians across the sphere at the star) and laid onto the sphere, so a crack keeps its length however big the barrier is.
     */
    static List<Line> lines(Vec3 dir, long seed) {
        RandomSource rng = RandomSource.create(seed);
        Vec3 e1 = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0, 1, 0)).normalize() : dir.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = dir.cross(e1).normalize();
        List<Line> out = new ArrayList<>();
        int arms = 5 + rng.nextInt(3);
        for (int i = 0; i < arms; i++) {
            double heading = Mth.TWO_PI * i / arms + (rng.nextDouble() - 0.5) * 0.7;
            grow(out, rng, dir, e1, e2, 0, 0, heading, 0f, 1f, 16 + rng.nextInt(8), 0);
        }
        return out;
    }

    private static void grow(List<Line> out, RandomSource rng, Vec3 dir, Vec3 e1, Vec3 e2, double u, double v, double heading, float at,
            float width, int steps, int depth) {
        List<Vec3> points = new ArrayList<>();
        List<Float> ats = new ArrayList<>();
        points.add(onSphere(dir, e1, e2, u, v));
        ats.add(at);
        for (int k = 0; k < steps; k++) {
            // Straight runs and sharp kinks, as glass gives way: never a smooth curve.
            heading += rng.nextFloat() < 0.35f ? (rng.nextDouble() - 0.5) * 1.8 : (rng.nextDouble() - 0.5) * 0.2;
            double step = 0.05 + rng.nextDouble() * 0.06;
            u += Math.cos(heading) * step;
            v += Math.sin(heading) * step;
            at += (float) step;
            points.add(onSphere(dir, e1, e2, u, v));
            ats.add(at);
            if (depth < 2 && k > 1 && rng.nextFloat() < 0.2f) {
                double side = rng.nextBoolean() ? 1 : -1;
                grow(out, rng, dir, e1, e2, u, v, heading + side * (0.5 + rng.nextDouble() * 0.6), at, width * 0.6f, 3 + rng.nextInt(5), depth + 1);
            }
        }
        float[] a = new float[ats.size()];
        for (int k = 0; k < a.length; k++) a[k] = ats.get(k);
        out.add(new Line(points.toArray(new Vec3[0]), a, width));
    }

    /** (u, v) radians across the sphere from {@code dir}, as a point on it (a unit vector). */
    static Vec3 onSphere(Vec3 dir, Vec3 e1, Vec3 e2, double u, double v) {
        double rho = Math.sqrt(u * u + v * v);
        if (rho < 1.0e-9) return dir;
        Vec3 t = e1.scale(u / rho).add(e2.scale(v / rho));
        return dir.scale(Math.cos(rho)).add(t.scale(Math.sin(rho))).normalize();
    }

    // ---------------------------------------------------------------------------------------------- drawing

    /**
     * Its cracks as far as each star reaches, on the sphere of radius {@code r} (cut where the barrier hasn't risen to, {@code top}).
     * Seen from outside: a white core with a soft white glow round it. From inside, against the lighter sky: the core with a dark rim.
     * The glow sits a hair behind the core, so it never draws over it.
     */
    static void draw(VertexConsumer out, PoseStack.Pose p, List<Star> stars, float r, float top, boolean inside, float alpha) {
        float lift = inside ? -1f : 1f;
        float coreR = r * (1f + lift * 0.006f), haloR = r * (1f + lift * 0.004f);
        int haloRgb = inside ? 0x000000 : 0xFFFFFF;
        int haloA = Math.round((inside ? 150 : 110) * alpha), coreA = Math.round(255 * alpha);
        for (Star s : stars) {
            float reach = s.shown;
            if (reach <= 1.0e-3f) continue;
            for (Line line : s.lines) {
                if (line.at[0] >= reach) continue;
                for (int k = 0; k + 1 < line.points.length; k++) {
                    float a0 = line.at[k], a1 = line.at[k + 1];
                    if (a0 >= reach) break;
                    Vec3 from = line.points[k], to = line.points[k + 1];
                    if (a1 > reach) {
                        to = from.add(to.subtract(from).scale((reach - a0) / (a1 - a0))).normalize();
                        a1 = reach;
                    }
                    if (from.y * r > top || to.y * r > top) continue; // not risen there yet
                    // Thinner toward the tip of what's grown so far.
                    float w0 = line.width * (1f - 0.65f * a0 / reach), w1 = line.width * (1f - 0.65f * a1 / reach);
                    ribbon(out, p, from.scale(haloR), to.scale(haloR), HALO * w0, HALO * w1, haloRgb, haloA, true);
                    ribbon(out, p, from.scale(coreR), to.scale(coreR), CORE * w0 + 0.01f, CORE * w1 + 0.01f, 0xFFFFFF, coreA, false);
                }
            }
        }
    }

    /**
     * A strip along the sphere from {@code a} to {@code b}, {@code w0} to {@code w1} wide each side. Faded to nothing at its edges when
     * {@code soft} (a glow), solid otherwise.
     */
    static void ribbon(VertexConsumer out, PoseStack.Pose p, Vec3 a, Vec3 b, float w0, float w1, int rgb, int alpha, boolean soft) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0e-10) return;
        Vec3 side = along.cross(a.add(b)).normalize();
        Vec3 s0 = side.scale(w0), s1 = side.scale(w1);
        int edge = soft ? 0 : alpha;
        // Each half from the middle out, so a glow fades to its edges.
        quad(out, p, a, b, b.add(s1), a.add(s0), rgb, alpha, alpha, edge, edge);
        quad(out, p, a, b, b.subtract(s1), a.subtract(s0), rgb, alpha, alpha, edge, edge);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose p, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, int rgb, int a0, int a1, int a2, int a3) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        Vec3[] vs = {v0, v1, v2, v3};
        int[] as = {a0, a1, a2, a3};
        for (int i = 0; i < 4; i++) {
            Vec3 v = vs[i];
            // Facing up, where the entity shader's two lights are: lit in full, so white stays white whatever way the shell faces.
            out.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, Mth.clamp(as[i], 0, 255)).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 1f, 0f);
        }
    }
}
