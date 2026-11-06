package com.cnethuka.kegare.melee.client;

/** Two presses of one key within a window. A third press right after doesn't count as another double: it starts over. */
final class DoubleTap {
    private long lastPress = -1;

    /** @return whether this press completes a double tap ({@code windowMs} 0 = off) */
    boolean press(long nowMs, long windowMs) {
        if (windowMs > 0 && lastPress >= 0 && nowMs - lastPress <= windowMs) {
            lastPress = -1;
            return true;
        }
        lastPress = nowMs;
        return false;
    }

    void reset() {
        lastPress = -1;
    }
}
