package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Malice burning on a player's arm: two slightly inflated tubes round the arm with cutout pixel fire on them, so it wraps the fist and
 * forearm from every side and follows every pose. The inner one sits tight on the arm, and the outer one is big enough for its tips to break
 * past the arm's outline. Every texel is either solid or a hole, with no soft alpha, and near-black runs through it so the shape holds on any
 * background. Fire ignores the world's light, so it burns the same by day and by night.
 * <p>
 * In first person with something in hand there's no arm to wrap, so one clump burns round the grip instead ({@link #drawGrip}). Your own empty
 * hand is too near the camera for this: see {@link FistFlames}.
 */
final class FistFire {
    static final ResourceLocation TEXTURE = Kegare.id("textures/entity/fist_fire.png");

    static final int SHEET_WIDTH = 280;
    static final int SHEET_HEIGHT = 84;
    private static final float PIXEL = 1f / 16f;
    private static final float HALF_DEPTH = 2f * PIXEL;
    private static final float HALF_WIDTH = 2f * PIXEL;
    private static final float HALF_WIDTH_SLIM = 1.5f * PIXEL;
    /** How much of the Malice color's way to white the hot heart goes. Much more and blue fire starts to look like ice. */
    private static final float HEART_PALENESS = 0.38f;
    /** How fast Reinforced Fists' low burn turns over, against the ready fist's: steadier. */
    static final float LOW_PACE = 0.5f;

    /**
     * One tube.
     *
     * @param face    texels across one side of the arm; one texel is about one pixel of skin, so a bigger tube has more of them, not bigger ones
     * @param rows    texels from the fist toward the elbow
     * @param sheetY  where its row of frames is in the sheet
     * @param risingY where the row of frames for an arm held up is: the same fire going the other way
     * @param past    how far past the knuckles that one reaches, in blocks
     * @param inflate how far out from the arm's surface it sits, in blocks. The skin's sleeve is at 0.25 of a pixel, so it has to clear that.
     * @param offset  frames ahead of the others, so the layers don't flicker in step
     */
    record Layer(int face, int rows, int sheetY, int risingY, float past, float inflate, int offset) {
        /** The four sides unwrapped, then the end of the fist. */
        int frameWidth() {
            return 5 * face;
        }
    }

    static final Layer OUTER = new Layer(7, 10, 0, 26, 3f * PIXEL, 1.6f * PIXEL, 2);
    static final Layer INNER = new Layer(5, 8, 10, 36, 2f * PIXEL, 0.4f * PIXEL, 0);
    static final Layer HEART = new Layer(5, 8, 18, 44, 2f * PIXEL, 0.45f * PIXEL, 0);

    static final int GRIP_WIDTH = 14;
    static final int GRIP_ROWS = 16;
    private static final int GRIP_BACK_Y = 52;
    private static final int GRIP_FRONT_Y = 68;
    /** A flat side seen nearly edge on squashes into a thin line that reads as wire, not fire. Below this much of it facing the camera, it isn't drawn. */
    private static final float EDGE_ON = 0.2f;
    /** The end of the fist, the same, and more so: it only has anything to say when the arm's pointing your way. */
    private static final float EDGE_ON_END = 0.42f;
    /** One texel of the clump in view space, at the depth of the grip: chunky enough to see the flames' shapes, and small enough to stay in the corner. */
    private static final float GRIP_TEXEL = 0.016f;
    /** Moved along the line from the camera, so they line up on the screen: one behind what's held and one in front of it. */
    private static final float GRIP_BEHIND = 1.42f;
    private static final float GRIP_IN_FRONT = 0.74f;

    private FistFire() {}

    static RenderType renderType() {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    /**
     * The fire on an arm, with the pose in the arm's space.
     *
     * @param toCamera toward the camera, in the axes the pose comes out in
     * @param up       which way is up, in those axes
     */
    static void drawArm(PoseStack.Pose pose, VertexConsumer out, int rgb, float time, float handEnd, float centre, boolean slim, Vector3f toCamera, Vector3f up) {
        Vector3f look = pose.normal().transformTranspose(toCamera, new Vector3f()).normalize();
        Vector3f upward = pose.normal().transformTranspose(up, new Vector3f()).normalize();
        boolean raised = FistFlames.pointsUp(look, upward);
        tube(pose, out, OUTER, rgb, time, handEnd, centre, slim, raised, look);
        tube(pose, out, INNER, rgb, time, handEnd, centre, slim, raised, look);
        tube(pose, out, HEART, FistFlames.mix(rgb, 0xFFFFFF, HEART_PALENESS), time, handEnd, centre, slim, raised, look);
    }

    /**
     * Reinforced Fists' low burn: the same fire, smaller and steadier. Only the inner tube and its heart (nothing breaks past the arm's
     * outline), turning over at {@code LOW_PACE} of the ready fist's rate.
     */
    static void drawLow(PoseStack.Pose pose, VertexConsumer out, int rgb, float time, float handEnd, float centre, boolean slim, Vector3f toCamera, Vector3f up) {
        Vector3f look = pose.normal().transformTranspose(toCamera, new Vector3f()).normalize();
        Vector3f upward = pose.normal().transformTranspose(up, new Vector3f()).normalize();
        boolean raised = FistFlames.pointsUp(look, upward);
        float slow = time * LOW_PACE;
        tube(pose, out, INNER, rgb, slow, handEnd, centre, slim, raised, look);
        tube(pose, out, HEART, FistFlames.mix(rgb, 0xFFFFFF, HEART_PALENESS), slow, handEnd, centre, slim, raised, look);
    }

    /** The low burn round the grip in first person: only the clump behind what's held. */
    static void drawGripLow(VertexConsumer out, int rgb, float time, Vector3f base, Vector3f right, Vector3f up) {
        billboard(out, base, GRIP_BEHIND, GRIP_BACK_Y, FistFlames.frame(time * LOW_PACE, 0), rgb, right, up);
    }

    private static void tube(PoseStack.Pose pose, VertexConsumer out, Layer layer, int rgb, float time, float handEnd, float centre, boolean slim, boolean raised, Vector3f look) {
        float grow = layer.inflate();
        float halfX = (slim ? HALF_WIDTH_SLIM : HALF_WIDTH) + grow, halfZ = HALF_DEPTH + grow;
        float x0 = centre - halfX, x1 = centre + halfX, z0 = -halfZ, z1 = halfZ;
        float knuckles = handEnd + grow, bottom = raised ? knuckles + layer.past() : knuckles, top = bottom - layer.rows() * PIXEL;
        int across = slim ? layer.face() - 1 : layer.face(); // a slim arm's narrow sides show less of the unwrap, at the same size
        int u = FistFlames.frame(time, layer.offset()) * layer.frameWidth();
        int v0 = raised ? layer.risingY() : layer.sheetY(), v1 = v0 + layer.rows();
        boolean seesZ = Math.abs(look.z) > EDGE_ON, seesX = Math.abs(look.x) > EDGE_ON;
        if (seesZ) side(pose, out, x0, z1, x1, z1, top, bottom, u, u + across, v0, v1, rgb);
        u += layer.face();
        if (seesX) side(pose, out, x1, z1, x1, z0, top, bottom, u, u + layer.face(), v0, v1, rgb);
        u += layer.face();
        if (seesZ) side(pose, out, x1, z0, x0, z0, top, bottom, u, u + across, v0, v1, rgb);
        u += layer.face();
        if (seesX) side(pose, out, x0, z0, x0, z1, top, bottom, u, u + layer.face(), v0, v1, rgb);
        u += layer.face();
        if (Math.abs(look.y) > EDGE_ON_END) {
            // The end of the fist, from the unwrap's own row whichever way the fire's going.
            int capV = layer.sheetY();
            vertex(out, pose, x0, knuckles, z0, u, capV, rgb);
            vertex(out, pose, x1, knuckles, z0, u + across, capV, rgb);
            vertex(out, pose, x1, knuckles, z1, u + across, capV + layer.face(), rgb);
            vertex(out, pose, x0, knuckles, z1, u, capV + layer.face(), rgb);
        }
    }

    private static void side(PoseStack.Pose pose, VertexConsumer out, float xa, float za, float xb, float zb, float top, float bottom, int ua, int ub, int v0, int v1, int rgb) {
        vertex(out, pose, xa, top, za, ua, v0, rgb);
        vertex(out, pose, xb, top, zb, ub, v0, rgb);
        vertex(out, pose, xb, bottom, zb, ub, v1, rgb);
        vertex(out, pose, xa, bottom, za, ua, v1, rgb);
    }

    /**
     * One clump round what's held in first person, facing the camera: a little behind the item, and most of it in front, with gaps that let
     * the grip show through.
     *
     * @param base  the middle of the clump's bottom edge, from the camera, at the depth of the grip
     * @param right what the camera calls right, one unit long
     * @param up    what the camera calls up, one unit long
     */
    static void drawGrip(VertexConsumer out, int rgb, float time, Vector3f base, Vector3f right, Vector3f up) {
        billboard(out, base, GRIP_BEHIND, GRIP_BACK_Y, FistFlames.frame(time, 0), rgb, right, up);
        billboard(out, base, GRIP_IN_FRONT, GRIP_FRONT_Y, FistFlames.frame(time, 3), rgb, right, up);
    }

    /** The middle of the clump, where curls come off it. */
    static Vector3f gripMiddle(Vector3f base, Vector3f up) {
        return new Vector3f(up).mul(GRIP_ROWS * GRIP_TEXEL * 0.45f).add(base);
    }

    private static void billboard(VertexConsumer out, Vector3f base, float along, int sheetY, int frame, int rgb, Vector3f right, Vector3f up) {
        float x = base.x * along, y = base.y * along, z = base.z * along;
        float half = GRIP_WIDTH * GRIP_TEXEL * 0.5f * along, height = GRIP_ROWS * GRIP_TEXEL * along;
        float acrossX = right.x * half, acrossY = right.y * half, acrossZ = right.z * half;
        float topX = x + up.x * height, topY = y + up.y * height, topZ = z + up.z * height;
        int u0 = frame * GRIP_WIDTH, u1 = u0 + GRIP_WIDTH, v1 = sheetY + GRIP_ROWS;
        raw(out, topX - acrossX, topY - acrossY, topZ - acrossZ, u0, sheetY, rgb);
        raw(out, topX + acrossX, topY + acrossY, topZ + acrossZ, u1, sheetY, rgb);
        raw(out, x + acrossX, y + acrossY, z + acrossZ, u1, v1, rgb);
        raw(out, x - acrossX, y - acrossY, z - acrossZ, u0, v1, rgb);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int u, int v, int rgb) {
        out.addVertex(pose, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(u / (float) SHEET_WIDTH, v / (float) SHEET_HEIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    private static void raw(VertexConsumer out, float x, float y, float z, int u, int v, int rgb) {
        out.addVertex(x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(u / (float) SHEET_WIDTH, v / (float) SHEET_HEIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}
