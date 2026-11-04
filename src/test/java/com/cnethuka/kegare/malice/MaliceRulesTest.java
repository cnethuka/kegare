package com.cnethuka.kegare.malice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Running on Empty (visible Malice hits 0) and Overflowing (born Vast). */
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
    void onlyVastIsOverflowing() {
        assertTrue(MaliceRules.isOverflowing(MaliceTier.VAST));
        for (MaliceTier tier : MaliceTier.values()) {
            if (tier != MaliceTier.VAST) assertFalse(MaliceRules.isOverflowing(tier), tier.name());
        }
    }
}
