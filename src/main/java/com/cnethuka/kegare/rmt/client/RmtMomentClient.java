package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.rmt.RmtNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The moment to learn healing others (DESIGN §3.17), drawn here as our own mesh so it shows at every effects setting (it's what you play
 * by): a soft green glow behind the one who glows for you, breathing, round their silhouette; only you are told. Green, like the orb:
 * it's what teaches it. Healing itself is white, and shows on the healer's hand (RmtFxAuthoring).
 */
final class RmtMomentClient {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int GREEN = 0x5FE38A;
    private static RmtNet.Moment moment = RmtNet.Moment.NONE;

    private RmtMomentClient() {}

    static void init() {
        RmtNet.Moment.setClientHandler(m -> moment = m);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> moment = RmtNet.Moment.NONE);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) render(e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
        });
    }

    private static void render(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || moment.target() < 0) return;
        long clock = level.getGameTime();
        float time = clock + partial;
        Vec3 cam = camera.getPosition();
        Matrix4f m = new PoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        if (moment.target() >= 0 && clock < moment.until()) {
            Entity glowing = level.getEntity(moment.target());
            if (glowing != null) glow(out, m, camera, glowing, partial, time, moment.until() - clock);
        }
        buffers.endBatch(type);
    }

    /**
     * A soft green glow behind {@code e}, facing you: rings fading out from its outline, breathing, the last second fading away. Drawn at its
     * middle, so its own body hides the centre and the glow shows round it.
     */
    private static void glow(VertexConsumer out, Matrix4f m, Camera camera, Entity e, float partial, float time, long ticksLeft) {
        Vec3 centre = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0).subtract(camera.getPosition());
        Vector3f l = camera.getLeftVector(), u = camera.getUpVector();
        Vec3 left = new Vec3(l.x(), l.y(), l.z()), up = new Vec3(u.x(), u.y(), u.z());
        double rx = e.getBbWidth() * 0.5 + 0.55, ry = e.getBbHeight() * 0.5 + 0.45;
        float breath = 0.75f + 0.25f * Mth.sin(time * 0.25f);
        float fade = Mth.clamp((ticksLeft - partial) / 20f, 0f, 1f); // its last second fades it out
        int inner = (int) (120 * breath * fade), mid = (int) (55 * breath * fade);
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            double a0 = i * Math.PI * 2 / steps, a1 = (i + 1) * Math.PI * 2 / steps;
            Vec3 d0 = left.scale(Math.cos(a0)).add(up.scale(Math.sin(a0))), d1 = left.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
            Vec3 i0 = centre.add(scale(d0, rx * 0.45, ry * 0.45, left, up)), i1 = centre.add(scale(d1, rx * 0.45, ry * 0.45, left, up));
            Vec3 m0 = centre.add(scale(d0, rx * 0.8, ry * 0.8, left, up)), m1 = centre.add(scale(d1, rx * 0.8, ry * 0.8, left, up));
            Vec3 o0 = centre.add(scale(d0, rx * 1.25, ry * 1.25, left, up)), o1 = centre.add(scale(d1, rx * 1.25, ry * 1.25, left, up));
            vertex(out, m, centre, GREEN, inner);
            vertex(out, m, centre, GREEN, inner);
            vertex(out, m, i1, GREEN, inner);
            vertex(out, m, i0, GREEN, inner);
            quad(out, m, i0, i1, m1, m0, inner, mid);
            quad(out, m, m0, m1, o1, o0, mid, 0);
        }
    }

    /** {@code d} (a unit direction in the camera's plane) stretched to an ellipse {@code rx} wide and {@code ry} tall. */
    private static Vec3 scale(Vec3 d, double rx, double ry, Vec3 left, Vec3 up) {
        double x = d.dot(left), y = d.dot(up);
        return left.scale(x * rx).add(up.scale(y * ry));
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 a0, Vec3 a1, Vec3 b1, Vec3 b0, int alphaA, int alphaB) {
        vertex(out, m, a0, GREEN, alphaA);
        vertex(out, m, a1, GREEN, alphaA);
        vertex(out, m, b1, GREEN, alphaB);
        vertex(out, m, b0, GREEN, alphaB);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, int rgb, int alpha) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255))
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}
