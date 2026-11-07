package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DashKickTest {
    @Test
    void itComesAndGoesAndNeverLingers() {
        assertEquals(0f, DashKick.envelope(-1f));
        assertEquals(0f, DashKick.envelope(0f));
        assertEquals(1f, DashKick.envelope(DashKick.RISE));
        assertEquals(0f, DashKick.envelope(DashKick.DURATION));
        assertEquals(0f, DashKick.envelope(500f));
        for (float age = DashKick.RISE; age < DashKick.DURATION; age += 0.25f) {
            assertTrue(DashKick.envelope(age + 0.25f) <= DashKick.envelope(age), "only falls after its peak");
        }
    }

    @Test
    void theKickIsBriefAndScales() {
        assertEquals(1f + DashKick.FOV_PEAK, DashKick.fovMultiplier(DashKick.RISE, 1f), 1.0e-5f);
        assertEquals(1f + DashKick.FOV_PEAK / 2f, DashKick.fovMultiplier(DashKick.RISE, 0.5f), 1.0e-5f, "half at Reduced");
        assertEquals(1f, DashKick.fovMultiplier(DashKick.RISE, 0f), "none at Vanilla or with FOV Effects off");
        assertEquals(1f, DashKick.fovMultiplier(DashKick.DURATION, 1f), "gone when it's over");
    }

    @Test
    void streaksNeverCrossTheView() {
        assertEquals(0f, DashKick.edgeFade(0f), "the crosshair");
        assertEquals(0f, DashKick.edgeFade(DashKick.CLEAR_RADIUS));
        assertEquals(1f, DashKick.edgeFade(1f), "the screen's edge");
        assertTrue(DashKick.edgeFade(0.75f) > 0f && DashKick.edgeFade(0.75f) < 1f);
    }
}
