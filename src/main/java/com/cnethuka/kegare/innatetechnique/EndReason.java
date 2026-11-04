package com.cnethuka.kegare.innatetechnique;

/** Why a charge, channel or toggle ended. Passed to {@link Ability#onEnd}. */
public enum EndReason {
    /** Key released (channel) or toggled off by the player. */
    RELEASED,
    /** Reserve could not pay upkeep. */
    OUT_OF_MALICE,
    /** Channel hit its max duration. */
    EXPIRED,
    /** Stunned mid-cast. */
    INTERRUPTED,
    /** Charge released too early, or cancelled for another non-player reason. */
    CANCELLED,
    /** Innate technique was changed (reroll, admin, rebirth). */
    INNATE_TECHNIQUE_CHANGED,
    DEATH,
    LOGOUT,
    /** Changed dimension (a charge, channel or toggle never carries through a portal). */
    DIMENSION_CHANGE
}
