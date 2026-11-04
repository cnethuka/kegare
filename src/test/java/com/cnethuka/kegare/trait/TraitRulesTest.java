package com.cnethuka.kegare.trait;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fix 1: Severed players never roll (or hold) Malice-based traits such as lensing_eyes. */
class TraitRulesTest {
    @Test
    void severedNeverRollsMaliceTraits() {
        // Even a guaranteed (100%) Malice-based trait is skipped for a Severed player.
        assertFalse(TraitRules.canRollAtBirth(1.0, true, true));
        assertFalse(TraitRules.canRollAtBirth(0.01, true, true));
    }

    @Test
    void severedCanStillRollTraitsThatDontNeedMalice() {
        assertTrue(TraitRules.canRollAtBirth(0.5, false, true));
    }

    @Test
    void playersWithMaliceRollMaliceTraits() {
        assertTrue(TraitRules.canRollAtBirth(0.01, true, false));
        assertTrue(TraitRules.canRollAtBirth(0.01, false, false));
    }

    @Test
    void zeroChanceIsNeverRolled() {
        assertFalse(TraitRules.canRollAtBirth(0.0, false, false));
    }

    @Test
    void severedCannotHoldMaliceTraits() {
        assertFalse(TraitRules.canHold(true, true));
        assertTrue(TraitRules.canHold(true, false));
        assertTrue(TraitRules.canHold(false, true));
    }
}
