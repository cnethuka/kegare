package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.fists.FistsPayload;
import com.cnethuka.kegare.melee.MeleeTell;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Reinforced Fists on a player (DESIGN §3.28): a low, steady burn on both fists in the Malice color the server sent this client
 * ({@link com.cnethuka.kegare.fists.FistsTell}), drawn at every effects setting. The ready Strike's fire, drawn on top of it on the
 * striking fist, is bigger, so the two can be told apart.
 */
public final class FistBurn {
    private static final Map<Integer, FistsPayload> SHOWN = new HashMap<>();
    /** Your own hand in first person: as for the ready fist, the fire has to stay small by the camera. */
    private static final float RADIUS = 3f / 16f;
    private static final float RADIUS_SLIM = 2.7f / 16f;

    private FistBurn() {}

    static void update(FistsPayload payload) {
        if (payload.flags() == 0) SHOWN.remove(payload.entityId());
        else SHOWN.put(payload.entityId(), payload);
    }

    static void clear() {
        SHOWN.clear();
    }

    /** What's on this player (null if nothing), as sent. */
    @Nullable
    static FistsPayload on(Player player) {
        FistsPayload shown = SHOWN.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || mc.player == null || player.isInvisibleTo(mc.player)) return null;
        return shown;
    }

    /** The burn's color on this player, brightened for drawing, or null if Fists aren't on (or you can't see them). */
    @Nullable
    static Integer colorOn(Player player) {
        FistsPayload shown = on(player);
        return shown == null || !shown.has(FistsPayload.BURN) ? null : MeleeTell.bright(shown.color()) & 0xFFFFFF;
    }

    /** Their Malice color for the zone's flicker, brightened, or null if they aren't in the zone (or you can't see them). */
    @Nullable
    static Integer zoneOn(Player player) {
        FistsPayload shown = on(player);
        return shown == null || !shown.has(FistsPayload.ZONE) ? null : MeleeTell.bright(shown.color()) & 0xFFFFFF;
    }

    private static final net.minecraft.client.renderer.RenderType FLICKER = net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png"));
    private static final int BLACK = 0x050307;
    /** How many chips can be round a fist at once, and how often one shows (per tick, each). */
    private static final int FLICKER_CHIPS = 4;
    private static final float FLICKER_CHANCE = 0.3f;

    /**
     * The zone, on one fist: a faint flicker of black sparks, a few small chips with a rim of the color, here for a tick and gone, never the
     * same place twice. With the pose in the arm's space.
     */
    static void drawFlicker(PoseStack pose, MultiBufferSource buffers, int rgb, int tick, int seed, boolean slim, boolean leftArm, Vector3f toCamera) {
        Vector3f look = pose.last().normal().transformTranspose(toCamera, new Vector3f()).normalize();
        Vector3f right = new Vector3f(look).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1.0e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(look).normalize();
        float cx = FistShellMesh.armCentre(slim, leftArm), cy = FistShellMesh.fistY();
        com.mojang.blaze3d.vertex.VertexConsumer out = buffers.getBuffer(FLICKER);
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(tick * 341873128712L + seed * 132897987541L);
        for (int i = 0; i < FLICKER_CHIPS; i++) {
            float x = cx + (r.nextFloat() - 0.5f) * 0.42f, y = cy + (r.nextFloat() - 0.6f) * 0.4f, z = (r.nextFloat() - 0.5f) * 0.42f;
            float size = 0.022f + 0.018f * r.nextFloat();
            if (r.nextFloat() > FLICKER_CHANCE) continue;
            chip(out, pose.last(), x, y, z, right, up, size * 1.7f, rgb);
            chip(out, pose.last(), x + look.x * 0.004f, y + look.y * 0.004f, z + look.z * 0.004f, right, up, size, BLACK);
        }
    }

    private static void chip(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, Vector3f right, Vector3f up,
                             float r, int rgb) {
        vertex(out, pose, x + up.x * r, y + up.y * r, z + up.z * r, rgb);
        vertex(out, pose, x + right.x * r, y + right.y * r, z + right.z * r, rgb);
        vertex(out, pose, x - up.x * r, y - up.y * r, z - up.z * r, rgb);
        vertex(out, pose, x - right.x * r, y - right.y * r, z - right.z * r, rgb);
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int rgb) {
        out.addVertex(pose, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(0.5f, 0.5f)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(0f, 1f, 0f);
    }

    /** The burn on one arm, with the pose in the arm's space. {@code ownHand}: your own, by the camera. */
    static void drawArm(PoseStack pose, MultiBufferSource buffers, int rgb, float time, boolean slim, boolean leftArm, Vector3f toCamera, Vector3f up,
                        boolean ownHand) {
        float centre = FistShellMesh.armCentre(slim, leftArm);
        if (!ownHand) {
            FistFire.drawLow(pose.last(), buffers.getBuffer(FistFire.renderType()), rgb, time, FistShellMesh.handEnd(), centre, slim, toCamera, up);
        } else {
            FistFlames.drawOwnHand(pose.last(), buffers.getBuffer(FistFlames.renderType()), rgb, time * FistFire.LOW_PACE, FistShellMesh.handEnd(), centre,
                    slim ? RADIUS_SLIM : RADIUS, toCamera, up, leftArm, true);
        }
    }
}
