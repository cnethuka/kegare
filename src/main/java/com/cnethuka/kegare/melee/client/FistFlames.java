package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Malice burning on your own empty hand in first person, and the little curls that come off a ready fist anywhere. The hand is right by the
 * camera there, so the layers on a body ({@link FistFire}) would fill the corner of the screen: this is flat pixel-art fire round it instead,
 * flickering frame by frame. Every sprite is greyscale, tinted here with the color the viewer should see. Hard-edged and depth-writing like the
 * rest of a player, so it stays chunky, and light from the world doesn't touch it, so it burns the same by day and by night.
 * <p>
 * The flames are quads hung on the arm's axis and turned to face the camera, one behind the arm for the mass, and two in front for licks that
 * leave the fist showing.
 */
final class FistFlames {
    static final ResourceLocation TEXTURE = Kegare.id("textures/entity/fist_flame.png");

    private static final int SHEET_WIDTH = 112;
    private static final int SHEET_HEIGHT = 49;
    static final int FLAME_FRAMES = 8;
    private static final int FLAME_TEXELS = 14;
    static final int WISP_FRAMES = 4;
    private static final int WISP_TEXELS = 7;
    /** Rows of frames in the sheet. */
    private static final int ROW_MASS = 0;
    private static final int ROW_LICKS = 1;
    private static final int ROW_HEART = 2;
    /** Ten frames a second: anything smoother stops looking like pixel art. */
    private static final int TICKS_PER_FRAME = 2;
    private static final float PIXEL = 1f / 16f;
    /** How far past the arm's end the fire starts: it wraps the fist and climbs the forearm, with next to nothing hanging below the hand. */
    private static final float PAST_KNUCKLES = 1f * PIXEL;
    /** How much of the Malice color's way to white the hot heart goes. Much more and blue fire starts to look like ice. */
    private static final float HEART_PALENESS = 0.38f;

    /** How much of an arm's length has to point up for its fire to go past the knuckles. */
    private static final float RAISED_FROM = 0.2f;
    /** On a raised arm, how much of the fire is below the end of the hand. */
    private static final float RAISED_BELOW_HAND = 0.4f;
    /** How big the fire is, as fractions of the sprite's full size: that arm is right by the camera, so the full size would fill the corner of the screen. */
    private static final float WIDTH = 0.5f;
    private static final float HEIGHT = 0.6f;

    static final int WISP_LIFE_TICKS = 10;
    /** Curls start on the surface of the fire, which stands a little way off the arm. Any closer in and the arm hides them. */
    private static final float WISP_RADIUS = 0.2f;

    private FistFlames() {}

    static RenderType renderType() {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    /** Which frame of a loop shows at this time. */
    static int frame(float time, int offset) {
        return Math.floorMod(Mth.floor(time) / TICKS_PER_FRAME + offset, FLAME_FRAMES);
    }

    /**
     * The fire on your own hand, with the pose already in the arm's space.
     *
     * @param handEnd  where the arm's cube ends in that space, down its length
     * @param centre   the arm's middle in that space, across it (a player's arm cube is off-centre)
     * @param radius   how far out of the middle a quad has to sit to clear the arm's corners
     * @param toCamera toward the camera, in the axes the pose comes out in
     * @param up       which way is up, in those axes
     */
    static void drawOwnHand(PoseStack.Pose pose, VertexConsumer out, int rgb, float time, float handEnd, float centre, float radius,
                            Vector3f toCamera, Vector3f up, boolean flipped) {
        drawOwnHand(pose, out, rgb, time, handEnd, centre, radius, toCamera, up, flipped, false);
    }

    /** {@code low}: Reinforced Fists' low burn, smaller and steadier (the caller slows {@code time}). */
    static void drawOwnHand(PoseStack.Pose pose, VertexConsumer out, int rgb, float time, float handEnd, float centre, float radius,
                            Vector3f toCamera, Vector3f up, boolean flipped, boolean low) {
        Vector3f look = pose.normal().transformTranspose(toCamera, new Vector3f()).normalize();
        // The two ways across the arm's length: toward the camera, and side to side as the camera sees it.
        float towardX = look.x, towardZ = look.z;
        float across = Mth.sqrt(towardX * towardX + towardZ * towardZ);
        if (across < 0.001f) {
            towardX = 0f;
            towardZ = 1f;
        } else {
            towardX /= across;
            towardZ /= across;
        }
        // Fire goes up the screen. On an arm hanging down that's from the fist toward the elbow; on one held up it's past the knuckles.
        float length = FLAME_TEXELS * PIXEL * HEIGHT;
        Vector3f upward = pose.normal().transformTranspose(up, new Vector3f()).normalize();
        float base = pointsUp(look, upward) ? handEnd - length * RAISED_BELOW_HAND : handEnd + PAST_KNUCKLES;
        float tips = pointsUp(look, upward) ? base + length : base - length;
        float half = FLAME_TEXELS * PIXEL * 0.5f * WIDTH;
        int pale = mix(rgb, 0xFFFFFF, HEART_PALENESS);

        sleeve(pose, out, centre, tips, base, half, -radius, towardX, towardZ, ROW_MASS, frame(time, 0), flipped, rgb);
        if (low) { // Reinforced Fists' low burn: no licks in front, and steadier
            sleeve(pose, out, centre, tips, base, half, radius + 0.1f * PIXEL, towardX, towardZ, ROW_HEART, frame(time, 5), flipped, pale);
            return;
        }
        sleeve(pose, out, centre, tips, base, half, radius, towardX, towardZ, ROW_LICKS, frame(time, 3), !flipped, rgb);
        sleeve(pose, out, centre, tips, base, half, radius + 0.1f * PIXEL, towardX, towardZ, ROW_HEART, frame(time, 5), flipped, pale);
    }

    /**
     * Does the arm's length (down its y, toward the hand) point up, as the camera sees it? It's what's left of it across the view that counts:
     * your own arm in first person mostly points away from you, and the hand is still the high end of it on the screen.
     */
    static boolean pointsUp(Vector3f look, Vector3f upward) {
        Vector3f seen = new Vector3f(-look.x * look.y, 1f - look.y * look.y, -look.z * look.y);
        return seen.lengthSquared() > 1.0e-4f && seen.normalize().dot(upward) > RAISED_FROM;
    }

    private static void sleeve(PoseStack.Pose pose, VertexConsumer out, float centre, float tips, float base, float half, float offset,
                               float towardX, float towardZ, int row, int frame, boolean flipped, int rgb) {
        float sideX = towardZ, sideZ = -towardX;
        float x = centre + towardX * offset, z = towardZ * offset;
        float u0 = frameU(frame), u1 = u0 + FLAME_TEXELS / (float) SHEET_WIDTH;
        if (flipped) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        float v0 = row * FLAME_TEXELS / (float) SHEET_HEIGHT, v1 = v0 + FLAME_TEXELS / (float) SHEET_HEIGHT;
        vertex(out, pose, x - sideX * half, tips, z - sideZ * half, u0, v0, rgb);
        vertex(out, pose, x + sideX * half, tips, z + sideZ * half, u1, v0, rgb);
        vertex(out, pose, x + sideX * half, base, z + sideZ * half, u1, v1, rgb);
        vertex(out, pose, x - sideX * half, base, z - sideZ * half, u0, v1, rgb);
    }

    /**
     * One curl, as a square facing the camera.
     *
     * @param right what the camera calls right, one unit long
     * @param up    what the camera calls up, one unit long
     * @param size  how wide the square is
     */
    static void drawWisp(VertexConsumer out, float x, float y, float z, Vector3f right, Vector3f up, float size, float age, boolean flipped, int rgb) {
        int frame = Math.min(WISP_FRAMES - 1, (int) (age * WISP_FRAMES));
        float u0 = frame * WISP_TEXELS / (float) SHEET_WIDTH, u1 = u0 + WISP_TEXELS / (float) SHEET_WIDTH;
        if (flipped) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        float v0 = 3 * FLAME_TEXELS / (float) SHEET_HEIGHT, v1 = v0 + WISP_TEXELS / (float) SHEET_HEIGHT;
        float half = size * 0.5f;
        raw(out, x - right.x * half + up.x * half, y - right.y * half + up.y * half, z - right.z * half + up.z * half, u0, v0, rgb);
        raw(out, x + right.x * half + up.x * half, y + right.y * half + up.y * half, z + right.z * half + up.z * half, u1, v0, rgb);
        raw(out, x + right.x * half - up.x * half, y + right.y * half - up.y * half, z + right.z * half - up.z * half, u1, v1, rgb);
        raw(out, x - right.x * half - up.x * half, y - right.y * half - up.y * half, z - right.z * half - up.z * half, u0, v1, rgb);
    }

    /**
     * Where a curl has got to, from the fist: it comes off the surface of the fire partway up it and goes a little higher, round in a small spiral, slowing as it goes.
     *
     * @param age  from 0 to 1 over its life
     * @param seed from 0 to 1, a different one for each
     */
    static Vector3f wispPath(float age, float seed, Vector3f dest) {
        float rise = 0.1f + 0.1f * seed + 0.22f * Mth.sqrt(age);
        float radius = WISP_RADIUS + 0.05f * age;
        float angle = Mth.TWO_PI * (seed + age * (seed < 0.5f ? 1.1f : -1.1f));
        return dest.set(radius * Mth.cos(angle), rise, radius * Mth.sin(angle));
    }

    private static float frameU(int frame) {
        return frame * FLAME_TEXELS / (float) SHEET_WIDTH;
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, int rgb) {
        out.addVertex(pose, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    private static void raw(VertexConsumer out, float x, float y, float z, float u, float v, int rgb) {
        out.addVertex(x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    static int mix(int from, int to, float t) {
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }
}
