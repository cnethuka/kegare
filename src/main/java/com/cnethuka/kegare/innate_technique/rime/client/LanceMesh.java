package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Glacial Lance's mesh ({@code models/fx/glacial_lance.obj}, from Blockbench) and how it's drawn. In blocks, along +Z, tip forward,
 * the fist round the origin. Every face is flat and every texel opaque.
 * <p>
 * Two passes over the same triangles. The body is solid and lit by the world like any entity, its faces shaded by which way they
 * point, so the planes read; light never drops below {@link #LIGHT_FLOOR}. Then the ridges and cracks again from
 * {@code glacial_lance_glow.png}, cut out hard and full-bright, so at night the lance is still a shape drawn in pale lines with
 * faint cracks through it where the navy would vanish.
 */
public final class LanceMesh {
    public static final ResourceLocation MODEL = Kegare.id("models/fx/glacial_lance.obj");
    public static final ResourceLocation TEXTURE = Kegare.id("textures/entity/glacial_lance.png");
    public static final ResourceLocation GLOW = Kegare.id("textures/entity/glacial_lance_glow.png");
    /** Block light the body never drops below: dark at night, but navy, not black. */
    static final int LIGHT_FLOOR = 5;
    /** How much the faces' shading leans toward the world's up: the planes stay distinct, the undersides don't go black. */
    private static final float SHADE_LIFT = 0.55f;

    /** One chunk or shard: triangles as x, y, z, u, v, then the face's normal, nine floats a corner. */
    public record Piece(String name, float[] corners, float minZ, float maxZ) {
        int triangles() {
            return corners.length / 27;
        }
    }

    /**
     * One mesh of Rime's ice and its two textures, drawn the same way: the lance, and Glacial Spire's spike
     * (tools/blockbench/build_glacial_spire.py, the same painter at a coarser density).
     */
    public static final class Model {
        final ResourceLocation obj;
        final ResourceLocation texture;
        final ResourceLocation glowTexture;
        @Nullable private Map<String, Piece> pieces;

        public Model(ResourceLocation obj, ResourceLocation texture, ResourceLocation glowTexture) {
            this.obj = obj;
            this.texture = texture;
            this.glowTexture = glowTexture;
        }

        @Nullable
        public Piece piece(String name) {
            if (pieces == null) pieces = load();
            return pieces.get(name);
        }

        /** Resources reloaded: read the mesh again when it's next drawn. */
        public void forget() {
            pieces = null;
        }

        private Map<String, Piece> load() {
            try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(obj)) {
                return parse(reader);
            } catch (IOException | RuntimeException e) {
                Kegare.LOGGER.warn("Couldn't read {}; it won't be drawn", obj, e);
                return Map.of();
            }
        }

        public RenderType body() {
            return RenderType.entitySolid(texture);
        }

        public RenderType glow() {
            return RenderType.entityCutout(glowTexture);
        }

        /** Draws pieces into both passes, each about the origin by its own {@code scales} entry (0 skips it). */
        public void draw(PoseStack.Pose pose, MultiBufferSource buffers, List<String> names, float[] scales, int light) {
            VertexConsumer body = buffers.getBuffer(body());
            int lit = light(light);
            for (int i = 0; i < names.size(); i++) {
                Piece piece = scales[i] > 0f ? piece(names.get(i)) : null;
                if (piece != null) emit(pose, body, piece, scales[i], lit, false);
            }
            VertexConsumer glow = buffers.getBuffer(glow());
            for (int i = 0; i < names.size(); i++) {
                Piece piece = scales[i] > 0f ? piece(names.get(i)) : null;
                if (piece != null) emit(pose, glow, piece, scales[i], LightTexture.FULL_BRIGHT, true);
            }
        }

        /** Draws pieces each in its own pose (moved, turned, scaled), both passes, with the same world light. */
        public void draw(MultiBufferSource buffers, List<PoseStack.Pose> poses, List<String> names, int light) {
            VertexConsumer body = buffers.getBuffer(body());
            int lit = light(light);
            for (int i = 0; i < names.size(); i++) {
                Piece piece = piece(names.get(i));
                if (piece != null) emit(poses.get(i), body, piece, 1f, lit, false);
            }
            VertexConsumer glow = buffers.getBuffer(glow());
            for (int i = 0; i < names.size(); i++) {
                Piece piece = piece(names.get(i));
                if (piece != null) emit(poses.get(i), glow, piece, 1f, LightTexture.FULL_BRIGHT, true);
            }
        }
    }

    /** The lance. */
    public static final Model LANCE = new Model(MODEL, TEXTURE, GLOW);
    /** Glacial Spire's spike: its pieces and the chunks it breaks into (SpireShape). */
    public static final Model SPIRE = new Model(Kegare.id("models/fx/glacial_spire.obj"), Kegare.id("textures/entity/glacial_spire.png"),
            Kegare.id("textures/entity/glacial_spire_glow.png"));

    private LanceMesh() {}

    @Nullable
    public static Piece piece(String name) {
        return LANCE.piece(name);
    }

    /** Frost Shard's and Splinter's thrown crystals and the chips they break into (tools/blockbench/build_frost_shard.py). */
    public static final Model SHARD = new Model(Kegare.id("models/fx/frost_shard.obj"), Kegare.id("textures/entity/frost_shard.png"),
            Kegare.id("textures/entity/frost_shard_glow.png"));

    /** Resources reloaded: read the meshes again when they're next drawn. */
    public static void forget() {
        LANCE.forget();
        SPIRE.forget();
        SHARD.forget();
    }

    /** Reads Blockbench's OBJ: one object per piece, triangles, texture coordinates with v counted from the bottom. */
    static Map<String, Piece> parse(Reader in) throws IOException {
        BufferedReader reader = in instanceof BufferedReader b ? b : new BufferedReader(in);
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        Map<String, List<Float>> corners = new HashMap<>();
        List<String> order = new ArrayList<>();
        String current = "";
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            switch (parts[0]) {
                case "o", "g" -> {
                    current = parts.length > 1 ? parts[1] : "";
                    if (!order.contains(current)) order.add(current);
                }
                case "v" -> positions.add(new float[] {Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                case "vt" -> uvs.add(new float[] {Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                case "f" -> {
                    // A face of n corners, fanned out into triangles (Blockbench writes triangles, but don't count on it).
                    int n = parts.length - 1;
                    int[][] index = new int[n][];
                    for (int i = 0; i < n; i++) {
                        String[] ref = parts[i + 1].split("/");
                        index[i] = new int[] {Integer.parseInt(ref[0]) - 1, Integer.parseInt(ref[1]) - 1};
                    }
                    List<Float> out = corners.computeIfAbsent(current, k -> new ArrayList<>());
                    for (int i = 1; i + 1 < n; i++) triangle(out, positions, uvs, index[0], index[i], index[i + 1]);
                }
                default -> { }
            }
        }
        Map<String, Piece> pieces = new HashMap<>();
        for (String name : order) {
            List<Float> list = corners.get(name);
            if (list == null) continue;
            float[] values = new float[list.size()];
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < values.length; i++) {
                values[i] = list.get(i);
                if (i % 9 == 2) {
                    minZ = Math.min(minZ, values[i]);
                    maxZ = Math.max(maxZ, values[i]);
                }
            }
            pieces.put(name, new Piece(name, values, minZ, maxZ));
        }
        return pieces;
    }

    private static void triangle(List<Float> out, List<float[]> positions, List<float[]> uvs, int[] a, int[] b, int[] c) {
        float[] pa = positions.get(a[0]), pb = positions.get(b[0]), pc = positions.get(c[0]);
        Vector3f normal = new Vector3f(pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2])
                .cross(pc[0] - pa[0], pc[1] - pa[1], pc[2] - pa[2]);
        if (normal.lengthSquared() < 1.0e-12f) return;
        normal.normalize();
        for (int[] corner : new int[][] {a, b, c}) {
            float[] p = positions.get(corner[0]);
            float[] t = uvs.get(corner[1]);
            out.add(p[0]);
            out.add(p[1]);
            out.add(p[2]);
            out.add(t[0]);
            out.add(1f - t[1]);
            out.add(normal.x);
            out.add(normal.y);
            out.add(normal.z);
            out.add(0f);
        }
    }

    // ---------------------------------------------------------------------------------------------- drawing

    public static RenderType body() {
        return LANCE.body();
    }

    public static RenderType glow() {
        return LANCE.glow();
    }

    /** World light with the floor under it. */
    public static int light(int packedLight) {
        int block = Math.max(LightTexture.block(packedLight), LIGHT_FLOOR);
        return LightTexture.pack(block, LightTexture.sky(packedLight));
    }

    /**
     * Draws pieces into both passes. Each is scaled about the fist by its own {@code scales} entry (0 skips it).
     *
     * @param names  the pieces, in the same order as {@code scales}
     * @param light  packed world light where the lance is
     */
    public static void draw(PoseStack.Pose pose, MultiBufferSource buffers, List<String> names, float[] scales, int light) {
        LANCE.draw(pose, buffers, names, scales, light);
    }

    /** One piece into one pass. Triangles go out as quads with the last corner twice, which is what an entity render type takes. */
    static void emit(PoseStack.Pose pose, VertexConsumer out, Piece piece, float scale, int light, boolean glow) {
        float[] c = piece.corners();
        Vector3f n = new Vector3f();
        for (int t = 0; t < piece.triangles(); t++) {
            int base = t * 27;
            if (glow) {
                n.set(0f, 1f, 0f); // straight up, unturned: the shader's two lights both reach it, so it's never shaded
            } else {
                // The pose comes out along the world's axes (the camera's turn is in the view matrix), and so do the shader's lights.
                pose.transformNormal(n.set(c[base + 5], c[base + 6], c[base + 7]), n);
                n.y += SHADE_LIFT;
                n.normalize();
            }
            for (int k = 0; k < 4; k++) {
                int i = base + Math.min(k, 2) * 9;
                out.addVertex(pose, c[i] * scale, c[i + 1] * scale, c[i + 2] * scale)
                        .setColor(255, 255, 255, 255)
                        .setUv(c[i + 3], c[i + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(n.x, n.y, n.z);
            }
        }
    }
}
