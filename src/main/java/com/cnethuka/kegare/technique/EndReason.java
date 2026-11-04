package com.cnethuka.kegare.technique;

/** Why a charge, channel or toggle ended. Passed to {@link Ability#onEnd}. */
public enum EndReason {
    /** Key released (channel) or toggled off by the player. */
    RELEASED,
    /** Reserve could not pay upkeep. */
    OUT_OF_CE,
    /** Channel hit its max duration. */
    EXPIRED,
    /** Stunned mid-cast. */
    INTERRUPTED,
    /** Charge released too early, or cancelled for another non-player reason. */
    CANCELLED,
    /** Technique was changed (reroll, admin, rebirth). */
    TECHNIQUE_CHANGED,
    DEATH,
    LOGOUT
}
