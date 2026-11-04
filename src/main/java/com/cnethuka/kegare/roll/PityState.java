package com.cnethuka.kegare.roll;

import com.cnethuka.kegare.technique.TechniqueRarity;

/** Immutable pity snapshot and its transition rules. Pure Java (unit-tested). */
public record PityState(int rollsSinceLegendary, int totalRolls) {
    /** Counter the roll is evaluated with: non-counting rolls use base weights (as if the counter were 0). */
    public int effectiveCounter(RollReason reason) {
        return reason.countsTowardPity() ? rollsSinceLegendary : 0;
    }

    /** State after a roll: non-counting rolls change nothing; counting rolls advance or reset pity. */
    public PityState after(TechniqueRarity result, RollReason reason) {
        if (!reason.countsTowardPity()) return this;
        return new PityState(PityRoller.nextCounter(rollsSinceLegendary, result), totalRolls + 1);
    }
}
