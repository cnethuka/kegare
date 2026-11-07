package com.cnethuka.kegare.innatetechnique;

/**
 * The only thing a client may send about casting: which key went down or up.
 * The server decides everything else (innate technique, ability, cost, cooldown, stun).
 */
public enum CastIntent {
    /** The selected ability's base version (a number key, which has just selected it). */
    CAST_PRESS,
    CAST_RELEASE,
    /** The selected ability's special (R, with a number key that has just selected it or on its own). */
    SPECIAL_PRESS,
    SPECIAL_RELEASE,
    ULTIMATE_PRESS,
    ULTIMATE_RELEASE
}
