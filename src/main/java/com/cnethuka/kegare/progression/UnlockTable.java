package com.cnethuka.kegare.progression;

/**
 * The default Refinement level each part of a technique unlocks at, by default loadout order: abilities 1 and 2 at 0,
 * then a special, an ability, a special and so on, the ultimate at 8. Levels with nothing to give (a technique with
 * four abilities has no ability 5) simply unlock nothing.
 */
public final class UnlockTable {
    private static final int[] ABILITY = {0, 0, 2, 4, 6};
    private static final int[] SPECIAL = {1, 3, 5, 7, 9};
    public static final int ULTIMATE = 8;
    /** A technique with more than five abilities has to set the extras itself; until it does, they wait at the top. */
    private static final int BEYOND_THE_TABLE = 10;

    private UnlockTable() {}

    /** @param index 0 for the technique's first ability */
    public static int ability(int index) {
        return index < ABILITY.length ? ABILITY[index] : BEYOND_THE_TABLE;
    }

    /** @param index 0 for the special of the technique's first ability */
    public static int special(int index) {
        return index < SPECIAL.length ? SPECIAL[index] : BEYOND_THE_TABLE;
    }
}
