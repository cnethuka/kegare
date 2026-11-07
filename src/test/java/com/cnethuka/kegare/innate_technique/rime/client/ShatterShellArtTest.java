package com.cnethuka.kegare.innate_technique.rime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Shatter Shell's look: a ring that shows exactly how far the burst reaches (the smooth shock ring's front, the frost left on the
 * ground, the plates flung out to it), whatever the reach is set to, and the cast's pose. Visual only: nothing here touches what
 * the burst does.
 */
class ShatterShellArtTest {
    /** The thick rime of the frost ring itself (build_block_marks.py RIME). */
    private static final int RIME = 230 << 24 | 230 << 16 | 244 << 8 | 255;

    private static Path project(String path) {
        return Path.of(System.getProperty("kegare.projectDir", "."), path);
    }

    private static JsonObject json(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(project(path))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static BlockMarks.Pattern mark() throws IOException {
        BufferedImage art = ImageIO.read(project("src/main/resources/assets/kegare/textures/marks/shatter_ring.png").toFile());
        BufferedImage when = ImageIO.read(project("src/main/resources/assets/kegare/textures/marks/shatter_ring_order.png").toFile());
        int w = art.getWidth(), h = art.getHeight();
        int[] argb = new int[w * h], order = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = art.getRGB(x, y);
                argb[y * w + x] = c >>> 24 == 0 ? 0 : c;
                order[y * w + x] = when.getRaster().getSample(x, y, 0);
            }
        }
        return new BlockMarks.Pattern(w, h, argb, order);
    }

    /** The distances (texels) from the middle of every texel of the ring's rime. */
    private static List<Double> rime(BlockMarks.Pattern p) {
        List<Double> out = new ArrayList<>();
        double c = p.width() / 2.0;
        for (int y = 0; y < p.height(); y++) {
            for (int x = 0; x < p.width(); x++) {
                if (p.argb()[y * p.width() + x] == RIME) out.add(Math.hypot(x + 0.5 - c, y + 0.5 - c));
            }
        }
        Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------------------------------ the frost on the ground

    /** Drawn for the default reach: the rime ring at 4.5 blocks, unbroken all the way round. */
    @Test
    void theFrostRingIsAtTheReach() throws IOException {
        BlockMarks.Pattern p = mark();
        List<Double> d = rime(p);
        double median = d.get(d.size() / 2);
        assertEquals(ShatterClient.ART_RADIUS, median, 1.5, "the ring's rime sits at the reach");
        assertEquals(4.5, ShatterClient.ART_RADIUS / (double) BlockMarks.TEXELS, 1e-9, "the art is drawn for the default 4.5 blocks");
        boolean[] round = new boolean[72];
        double c = p.width() / 2.0;
        for (int y = 0; y < p.height(); y++) {
            for (int x = 0; x < p.width(); x++) {
                double r = Math.hypot(x + 0.5 - c, y + 0.5 - c);
                if (p.argb()[y * p.width() + x] == RIME && Math.abs(r - ShatterClient.ART_RADIUS) < 3) {
                    double a = Math.atan2(y + 0.5 - c, x + 0.5 - c) + Math.PI;
                    round[(int) (a / (2 * Math.PI) * 72) % 72] = true;
                }
            }
        }
        for (int i = 0; i < 72; i++) assertTrue(round[i], "the ring is broken at " + i * 5 + " degrees");
    }

    /** Another reach moves the ring out or in to it, its width kept: the frost always shows where the knockback stops. */
    @Test
    void theFrostRingFollowsTheConfiguredReach() throws IOException {
        BlockMarks.Pattern art = mark();
        assertSame(art, ShatterClient.ring(art, 4.5f), "the default reach is the art as drawn");
        double width = rime(art).get(rime(art).size() * 9 / 10) - rime(art).get(rime(art).size() / 10);
        for (float reach : new float[] {2.5f, 3f, 6f, 8f}) {
            BlockMarks.Pattern p = ShatterClient.ring(art, reach);
            List<Double> d = rime(p);
            assertEquals(reach * BlockMarks.TEXELS, d.get(d.size() / 2), 1.5, "the ring at " + reach + " blocks");
            double w = d.get(d.size() * 9 / 10) - d.get(d.size() / 10);
            assertEquals(width, w, 1.5, "the ring keeps its width at " + reach);
        }
    }

    // ------------------------------------------------------------------------------------------ the shock ring and the plates

    /** The shock's front races out of the feet and stops at the reach, never past it. */
    @Test
    void theShockStopsAtTheReach() {
        for (float reach : new float[] {2.5f, 4.5f, 8f}) {
            float last = 0f;
            for (float t = 0f; t <= ShatterClient.EXPAND + ShatterClient.FADE; t += 0.25f) {
                float r = ShatterClient.front(reach, t);
                assertTrue(r >= last, "it only goes out");
                assertTrue(r <= reach + 1e-4f, "never past the reach");
                last = r;
            }
            assertEquals(reach, ShatterClient.front(reach, ShatterClient.EXPAND), 1e-4f, "at the reach once it's out");
            assertTrue(ShatterClient.front(reach, ShatterClient.EXPAND * 0.4f) > reach * 0.7f, "most of the way in the first two ticks");
        }
        assertTrue(ShatterClient.EXPAND <= 6, "sudden: out in under a third of a second");
    }

    /** The plates are flung out flat to about the ring and come down to the ground there as they shrink away. */
    @Test
    void thePlatesFlyOutToTheRing() {
        float reach = 4.1f;
        float speed = ShellClient.burstSpeed(reach);
        for (float up : new float[] {0.04f, 0.1f}) {
            Vector3f end = ShellClient.travelled(new Vector3f(speed, up, 0f), ShellClient.BURST_GRAVITY, true, ShellClient.BURST_TICKS);
            assertTrue(end.x > (reach - 0.3f) * 0.9f && end.x <= reach - 0.3f + 1e-4f, "out to about the ring: " + end.x);
            assertTrue(end.y < -0.4f && end.y > -1.3f, "down from the chest to about the ground: " + end.y);
        }
    }

    @Test
    void theShockTextureIsSmoothWithABrightFront() throws IOException {
        BufferedImage ring = ImageIO.read(project("src/main/resources/assets/kegare/textures/fx/shatter_ring.png").toFile());
        int brightest = 0, at = -1;
        for (int x = 0; x < ring.getWidth(); x++) {
            int alpha = ring.getRGB(x, 0) >>> 24;
            if (alpha > brightest) {
                brightest = alpha;
                at = x;
            }
        }
        assertEquals(ShatterClient.FRONT, (at + 0.5f) / ring.getWidth(), 0.04f, "the front where ShatterClient draws it");
        assertTrue(brightest > 240, "a bright front");
        assertTrue(ring.getRGB(at, ring.getHeight() - 1) >>> 24 < 8, "the wall fades out at its top");
        assertTrue(json("src/main/resources/assets/kegare/textures/fx/shatter_ring.png.mcmeta").getAsJsonObject("texture")
                .get("blur").getAsBoolean(), "filtered smooth: it's part of the technique, not a mark on the blocks");
    }

    // ------------------------------------------------------------------------------------------ the body

    @Test
    void palLoadsTheCast() throws IOException {
        String path = "src/main/resources/assets/kegare/player_animations/shatter_shell.json";
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json(path));
        Animation cast = loaded.get(RimeIds.ANIM_SHATTER_SHELL_CAST.getPath());
        assertNotNull(cast);
        assertEquals(Animation.LoopType.PLAY_ONCE, cast.loopType());
        JsonObject bones = json(path).getAsJsonObject("animations").getAsJsonObject(RimeIds.ANIM_SHATTER_SHELL_CAST.getPath())
                .getAsJsonObject("bones");
        for (String bone : new String[] {"right_arm", "left_arm", "torso"}) assertTrue(bones.has(bone), "the cast keys the " + bone);
        assertFalse(json("src/main/resources/assets/kegare/player_animations/rime.json").getAsJsonObject("animations")
                .has(RimeIds.ANIM_SHATTER_SHELL_CAST.getPath()), "one source for it");
    }
}
