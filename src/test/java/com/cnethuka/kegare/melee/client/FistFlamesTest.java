package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The ready fist's fire: the sheet it's drawn from, and the arithmetic of its frames and curls. */
class FistFlamesTest {
    private static final int FRAME = 14;
    private static final int WISP = 7;
    private static final Path TEXTURES = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/textures/entity");

    private static BufferedImage sheet() throws IOException {
        return ImageIO.read(TEXTURES.resolve("fist_flame.png").toFile());
    }

    @Test
    void theSheetIsGreyscaleWithHardEdges() throws IOException {
        BufferedImage sheet = sheet();
        assertEquals(FistFlames.FLAME_FRAMES * FRAME, sheet.getWidth());
        assertEquals(3 * FRAME + WISP, sheet.getHeight());
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int argb = sheet.getRGB(x, y);
                int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                assertTrue(a == 0 || a == 255, "no soft alpha at " + x + "," + y);
                if (a == 255) {
                    assertEquals(r, g, "grey at " + x + "," + y);
                    assertEquals(g, b, "grey at " + x + "," + y);
                }
            }
        }
    }

    private static String frameOf(BufferedImage sheet, int x0, int y0, int size) {
        StringBuilder out = new StringBuilder();
        for (int y = y0; y < y0 + size; y++) {
            for (int x = x0; x < x0 + size; x++) out.append(Integer.toHexString(sheet.getRGB(x, y))).append(',');
        }
        return out.toString();
    }

    private static boolean blank(BufferedImage sheet, int x0, int y0, int size) {
        for (int y = y0; y < y0 + size; y++) {
            for (int x = x0; x < x0 + size; x++) if (sheet.getRGB(x, y) >>> 24 != 0) return false;
        }
        return true;
    }

    @Test
    void everyFrameHasFireAndEachDiffersFromTheNext() throws IOException {
        BufferedImage sheet = sheet();
        for (int row = 0; row < 3; row++) {
            for (int f = 0; f < FistFlames.FLAME_FRAMES; f++) {
                assertFalse(blank(sheet, f * FRAME, row * FRAME, FRAME), "row " + row + " frame " + f);
                int next = (f + 1) % FistFlames.FLAME_FRAMES;
                assertNotEquals(frameOf(sheet, f * FRAME, row * FRAME, FRAME), frameOf(sheet, next * FRAME, row * FRAME, FRAME),
                        "it has to flicker: row " + row + " frame " + f);
            }
        }
        for (int f = 0; f < FistFlames.WISP_FRAMES; f++) assertFalse(blank(sheet, f * WISP, 3 * FRAME, WISP), "curl " + f);
    }

    @Test
    void theFramesLoopThroughAllOfThemAtTenASecond() {
        Set<Integer> seen = new HashSet<>();
        for (int tick = 0; tick < 2 * FistFlames.FLAME_FRAMES; tick++) {
            int frame = FistFlames.frame(tick + 0.5f, 3);
            assertTrue(frame >= 0 && frame < FistFlames.FLAME_FRAMES);
            seen.add(frame);
        }
        assertEquals(FistFlames.FLAME_FRAMES, seen.size());
        assertEquals(FistFlames.frame(0f, 0), FistFlames.frame(1.9f, 0), "each frame holds for two ticks");
        assertNotEquals(FistFlames.frame(0f, 0), FistFlames.frame(2f, 0));
        assertEquals(FistFlames.frame(0f, 0), FistFlames.frame(2f * FistFlames.FLAME_FRAMES, 0), "and it comes round");
        assertNotEquals(FistFlames.frame(0f, 0), FistFlames.frame(0f, 3), "layers don't flicker in step");
    }

    @Test
    void aCurlGoesUpAndStaysCloseToTheFist() {
        for (float seed : new float[] {0f, 0.3f, 0.7f, 0.99f}) {
            float last = -1f;
            for (float age = 0f; age < 1f; age += 0.1f) {
                Vector3f at = FistFlames.wispPath(age, seed, new Vector3f());
                assertTrue(at.y > last, "always rising");
                last = at.y;
                assertTrue(Math.hypot(at.x, at.z) <= 0.21, "it curls, it doesn't fly off");
            }
            assertTrue(last > 0.4f && last < 0.8f, "it ends a little above the fist: " + last);
        }
    }

    @Test
    void fireGoesUpTheScreenWhicheverWayTheArmPoints() {
        Vector3f side = new Vector3f(0f, 0f, 1f);
        // Arm-space: the arm runs down y, toward the hand. Seen from the side, with world up against it, it hangs; with world up along it, it's raised.
        assertFalse(FistFlames.pointsUp(side, new Vector3f(0f, -1f, 0f)), "hanging");
        assertTrue(FistFlames.pointsUp(side, new Vector3f(0f, 1f, 0f)), "held up");
        Vector3f nearlyAlong = new Vector3f(0.1f, -0.99f, 0f).normalize();
        assertTrue(FistFlames.pointsUp(nearlyAlong, new Vector3f(0.99f, 0.1f, 0f).normalize()), "pointing away with the hand high on the screen");
        assertFalse(FistFlames.pointsUp(new Vector3f(0f, 1f, 0f), new Vector3f(1f, 0f, 0f)), "dead along the arm there's no up to go by");
    }

    @Test
    void theHeartIsPalerThanTheFireButTheSameHue() {
        int rime = 0x376BFF;
        int pale = FistFlames.mix(rime, 0xFFFFFF, 0.55f);
        assertTrue((pale >> 16 & 0xFF) > (rime >> 16 & 0xFF));
        assertTrue((pale & 0xFF) >= (pale >> 16 & 0xFF), "still blue");
    }

    @Test
    void theShellIsGone() {
        assertFalse(Files.exists(TEXTURES.resolve("fist_shell.png")), "the smooth shell's texture");
        assertTrue(Files.exists(TEXTURES.resolve("fist_strain.png")), "a Severed fist keeps its strain");
    }
}
