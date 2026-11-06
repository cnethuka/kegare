package com.cnethuka.kegare.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CastKeysTest {
    @Test
    void aPressOnlyStartsSomethingWhileAllowed() {
        assertTrue(CastKeys.down(true, false, true), "mode on: the press counts");
        assertFalse(CastKeys.down(true, false, false), "mode off: the press does nothing");
    }

    @Test
    void aHeldKeyKeepsGoingAfterTheModeEnds() {
        assertTrue(CastKeys.down(true, true, false), "still held, so no release is sent yet");
    }

    @Test
    void lettingGoAlwaysCountsAsUp() {
        assertFalse(CastKeys.down(false, true, false), "released with the mode off: the cast still gets its release");
        assertFalse(CastKeys.down(false, true, true));
        assertFalse(CastKeys.down(false, false, true));
    }
}
