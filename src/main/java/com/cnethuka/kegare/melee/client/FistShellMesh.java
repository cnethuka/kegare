package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The shell over a hand and forearm, read from its Blockbench OBJ export ({@code art/blockbench/fist_shell.bbmodel}). Modeled on an arm
 * hanging down with the hand at y=0; drawn in a player model's arm space, so it follows whatever pose the arm is in. It fades out by
 * height toward the elbow, per vertex, so the texture can scroll along it freely.
 */
final class FistShellMesh {
    /** A player arm's cube ends here in its own space: 10 pixels below the pivot. */
    private static final float HAND_END = 10f / 16f;
    private static final float FADE_FROM = 2.8f / 16f;
    private static final float FADE_TO = 6.6f / 16f;
    /** x y z u v nx ny nz, four to a quad. */
    private static final int STRIDE = 8;

    private static FistShellMesh regular;
    private static FistShellMesh slim;

    private final float[] vertices;

    private FistShellMesh(float[] vertices) {
        this.vertices = vertices;
    }

    static FistShellMesh of(boolean slimArm) {
        if (slimArm) {
            if (slim == null) slim = load(Kegare.id("models/fx/fist_shell_slim.obj"));
            return slim;
        }
        if (regular == null) regular = load(Kegare.id("models/fx/fist_shell_regular.obj"));
        return regular;
    }

    /** After a resource reload. */
    static void forget() {
        regular = null;
        slim = null;
    }

    /** Where the arm's middle is in its own space: a right arm's cube is off-centre toward the body's outside. */
    static float armCentre(boolean slimArm, boolean leftArm) {
        float centre = (slimArm ? 0.5f : 1f) / 16f;
        return leftArm ? centre : -centre;
    }

    /** The middle of the fist, just past the knuckles, in the arm's space. */
    static float fistY() {
        return HAND_END + 0.5f / 16f;
    }

    /**
     * @param grow   how much bigger round the arm's axis (1 = as modeled)
     * @param scroll added to V, so what's in the texture flows along the arm
     * @param lit    shade it by its real normals (a physical thing), or face it straight at the light (it glows, so sides aren't darker)
     */
    void draw(PoseStack.Pose pose, VertexConsumer out, boolean leftArm, float centre, float grow, float scroll, int rgb, float alpha, int light, boolean lit) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float mirror = leftArm ? -1f : 1f;
        for (int i = 0; i < vertices.length; i += STRIDE) {
            float y = vertices[i + 1];
            float x = vertices[i] * grow * mirror + centre;
            float z = vertices[i + 2] * grow;
            int a = Math.round(255f * alpha * fade(y));
            VertexConsumer vertex = out.addVertex(pose, x, HAND_END - y, z).setColor(r, g, b, a).setUv(vertices[i + 3], vertices[i + 4] + scroll)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light);
            if (lit) vertex.setNormal(pose, vertices[i + 5] * mirror, -vertices[i + 6], vertices[i + 7]);
            else vertex.setNormal(0f, 1f, 0f);
        }
    }

    /** 1 up to the wrist, easing to 0 at the elbow. */
    static float fade(float y) {
        float t = Math.max(0f, Math.min(1f, (y - FADE_FROM) / (FADE_TO - FADE_FROM)));
        return 1f - t * t * (3f - 2f * t);
    }

    private static FistShellMesh load(ResourceLocation id) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Float> out = new ArrayList<>();
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(floats(parts));
                    case "vt" -> uvs.add(floats(parts));
                    case "vn" -> normals.add(floats(parts));
                    case "f" -> {
                        if (parts.length != 5) continue; // quads only: a triangle has no place in this mesh
                        for (int i = 1; i <= 4; i++) {
                            String[] index = parts[i].split("/");
                            float[] p = positions.get(Integer.parseInt(index[0]) - 1);
                            float[] t = uvs.get(Integer.parseInt(index[1]) - 1);
                            float[] n = normals.get(Integer.parseInt(index[2]) - 1);
                            for (float value : new float[] {p[0], p[1], p[2], t[0], t[1], n[0], n[1], n[2]}) out.add(value);
                        }
                    }
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException e) {
            Kegare.LOGGER.warn("Couldn't read {}; no shell on a ready fist", id, e);
        }
        float[] vertices = new float[out.size()];
        for (int i = 0; i < vertices.length; i++) vertices[i] = out.get(i);
        return new FistShellMesh(vertices);
    }

    private static float[] floats(String[] parts) {
        float[] values = new float[parts.length - 1];
        for (int i = 1; i < parts.length; i++) values[i - 1] = Float.parseFloat(parts[i]);
        return values;
    }
}
