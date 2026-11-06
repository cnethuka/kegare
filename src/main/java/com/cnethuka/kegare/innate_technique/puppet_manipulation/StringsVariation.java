package com.cnethuka.kegare.innate_technique.puppet_manipulation;

/** What a string's second stage does: cast key or R, on the attached target (or nothing) or on a different target. */
public enum StringsVariation {
    /** Cast key, same target or nothing: pull the target to you. */
    REEL_IN,
    /** Cast key, a different target: both are pulled into each other, collide, and stay tethered. */
    TIE,
    /** R, same target or nothing: pull yourself to the target. */
    ZIP,
    /** R, a different target: both are leashed to you, and you're slowed. */
    LEASH
}
