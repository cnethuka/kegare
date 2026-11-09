package com.cnethuka.kegare.sixeyes.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How Reads put numbers into words, and where the focus's panel goes: never over the crosshair (DESIGN §3.32). */
class ReadsTextTest {
    @Test
    void numbersReadAsAPlayerWouldSayThem() {
        assertEquals("14", ReadsText.amount(14f));
        assertEquals("13.5", ReadsText.amount(13.5f));
        assertEquals("0.3", ReadsText.amount(0.25f));
        assertEquals("0:30", ReadsText.duration(600));
        assertEquals("0:01", ReadsText.duration(1), "a tick left is still a second");
        assertEquals("12:05", ReadsText.duration(20 * 725));
        assertEquals("∞", ReadsText.duration(-1), "one that never runs out");
        assertEquals("I", ReadsText.level(0));
        assertEquals("II", ReadsText.level(1));
        assertEquals("11", ReadsText.level(10));
        assertEquals("130%", ReadsText.share(0.13f, 0.1f));
        assertEquals("1.5", ReadsText.seconds(30));
    }

    @Test
    void manyEffectsAreShortened() {
        assertFalse(ReadsText.shortened(3), "three get a line each");
        assertTrue(ReadsText.shortened(4), "four are shortened");
        assertEquals(3, ReadsText.effectsShown(3));
        assertEquals(9, ReadsText.effectsShown(14), "three rows of three, then +5 more");
    }

    @Test
    void thePanelNeverCoversTheCrosshairAndStaysOnScreen() {
        int[][] screens = {{427, 240}, {320, 240}, {640, 360}, {960, 540}};
        int[][] panels = {{90, 60}, {150, 110}, {180, 96}, {200, 160}, {300, 200}};
        int clear = 22;
        for (int[] screen : screens) {
            for (int[] panel : panels) {
                for (int tx = -50; tx <= screen[0] + 50; tx += 13) {
                    for (int ty = -50; ty <= screen[1] + 50; ty += 11) {
                        int[] at = ReadsText.placePanel(tx, ty, panel[0], panel[1], screen[0], screen[1], clear, 10);
                        boolean fitsBeside = panel[0] <= screen[0] / 2 - clear - 4;
                        if (at == null) {
                            assertFalse(fitsBeside, "a panel that fits beside the crosshair always gets a place: " + panel[0] + "x" + panel[1] + " on " + screen[0] + "x" + screen[1]);
                            continue; // the client draws it smaller
                        }
                        String where = "screen " + screen[0] + "x" + screen[1] + ", panel " + panel[0] + "x" + panel[1] + ", tag at " + tx + "," + ty;
                        assertFalse(ReadsText.overlaps(at[0], at[1], panel[0], panel[1], screen[0] / 2f, screen[1] / 2f, clear), "over the crosshair: " + where);
                        assertTrue(at[0] >= 0 && at[0] + panel[0] <= screen[0], "off the side: " + where);
                        assertTrue(at[1] >= 0 && at[1] + panel[1] <= screen[1], "off the top or bottom: " + where);
                    }
                }
            }
        }
    }

    @Test
    void thePanelKeepsItsSideWhileItsBodyCrossesTheCrosshair() {
        int[] start = ReadsText.placePanel(225, 120, 100, 60, 427, 240, 22, 10, null);
        assertEquals(1, start[2], "right of the crosshair: on the right");
        int[] crossed = ReadsText.placePanel(205, 120, 100, 60, 427, 240, 22, 10, true);
        assertEquals(1, crossed[2], "just past it, it stays on the right");
        assertFalse(ReadsText.overlaps(crossed[0], crossed[1], 100, 60, 213.5f, 120f, 22));
        int[] far = ReadsText.placePanel(150, 120, 100, 60, 427, 240, 22, 10, true);
        assertEquals(0, far[2], "well past it, it moves over");
    }

    @Test
    void thePanelSitsBesideItsBodyOnTheSideAwayFromTheCrosshair() {
        int[] right = ReadsText.placePanel(250, 120, 100, 60, 427, 240, 22, 10);
        assertTrue(right[0] >= 260, "a body right of the crosshair: the panel to its right");
        int[] left = ReadsText.placePanel(170, 120, 100, 60, 427, 240, 22, 10);
        assertTrue(left[0] + 100 <= 160, "left of it: to its left");
        assertEquals(90, left[1], "level with it");
    }
}
