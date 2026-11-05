package com.cnethuka.kegare.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Severed players never roll, hold or keep Malice-based traits such as six_eyes. */
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
    void becomingSeveredTransfersUniqueInheritableMaliceTraits() {
        // six_eyes: requiresMalice + unique + inheritable -> normal 30s transfer
        assertEquals(TraitRules.SeveredAction.TRANSFER, TraitRules.whenSevered(true, true, true));
    }

    @Test
    void becomingSeveredRemovesOtherMaliceTraits() {
        assertEquals(TraitRules.SeveredAction.REMOVE, TraitRules.whenSevered(true, false, false)); // plain trait
        assertEquals(TraitRules.SeveredAction.REMOVE, TraitRules.whenSevered(true, true, false));  // unique, not inheritable: released
    }

    @Test
    void becomingSeveredKeepsTraitsThatDontNeedMalice() {
        assertEquals(TraitRules.SeveredAction.KEEP, TraitRules.whenSevered(false, false, false));
        assertEquals(TraitRules.SeveredAction.KEEP, TraitRules.whenSevered(false, true, true));
    }

    @Test
    void severedCannotHoldMaliceTraits() {
        assertFalse(TraitRules.canHold(true, true));
        assertTrue(TraitRules.canHold(true, false));
        assertTrue(TraitRules.canHold(false, true));
    }
}
