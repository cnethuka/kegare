package com.cnethuka.kegare.client;

/** When V counts as down. Plain logic, kept apart from the client classes so it can be tested on its own. */
final class CastKeys {
    private CastKeys() {}

    /**
     * V only starts something while it's allowed (Technique Mode is on). Once held it stays held, whatever happened to the mode
     * since, so a cast that began always gets its release.
     */
    static boolean down(boolean keyDown, boolean wasHeld, boolean allowed) {
        return keyDown && (wasHeld || allowed);
    }
}
