package com.cnethuka.kegare.sixeyes.client;

import java.util.Locale;

/**
 * How Six Eyes' Reads put numbers into words, and where the focus's panel goes (DESIGN §3.32). Plain Java, so it's tested without a screen.
 */
public final class ReadsText {
    /** Up to this many effects get a line each, name and all; more are shortened to an icon, a level and the time left. */
    public static final int FULL_EFFECTS = 3;
    /** Shortened effects per row, and rows at most: past that, "+N more". */
    public static final int EFFECTS_PER_ROW = 3, EFFECT_ROWS = 3;

    private ReadsText() {}

    /** Health and the like: whole numbers as they are, anything else to one place ("14", "13.5"). */
    public static String amount(float value) {
        float rounded = Math.round(value * 10f) / 10f;
        if (rounded == Math.rint(rounded)) return Integer.toString((int) rounded);
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Time left on an effect: "0:30", "12:05"; {@code -1} never runs out ("∞"). */
    public static String duration(int ticks) {
        if (ticks < 0) return "∞";
        int seconds = (ticks + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Seconds to one place, for stuns and Burnout ("1.2"). */
    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20f);
    }

    /** An effect's level, I to X, then the number. */
    public static String level(int amplifier) {
        String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return amplifier >= 0 && amplifier < roman.length ? roman[amplifier] : Integer.toString(amplifier + 1);
    }

    /** Movement as its share of what the body has with no modifier ("130%"). */
    public static String share(float value, float base) {
        if (base <= 0) return "";
        return Math.round(value / base * 100f) + "%";
    }

    /** Knockback resistance as a share ("10%"). */
    public static String percent(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }

    /** Whether this many effects are shortened. */
    public static boolean shortened(int effects) {
        return effects > FULL_EFFECTS;
    }

    /** How many effects show; the rest are "+N more". */
    public static int effectsShown(int effects) {
        return shortened(effects) ? Math.min(effects, EFFECTS_PER_ROW * EFFECT_ROWS) : effects;
    }

    /**
     * Where the focus's panel goes: beside the body, on the side away from the crosshair, and never over the box round it ({@code clear}
     * either way); inside the screen. If neither side has room, above or below the crosshair. Null if it fits nowhere at this size (the
     * client draws it smaller). Returns {x, y}.
     */
    @javax.annotation.Nullable
    public static int[] placePanel(float tagX, float tagY, int w, int h, int screenW, int screenH, int clear, int gap) {
        return placePanel(tagX, tagY, w, h, screenW, screenH, clear, gap, null);
    }

    /** Within this of the crosshair, the panel keeps the side it was on: a body crossing the crosshair doesn't make it flip back and forth. */
    public static final int KEEP_SIDE = 24;

    /** The same, keeping to {@code preferRight}'s side while the body is near the crosshair. The third value is 1 if it went right. */
    @javax.annotation.Nullable
    public static int[] placePanel(float tagX, float tagY, int w, int h, int screenW, int screenH, int clear, int gap, @javax.annotation.Nullable Boolean preferRight) {
        if (w > screenW - 4 || h > screenH - 4) return null;
        float cx = screenW / 2f, cy = screenH / 2f;
        boolean right = tagX >= cx;
        if (preferRight != null && Math.abs(tagX - cx) < KEEP_SIDE) right = preferRight;
        int y = clamp(Math.round(tagY - h / 2f), 2, screenH - h - 2);
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean onRight = attempt == 0 ? right : !right;
            int x = onRight ? Math.round(Math.max(tagX + gap, cx + clear)) : Math.round(Math.min(tagX - gap - w, cx - clear - w));
            x = clamp(x, 2, screenW - w - 2);
            if (!overlaps(x, y, w, h, cx, cy, clear)) return new int[] {x, y, onRight ? 1 : 0};
        }
        int x = clamp(Math.round(tagX - w / 2f), 2, screenW - w - 2);
        for (int candidate : new int[] {Math.round(cy - clear - h), Math.round(cy + clear)}) {
            int at = clamp(candidate, 2, screenH - h - 2);
            if (!overlaps(x, at, w, h, cx, cy, clear)) return new int[] {x, at, x + w / 2f >= cx ? 1 : 0};
        }
        return null;
    }

    /** Whether a rectangle covers the crosshair's box. */
    public static boolean overlaps(int x, int y, int w, int h, float cx, float cy, int clear) {
        return x < cx + clear && x + w > cx - clear && y < cy + clear && y + h > cy - clear;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }
}
