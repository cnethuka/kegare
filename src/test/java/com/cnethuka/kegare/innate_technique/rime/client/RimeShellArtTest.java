package com.cnethuka.kegare.innate_technique.rime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Rime Shell's look: solid plates of the lance's ice on the body (each in its body part's space, touching the skin, standing a
 * pixel or two off it), forming one after another as the arms are flung out, and the toggle's pose. Visual only: nothing here
 * touches what the shell does.
 */
class RimeShellArtTest {
    /** Each part's box about its pivot, in pixels (the player model's; Blockbench's axes: up +Y, the player's right +X, front -Z). */
    private static final Map<String, float[]> BOXES = Map.of(
            "torso", new float[] {-4, 4, -12, 0, -2, 2},
            "right_arm", new float[] {-1, 3, -10, 2, -2, 2},
            "left_arm", new float[] {-3, 1, -10, 2, -2, 2},
            "right_leg", new float[] {-2, 2, -12, 0, -2, 2},
            "left_leg", new float[] {-2, 2, -12, 0, -2, 2});

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static BufferedImage image(String path) throws IOException {
        return ImageIO.read(project("src/main/resources/assets/kegare/textures/" + path).toFile());
    }

    private static Map<String, LanceMesh.Piece> mesh() throws IOException {
        try (Reader reader = Files.newBufferedReader(project("src/main/resources/assets/kegare/models/fx/rime_shell.obj"))) {
            return LanceMesh.parse(reader);
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Vector3f corner(float[] c, int i) {
        return new Vector3f(c[i * 9], c[i * 9 + 1], c[i * 9 + 2]);
    }

    /** How far a point (pixels) is outside a part's box; 0 on or inside it. */
    private static float outside(Vector3f p, float[] box) {
        float dx = Math.max(Math.max(box[0] - p.x, 0f), p.x - box[1]);
        float dy = Math.max(Math.max(box[2] - p.y, 0f), p.y - box[3]);
        float dz = Math.max(Math.max(box[4] - p.z, 0f), p.z - box[5]);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------------------------------ the plates

    @Test
    void everyPlateIsInTheMesh() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        assertEquals(ShellClient.PLATES.size(), pieces.size(), "one object a plate, no strays");
        int at = 0;
        for (int p = 0; p < ShellClient.PARTS.length; p++) {
            for (int i = 0; i < ShellClient.COUNTS[p]; i++, at++) {
                String name = ShellClient.PARTS[p] + "_" + i;
                assertEquals(name, ShellClient.PLATES.get(at));
                LanceMesh.Piece piece = pieces.get(name);
                assertNotNull(piece, name);
                assertTrue(piece.triangles() >= 12, name + " is a slab, not a card");
            }
        }
        assertTrue(ShellClient.PLATES.get(0).startsWith("torso"), "the chest forms first");
    }

    /** Hugging the body: every plate touches its part's skin and stands no more than a couple of pixels off it. */
    @Test
    void thePlatesHugTheBody() throws IOException {
        for (Map.Entry<String, LanceMesh.Piece> e : mesh().entrySet()) {
            String name = e.getKey();
            float[] box = BOXES.get(name.substring(0, name.lastIndexOf('_')));
            float[] c = e.getValue().corners();
            float near = Float.MAX_VALUE, far = 0f;
            for (int t = 0; t < e.getValue().triangles(); t++) {
                Vector3f a = corner(c, t * 3).mul(16f), b = corner(c, t * 3 + 1).mul(16f), d = corner(c, t * 3 + 2).mul(16f);
                for (int i = 0; i <= 8; i++) {
                    for (int j = 0; i + j <= 8; j++) {
                        float u = i / 8f, v = j / 8f;
                        Vector3f q = new Vector3f(a).mul(1 - u - v).add(new Vector3f(b).mul(u)).add(new Vector3f(d).mul(v));
                        float off = outside(q, box);
                        near = Math.min(near, off);
                        far = Math.max(far, off);
                    }
                }
            }
            assertTrue(near < 0.01f, name + " floats " + near + " px off the body");
            assertTrue(far < 2.5f, name + " stands " + far + " px out: armour, not a bubble");
        }
    }

    @Test
    void everyPlateIsWoundOutward() throws IOException {
        for (Map.Entry<String, LanceMesh.Piece> e : mesh().entrySet()) {
            float[] c = e.getValue().corners();
            double volume = 0;
            for (int t = 0; t < e.getValue().triangles(); t++) {
                Vector3f a = corner(c, t * 3), b = corner(c, t * 3 + 1), d = corner(c, t * 3 + 2);
                volume += a.dot(new Vector3f(b).cross(d)) / 6.0;
            }
            assertTrue(volume > 0, e.getKey() + " is wound inward");
        }
    }

    /** Solid ice, not glass: the colour has no see-through texel, and the glow is only the lines, fully on or off. */
    @Test
    void theTexturesAreSolidAndSmooth() throws IOException {
        BufferedImage body = image("entity/rime_shell.png");
        List<Integer> texels = new ArrayList<>();
        for (int y = 0; y < body.getHeight(); y += 3) {
            for (int x = 0; x < body.getWidth(); x += 3) {
                int argb = body.getRGB(x, y);
                assertEquals(255, argb >>> 24, "see-through at " + x + "," + y);
                texels.add(argb);
            }
        }
        texels.sort(Comparator.comparingInt(argb -> ((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255)));
        int quarter = texels.size() / 4;
        long darkRed = 0, darkBlue = 0, lightRed = 0;
        for (int i = 0; i < quarter; i++) {
            darkRed += (texels.get(i) >> 16) & 255;
            darkBlue += texels.get(i) & 255;
            lightRed += (texels.get(texels.size() - 1 - i) >> 16) & 255;
        }
        assertTrue(darkBlue > darkRed * 1.6, "the faces' cores are navy");
        assertTrue(lightRed / quarter > 200, "fading to pale frost");
        BufferedImage glow = image("entity/rime_shell_glow.png");
        boolean any = false;
        for (int y = 0; y < glow.getHeight(); y++) {
            for (int x = 0; x < glow.getWidth(); x++) {
                int alpha = glow.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "the glow is half see-through at " + x + "," + y);
                any |= alpha == 255;
            }
        }
        assertTrue(any, "the glow draws something");
        for (String name : new String[] {"rime_shell", "rime_shell_glow"}) {
            JsonObject meta = json("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta");
            assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name + " is filtered smooth");
        }
    }

    // ------------------------------------------------------------------------------------------ forming

    /** Nothing while the arms cross; then each plate pops out a little past its size and settles, one after another. */
    @Test
    void thePlatesFormAsTheArmsAreFlungOut() {
        assertEquals(0f, ShellClient.formed(0, ShellClient.DELAY), "nothing while the arms are crossed over the chest");
        float peak = 0f;
        for (float t = 0f; t <= ShellClient.formTicks() + 2f; t += 0.05f) peak = Math.max(peak, ShellClient.formed(0, t));
        assertEquals(1f + ShellClient.OVERSHOOT, peak, 0.01f, "it pops past its size");
        int last = ShellClient.PLATES.size() - 1;
        assertEquals(0f, ShellClient.formed(last, ShellClient.DELAY + 1f), "the last plate waits its turn");
        assertEquals(1f, ShellClient.formed(last, ShellClient.formTicks()), 1e-4f, "every plate in place by the end");
        assertEquals(1f, ShellClient.formed(last, 1000f), "and stays so");
        assertTrue(ShellClient.formTicks() <= 14f, "the whole shell up within about two thirds of a second");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheToggle() throws IOException {
        String path = "src/main/resources/assets/kegare/player_animations/rime_shell.json";
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json(path));
        Animation toggle = loaded.get(RimeIds.ANIM_RIME_SHELL_TOGGLE.getPath());
        assertNotNull(toggle);
        assertEquals(Animation.LoopType.PLAY_ONCE, toggle.loopType());
        JsonObject bones = json(path).getAsJsonObject("animations").getAsJsonObject(RimeIds.ANIM_RIME_SHELL_TOGGLE.getPath())
                .getAsJsonObject("bones");
        for (String bone : new String[] {"right_arm", "left_arm", "torso"}) assertTrue(bones.has(bone), "the toggle keys the " + bone);
        assertFalse(json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations")
                .has(RimeIds.ANIM_RIME_SHELL_TOGGLE.getPath()), "one source for it");
    }

    @Test
    void theBlockbenchSourceIsInTheRepo() throws IOException {
        JsonObject model = json("art/blockbench/rime_shell.bbmodel");
        assertNotNull(model.get("meta"));
        for (var e : model.getAsJsonArray("elements")) assertEquals("mesh", e.getAsJsonObject().get("type").getAsString());
    }
}
