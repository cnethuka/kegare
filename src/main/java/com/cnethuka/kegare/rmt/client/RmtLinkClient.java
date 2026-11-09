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
 * What heal-others looks like (DESIGN §3.17), drawn here as our own mesh (it shows at every effects setting: the link is the tell, and the
 * moment is what you play by). The link: a white core in a soft green glow from the healer's hand to the healed one's chest, a slow
 * brightening running along it toward them, seen by everyone. In your own first person it starts low and to the right, in front of you,
 * never at your eyes. The moment: a soft green glow behind the one who glows for you, breathing, round their silhouette; only you are told.
 * White and green, never Malice violet.
 */
final class RmtLinkClient {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** A link not heard of for this long (its healer resends every second) goes out. */
    private static final int LINK_TIMEOUT = 30;
    private static final int GREEN = 0x5FE38A, CORE = 0xF2FFF6, GLOW = 0x8CF5A9;
    private static final int SEGMENTS = 20;

    private record Heard(int target, long at) {}

    private static final Map<Integer, Heard> LINKS = new HashMap<>();
    private static RmtNet.Moment moment = RmtNet.Moment.NONE;

    private RmtLinkClient() {}

    static void init() {
        RmtNet.Link.setClientHandler(link -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (!link.on()) LINKS.remove(link.caster());
            else if (level != null) LINKS.put(link.caster(), new Heard(link.target(), level.getGameTime()));
        });
        RmtNet.Moment.setClientHandler(m -> moment = m);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            LINKS.clear();
            moment = RmtNet.Moment.NONE;
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) render(e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
        });
    }

    private static void render(Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (LINKS.isEmpty() && moment.target() < 0)) return;
        long clock = level.getGameTime();
        float time = clock + partial;
        Vec3 cam = camera.getPosition();
        Matrix4f m = new PoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer out = buffers.getBuffer(type);
        for (Iterator<Map.Entry<Integer, Heard>> it = LINKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Heard> link = it.next();
            if (clock - link.getValue().at() > LINK_TIMEOUT) {
                it.remove();
                continue;
            }
            Entity caster = level.getEntity(link.getKey()), target = level.getEntity(link.getValue().target());
            if (caster == null || target == null) continue;
            Vec3 from = hand(mc, caster, camera, partial);
            Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0);
            beam(out, m, cam, from, to, time);
        }
        if (moment.target() >= 0 && clock < moment.until()) {
            Entity glowing = level.getEntity(moment.target());
            if (glowing != null) glow(out, m, camera, glowing, partial, time, moment.until() - clock);
        }
        buffers.endBatch(type);
    }

    /** Where the link leaves the healer: their right hand; in your own first person, low and to the right, in front of you. */
    private static Vec3 hand(Minecraft mc, Entity caster, Camera camera, float partial) {
        if (caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            Vector3f look = camera.getLookVector(), left = camera.getLeftVector(), up = camera.getUpVector();
            return camera.getPosition().add(look.x() * 0.75 - left.x() * 0.32 - up.x() * 0.38, look.y() * 0.75 - left.y() * 0.32 - up.y() * 0.38,
                    look.z() * 0.75 - left.z() * 0.32 - up.z() * 0.38);
        }
        float yaw = Mth.lerp(partial, caster instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRotO : caster.yRotO,
                caster instanceof net.minecraft.world.entity.LivingEntity l2 ? l2.yBodyRot : caster.getYRot()) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw); // forward
        double rx = -fz, rz = fx;                     // right
        return caster.getPosition(partial).add(fx * 0.45 + rx * 0.38, caster.getBbHeight() * 0.62, fz * 0.45 + rz * 0.38);
    }

    /** A camera-facing ribbon: a narrow white core, a wider green glow fading to nothing at its edges, a brightening running along. */
    private static void beam(VertexConsumer out, Matrix4f m, Vec3 cam, Vec3 from, Vec3 to, float time) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 1.0e-4) return;
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = i / (double) SEGMENTS, t1 = (i + 1) / (double) SEGMENTS;
            Vec3 a = from.add(axis.scale(t0)), b = from.add(axis.scale(t1));
            Vec3 side = axis.cross(a.subtract(cam)).normalize();
            float pulse = 0.65f + 0.35f * Mth.sin((float) (t0 * Math.PI * 4) - time * 0.35f);
            ribbon(out, m, cam, a, b, side, 0.2, GLOW, (int) (110 * pulse));
            ribbon(out, m, cam, a, b, side, 0.055, CORE, (int) (240 * pulse));
        }
    }

    /** One stretch of a ribbon {@code half} wide each side of its line, {@code alpha} along the middle and none at its edges. */
    private static void ribbon(VertexConsumer out, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, double half, int rgb, int alpha) {
        Vec3 s = side.scale(half);
        for (int k = -1; k <= 1; k += 2) {
            Vec3 ea = a.add(s.scale(k)), eb = b.add(s.scale(k));
            vertex(out, m, a.subtract(cam), rgb, alpha);
            vertex(out, m, b.subtract(cam), rgb, alpha);
            vertex(out, m, eb.subtract(cam), rgb, 0);
            vertex(out, m, ea.subtract(cam), rgb, 0);
        }
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
