package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What the eyes draw in the world (DESIGN §3.32), through walls, only for a bearer and at every effects setting:
 * <ul>
 * <li>past render distance, a glowing marker in a player's Malice color where they really are (and for every pin); a void's marker is a
 * dark ring;</li>
 * <li>within it, a Severed player shows as a blurred distortion, a dark smear the shape of a body with pale ripples where the flow bends,
 * never an outline. (Everyone else's outline is vanilla's glow pass, colored by {@link SightClient#outlineColor}.)</li>
 * </ul>
 */
public final class SightRender {
    static final ResourceLocation MARKER = Kegare.id("textures/entity/sight_marker.png");
    static final ResourceLocation VOID = Kegare.id("textures/entity/void_distortion.png");
    static final int VOID_FRAMES = 8;

    private SightRender() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) render(e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
        });
    }

    /** Past this, a player isn't on this client at all: their marker shows instead. */
    static double renderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
    }

    private static void render(Camera camera, float partial) {
        if (!SightClient.isBearer()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate(), up = new Vector3f(camera.getUpVector());
        long now = SightClient.now();
        double far = renderDistance();

        VertexConsumer markers = buffers.getBuffer(SightRenderTypes.THROUGH_WALLS.apply(MARKER));
        for (SightClient.Seen seen : SightClient.players()) {
            Entity e = level.getEntity(seen.entityId);
            Vec3 at = seen.position(now);
            boolean pinned = SightClient.pinned(seen);
            if (e != null && e.distanceTo(mc.player) <= far && !pinned) continue; // here: the outline (or the void) shows them
            marker(markers, pose, at.add(0, 1.0, 0).subtract(cam), right, up, seen.severed ? 0x101018 : SightClient.bright(seen.color),
                    seen.severed ? 0.9f : 1f, pinned, SightClient.pulsing(seen.entityId));
        }
        for (SightClient.Pin pin : SightClient.pins()) {
            if (pin.name() != null) continue; // players were drawn above
            marker(markers, pose, pin.pos().add(0, 0.6, 0).subtract(cam), right, up, pin.color(), 0.9f, true, SightClient.pulsing(pin.entityId()));
        }
        buffers.endBatch(SightRenderTypes.THROUGH_WALLS.apply(MARKER));

        // Voids within reach: a distortion, never an outline. Behind walls the whole smear (drawn just in front of the body, so only what
        // stands between shows it); in the open, only faintly round them, so the body itself stays plain to see.
        int frame = (int) ((now / 110) % VOID_FRAMES);
        for (boolean behind : new boolean[] {true, false}) {
            var type = (behind ? SightRenderTypes.BEHIND_WALLS : SightRenderTypes.IN_THE_OPEN).apply(VOID);
            VertexConsumer voids = buffers.getBuffer(type);
            for (SightClient.Seen seen : SightClient.players()) {
                if (!seen.severed) continue;
                Entity e = level.getEntity(seen.entityId);
                if (e == null || e.distanceTo(mc.player) > far) continue;
                Vec3 centre = e.getPosition(partial).add(0, e.getBbHeight() / 2, 0);
                Vec3 toCam = cam.subtract(centre);
                Vec3 at = (toCam.lengthSqr() > 1.0e-4 ? centre.add(toCam.normalize().scale(behind ? 0.55 : -0.4)) : centre).subtract(cam);
                float wobble = 1f + 0.05f * Mth.sin(now / 160f);
                Vector3f flatRight = new Vector3f(right.x, 0, right.z);
                if (flatRight.lengthSquared() < 1.0e-4f) flatRight.set(1, 0, 0);
                flatRight.normalize();
                quad(voids, pose.last().pose(), at, flatRight, new Vector3f(0, 1, 0), 0.62f * wobble, 1.15f * wobble,
                        frame / (float) VOID_FRAMES, (frame + 1) / (float) VOID_FRAMES, 0xFFFFFF, behind ? 0.85f : 0.3f);
            }
            buffers.endBatch(type);
        }
    }

    /**
     * A marker: the same size on screen however far, bigger when pinned, flashing on a pulse. Drawn along the right line but no further
     * than half the render distance, so neither the far clip plane nor the fog swallows it (past it, a player is hundreds of blocks off).
     */
    private static void marker(VertexConsumer out, PoseStack pose, Vec3 at, Vector3f right, Vector3f up, int rgb, float alpha, boolean pinned, boolean pulse) {
        double distance = at.length();
        double drawn = Math.min(distance, Math.max(6.0, renderDistance() * 0.5));
        if (distance > 1.0e-3 && drawn < distance) at = at.scale(drawn / distance);
        float size = (float) Math.max(0.5, drawn * 0.035) * (pinned ? 1.25f : 1f);
        if (pulse && (SightClient.now() / 75) % 2 == 0) rgb = 0xFFFFFF;
        quad(out, pose.last().pose(), at, right, up, size, size, 0f, 1f, rgb, alpha);
    }

    static void quad(VertexConsumer out, Matrix4f pose, Vec3 at, Vector3f right, Vector3f up, float halfW, float halfH, float u0, float u1, int rgb, float alpha) {
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        vertex(out, pose, x - right.x * halfW - up.x * halfH, y - right.y * halfW - up.y * halfH, z - right.z * halfW - up.z * halfH, u0, 1f, rgb, alpha);
        vertex(out, pose, x + right.x * halfW - up.x * halfH, y + right.y * halfW - up.y * halfH, z + right.z * halfW - up.z * halfH, u1, 1f, rgb, alpha);
        vertex(out, pose, x + right.x * halfW + up.x * halfH, y + right.y * halfW + up.y * halfH, z + right.z * halfW + up.z * halfH, u1, 0f, rgb, alpha);
        vertex(out, pose, x - right.x * halfW + up.x * halfH, y - right.y * halfW + up.y * halfH, z - right.z * halfW + up.z * halfH, u0, 0f, rgb, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z, float u, float v, int rgb, float alpha) {
        out.addVertex(pose, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Mth.clamp((int) (alpha * 255), 0, 255)).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}
