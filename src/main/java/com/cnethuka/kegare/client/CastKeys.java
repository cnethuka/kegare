package com.cnethuka.kegare.client;

/** When a cast key counts as down. Plain logic, kept apart from the client classes so it can be tested on its own. */
final class CastKeys {
    private CastKeys() {}

    /**
     * R and V only start something while they are allowed (Technique Mode is on, or there is something R has to end). One
     * already held stays held, whatever happened to the mode since, so a cast that began always gets its release.
     */
    static boolean down(boolean keyDown, boolean wasHeld, boolean allowed) {
        return keyDown && (wasHeld || allowed);
    }
}
