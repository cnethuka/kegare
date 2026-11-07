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
    private static final int NEAR_BLACK = 40;
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
    void aCurlComesOffTheFireAndStaysCloseToIt() {
        for (float seed : new float[] {0f, 0.3f, 0.7f, 0.99f}) {
            assertTrue(FistFlames.wispPath(0f, seed, new Vector3f()).y > 0.1f, "it starts partway up the fire, not at the knuckles");
            float last = -1f;
            for (float age = 0f; age < 1f; age += 0.1f) {
                Vector3f at = FistFlames.wispPath(age, seed, new Vector3f());
                assertTrue(at.y > last, "always rising");
                last = at.y;
                assertTrue(Math.hypot(at.x, at.z) <= 0.13, "it curls, it doesn't fly off");
            }
            assertTrue(last < 0.6f, "it ends about where the tongues do, well short of the shoulder: " + last);
        }
    }

    private static int fireWidth(BufferedImage sheet, int frame, int row, int y) {
        int width = 0;
        for (int x = 0; x < FRAME; x++) if (sheet.getRGB(frame * FRAME + x, row * FRAME + y) >>> 24 != 0) width++;
        return width;
    }

    @Test
    void nothingHangsBelowTheHand() throws IOException {
        BufferedImage sheet = sheet();
        for (int f = 0; f < FistFlames.FLAME_FRAMES; f++) {
            int widest = 0;
            for (int y = 0; y < FRAME; y++) widest = Math.max(widest, fireWidth(sheet, f, 0, y));
            assertTrue(fireWidth(sheet, f, 0, FRAME - 1) <= 6, "the base is a narrow cup, frame " + f);
            assertTrue(fireWidth(sheet, f, 0, FRAME - 1) < widest, "and the fire's wider further up, frame " + f);
            int sum = 0, count = 0;
            for (int y = 0; y < FRAME; y++) {
                for (int x = 0; x < FRAME; x++) {
                    if (sheet.getRGB(f * FRAME + x, y) >>> 24 != 0) {
                        sum += FRAME - 1 - y;
                        count++;
                    }
                }
            }
            assertTrue(sum / (float) count >= 2.5f, "most of it is up round the fist and forearm, frame " + f);
        }
    }

    private static boolean nearBlack(BufferedImage sheet, int x, int y) {
        int argb = sheet.getRGB(x, y);
        return argb >>> 24 != 0 && (argb & 0xFF) < NEAR_BLACK;
    }

    @Test
    void nearBlackMixesIntoTheOuterEdgeOfTheMassOnly() throws IOException {
        BufferedImage sheet = sheet();
        for (int f = 0; f < FistFlames.FLAME_FRAMES; f++) {
            int dark = 0;
            for (int y = 0; y < FRAME; y++) {
                int first = -1, last = -1;
                for (int x = 0; x < FRAME; x++) {
                    if (sheet.getRGB(f * FRAME + x, y) >>> 24 != 0) {
                        if (first < 0) first = x;
                        last = x;
                    }
                }
                for (int x = 0; x < FRAME; x++) {
                    if (!nearBlack(sheet, f * FRAME + x, y)) continue;
                    dark++;
                    boolean outer = x == first || x == last || sheet.getRGB(f * FRAME + x, y + 1) >>> 24 == 0;
                    assertTrue(outer, "only on the outer edge: frame " + f + " at " + x + "," + y);
                }
            }
            assertTrue(dark >= 4, "enough to hold the shape against a bright sky, frame " + f + ": " + dark);
            for (int y = FRAME; y < 3 * FRAME; y++) {
                for (int x = f * FRAME; x < (f + 1) * FRAME; x++) assertFalse(nearBlack(sheet, x, y), "the fire in front stays clean: " + x + "," + y);
            }
        }
    }

    @Test
    void noNearBlackAlongTheBottomOfTheMass() throws IOException {
        BufferedImage sheet = sheet();
        for (int f = 0; f < FistFlames.FLAME_FRAMES; f++) {
            for (int x = 0; x < FRAME; x++) {
                assertFalse(nearBlack(sheet, f * FRAME + x, FRAME - 1), "that's what showed as a dark bar under the fist: frame " + f);
            }
        }
    }

    @Test
    void theTipsOfTheFireStayInColor() throws IOException {
        BufferedImage sheet = sheet();
        for (int f = 0; f < FistFlames.FLAME_FRAMES; f++) {
            int top = 0;
            while (fireWidth(sheet, f, 0, top) == 0) top++;
            for (int x = 0; x < FRAME; x++) {
                assertFalse(nearBlack(sheet, f * FRAME + x, top), "a lit tip, not an outlined one, frame " + f);
            }
        }
    }

    @Test
    void tipsLeanOverToStandUprightOnATiltedArmWithinReason() {
        assertEquals(0f, FistFlames.lean(0f, -1f, 1f), 1.0e-5f, "an arm straight down needs no lean");
        assertTrue(FistFlames.lean(0.3f, -0.95f, 1f) > 0f, "toward up");
        assertTrue(FistFlames.lean(-0.3f, -0.95f, 1f) < 0f, "whichever side that is");
        assertEquals(FistFlames.lean(1f, 0f, 1f), FistFlames.lean(1f, 0.01f, 1f), 1.0e-5f, "a level arm doesn't flip it about");
        assertTrue(FistFlames.lean(1f, 0f, 1f) <= 0.7f, "and it never lays the fire flat");
    }

    @Test
    void onABodyTheFireStopsPartwayUpTheArm() {
        float arm = 12f / 16f;
        assertTrue(FistFlames.Fit.BODY.height() * FRAME / 16f < arm, "shorter than the arm it's on, or it's up by the shoulder");
        assertTrue(FistFlames.Fit.BODY.leans(), "and on a tilted arm it still goes up");
    }

    @Test
    void inFirstPersonAHeldItemGetsMoreFireThanAnEmptyHandAndNeitherLeans() {
        assertTrue(FistFlames.Fit.GRIP.width() > FistFlames.Fit.FIRST_PERSON.width(), "the item hides part of it");
        assertTrue(FistFlames.Fit.GRIP.height() > FistFlames.Fit.FIRST_PERSON.height());
        assertTrue(FistFlames.Fit.GRIP.width() < 1f && FistFlames.Fit.GRIP.height() < 1f, "still no big blob by the camera");
        assertEquals(0f, FistFlames.Fit.GRIP.flare(), 0f);
        assertEquals(0f, FistFlames.Fit.FIRST_PERSON.flare(), 0f);
        assertFalse(FistFlames.Fit.GRIP.leans());
        assertFalse(FistFlames.Fit.FIRST_PERSON.leans());
    }

    @Test
    void aCurlIsShortLived() {
        assertTrue(FistFlames.WISP_LIFE_TICKS <= 10, "half a second, then it's gone: " + FistFlames.WISP_LIFE_TICKS);
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
        int pale = FistFlames.mix(rime, 0xFFFFFF, 0.38f);
        assertTrue((pale >> 16 & 0xFF) > (rime >> 16 & 0xFF));
        assertTrue((pale & 0xFF) >= (pale >> 16 & 0xFF), "still blue");
    }

    @Test
    void theShellIsGone() {
        assertFalse(Files.exists(TEXTURES.resolve("fist_shell.png")), "the smooth shell's texture");
        assertTrue(Files.exists(TEXTURES.resolve("fist_strain.png")), "a Severed fist keeps its strain");
    }
}
