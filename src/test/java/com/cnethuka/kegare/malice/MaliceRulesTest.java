package com.cnethuka.kegare.malice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Running on Empty (visible Malice hits 0), Overflowing (born Vast) and regen. */
class MaliceRulesTest {
    @Test
    void spendingTheLastPointHitsEmpty() {
        assertTrue(MaliceRules.hitsEmpty(10, 0));      // dash with exactly 10
        assertTrue(MaliceRules.hitsEmpty(10.4, 0.4));  // HUD shows 0 afterwards
        assertTrue(MaliceRules.hitsEmpty(1.0, 0.95));  // upkeep tick crossing 1 -> 0.x
    }

    @Test
    void stayingAboveOrAlreadyEmptyDoesNotCount() {
        assertFalse(MaliceRules.hitsEmpty(10, 1));     // still shows 1
        assertFalse(MaliceRules.hitsEmpty(0.6, 0.1));  // was already showing 0
        assertFalse(MaliceRules.hitsEmpty(0, 0));      // Severed / empty stays empty
        assertFalse(MaliceRules.hitsEmpty(0, 5));      // regenerating
    }

    @Test
    void regenIsAPercentOfCapacityPerSecond() {
        assertEquals(7.5 / 20.0, MaliceRules.regenPerTick(250, 3.0), 1e-9, "Low");
        assertEquals(12.0 / 20.0, MaliceRules.regenPerTick(600, 2.0), 1e-9, "Average");
        assertEquals(22.5 / 20.0, MaliceRules.regenPerTick(1500, 1.5), 1e-9, "High");
        assertEquals(75.0 / 20.0, MaliceRules.regenPerTick(10_000, 0.75), 1e-9, "Vast");
        assertEquals(0.0, MaliceRules.regenPerTick(250, 0.0), 1e-9, "configured off");
    }

    @Test
    void onlyVastIsOverflowing() {
        assertTrue(MaliceRules.isOverflowing(MaliceTier.VAST));
        for (MaliceTier tier : MaliceTier.values()) {
            if (tier != MaliceTier.VAST) assertFalse(MaliceRules.isOverflowing(tier), tier.name());
        }
    }
}
