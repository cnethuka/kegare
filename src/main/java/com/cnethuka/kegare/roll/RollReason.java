package com.cnethuka.kegare.roll;

/** Why a technique roll happens. Decides pity and advancement side effects. Pure Java (unit-tested). */
public enum RollReason {
    /** First join. */
    BIRTH(true, false),
    /** Purification Talisman. */
    TALISMAN(true, true),
    /** {@code /kegare reroll}. */
    ADMIN(true, false),
    /**
     * The stored technique no longer exists (removed by an update). A free replacement: rolls on base weights
     * (no soft/hard pity), leaves the pity counter and roll total untouched, grants no Reawakening.
     */
    REPLACEMENT(false, false);

    private final boolean countsTowardPity;
    private final boolean grantsReawakening;

    RollReason(boolean countsTowardPity, boolean grantsReawakening) {
        this.countsTowardPity = countsTowardPity;
        this.grantsReawakening = grantsReawakening;
    }

    public boolean countsTowardPity() {
        return countsTowardPity;
    }

    public boolean grantsReawakening() {
        return grantsReawakening;
    }
}
