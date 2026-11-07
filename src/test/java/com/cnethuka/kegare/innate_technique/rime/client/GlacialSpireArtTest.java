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
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Glacial Spire's look: the solid spike (the lance's ice, in the lance's style), how it comes up, the pixel crack and shattered ground
 * it leaves on the blocks, and the body's animations. Visual only: nothing here touches what the spike does.
 */
class GlacialSpireArtTest {
    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static BufferedImage image(String path) throws IOException {
        return ImageIO.read(project("src/main/resources/assets/kegare/textures/" + path).toFile());
    }

    private static Map<String, LanceMesh.Piece> mesh() throws IOException {
        try (Reader reader = Files.newBufferedReader(project("src/main/resources/assets/kegare/models/fx/glacial_spire.obj"))) {
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

    private static float top(LanceMesh.Piece piece) {
        float[] c = piece.corners();
        float y = -1e9f;
        for (int i = 0; i < c.length / 9; i++) y = Math.max(y, c[i * 9 + 1]);
        return y;
    }

    // ------------------------------------------------------------------------------------------ the spike

    @Test
    void theMeshHasEveryPieceAndChunk() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        for (String name : SpireShape.PIECES) {
            assertNotNull(pieces.get(name), name);
            assertTrue(pieces.get(name).triangles() >= 12, name + " is solid crystals, not cards");
        }
        for (int i = 0; i < SpireShape.CHUNKS; i++) assertNotNull(pieces.get("chunk_" + i), "chunk_" + i);
    }

    /** Closed and wound outward, every piece: drawn with back faces culled, so from inside (launched off its point) it's not there. */
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

    /** The spire is the tallest, and stands over the hit's height (2.5) at full charge; its foot is sunk below the ground. */
    @Test
    void theSpireStandsTallerThanTheHit() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        float core = top(pieces.get("core"));
        assertTrue(core > 3f && core < 3.8f, "the point at " + core);
        for (String name : SpireShape.PIECES) assertTrue(top(pieces.get(name)) <= core + 1e-4f, name + " is taller than the spire");
        for (int i = 0; i < SpireShape.PIECES.size(); i++) {
            float drawnTop = top(pieces.get(SpireShape.PIECES.get(i)));
            assertTrue(SpireShape.TOP[i] >= drawnTop - 0.25f, SpireShape.PIECES.get(i) + " comes up from too shallow: it'd show under the ground");
        }
    }

    @Test
    void theTexturesAreSolidAndSmooth() throws IOException {
        BufferedImage body = image("entity/glacial_spire.png");
        for (int y = 0; y < body.getHeight(); y += 3) {
            for (int x = 0; x < body.getWidth(); x += 3) assertEquals(255, body.getRGB(x, y) >>> 24, "see-through at " + x + "," + y);
        }
        BufferedImage glow = image("entity/glacial_spire_glow.png");
        boolean any = false;
        for (int y = 0; y < glow.getHeight(); y++) {
            for (int x = 0; x < glow.getWidth(); x++) {
                int alpha = glow.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "the glow is half see-through at " + x + "," + y);
                any |= alpha == 255;
            }
        }
        assertTrue(any, "the glow draws something");
        for (String name : new String[] {"glacial_spire", "glacial_spire_glow"}) {
            JsonObject meta = json("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta");
            assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name + " is filtered smooth");
        }
    }

    // ------------------------------------------------------------------------------------------ how it comes up

    /** Sudden: out of the ground in a few ticks, fastest at the start, a little past full and back. */
    @Test
    void itShootsUpOvershootsAndSettles() {
        assertEquals(0f, SpireShape.rise(0f, 1), 1e-6f);
        assertTrue(SpireShape.rise(1f, 1) > 0.7f, "most of the way up in a tick");
        float peak = 0f;
        for (float t = 0f; t < 10f; t += 0.1f) peak = Math.max(peak, SpireShape.rise(t, 1));
        assertTrue(peak > 1.05f && peak <= 1f + SpireShape.OVERSHOOT + 1e-4f, "overshoots a little: " + peak);
        assertEquals(1f, SpireShape.rise(20f, 1), 1e-6f, "and settles");
        assertEquals(0f, SpireShape.sunk(20f, 1), 1e-6f, "standing on the ground");
        assertEquals(SpireShape.TOP[1], SpireShape.sunk(0f, 1), 1e-6f, "all of it under the ground at first");
    }

    /** In chunks: the foot and the spire first, the rest a beat behind. */
    @Test
    void itComesUpInChunks() {
        assertTrue(SpireShape.rise(0.5f, 0) > 0f && SpireShape.rise(0.5f, 1) > 0f);
        assertEquals(0f, SpireShape.rise(0.5f, 2), 1e-6f, "the crown is a beat behind");
        assertEquals(0f, SpireShape.rise(1.2f, 3), 1e-6f, "the heavy ones later still");
        assertTrue(SpireShape.rise(6f, 3) >= 1f, "all up well before it shatters");
    }

    /** More charge: taller, wider, and the heavy crystals, as the launch is higher. */
    @Test
    void moreChargeIsTallerAndHeavier() {
        assertTrue(SpireShape.height(1f) > SpireShape.height(0f));
        assertTrue(SpireShape.width(1f) > SpireShape.width(0f));
        assertEquals(1f, SpireShape.height(1f), 1e-6f);
        assertTrue(SpireShape.height(0f) * 3.4f > 2f, "even the least charge stands over a player");
        assertFalse(SpireShape.shown(3, 0.3f), "no heavy crystals for a weak one");
        assertTrue(SpireShape.shown(3, 1f));
        for (int i = 0; i < 3; i++) assertTrue(SpireShape.shown(i, 0f));
    }

    // ------------------------------------------------------------------------------------------ on the ground

    /** The crack is spread over exactly what's left of the warning, holds while the spike stands, then melts. */
    @Test
    void theCrackSpreadsOverTheWarningAndMeltsAfter() {
        int warning = 15, stand = 25;
        BlockMarks.Timing t = SpireClient.crackTiming(warning, stand);
        assertEquals(warning, t.spread(), "fully grown just as the spike comes");
        assertEquals(1f, t.reach(), 1e-6f, "out to the ring");
        assertTrue(t.end() > warning + stand, "still there while the spike stands");
        assertEquals(SpireClient.MELT_TICKS, t.melt());
        assertTrue(SpireClient.crackTiming(0, stand).spread() >= 3, "joined late: still grows, quickly");
    }

    @Test
    void moreChargeShattersMoreGround() {
        assertTrue(SpireClient.burstTiming(1f, 25).reach() > SpireClient.burstTiming(0f, 25).reach());
        assertEquals(1f, SpireClient.burstTiming(1f, 25).reach(), 1e-6f);
    }

    /** Hard pixel art at the blocks' own size, in the same few colours as the rest of the marks; the ring lands on the hit's reach. */
    @Test
    void theCrackIsPixelArtAtTheHitsReach() throws IOException {
        for (String name : new String[] {"spire_crack", "spire_burst"}) {
            BufferedImage art = image("marks/" + name + ".png");
            BufferedImage order = image("marks/" + name + "_order.png");
            assertEquals(art.getWidth(), order.getWidth(), name);
            assertEquals(art.getHeight(), order.getHeight(), name);
            assertFalse(Files.exists(project("src/main/resources/assets/kegare/textures/marks/" + name + ".png.mcmeta")), name + " must stay nearest-filtered");
        }
        BufferedImage crack = image("marks/spire_crack.png");
        int c = crack.getWidth() / 2;
        // A ring of crack 22-25 texels out (the hit's 1.5 blocks is 24): most headings cross it.
        int crossed = 0;
        for (int k = 0; k < 32; k++) {
            double a = 2 * Math.PI * k / 32;
            for (int r = 21; r <= 26; r++) {
                int x = (int) Math.floor(c + Math.cos(a) * r), y = (int) Math.floor(c + Math.sin(a) * r);
                if ((crack.getRGB(x, y) >>> 24) > 0) {
                    crossed++;
                    break;
                }
            }
        }
        assertTrue(crossed >= 22, "the ring at the hit's reach is too broken: " + crossed + "/32");
        assertEquals(0, crack.getRGB(1, 1) >>> 24, "nothing outside it");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheChargeAndTheSlam() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json("src/main/resources/assets/kegare/player_animations/glacial_spire.json"));
        Animation charge = loaded.get(RimeIds.ANIM_GLACIAL_SPIRE_CHARGE.getPath());
        Animation release = loaded.get(RimeIds.ANIM_GLACIAL_SPIRE_RELEASE.getPath());
        assertNotNull(charge, "the charge");
        assertNotNull(release, "the slam");
        assertNotEquals(Animation.LoopType.PLAY_ONCE, charge.loopType(), "the fist stays up until it's let go");
        assertEquals(Animation.LoopType.PLAY_ONCE, release.loopType());
        assertTrue(charge.boneAnimations().size() >= 3 && release.boneAnimations().size() >= 3, "the whole body");
    }

    @Test
    void rimeJsonNoLongerCarriesThem() throws IOException {
        assertFalse(RimeJson.has(RimeIds.ANIM_GLACIAL_SPIRE_CHARGE.getPath()));
        assertFalse(RimeJson.has(RimeIds.ANIM_GLACIAL_SPIRE_RELEASE.getPath()));
    }

    @Test
    void theBlockbenchSourceIsInTheRepo() throws IOException {
        JsonObject model = json("art/blockbench/glacial_spire.bbmodel");
        assertNotNull(model.get("meta"), "a Blockbench project");
        for (var e : model.getAsJsonArray("elements")) assertEquals("mesh", e.getAsJsonObject().get("type").getAsString(), "real meshes");
    }
}
