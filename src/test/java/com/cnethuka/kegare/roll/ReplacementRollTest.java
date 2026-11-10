package com.cnethuka.kegare.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import org.junit.jupiter.api.Test;

/** The replacement roll for an innate technique removed by an update is free. */
class ReplacementRollTest {
    private static final PityRoller.Settings DEFAULTS = new PityRoller.Settings(new int[]{50, 30, 15, 5}, 30, 5, 50);
    private static final boolean[] ALL = {true, true, true, true};

    @Test
    void replacementDoesNotCountTowardPityOrGrantReawakening() {
        assertFalse(RollReason.REPLACEMENT.countsTowardPity());
        assertFalse(RollReason.REPLACEMENT.grantsReawakening());
    }

    @Test
    void replacementLeavesPityStateUntouchedForEveryOutcome() {
        PityState before = new PityState(17, 42);
        for (InnateTechniqueRarity rarity : InnateTechniqueRarity.values()) {
            assertEquals(before, before.after(rarity, RollReason.REPLACEMENT), "outcome " + rarity);
        }
    }

    @Test
    void replacementRollsOnBaseWeightsWithoutConsumingPity() {
        PityState armed = new PityState(49, 80); // the next counted roll would be hard pity
        int counter = armed.effectiveCounter(RollReason.REPLACEMENT);
        assertEquals(0, counter);
        assertFalse(PityRoller.isHardPity(DEFAULTS, counter));
        assertEquals(5, PityRoller.effectiveWeights(DEFAULTS, counter, ALL)[InnateTechniqueRarity.LEGENDARY.ordinal()]);
        PityRoller.Result r = PityRoller.pick(DEFAULTS, counter, ALL, bound -> 0).orElseThrow();
        assertEquals(InnateTechniqueRarity.COMMON, r.rarity());
        assertFalse(r.byHardPity());
        // ...and the armed pity is still there for the player's next real roll.
        assertEquals(armed, armed.after(r.rarity(), RollReason.REPLACEMENT));
        assertTrue(PityRoller.isHardPity(DEFAULTS, armed.effectiveCounter(RollReason.TALISMAN)));
    }

    @Test
    void countedRollsStillAdvanceAndReset() {
        PityState s = new PityState(10, 20);
        assertEquals(new PityState(11, 21), s.after(InnateTechniqueRarity.RARE, RollReason.TALISMAN));
        assertEquals(new PityState(11, 21), s.after(InnateTechniqueRarity.COMMON, RollReason.ADMIN));
        assertEquals(new PityState(11, 21), s.after(InnateTechniqueRarity.EPIC, RollReason.BIRTH));
        assertEquals(new PityState(0, 21), s.after(InnateTechniqueRarity.LEGENDARY, RollReason.TALISMAN));
    }

    @Test
    void onlyTheTalismanGrantsReawakening() {
        assertTrue(RollReason.TALISMAN.grantsReawakening());
        assertFalse(RollReason.BIRTH.grantsReawakening());
        assertFalse(RollReason.ADMIN.grantsReawakening());
    }
}
