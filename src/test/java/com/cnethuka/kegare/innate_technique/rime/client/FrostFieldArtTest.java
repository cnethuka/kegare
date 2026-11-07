package com.cnethuka.kegare.innate_technique.rime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Frost Field's look: the solid crystals that jut out of the frosted ground, where the frost goes (the cone's own footprint, racing
 * out, a mat not a sheet), and the channel's pose. Visual only: nothing here touches what the cone does.
 */
class FrostFieldArtTest {
    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static BufferedImage image(String path) throws IOException {
        return ImageIO.read(project("src/main/resources/assets/kegare/textures/" + path).toFile());
    }

    private static Map<String, LanceMesh.Piece> mesh() throws IOException {
        try (Reader reader = Files.newBufferedReader(project("src/main/resources/assets/kegare/models/fx/frost_field.obj"))) {
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

    // ------------------------------------------------------------------------------------------ the crystals

    /** Solid clusters, small: a hand's height or two out of the ground, their feet below it so they can rise out of it. */
    @Test
    void theCrystalsAreSmallSolidClusters() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        for (int i = 0; i < 4; i++) {
            LanceMesh.Piece piece = pieces.get("cluster_" + i);
            assertNotNull(piece, "cluster_" + i);
            assertTrue(piece.triangles() >= 24, "crystals, not cards");
            float[] c = piece.corners();
            float top = -1e9f, bottom = 1e9f;
            for (int k = 0; k < c.length / 9; k++) {
                top = Math.max(top, c[k * 9 + 1]);
                bottom = Math.min(bottom, c[k * 9 + 1]);
            }
            assertTrue(top > 0.3f && top < 0.9f, "cluster_" + i + " stands " + top);
            assertTrue(bottom < -0.1f, "its foot is under the ground");
        }
    }

    @Test
    void everyClusterIsWoundOutward() throws IOException {
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

    @Test
    void theTexturesAreSolidAndSmooth() throws IOException {
        BufferedImage body = image("entity/frost_field.png");
        for (int y = 0; y < body.getHeight(); y += 3) {
            for (int x = 0; x < body.getWidth(); x += 3) assertEquals(255, body.getRGB(x, y) >>> 24, "see-through at " + x + "," + y);
        }
        BufferedImage glow = image("entity/frost_field_glow.png");
        boolean any = false;
        for (int y = 0; y < glow.getHeight(); y++) {
            for (int x = 0; x < glow.getWidth(); x++) {
                int alpha = glow.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "the glow is half see-through at " + x + "," + y);
                any |= alpha == 255;
            }
        }
        assertTrue(any, "the glow draws something");
        for (String name : new String[] {"frost_field", "frost_field_glow"}) {
            JsonObject meta = json("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta");
            assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name + " is filtered smooth");
        }
    }

    /** Jutting up briefly: out of the ground in two ticks, a moment standing, then gone back into it. */
    @Test
    void aCrystalShootsUpHoldsAndSinks() {
        assertEquals(0f, FieldClient.risen(0f, 6), 1e-6f);
        assertTrue(FieldClient.risen(1f, 6) > 0.7f, "most of the way up in a tick");
        assertEquals(1f, FieldClient.risen(FieldClient.RISE + 3, 6), 1e-6f, "standing");
        assertEquals(0f, FieldClient.risen(FieldClient.RISE + 6 + FieldClient.SINK, 6), 1e-6f, "and back under");
        float peak = 0f;
        for (float t = 0f; t < 20f; t += 0.1f) peak = Math.max(peak, FieldClient.risen(t, 6));
        assertTrue(peak < 1.1f, "a small overshoot at most: " + peak);
    }

    // ------------------------------------------------------------------------------------------ the frost

    /** The frost goes where the cone really reaches: within its range and half-angle of the look. */
    @Test
    void theFrostFollowsTheCone() {
        Vec3 eye = new Vec3(0, 1.62, 0);
        Vec3 look = new Vec3(1, 0, 0);
        double cos = Math.cos(Math.toRadians(35));
        assertTrue(FieldClient.inCone(eye, look, new Vec3(4, 0, 0), 6, cos), "ahead, on the ground");
        assertFalse(FieldClient.inCone(eye, look, new Vec3(1, 0, 0), 6, cos), "at the feet: below the cone");
        assertFalse(FieldClient.inCone(eye, look, new Vec3(7, 0, 0), 6, cos), "past its range");
        assertFalse(FieldClient.inCone(eye, look, new Vec3(3, 0, 3), 6, cos), "outside its half-angle");
        assertFalse(FieldClient.inCone(eye, look, new Vec3(-4, 0, 0), 6, cos), "behind");
    }

    /** It races out from the caster when the channel starts, a fraction of a second end to end; after that it's just kept up. */
    @Test
    void theFrostRacesOut() {
        assertEquals(0, FieldClient.raceDelay(0, 0));
        assertTrue(FieldClient.raceDelay(6, 0) >= 3 && FieldClient.raceDelay(6, 0) <= 8, "out to its range in a few ticks");
        assertTrue(FieldClient.raceDelay(2, 0) < FieldClient.raceDelay(5, 0), "nearer first");
        assertEquals(0, FieldClient.raceDelay(6, 20), "kept up once it's there");
    }

    /** A mat, not a white sheet: laid from every other block, each block's patch always the same (laying it again keeps it alive). */
    @Test
    void theFrostIsAMatNotASheet() {
        int seeded = 0;
        for (int x = -8; x < 8; x++) {
            for (int z = -8; z < 8; z++) {
                if (FieldClient.seeds(x, z)) seeded++;
                assertEquals(FieldClient.cellHash(x, z), FieldClient.cellHash(x, z));
            }
        }
        assertEquals(128, seeded, "every other block");
        assertNotEquals(FieldClient.cellHash(0, 0), FieldClient.cellHash(1, 0));
        assertTrue(FieldClient.SPREAD + FieldClient.HOLD > FieldClient.REFRESH_TICKS, "kept up without flickering while it's held");
        assertTrue(FieldClient.MELT >= 20, "melts over a second or more, from the edge in");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheChannel() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json("src/main/resources/assets/kegare/player_animations/frost_field.json"));
        Animation channel = loaded.get(RimeIds.ANIM_FROST_FIELD_CHANNEL.getPath());
        assertNotNull(channel);
        assertNotEquals(Animation.LoopType.PLAY_ONCE, channel.loopType(), "held for as long as the channel");
        assertTrue(channel.boneAnimations().size() >= 3, "the whole upper body");
        assertFalse(json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations")
                .has(RimeIds.ANIM_FROST_FIELD_CHANNEL.getPath()), "one source for it");
    }

    @Test
    void theBlockbenchSourceIsInTheRepo() throws IOException {
        JsonObject model = json("art/blockbench/frost_field.bbmodel");
        assertNotNull(model.get("meta"));
        for (var e : model.getAsJsonArray("elements")) assertEquals("mesh", e.getAsJsonObject().get("type").getAsString());
    }
}
