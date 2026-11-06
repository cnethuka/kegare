package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DoubleTapTest {
    @Test
    void twoPressesInsideTheWindowCount() {
        DoubleTap tap = new DoubleTap();
        assertFalse(tap.press(1000, 300), "one press is just sprinting");
        assertTrue(tap.press(1300, 300), "exactly on the window");
    }

    @Test
    void tooSlowStartsOver() {
        DoubleTap tap = new DoubleTap();
        assertFalse(tap.press(1000, 300));
        assertFalse(tap.press(1301, 300), "too late");
        assertTrue(tap.press(1500, 300), "but that press can be the first of a new pair");
    }

    @Test
    void aThirdPressIsNotAnotherDouble() {
        DoubleTap tap = new DoubleTap();
        tap.press(1000, 300);
        assertTrue(tap.press(1100, 300));
        assertFalse(tap.press(1200, 300), "mashing doesn't send a dash per press");
    }

    @Test
    void aWindowOfZeroTurnsItOff() {
        DoubleTap tap = new DoubleTap();
        assertFalse(tap.press(1000, 0));
        assertFalse(tap.press(1010, 0));
    }
}
