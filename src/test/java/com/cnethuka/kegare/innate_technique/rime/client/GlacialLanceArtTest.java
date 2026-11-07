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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Glacial Lance's art as it ships (tools/blockbench/build_glacial_lance.py and tools/anims/glacial_lance.py write it): the mesh the
 * game reads, the textures' crisp edges, how the lance grows in the hand, and the frost it leaves.
 */
class GlacialLanceArtTest {
    private static final float MINIMUM = 0.2f;

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static BufferedImage image(String path) throws IOException {
        return ImageIO.read(project("src/main/resources/assets/kegare/textures/" + path).toFile());
    }

    private static Map<String, LanceMesh.Piece> mesh() throws IOException {
        try (Reader reader = Files.newBufferedReader(project("src/main/resources/assets/kegare/models/fx/glacial_lance.obj"))) {
            return LanceMesh.parse(reader);
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    // ------------------------------------------------------------------------------------------ the mesh

    @Test
    void theMeshHasEveryChunkAndShard() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        for (String name : LanceShape.PIECES) {
            assertNotNull(pieces.get(name), name);
            assertTrue(pieces.get(name).triangles() >= 4, name + " is a solid, not a card");
        }
        for (int i = 0; i < LanceShape.SHARDS; i++) assertNotNull(pieces.get("shard_" + i), "shard_" + i);
    }

    /** Every piece is closed and wound so its faces point out: the body is drawn with back faces culled. */
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

    /** The head leads: its point is the front of the lance, along +Z, and the grip is at the back of what's drawn first. */
    @Test
    void theHeadIsTheFront() throws IOException {
        Map<String, LanceMesh.Piece> pieces = mesh();
        float front = pieces.get("head").maxZ();
        for (String name : LanceShape.PIECES) assertTrue(pieces.get(name).maxZ() <= front + 1e-4f, name + " sticks out past the point");
        assertTrue(pieces.get("grip").minZ() < pieces.get("fore").minZ(), "the stub is the back of the minimum lance");
    }

    private static Vector3f corner(float[] c, int i) {
        return new Vector3f(c[i * 9], c[i * 9 + 1], c[i * 9 + 2]);
    }

    // ------------------------------------------------------------------------------------------ the textures

    /** The body is solid ice: not a texel of it see-through. */
    @Test
    void theBodyIsOpaque() throws IOException {
        BufferedImage body = image("entity/glacial_lance.png");
        assertEquals(1024, body.getWidth(), "high resolution, no pixel grid");
        assertEquals(1024, body.getHeight());
        for (int y = 0; y < body.getHeight(); y++) {
            for (int x = 0; x < body.getWidth(); x++) assertEquals(255, body.getRGB(x, y) >>> 24, "see-through at " + x + "," + y);
        }
    }

    /** The glowing edges are cut out, never half see-through: crisp, and nothing to sort. */
    @Test
    void theGlowIsCutOut() throws IOException {
        BufferedImage im = image("entity/glacial_lance_glow.png");
        boolean any = false;
        for (int y = 0; y < im.getHeight(); y++) {
            for (int x = 0; x < im.getWidth(); x++) {
                int alpha = im.getRGB(x, y) >>> 24;
                assertTrue(alpha == 0 || alpha == 255, "the glow is half see-through at " + x + "," + y);
                any |= alpha == 255;
            }
        }
        assertTrue(any, "the glow draws something");
    }

    /** Filtered smooth, not nearest: at this size the texels must never show as a grid. (Its frost on the blocks is the opposite.) */
    @Test
    void theLanceTexturesAreFilteredSmooth() throws IOException {
        for (String name : new String[] {"glacial_lance", "glacial_lance_glow"}) {
            JsonObject meta = json("src/main/resources/assets/kegare/textures/entity/" + name + ".png.mcmeta");
            assertTrue(meta.getAsJsonObject("texture").get("blur").getAsBoolean(), name);
        }
    }

    /** The trail's line is the same all along it (its taper and fade are the effect's), solid down the middle. */
    @Test
    void theTrailLineIsEvenAlongItself() throws IOException {
        BufferedImage line = image("fx/glacial_lance_trail.png");
        int middle = line.getHeight() / 2;
        assertEquals(255, line.getRGB(0, middle) >>> 24, "solid core");
        assertEquals(0, line.getRGB(0, 0) >>> 24 & 0xF0, "faded out at the side");
        for (int y = 0; y < line.getHeight(); y++) {
            for (int x = 1; x < line.getWidth(); x++) assertEquals(line.getRGB(0, y), line.getRGB(x, y), "differs along the line at " + x + "," + y);
        }
    }

    // ------------------------------------------------------------------------------------------ growing in the hand

    @Test
    void chunksArriveInOrderFromTheStubToTheFullLance() {
        assertEquals(1, LanceShape.count(0f, MINIMUM), "a stub from the start");
        assertEquals(1, LanceShape.count(MINIMUM - 0.01f, MINIMUM), "short of the minimum: only the stub, which crumbles");
        assertEquals(2, LanceShape.count(MINIMUM, MINIMUM), "at the minimum, a javelin's first chunk");
        assertEquals(LanceShape.PIECES.size(), LanceShape.count(1f, MINIMUM), "full charge, every chunk");
        float last = -1f;
        for (int i = 0; i < LanceShape.PIECES.size(); i++) {
            float at = LanceShape.arrival(i, MINIMUM);
            assertTrue(at > last, LanceShape.PIECES.get(i) + " arrives after the one before");
            last = at;
        }
        int seen = 0;
        for (float charge = 0f; charge <= 1f; charge += 0.01f) {
            int now = LanceShape.count(charge, MINIMUM);
            assertTrue(now >= seen, "never shrinks while charging");
            seen = now;
        }
    }

    /** A chunk snaps in: out of the fist a little past its size, then back, and nothing before it arrives. */
    @Test
    void aChunkSnapsIntoPlace() {
        assertEquals(0.5f, LanceShape.snap(0f), 1e-5f);
        assertTrue(LanceShape.snap(0.5f) > 1f, "overshoots");
        assertEquals(1f, LanceShape.snap(1f), 1e-5f);
        assertEquals(1f, LanceShape.snap(5f), 1e-5f);
        float full = 40f;
        float arrives = LanceShape.arrival(3, MINIMUM) * full;
        assertEquals(0f, LanceShape.held(3, arrives - 0.5f, full, MINIMUM), "not yet");
        assertTrue(LanceShape.held(3, arrives + 0.1f, full, MINIMUM) > 0f, "there the moment it arrives");
        assertEquals(1f, LanceShape.held(3, arrives + LanceShape.SNAP_TICKS + 1f, full, MINIMUM), 1e-5f, "settled");
    }

    /** The game holds the lance by the same numbers the animation preview does (tools/anims/glacial_lance.py's LANCE_GRIP). */
    @Test
    void theGameHoldsItWhereThePreviewDoes() throws IOException {
        String source = Files.readString(project("tools/anims/glacial_lance.py"));
        Matcher m = Pattern.compile("LANCE_GRIP = \\{\"position\": \\(([^)]*)\\), \"rotation\": \\(([^)]*)\\), \"scale\": ([0-9.]+)\\}").matcher(source);
        assertTrue(m.find(), "LANCE_GRIP in glacial_lance.py");
        assertVector(LanceHold.GRIP_POSITION, m.group(1), "position");
        assertVector(LanceHold.GRIP_ROTATION, m.group(2), "rotation");
        assertEquals(Float.parseFloat(m.group(3)), LanceHold.GRIP_SCALE, 1e-4f, "scale");
    }

    private static void assertVector(Vector3f actual, String python, String what) {
        String[] parts = python.split(",");
        assertEquals(Float.parseFloat(parts[0].trim()), actual.x, 1e-4f, what + " x");
        assertEquals(Float.parseFloat(parts[1].trim()), actual.y, 1e-4f, what + " y");
        assertEquals(Float.parseFloat(parts[2].trim()), actual.z, 1e-4f, what + " z");
    }

    /** Your own small lance sits low and to the right, pointing away from you: the middle of the screen stays clear. */
    @Test
    void yourOwnLanceStaysOutOfTheMiddle() {
        assertTrue(LanceHold.SELF_GRIP.x > 0.5f && LanceHold.SELF_GRIP.y < -0.4f, "the bottom right");
        assertTrue(LanceHold.SELF_POINTING.z < 0f, "pointing away from the camera");
        assertTrue(LanceHold.SELF_SCALE < 0.5f, "small");
    }

    // ------------------------------------------------------------------------------------------ what it leaves

    /**
     * Its frost spreads out from the middle in a few ticks, holds whole for most of its life, then melts from the edge in, and is all
     * gone when its life is up.
     */
    @Test
    void itsFrostSpreadsHoldsAndMelts() {
        float charge = 0.5f;
        int life = LanceAftermath.markLife(charge);
        BlockMarks.Timing t = LanceAftermath.frostTiming(charge, 1f);
        int reached = Math.round(t.reach() * 255);
        assertEquals(0, t.appear(0), "the middle is there at once");
        assertTrue(t.appear(reached) > t.appear(0) && t.appear(reached) <= LanceAftermath.MARK_GROW_TICKS, "its edge within a few ticks");
        assertEquals(-1, t.appear(Math.min(255, reached + 8)), "no further than the charge reaches");
        assertTrue(t.vanish(reached, 0) < t.vanish(0, 0), "the edge melts before the middle");
        assertTrue(t.vanish(reached, -2) > life / 2, "whole for most of its life");
        assertTrue(t.vanish(0, 2) <= life + 2, "gone when its life is up");
        assertTrue(t.end() <= life + 2);
    }

    /** A full charge leaves more of everything: frost that reaches further and lasts longer, a bigger burst. */
    @Test
    void aFullChargeLeavesMore() {
        assertTrue(LanceAftermath.frostTiming(1f, 1f).reach() > LanceAftermath.frostTiming(0f, 1f).reach());
        assertEquals(1f, LanceAftermath.frostTiming(1f, 1f).reach(), 1e-6f, "a full charge shows all of it");
        assertTrue(LanceAftermath.frostTiming(1f, 0.7f).reach() < 1f, "the patch below a wall hit is smaller");
        assertTrue(LanceAftermath.markRadius(1f) > LanceAftermath.markRadius(0f));
        assertTrue(LanceAftermath.markLife(1f) > LanceAftermath.markLife(0f));
        assertTrue(LanceAftermath.markLife(0f) >= 60, "a few seconds at least");
        assertTrue(LanceAftermath.size(1f) > LanceAftermath.size(0f));
        assertTrue(LanceAftermath.STUCK_TICKS >= 15 && LanceAftermath.STUCK_TICKS <= 30, "stuck about a second");
    }

    // ------------------------------------------------------------------------------------------ the body's animations

    @Test
    void palLoadsTheChargeAndTheThrow() throws IOException {
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json("src/main/resources/assets/kegare/player_animations/glacial_lance.json"));
        Animation charge = loaded.get(RimeIds.ANIM_GLACIAL_LANCE_CHARGE.getPath());
        Animation release = loaded.get(RimeIds.ANIM_GLACIAL_LANCE_RELEASE.getPath());
        assertNotNull(charge, "the charge");
        assertNotNull(release, "the throw");
        assertNotEquals(Animation.LoopType.PLAY_ONCE, charge.loopType(), "the charge holds the throw drawn until it's let go");
        assertEquals(Animation.LoopType.PLAY_ONCE, release.loopType());
        assertTrue(charge.boneAnimations().size() >= 3 && release.boneAnimations().size() >= 3, "the whole body throws");
    }

    /** They moved out of rime.json: one source for each, or which one plays depends on load order. */
    @Test
    void rimeJsonNoLongerCarriesThem() throws IOException {
        JsonObject animations = json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations");
        assertFalse(animations.has(RimeIds.ANIM_GLACIAL_LANCE_CHARGE.getPath()));
        assertFalse(animations.has(RimeIds.ANIM_GLACIAL_LANCE_RELEASE.getPath()));
    }

    @Test
    void theBlockbenchSourceIsInTheRepo() throws IOException {
        JsonObject model = json("art/blockbench/glacial_lance.bbmodel");
        assertNotNull(model.get("meta"), "a Blockbench project");
        var elements = model.getAsJsonArray("elements");
        assertEquals(LanceShape.PIECES.size() + LanceShape.SHARDS, elements.size(), "a mesh for every chunk and shard");
        for (var e : elements) assertEquals("mesh", e.getAsJsonObject().get("type").getAsString(), "real meshes, not cubes");
    }
}
