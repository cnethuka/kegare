package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.api.dominion.DominionLook;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A Dominion (DESIGN §3.35), drawn. Opening: ink floods the ground from its caster's feet and the barrier expands from them as a sphere,
 * thin and smoky at first, solid black as it reaches its size; sealing, it snaps solid with a pulse. Sealed: from the world a smooth,
 * solid wall of its shell color (pitch black for the Incomplete Dominion), and inside its inner space a sky, hazy at the horizon and
 * darker overhead (the space itself is real blocks). White cracks glow on both sides where it's been hit and spread as it weakens
 * ({@link BarrierCracks}). Contested in a clash, it flickers half-there and a bright seam grinds where the two meet.
 */
public class DominionRenderer extends EntityRenderer<DominionEntity> {
    static final ResourceLocation WHITE = Kegare.id("textures/marks/white.png");
    /** Fine enough that its outline is a curve, not a polygon, close up. */
    private static final int SLICES = 96, STACKS = 48;
    /** The ink has spread to its full reach this far through the opening. */
    static final float INK_SPREAD = 0.35f;
    /** How far off a top face the ink is drawn, so it never flickers into it. */
    private static final float LIFT = 0.012f;
    /** Re-read the ground's heights this often while it opens. */
    private static final int GROUND_REFRESH = 10;
    /** The pulse as it seals, in ticks. */
    private static final int SEAL_TICKS = 4;

    public DominionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DominionEntity entity) {
        return WHITE;
    }

    @Override
    public void render(DominionEntity d, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        DominionLook look = d.look();
        float time = d.tickCount + partialTick;
        boolean clash = d.state() == DominionEntity.State.CLASH;
        float flicker = clash ? 0.5f + 0.12f * Mth.sin(time * 0.9f + d.getId()) + 0.05f * Mth.sin(time * 2.3f) : 1f;
        PoseStack.Pose p = pose.last();
        float r = (float) d.radius();
        List<BarrierCracks.Star> stars = BarrierCracks.stars(d);

        if (d.isInner() || d.localTrapped()) {
            // Inside: its sky, hazy (its fog color) at the horizon, its sky color overhead, opaque, so no other sky shows.
            shell(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), p, r, r, look.fogColor(), look.skyColor(), flicker);
            if (!stars.isEmpty()) BarrierCracks.draw(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), p, stars, r, r, true, flicker);
        } else if (d.state() == DominionEntity.State.OPENING) {
            opening(d, partialTick, pose, buffers, mc.level, look);
        } else {
            // Sealed, from the world: a solid wall, swelling a moment as it seals.
            int sealedFor = d.closedAt() < 0 ? SEAL_TICKS : d.tickCount - d.closedAt();
            float pulse = sealedFor < SEAL_TICKS ? 1f + 0.05f * (1f - (sealedFor + partialTick) / SEAL_TICKS) : 1f;
            shell(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), p, r * pulse, r * pulse, look.shellColor(), look.shellColor(), flicker);
            if (!stars.isEmpty()) BarrierCracks.draw(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), p, stars, r, r, false, flicker);
        }
        // The seam where two clashing barriers meet: drawn once, by the one with the lower id.
        if (clash && d.level().getEntity(d.clashWith()) instanceof DominionEntity other && d.getId() < other.getId()) seam(d, other, time, pose, buffers);
    }

    // ---------------------------------------------------------------------------------------------- opening

    /** How far through its opening it is, 0 to 1. */
    static float opened(DominionEntity d, float partialTick) {
        return Mth.clamp((d.tickCount + partialTick) / Math.max(1, d.openTicks()), 0f, 1f);
    }

    /** The barrier's radius while it opens: out from the caster fast, easing into its full size. */
    static float openRadius(DominionEntity d, float t) {
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        return Math.max(0.4f, (float) d.radius() * ease);
    }

    /** The ink's reach on the ground while it opens: out to the barrier's radius in the first third. */
    static float inkReach(DominionEntity d, float t) {
        return (float) d.radius() * Math.min(1f, t / INK_SPREAD);
    }

    /** Ink spreading over the ground from the caster, and the barrier expanding from them: smoky and see-through, then solid. */
    private static void opening(DominionEntity d, float partialTick, PoseStack pose, MultiBufferSource buffers, Level level, DominionLook look) {
        float t = opened(d, partialTick);
        ink(d, t, pose, buffers, level);
        PoseStack.Pose p = pose.last();
        float r = openRadius(d, t);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        // A thin haze just ahead of it, gone as it thickens: its front, still smoke.
        shell(out, p, r * 1.07f, r * 1.07f, look.shellColor(), look.shellColor(), 0.28f * (1f - t));
        shell(out, p, r, r, look.shellColor(), look.shellColor(), 0.3f + 0.7f * t * t);
    }

    /** Black over the ground's top faces round the caster, its edge ragged and soft, out to {@link #inkReach}. */
    private static void ink(DominionEntity d, float t, PoseStack pose, MultiBufferSource buffers, Level level) {
        float reach = inkReach(d, t);
        if (reach < 0.3f) return;
        int[] cols = columns(d, level);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        PoseStack.Pose p = pose.last();
        Vec3 c = d.centre();
        float phase = d.getId() * 1.7f;
        for (int i = 0; i < cols.length; i += 3) {
            int x = cols[i], y = cols[i + 1], z = cols[i + 2];
            double dx = x + 0.5 - c.x, dz = z + 0.5 - c.z, dist = Math.sqrt(dx * dx + dz * dz);
            double angle = Math.atan2(dz, dx);
            double edge = reach * (0.86 + 0.08 * Math.sin(angle * 5 + phase) + 0.06 * Math.sin(angle * 11 + phase * 2.3));
            if (dist > edge) continue;
            int alpha = Math.round(235 * Mth.clamp((float) ((edge - dist) / 1.5), 0f, 1f));
            if (alpha <= 0) continue;
            float fx = (float) (x - c.x), fy = (float) (y - c.y) + LIFT, fz = (float) (z - c.z);
            float[][] corners = {{fx, fz}, {fx, fz + 1}, {fx + 1, fz + 1}, {fx + 1, fz}};
            for (float[] corner : corners) {
                out.addVertex(p, corner[0], fy, corner[1]).setColor(0, 0, 0, alpha).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 1f, 0f);
            }
        }
    }

    /** Every top face of the ground round it (from the heightmap a client has: MOTION_BLOCKING; it's never sent the others), kept a while. */
    private static int[] columns(DominionEntity d, Level level) {
        DominionEntity.ClientCache cache = d.clientCache();
        if (cache.columns != null && d.tickCount - cache.columnsAt < GROUND_REFRESH) return cache.columns;
        Vec3 c = d.centre();
        int r = Mth.ceil(d.radius());
        List<Integer> out = new ArrayList<>();
        for (int x = Mth.floor(c.x) - r; x <= Mth.floor(c.x) + r; x++) {
            for (int z = Mth.floor(c.z) - r; z <= Mth.floor(c.z) + r; z++) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (!d.contains(new Vec3(x + 0.5, y, z + 0.5))) continue;
                out.add(x);
                out.add(y);
                out.add(z);
            }
        }
        cache.columns = out.stream().mapToInt(Integer::intValue).toArray();
        cache.columnsAt = d.tickCount;
        return cache.columns;
    }

    // ---------------------------------------------------------------------------------------------- the shell

    /** The sphere, smooth and untextured, cut above {@code top}. Colored {@code low} at the horizon and {@code high} overhead. */
    static void shell(VertexConsumer out, PoseStack.Pose p, float r, float top, int low, int high, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 0) return;
        for (int i = 0; i < STACKS; i++) {
            float v0 = (float) i / STACKS, v1 = (float) (i + 1) / STACKS;
            float a0 = Mth.PI * (v0 - 0.5f), a1 = Mth.PI * (v1 - 0.5f);
            float y0 = Mth.sin(a0) * r, y1 = Mth.sin(a1) * r;
            if (y0 > top) break;
            y1 = Math.min(y1, top);
            float r0 = Mth.cos(a0) * r, r1 = (float) Math.sqrt(Math.max(0, r * r - y1 * y1));
            int c0 = color(low, high, Math.max(0f, y0 / r)), c1 = color(low, high, Math.max(0f, y1 / r));
            for (int j = 0; j < SLICES; j++) {
                float t0 = Mth.TWO_PI * j / SLICES, t1 = Mth.TWO_PI * (j + 1) / SLICES;
                float x00 = Mth.cos(t0) * r0, z00 = Mth.sin(t0) * r0, x01 = Mth.cos(t1) * r0, z01 = Mth.sin(t1) * r0;
                float x10 = Mth.cos(t0) * r1, z10 = Mth.sin(t0) * r1, x11 = Mth.cos(t1) * r1, z11 = Mth.sin(t1) * r1;
                vertex(out, p, x00, y0, z00, c0, a);
                vertex(out, p, x01, y0, z01, c0, a);
                vertex(out, p, x11, y1, z11, c1, a);
                vertex(out, p, x10, y1, z10, c1, a);
            }
        }
    }

    private static int color(int low, int high, float t) {
        int r = Math.round(Mth.lerp(t, low >> 16 & 0xFF, high >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, low >> 8 & 0xFF, high >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, low & 0xFF, high & 0xFF));
        return r << 16 | g << 8 | b;
    }

    static int brighter(int rgb, float by) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return Math.round(r + (255 - r) * by) << 16 | Math.round(g + (255 - g) * by) << 8 | Math.round(b + (255 - b) * by);
    }

    /** Facing up, where the entity shader's two lights are: lit in full, so its color is the same all round (no shading on a sky or a wall). */
    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, int rgb, int a) {
        out.addVertex(p, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 1f, 0f);
    }

    // ---------------------------------------------------------------------------------------------- a clash's seam

    /** Where two spheres meet: the circle they share, as (centre, axis, radius), or empty if they don't. */
    static Optional<double[]> meeting(Vec3 c1, double r1, Vec3 c2, double r2) {
        Vec3 axis = c2.subtract(c1);
        double dist = axis.length();
        if (dist < 1.0e-6 || dist >= r1 + r2 || dist <= Math.abs(r1 - r2)) return Optional.empty();
        double a = (dist * dist + r1 * r1 - r2 * r2) / (2 * dist);
        double h = Math.sqrt(Math.max(0, r1 * r1 - a * a));
        Vec3 n = axis.scale(1 / dist), m = c1.add(n.scale(a));
        return Optional.of(new double[] {m.x, m.y, m.z, n.x, n.y, n.z, h});
    }

    /**
     * A bright band round the circle where the two barriers grind against each other, jittering: two strips crossed (one flat in the
     * circle's plane, one across it), so it reads from every side, lifted just off the shells so it never flickers into them.
     */
    private static void seam(DominionEntity d, DominionEntity other, float time, PoseStack pose, MultiBufferSource buffers) {
        Optional<double[]> meet = meeting(d.centre(), d.radius(), other.centre(), other.radius());
        if (meet.isEmpty()) return;
        double[] m = meet.get();
        Vec3 mid = new Vec3(m[0], m[1], m[2]).subtract(d.centre()), n = new Vec3(m[3], m[4], m[5]);
        Vec3 e1 = Math.abs(n.y) < 0.9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = n.cross(e1).normalize();
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PoseStack.Pose p = pose.last();
        int segments = 96;
        float pulse = 0.7f + 0.3f * Mth.sin(time * 1.7f);
        int alpha = Math.round(230 * pulse);
        double h = m[6] + 0.12; // just outside the crease the two shells make
        for (int i = 0; i < segments; i++) {
            double t0 = Mth.TWO_PI * i / segments, t1 = Mth.TWO_PI * (i + 1) / segments;
            double w0 = 0.3 + 0.22 * Mth.sin(time * 3.1f + i * 1.3f), w1 = 0.3 + 0.22 * Mth.sin(time * 3.1f + (i + 1) * 1.3f);
            Vec3 r0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), r1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
            Vec3 a = mid.add(r0.scale(h)), b = mid.add(r1.scale(h));
            seamQuad(out, p, a.subtract(n.scale(w0)), b.subtract(n.scale(w1)), b.add(n.scale(w1)), a.add(n.scale(w0)), alpha);
            seamQuad(out, p, a.subtract(r0.scale(w0)), b.subtract(r1.scale(w1)), b.add(r1.scale(w1)), a.add(r0.scale(w0)), alpha);
        }
    }

    private static void seamQuad(VertexConsumer out, PoseStack.Pose p, Vec3 a, Vec3 b, Vec3 c, Vec3 e, int alpha) {
        for (Vec3 v : new Vec3[] {a, b, c, e}) {
            out.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(238, 234, 228, alpha).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 1f, 0f);
        }
    }
}
