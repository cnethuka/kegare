package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Point Maker's mark on the client (DESIGN §3.26), seen only by its caster: a small clock face stamped on the top of the marked block,
 * pixel art on the block's own 16-texel grid with hard pixels. A bronze rim, twelve hour marks that go dark one by one, clockwise from
 * twelve, as the mark runs out, and the two hands stopped where the world's clock stood when it was marked (twelve to the north).
 * The hands and the marks still to come glow a little, so it reads at night.
 */
public final class MarkClient {
    static final int TEXELS = 16;
    private static TimeNet.Mark mark = TimeNet.Mark.none();
    private static long receivedAt;
    /** The art, texel by texel (0 = none), and how bright each glows (block light 0 to 15). */
    private static int[] argb = new int[TEXELS * TEXELS];
    private static int[] glow = new int[TEXELS * TEXELS];
    private static int drawnFor = -1;

    private MarkClient() {}

    static void onMark(TimeNet.Mark m) {
        mark = m;
        ClientLevel level = Minecraft.getInstance().level;
        receivedAt = level == null ? 0 : level.getGameTime();
        drawnFor = -1;
    }

    /** The mark, if one's out: for the HUD. */
    static TimeNet.Mark current() {
        return mark;
    }

    /** Ticks left on the mark, or 0. */
    static long remaining() {
        ClientLevel level = Minecraft.getInstance().level;
        if (!mark.present() || level == null) return 0;
        return Math.max(0, mark.until() - level.getGameTime());
    }

    static void clear() {
        mark = TimeNet.Mark.none();
        drawnFor = -1;
    }

    /** How many of the twelve marks are still lit. */
    static int lit() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0;
        long total = Math.max(1, mark.until() - receivedAt);
        return Mth.clamp((int) Math.ceil(12.0 * remaining() / total), 0, 12);
    }

    /** The art: rim, hour marks (the first {@code 12 - lit} of them gone dark), hands, pivot. Package-visible for the tests. */
    static void paint(int lit, float hourHand, float minuteHand, int[] argb, int[] glow) {
        java.util.Arrays.fill(argb, 0);
        java.util.Arrays.fill(glow, 0);
        float c = 7.5f;
        for (int z = 0; z < TEXELS; z++) {
            for (int x = 0; x < TEXELS; x++) {
                float dx = x - c, dz = z - c, r = Mth.sqrt(dx * dx + dz * dz);
                if (r >= 6.1f && r <= 7.4f) {
                    set(argb, glow, x, z, r > 6.8f ? TimeFx.BRONZE_DARK : TimeFx.BRONZE, 3);
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            boolean on = k >= 12 - lit;
            float a = Mth.TWO_PI * k / 12f;
            int length = k % 3 == 0 ? 2 : 1;
            for (int i = 0; i < length; i++) {
                float r = 5.2f - i;
                set(argb, glow, Math.round(c + Mth.sin(a) * r), Math.round(c - Mth.cos(a) * r), on ? TimeFx.SILVER : TimeFx.BRONZE_DARK, on ? 9 : 0);
            }
        }
        line(argb, glow, c, hourHand, 3.0f, TimeFx.BRIGHT);
        line(argb, glow, c, minuteHand, 4.4f, TimeFx.BRIGHT);
        for (int z = 7; z <= 8; z++) for (int x = 7; x <= 8; x++) set(argb, glow, x, z, TimeFx.BRONZE, 6);
    }

    private static void line(int[] argb, int[] glow, float c, float angle, float length, int rgb) {
        for (float t = 0.5f; t <= length; t += 0.25f) {
            set(argb, glow, Math.round(c + Mth.sin(angle) * t), Math.round(c - Mth.cos(angle) * t), rgb, 12);
        }
    }

    private static void set(int[] argb, int[] glow, int x, int z, int rgb, int g) {
        if (x < 0 || z < 0 || x >= TEXELS || z >= TEXELS) return;
        argb[z * TEXELS + x] = 0xFF000000 | rgb;
        glow[z * TEXELS + x] = g;
    }

    static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!mark.present() || level == null || remaining() <= 0) return;
        BlockPos pos = mark.block();
        if (Vec3.atCenterOf(pos).distanceToSqr(cam) > 96 * 96) return;
        int lit = lit();
        if (lit != drawnFor) {
            paint(lit, mark.hourHand(), mark.minuteHand(), argb, glow);
            drawnFor = lit;
        }
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        double top = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
        int light = LevelRenderer.getLightColor(level, pos.above());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutout(TimeFx.WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose().translate((float) (pos.getX() - cam.x), (float) (pos.getY() + top + 0.009 - cam.y),
                (float) (pos.getZ() - cam.z), new Matrix4f());
        float s = 1f / TEXELS;
        for (int z = 0; z < TEXELS; z++) {
            for (int x = 0; x < TEXELS; x++) {
                int c = argb[z * TEXELS + x];
                if (c == 0) continue;
                int l = lit(light, glow[z * TEXELS + x]);
                float x0 = x * s, x1 = (x + 1) * s, z0 = z * s, z1 = (z + 1) * s;
                vertex(out, m, x0, z0, c, l);
                vertex(out, m, x0, z1, c, l);
                vertex(out, m, x1, z1, c, l);
                vertex(out, m, x1, z0, c, l);
            }
        }
        buffers.endBatch(type);
    }

    /** The world's light here, but at least {@code glow} block light. */
    private static int lit(int light, int glow) {
        int block = Math.max(light & 0xFFFF, glow << 4);
        return (light & 0xFFFF0000) | block;
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float z, int argb, int light) {
        out.addVertex(m, x, 0f, z).setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, 255).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
    }
}
