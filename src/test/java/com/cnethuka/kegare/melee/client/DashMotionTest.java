package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DashMotionTest {
    @Test
    void goesFlatOutUntilTheLastBit() {
        assertEquals(1.0, DashMotion.speedAt(0.0, 4.0, 1.0), 1e-9);
        assertEquals(1.0, DashMotion.speedAt(3.0, 4.0, 1.0), 1e-9);
        assertEquals(0.4, DashMotion.speedAt(3.6, 4.0, 1.0), 1e-9, "only what's left");
    }

    @Test
    void endsOnItsDistance() {
        assertEquals(0.0, DashMotion.speedAt(4.0, 4.0, 1.0), 1e-9);
        assertEquals(0.0, DashMotion.speedAt(4.3, 4.0, 1.0), 1e-9, "overshot by held keys: no going back");
    }
}
