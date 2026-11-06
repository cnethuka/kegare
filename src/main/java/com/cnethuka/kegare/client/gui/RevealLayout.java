package com.cnethuka.kegare.client.gui;

/**
 * The reveal's sizing. Plain Java, so "fits at every GUI scale" is unit-testable. The reveal draws on a canvas at a whole-number multiple
 * of GUI units ({@link #canvasScale}), which keeps text and pixel art crisp and means the canvas is never under {@value #MIN_W}x{@value #MIN_H},
 * whatever the GUI scale or window: a layout that fits that fits everywhere.
 */
public final class RevealLayout {
    public static final int MIN_W = 320;
    public static final int MIN_H = 240;
    private static final int EMBLEM_ART = 48;
    private static final int EMBLEM_MAX = 192;

    private RevealLayout() {}

    /** GUI units per canvas unit, at least 1. */
    public static int canvasScale(int guiWidth, int guiHeight) {
        return Math.max(1, Math.min(guiWidth / MIN_W, guiHeight / MIN_H));
    }

    /** The largest whole-number scale at which text this wide fits (at least 1: text is never shrunk onto fractions of a pixel). */
    public static int textScale(int textWidth, int maxWidth, int maxScale) {
        for (int scale = maxScale; scale > 1; scale--) {
            if (textWidth * scale <= maxWidth) return scale;
        }
        return 1;
    }

    /** The distance between neighbouring slots in a centered row: normally slot + gap, closer if {@code count} slots wouldn't fit otherwise. */
    public static int rowStep(int count, int slot, int gap, int maxWidth) {
        if (count <= 1) return slot + gap;
        return Math.min(slot + gap, (maxWidth - slot) / (count - 1));
    }

    /** The left edge of a centered row. */
    public static int rowStart(int count, int slot, int step, int centerX) {
        return centerX - ((count - 1) * step + slot) / 2;
    }

    /** The largest crisp emblem size (a multiple of its art) that fits {@code room}, within sensible limits. */
    public static int emblemSize(int room) {
        return Math.max(EMBLEM_ART, Math.min(EMBLEM_MAX, room / EMBLEM_ART * EMBLEM_ART));
    }
}
