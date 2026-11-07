package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The fire on a body and round a held item: the sheet it's drawn from and how its layers sit on the arm. */
class FistFireTest {
    private static final int FRAMES = FistFlames.FLAME_FRAMES;
    /** Grey below this stays near-black whatever it's multiplied by. */
    private static final int NEAR_BLACK = 40;
    private static final Path SHEET = Path.of(System.getProperty("kegare.projectDir", "."), "src/main/resources/assets/kegare/textures/entity/fist_fire.png");
    private static final List<FistFire.Layer> ARM = List.of(FistFire.OUTER, FistFire.INNER);

    private static BufferedImage sheet() throws IOException {
        return ImageIO.read(SHEET.toFile());
    }

    private static boolean solid(BufferedImage sheet, int x, int y) {
        return sheet.getRGB(x, y) >>> 24 != 0;
    }

    private static boolean nearBlack(BufferedImage sheet, int x, int y) {
        return solid(sheet, x, y) && (sheet.getRGB(x, y) & 0xFF) < NEAR_BLACK;
    }

    /** How much of a row burns, over the loop, counting from the fist. */
    private static float cover(BufferedImage sheet, FistFire.Layer layer, int fromFist) {
        int burning = 0, around = 4 * layer.face();
        int y = layer.sheetY() + layer.rows() - 1 - fromFist;
        for (int f = 0; f < FRAMES; f++) {
            for (int x = 0; x < around; x++) if (solid(sheet, f * layer.frameWidth() + x, y)) burning++;
        }
        return burning / (float) (FRAMES * around);
    }

    private static float darkShare(BufferedImage sheet, int x0, int y0, int width, int height) {
        int burning = 0, dark = 0;
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                if (solid(sheet, x, y)) burning++;
                if (nearBlack(sheet, x, y)) dark++;
            }
        }
        return burning == 0 ? 0f : dark / (float) burning;
    }

    @Test
    void theSheetIsGreyscaleAndEveryTexelIsSolidOrAHole() throws IOException {
        BufferedImage sheet = sheet();
        assertEquals(FistFire.SHEET_WIDTH, sheet.getWidth());
        assertEquals(FistFire.SHEET_HEIGHT, sheet.getHeight());
        boolean holes = false;
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int argb = sheet.getRGB(x, y);
                int a = argb >>> 24;
                assertTrue(a == 0 || a == 255, "no soft alpha, which is what made the old shell look like glass: " + x + "," + y);
                if (a == 255) {
                    assertEquals(argb >> 16 & 0xFF, argb >> 8 & 0xFF, "grey at " + x + "," + y);
                    assertEquals(argb >> 8 & 0xFF, argb & 0xFF, "grey at " + x + "," + y);
                } else {
                    holes = true;
                }
            }
        }
        assertTrue(holes);
    }

    @Test
    void theLayersFitTheSheetAndTheArm() {
        for (FistFire.Layer layer : List.of(FistFire.OUTER, FistFire.INNER, FistFire.HEART)) {
            assertTrue(FRAMES * layer.frameWidth() <= FistFire.SHEET_WIDTH, "frames fit across");
            assertTrue(layer.sheetY() + Math.max(layer.rows(), layer.face()) <= FistFire.SHEET_HEIGHT, "the row fits");
            assertTrue(layer.risingY() + layer.rows() <= FistFire.SHEET_HEIGHT, "the rising row fits");
            float pixels = (4f + 2f * layer.inflate() * 16f) / layer.face();
            assertTrue(pixels > 0.9f && pixels < 1.15f, "a texel is about a pixel of skin on every layer: " + pixels);
            assertTrue(layer.inflate() * 16f > 0.25f, "out past the skin's sleeve");
        }
        assertTrue(FistFire.OUTER.inflate() > FistFire.INNER.inflate(), "the outer layer goes round the inner one");
        assertTrue(FistFire.OUTER.inflate() * 16f >= 1f, "by enough for its tips to break the arm's outline");
        assertTrue(FistFire.OUTER.inflate() * 16f <= 2f, "and it's still only slightly bigger than the arm");
    }

    @Test
    void everyFrameHasHolesInItAndFlickers() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            for (int f = 0; f < FRAMES; f++) {
                int holes = 0, burning = 0, around = 4 * layer.face();
                StringBuilder now = new StringBuilder(), next = new StringBuilder();
                for (int y = layer.sheetY(); y < layer.sheetY() + layer.rows(); y++) {
                    for (int x = 0; x < around; x++) {
                        if (solid(sheet, f * layer.frameWidth() + x, y)) burning++;
                        else holes++;
                        now.append(Integer.toHexString(sheet.getRGB(f * layer.frameWidth() + x, y))).append(',');
                        next.append(Integer.toHexString(sheet.getRGB((f + 1) % FRAMES * layer.frameWidth() + x, y))).append(',');
                    }
                }
                assertTrue(burning > 20, "frame " + f);
                assertTrue(holes > burning, "more holes than fire, frame " + f);
                assertNotEquals(now.toString(), next.toString(), "it has to flicker: frame " + f);
            }
        }
    }

    @Test
    void itsStrongestRoundTheFistAndThinsUpTheForearm() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            float fist = (cover(sheet, layer, 1) + cover(sheet, layer, 2)) / 2f;
            float forearm = (cover(sheet, layer, 3) + cover(sheet, layer, 4)) / 2f;
            float elbow = cover(sheet, layer, layer.rows() - 1);
            assertTrue(fist > forearm, "fist " + fist + ", forearm " + forearm);
            assertTrue(forearm > elbow, "forearm " + forearm + ", elbow " + elbow);
            assertTrue(elbow < 0.2f, "only scraps by the elbow: " + elbow);
        }
    }

    @Test
    void theHandCanStillBeMadeOutThroughIt() throws IOException {
        BufferedImage sheet = sheet();
        for (int row = 0; row < 3; row++) {
            float outer = cover(sheet, FistFire.OUTER, row), inner = cover(sheet, FistFire.INNER, row);
            float together = 1f - (1f - outer) * (1f - inner);
            assertTrue(together > 0.4f, "there's fire over the fist itself, row " + row + ": " + together);
            assertTrue(together < 0.9f, "and gaps to see the hand through, row " + row + ": " + together);
        }
    }

    @Test
    void itBreaksIntoDarkScrapsNearTheElbow() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            int top = layer.sheetY();
            int dark = 0, burning = 0;
            for (int f = 0; f < FRAMES; f++) {
                for (int x = 0; x < 4 * layer.face(); x++) {
                    int at = f * layer.frameWidth() + x;
                    if (solid(sheet, at, top)) burning++;
                    if (nearBlack(sheet, at, top)) dark++;
                }
            }
            assertTrue(burning > 0, "there are scraps");
            assertEquals(burning, dark, "and they're dark");
        }
    }

    @Test
    void aboutAThirdOfItIsNearBlack() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            int burning = 0, dark = 0;
            for (int f = 0; f < FRAMES; f++) {
                for (int y = layer.sheetY(); y < layer.sheetY() + layer.rows(); y++) {
                    for (int x = 0; x < 4 * layer.face(); x++) {
                        int at = f * layer.frameWidth() + x;
                        if (solid(sheet, at, y)) burning++;
                        if (nearBlack(sheet, at, y)) dark++;
                    }
                }
            }
            float share = dark / (float) burning;
            assertTrue(share > 0.25f && share < 0.42f, "near-black share: " + share);
        }
    }

    @Test
    void nearBlackStaysNearBlackAfterTinting() throws IOException {
        BufferedImage sheet = sheet();
        int[] colors = {0x376BFF, 0x1BFFE6, 0xC5B6FF};
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                if (!nearBlack(sheet, x, y)) continue;
                int grey = sheet.getRGB(x, y) & 0xFF;
                for (int rgb : colors) {
                    assertTrue(Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF)) * grey / 255 < NEAR_BLACK, "at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void nothingRunsDarkAlongTheBottom() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            int bottom = layer.sheetY() + layer.rows() - 1;
            for (int f = 0; f < FRAMES; f++) {
                int run = 0, longest = 0;
                for (int x = 0; x < 4 * layer.face(); x++) {
                    run = nearBlack(sheet, f * layer.frameWidth() + x, bottom) ? run + 1 : 0;
                    longest = Math.max(longest, run);
                }
                assertTrue(longest <= 3, "a long dark run reads as a bar under the fist, frame " + f + ": " + longest);
            }
        }
    }

    /** How many texels of a frame of a layer's raised fire are past the knuckles. */
    private static int pastTheKnuckles(BufferedImage sheet, FistFire.Layer layer, int frame) {
        int past = 0;
        int rowsPast = Math.round(layer.past() * 16f);
        for (int y = layer.risingY() + layer.rows() - rowsPast; y < layer.risingY() + layer.rows(); y++) {
            for (int x = 0; x < 4 * layer.face(); x++) if (solid(sheet, frame * layer.frameWidth() + x, y)) past++;
        }
        return past;
    }

    @Test
    void onARaisedArmTheFireReachesPastTheKnuckles() throws IOException {
        BufferedImage sheet = sheet();
        for (FistFire.Layer layer : ARM) {
            assertTrue(layer.past() > 0f);
            assertNotEquals(layer.sheetY(), layer.risingY());
        }
        for (int f = 0; f < FRAMES; f++) {
            int together = 0;
            for (FistFire.Layer layer : ARM) together += pastTheKnuckles(sheet, layer, (f + layer.offset()) % FRAMES);
            assertTrue(together >= 2, "some tip is over the knuckles at every moment, as the layers are drawn: " + f + ", " + together);
        }
    }

    @Test
    void theClumpRoundAGripHasGapsAndARoundBottom() throws IOException {
        BufferedImage sheet = sheet();
        for (int sheetY : new int[] {52, 68}) {
            for (int f = 0; f < FRAMES; f++) {
                int x0 = f * FistFire.GRIP_WIDTH;
                int burning = 0, widest = 0, lowest = 0;
                for (int y = sheetY; y < sheetY + FistFire.GRIP_ROWS; y++) {
                    int width = 0;
                    for (int x = x0; x < x0 + FistFire.GRIP_WIDTH; x++) if (solid(sheet, x, y)) width++;
                    burning += width;
                    widest = Math.max(widest, width);
                    if (y == sheetY + FistFire.GRIP_ROWS - 1) lowest = width;
                }
                assertTrue(burning > 20, "frame " + f);
                assertTrue(burning < 0.5f * FistFire.GRIP_WIDTH * FistFire.GRIP_ROWS, "mostly gaps, so it's never a blob: frame " + f + ", " + burning);
                assertTrue(lowest < widest, "it doesn't sit on a straight line: frame " + f);
                float share = darkShare(sheet, x0, sheetY, FistFire.GRIP_WIDTH, FistFire.GRIP_ROWS);
                assertFalse(share < 0.1f, "near-black runs through it too: frame " + f + ", " + share);
            }
        }
    }
}
