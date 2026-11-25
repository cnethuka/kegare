package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.api.dominion.DominionLook;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A Dominion (DESIGN §3.35), drawn: the barrier, a sphere round its centre rising out of the ground as it opens; from outside its shell
 * color, from inside its sky, darker overhead and hazy at the horizon. An Incomplete Dominion's shell is cracked all over (a smooth,
 * high-res texture), and a full one cracks as it takes hits. Inside, the ground's top faces are laid over with its pattern (pixel art,
 * 16 a block, in the world's light), and ruins stand on it, rising with the barrier. Contested in a clash, both shells flicker half-there
 * and a bright seam grinds where they meet. Nothing here touches the world: it's all drawn over it, so nothing is left when it goes.
 */
public class DominionRenderer extends EntityRenderer<DominionEntity> {
    static final ResourceLocation WHITE = Kegare.id("textures/marks/white.png");
    static final ResourceLocation CRACKED = Kegare.id("textures/entity/dominion/shell_cracked.png");
    /** The same cracks, faint: the inside of a cracked shell against its own sky. */
    static final ResourceLocation CRACKED_FAINT = Kegare.id("textures/entity/dominion/shell_cracked_faint.png");
    static final ResourceLocation RUIN = Kegare.id("textures/marks/ruin_stone.png");
    private static final int SLICES = 48, STACKS = 24;
    /** Ground tiles in a pattern's strip (16 x 16 each, side by side). */
    private static final int GROUND_VARIANTS = 4;
    /** How far off a top face the ground pattern is drawn, so it never flickers into it. */
    private static final float LIFT = 0.012f;
    /** Re-read the ground's heights this often (battle damage moves it). */
    private static final int GROUND_REFRESH = 40;
    /**
     * The inner space has its own grey daylight: its ground and ruins are drawn at least this bright (as block light, 0 to 15), so at
     * night they still match its ash sky.
     */
    private static final int GLOW = 11;

    public DominionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DominionEntity entity) {
        return WHITE;
    }

    @Override
    public void render(DominionEntity d, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float risen = d.risen(partialTick);
        if (risen <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean inside = d.contains(cam);
        DominionLook look = d.look();
        float time = d.tickCount + partialTick;
        boolean clash = d.state() == DominionEntity.State.CLASH;
        float flicker = clash ? 0.5f + 0.12f * Mth.sin(time * 0.9f + d.getId()) + 0.05f * Mth.sin(time * 2.3f) : 1f;

        if (look.groundPattern().isPresent()) ground(d, look.groundPattern().get(), risen, pose, buffers, mc.level);
        if (look.ruins()) ruins(d, risen, pose, buffers, mc.level);

        float r = (float) d.radius();
        float top = r * risen; // rising out of the ground round its feet: only what's below the top of the risen part is there yet
        PoseStack.Pose p = pose.last();
        if (inside) {
            // The sky inside: hazy (its fog color) at the horizon, its sky color overhead, all but opaque; its cracks faint against it.
            shell(buffers.getBuffer(RenderType.entityTranslucent(look.cracked() ? CRACKED_FAINT : WHITE)), p, r, top, look.fogColor(),
                    look.skyColor(), 0.97f * flicker, look.cracked() ? 1f : 0f);
        } else {
            shell(buffers.getBuffer(RenderType.entityTranslucent(look.cracked() ? CRACKED : WHITE)), p, r, top, look.shellColor(),
                    look.shellColor(), 0.86f * flicker, look.cracked() ? 1f : 0f);
        }
        // A full Dominion's shell cracks as it takes hits; an Incomplete one's is cracked from the start.
        float damage = 1f - d.barrier();
        if (!look.cracked() && damage > 0.02f) {
            int lighter = brighter(inside ? look.skyColor() : look.shellColor(), 0.35f);
            shell(buffers.getBuffer(RenderType.entityTranslucent(CRACKED)), p, r * 0.998f, top, lighter, lighter, Math.min(0.9f, damage) * flicker, 1f);
        }
        // The seam where two clashing barriers meet: drawn once, by the one with the lower id.
        if (clash && d.level().getEntity(d.clashWith()) instanceof DominionEntity other && d.getId() < other.getId()) seam(d, other, time, pose, buffers);
    }

    // ---------------------------------------------------------------------------------------------- the shell

    /**
     * The sphere, its quads cut where the barrier hasn't risen to yet. Colored {@code low} at the horizon and {@code high} overhead;
     * {@code textured}: 1 to show the texture's cracks (an Incomplete Dominion's), 0 for a plain shell.
     */
    static void shell(VertexConsumer out, PoseStack.Pose p, float r, float top, int low, int high, float alpha, float textured) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 0) return;
        for (int i = 0; i < STACKS; i++) {
            float v0 = (float) i / STACKS, v1 = (float) (i + 1) / STACKS;
            float a0 = Mth.PI * (v0 - 0.5f), a1 = Mth.PI * (v1 - 0.5f);
            float y0 = Mth.sin(a0) * r, y1 = Mth.sin(a1) * r;
            if (y0 > top) break;
            y1 = Math.min(y1, top);
            float lat1 = (float) Math.asin(Mth.clamp(y1 / r, -1f, 1f));
            float r0 = Mth.cos(a0) * r, r1 = (float) Math.sqrt(Math.max(0, r * r - y1 * y1));
            // The texture runs from overhead (0) to the horizon (1), mirrored below it.
            float tv0 = 1f - Math.abs(a0) / (Mth.PI / 2), tv1 = 1f - Math.abs(lat1) / (Mth.PI / 2);
            int c0 = color(low, high, Math.max(0f, y0 / r)), c1 = color(low, high, Math.max(0f, y1 / r));
            for (int j = 0; j < SLICES; j++) {
                float u0 = (float) j / SLICES, u1 = (float) (j + 1) / SLICES;
                float t0 = Mth.TWO_PI * u0, t1 = Mth.TWO_PI * u1;
                float x00 = Mth.cos(t0) * r0, z00 = Mth.sin(t0) * r0, x01 = Mth.cos(t1) * r0, z01 = Mth.sin(t1) * r0;
                float x10 = Mth.cos(t0) * r1, z10 = Mth.sin(t0) * r1, x11 = Mth.cos(t1) * r1, z11 = Mth.sin(t1) * r1;
                float su0 = textured > 0 ? u0 : 0.5f, su1 = textured > 0 ? u1 : 0.5f;
                float sv0 = textured > 0 ? tv0 : 0.5f, sv1 = textured > 0 ? tv1 : 0.5f;
                vertex(out, p, x00, y0, z00, su0, sv0, c0, a);
                vertex(out, p, x01, y0, z01, su1, sv0, c0, a);
                vertex(out, p, x11, y1, z11, su1, sv1, c1, a);
                vertex(out, p, x10, y1, z10, su0, sv1, c1, a);
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

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float u, float v, int rgb, int a) {
        float len = Mth.sqrt(x * x + y * y + z * z);
        float nx = len < 1.0e-4f ? 0 : x / len, ny = len < 1.0e-4f ? 1 : y / len, nz = len < 1.0e-4f ? 0 : z / len;
        out.addVertex(p, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    // ---------------------------------------------------------------------------------------------- the ground and the ruins

    /**
     * Every top face of the ground inside the sphere: (x, y, z) of the air above it, read from the heightmap a client has (MOTION_BLOCKING:
     * a client is never sent the others), kept a while.
     */
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

    /** The ground's top faces under its pattern, spreading out from the centre as the barrier rises. */
    private static void ground(DominionEntity d, ResourceLocation pattern, float risen, PoseStack pose, MultiBufferSource buffers, Level level) {
        int[] cols = columns(d, level);
        if (cols.length == 0) return;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(pattern.getNamespace(), "textures/marks/" + pattern.getPath() + ".png");
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose p = pose.last();
        Vec3 c = d.centre();
        double reach = d.radius() * Math.min(1f, risen * 1.15f);
        for (int i = 0; i < cols.length; i += 3) {
            int x = cols[i], y = cols[i + 1], z = cols[i + 2];
            double dx = x + 0.5 - c.x, dz = z + 0.5 - c.z;
            if (dx * dx + dz * dz > reach * reach) continue;
            int light = glow(LevelRenderer.getLightColor(level, new BlockPos(x, y, z)));
            int hash = (x * 73428767) ^ (z * 912931) ^ (y * 4271);
            int variant = Math.floorMod(hash, GROUND_VARIANTS), turn = Math.floorMod(hash >> 8, 4);
            float u0 = variant / (float) GROUND_VARIANTS, u1 = (variant + 1) / (float) GROUND_VARIANTS;
            float fx = (float) (x - c.x), fy = (float) (y - c.y) + LIFT, fz = (float) (z - c.z);
            float[][] corners = {{fx, fz}, {fx, fz + 1}, {fx + 1, fz + 1}, {fx + 1, fz}};
            float[][] uvs = {{u0, 0}, {u0, 1}, {u1, 1}, {u1, 0}};
            for (int k = 0; k < 4; k++) {
                float[] corner = corners[k], uv = uvs[(k + turn) % 4];
                out.addVertex(p, corner[0], fy, corner[1]).setColor(255, 255, 255, 255).setUv(uv[0], uv[1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 1f, 0f);
            }
        }
    }

    private static int glow(int light) {
        return LightTexture.block(light) >= GLOW ? light : LightTexture.pack(GLOW, LightTexture.sky(light));
    }

    /** A ruin: blocks of broken stone, by the world's grid, standing on the ground. Heights in blocks; the top one can be part of one. */
    record Ruin(int x, int z, float[] heights, boolean alongX) {}

    /** Its ruins: the same for everyone who sees it (seeded by where it is), out between half way and the shell. */
    @SuppressWarnings("unchecked")
    private static List<Ruin> ruinsOf(DominionEntity d) {
        DominionEntity.ClientCache cache = d.clientCache();
        if (cache.ruins != null) return (List<Ruin>) cache.ruins;
        Vec3 c = d.centre();
        RandomSource rng = RandomSource.create(Mth.floor(c.x) * 341873128712L ^ Mth.floor(c.z) * 132897987541L ^ Mth.floor(c.y));
        int count = KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.FULL ? 7 : 4;
        List<Ruin> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = Mth.TWO_PI * (i + rng.nextFloat() * 0.6f) / count;
            double dist = d.radius() * (0.5 + rng.nextDouble() * 0.32);
            int x = Mth.floor(c.x + Math.cos(angle) * dist), z = Mth.floor(c.z + Math.sin(angle) * dist);
            int length = rng.nextFloat() < 0.5f ? 1 : 2 + rng.nextInt(2);
            float[] heights = new float[length];
            for (int k = 0; k < length; k++) {
                heights[k] = length == 1 ? 1.5f + rng.nextFloat() * 1.6f : 0.4f + rng.nextFloat() * 1.4f;
            }
            out.add(new Ruin(x, z, heights, rng.nextBoolean()));
        }
        cache.ruins = out;
        return out;
    }

    private static void ruins(DominionEntity d, float risen, PoseStack pose, MultiBufferSource buffers, Level level) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(RUIN));
        PoseStack.Pose p = pose.last();
        Vec3 c = d.centre();
        float grow = Mth.clamp((risen - 0.3f) / 0.7f, 0f, 1f); // they rise with the barrier, after it's begun
        if (grow <= 0f) return;
        for (Ruin ruin : ruinsOf(d)) {
            for (int k = 0; k < ruin.heights().length; k++) {
                int bx = ruin.x() + (ruin.alongX() ? k : 0), bz = ruin.z() + (ruin.alongX() ? 0 : k);
                int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                if (!d.contains(new Vec3(bx + 0.5, by + 0.5, bz + 0.5))) continue;
                float h = ruin.heights()[k] * grow;
                int light = glow(LevelRenderer.getLightColor(level, new BlockPos(bx, by, bz)));
                column(out, p, (float) (bx - c.x), (float) (by - c.y), (float) (bz - c.z), h, light, Math.floorMod(bx * 31 + bz * 17, 2));
            }
        }
    }

    /** A 1 x 1 column of stone {@code h} blocks tall, a face per block so the texture keeps 16 a block. */
    private static void column(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float h, int light, int variant) {
        float u0 = variant * 0.5f, u1 = u0 + 0.5f;
        for (float y0 = 0; y0 < h - 1.0e-3f; y0 += 1f) {
            float y1 = Math.min(h, y0 + 1f), vTop = 1f - (y1 - y0);
            // Sides, shaded as the world shades them.
            face(out, p, x, y + y0, z, x + 1, y + y1, z, u0, u1, vTop, 0f, 0f, -1f, light, 0.8f);
            face(out, p, x + 1, y + y0, z + 1, x, y + y1, z + 1, u0, u1, vTop, 0f, 0f, 1f, light, 0.8f);
            face(out, p, x, y + y0, z + 1, x, y + y1, z, u0, u1, vTop, -1f, 0f, 0f, light, 0.6f);
            face(out, p, x + 1, y + y0, z, x + 1, y + y1, z + 1, u0, u1, vTop, 1f, 0f, 0f, light, 0.6f);
        }
        // The broken top.
        int shade = 255;
        float[][] corners = {{x, z}, {x, z + 1}, {x + 1, z + 1}, {x + 1, z}};
        float[][] uvs = {{u0, 0}, {u0, 1}, {u1, 1}, {u1, 0}};
        for (int k = 0; k < 4; k++) {
            out.addVertex(p, corners[k][0], y + h, corners[k][1]).setColor(shade, shade, shade, 255).setUv(uvs[k][0], uvs[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 1f, 0f);
        }
    }

    /** One upright face from (x0, y0, z0) to (x1, y1, z1), its texture's top cut to the part that's there ({@code vTop}). */
    private static void face(VertexConsumer out, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float u0, float u1,
            float vTop, float nx, float ny, float nz, int light, float shadeF) {
        int s = Math.round(255 * shadeF);
        out.addVertex(p, x0, y0, z0).setColor(s, s, s, 255).setUv(u0, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        out.addVertex(p, x1, y0, z1).setColor(s, s, s, 255).setUv(u1, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        out.addVertex(p, x1, y1, z1).setColor(s, s, s, 255).setUv(u1, vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        out.addVertex(p, x0, y1, z0).setColor(s, s, s, 255).setUv(u0, vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
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
            // Across the circle's plane.
            seamQuad(out, p, a.subtract(n.scale(w0)), b.subtract(n.scale(w1)), b.add(n.scale(w1)), a.add(n.scale(w0)), alpha);
            // Flat in it.
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
