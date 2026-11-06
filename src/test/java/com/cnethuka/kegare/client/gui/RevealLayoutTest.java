package com.cnethuka.kegare.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.client.CombatPanelLayer;
import org.junit.jupiter.api.Test;

class RevealLayoutTest {
    private static final int SLOT = CombatPanelLayer.SLOT_SIZE;

    /** Window sizes in pixels, each at the GUI scales Minecraft allows for it. */
    private static final int[][] WINDOWS = {{640, 480}, {854, 480}, {1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160}};

    @Test
    void theCanvasIsNeverSmallerThanTheMinimum() {
        for (int[] window : WINDOWS) {
            for (int guiScale = 1; guiScale <= 8; guiScale++) {
                // Minecraft's own limit: it won't pick a GUI scale that leaves the GUI under 320x240.
                if (window[0] / guiScale < RevealLayout.MIN_W || window[1] / guiScale < RevealLayout.MIN_H) continue;
                int guiW = window[0] / guiScale, guiH = window[1] / guiScale;
                int k = RevealLayout.canvasScale(guiW, guiH);
                assertTrue(guiW / k >= RevealLayout.MIN_W && guiH / k >= RevealLayout.MIN_H, window[0] + "x" + window[1] + " at scale " + guiScale);
            }
        }
    }

    @Test
    void oneToNineIconsFitWithoutClippingOrOverlapping() {
        for (int canvasW = RevealLayout.MIN_W; canvasW <= 960; canvasW += 7) {
            int center = canvasW / 2;
            for (int count = 1; count <= 9; count++) {
                int step = RevealLayout.rowStep(count, SLOT, 3, canvasW - 24);
                int first = RevealLayout.rowStart(count, SLOT, step, center);
                int end = first + (count - 1) * step + SLOT;
                assertTrue(first >= 0 && end <= canvasW, count + " icons on " + canvasW + ": " + first + ".." + end);
                assertTrue(step >= SLOT, count + " icons overlap on " + canvasW);
            }
        }
    }

    @Test
    void aFullRowCentersOnTheCanvas() {
        int step = RevealLayout.rowStep(9, SLOT, 3, 296);
        int first = RevealLayout.rowStart(9, SLOT, step, 160);
        assertEquals(320 - (first + 8 * step + SLOT), first);
    }

    @Test
    void textIsNeverShrunkBelowWholePixels() {
        assertEquals(1, RevealLayout.textScale(500, 300, 4));
        assertEquals(3, RevealLayout.textScale(100, 300, 4));
        assertEquals(4, RevealLayout.textScale(10, 300, 4));
    }

    @Test
    void emblemsStayMultiplesOfTheirArt() {
        assertEquals(48, RevealLayout.emblemSize(20));
        assertEquals(48, RevealLayout.emblemSize(95));
        assertEquals(96, RevealLayout.emblemSize(96));
        assertEquals(144, RevealLayout.emblemSize(170));
        assertEquals(192, RevealLayout.emblemSize(900));
    }
}
