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
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Frost Shard's look: a small solid crystal of the lance's ice, its chips, and the flick that throws it. Visual only. */
class FrostShardArtTest {
    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static Map<String, LanceMesh.Piece> mesh() throws IOException {
        try (Reader reader = Files.newBufferedReader(project("src/main/resources/assets/kegare/models/fx/frost_shard.obj"))) {
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

    private static float extent(LanceMesh.Piece piece, int axis) {
        float[] c = piece.corners();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < c.length / 9; i++) { // nine floats a corner, its position first
            lo = Math.min(lo, c[i * 9 + axis]);
            hi = Math.max(hi, c[i * 9 + axis]);
        }
        return hi - lo;
    }

    @Test
    void theMeshHasTheShardsAndChips() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        for (String name : new String[] {"frost_shard", "splinter", "chip_0", "chip_1", "chip_2", "chip_3"}) {
            assertNotNull(pieces.get(name), name);
            assertTrue(pieces.get(name).triangles() >= 8, name + " is a solid, not a card");
        }
    }

    @Test
    void everyPieceIsWoundOutward() throws IOException {
        for (LanceMesh.Piece piece : mesh().values()) {
            float[] c = piece.corners();
            double volume = 0;
            for (int t = 0; t < piece.triangles(); t++) {
                Vector3f a = corner(c, t * 3), b = corner(c, t * 3 + 1), d = corner(c, t * 3 + 2);
                volume += a.dot(new Vector3f(b).cross(d)) / 6.0;
            }
            assertTrue(volume > 0, piece.name() + " encloses " + volume + ": wound inward or open");
        }
    }

    /** Small and sharp, pointing the way it flies (+Z); the Splinter's is clearly smaller. */
    @Test
    void itIsSmallSharpAndTheSplinterLighter() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        LanceMesh.Piece shard = pieces.get("frost_shard"), splinter = pieces.get("splinter");
        float length = shard.maxZ() - shard.minZ();
        assertTrue(length > 0.45f && length < 0.7f, "about half a block long: " + length);
        assertTrue(extent(shard, 0) < length / 3.5f, "slim and sharp, not a lump");
        assertTrue(shard.maxZ() > -shard.minZ(), "the long point is the front");
        assertTrue(splinter.maxZ() - splinter.minZ() < length * 0.75f, "a Splinter's is shorter");
        assertTrue(extent(splinter, 0) < extent(shard, 0) * 0.75f, "and slimmer");
    }

    @Test
    void theTexturesAreSolidAndSmooth() throws IOException {
        BufferedImage body = ImageIO.read(project("src/main/resources/assets/kegare/textures/entity/frost_shard.png").toFile());
        for (int y = 0; y < body.getHeight(); y += 2) {
            for (int x = 0; x < body.getWidth(); x += 2) assertEquals(255, body.getRGB(x, y) >>> 24, "see-through at " + x + "," + y);
        }
        BufferedImage glow = ImageIO.read(project("src/main/resources/assets/kegare/textures/entity/frost_shard_glow.png").toFile());
        boolean any = false;
        for (int y = 0; y < glow.getHeight(); y++) {
            for (int x = 0; x < glow.getWidth(); x++) {
                int alpha = glow.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "the glow is half see-through at " + x + "," + y);
                any |= alpha == 255;
            }
        }
        assertTrue(any);
        for (String name : new String[] {"frost_shard", "frost_shard_glow"}) {
            JsonObject meta = json("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta");
            assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name + " is filtered smooth");
        }
    }

    /** A small shatter: a few chips; a Splinter's fewer and smaller. */
    @Test
    void itBreaksIntoAFewChips() {
        assertEquals(1f, ShardImpact.size(null), 1e-6f);
        assertTrue(ShardImpact.chips(1f) >= 4 && ShardImpact.chips(1f) <= 6, "a small shatter");
        assertTrue(ShardImpact.chips(0.6f) < ShardImpact.chips(1f));
    }

    /** A short line: never back past where it was thrown from, and no wider than a hair seen end-on (no sheet across the view). */
    @Test
    void itsLineIsShortAndThinEndOn() {
        assertEquals(0f, ShardRenderer.lineLength(1.7f, 0.2f), 1e-6f, "nothing at the hand");
        assertEquals(1.7f, ShardRenderer.lineLength(1.7f, 20f), 1e-6f, "never longer than its full length");
        assertTrue(ShardRenderer.lineLength(1.7f, 1f) < 1.7f, "grows out behind it as it leaves");
        assertEquals(0f, ShardRenderer.seenWidth(0.05f, 0f), 1e-6f, "seen straight down its length: nothing");
        assertEquals(0.05f, ShardRenderer.seenWidth(0.05f, 1f), 1e-6f, "side on: its full width");
        assertTrue(ShardRenderer.seenWidth(0.05f, 0.1f) <= 0.05f * 0.3f);
    }

    @Test
    void palLoadsTheFlick() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json("src/main/resources/assets/kegare/player_animations/frost_shard.json"));
        Animation cast = loaded.get(RimeIds.ANIM_FROST_SHARD_CAST.getPath());
        assertNotNull(cast);
        assertEquals(Animation.LoopType.PLAY_ONCE, cast.loopType());
        assertTrue(cast.boneAnimations().size() >= 3, "the whole upper body");
        assertFalse(json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations")
                .has(RimeIds.ANIM_FROST_SHARD_CAST.getPath()), "one source for it");
    }

    @Test
    void theBlockbenchSourceIsInTheRepo() throws IOException {
        JsonObject model = json("art/blockbench/frost_shard.bbmodel");
        assertNotNull(model.get("meta"));
        for (var e : model.getAsJsonArray("elements")) assertEquals("mesh", e.getAsJsonObject().get("type").getAsString());
    }
}
