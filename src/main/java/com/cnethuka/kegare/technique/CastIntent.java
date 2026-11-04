package com.cnethuka.kegare.technique;

/**
 * The only thing a client may send about casting: which key went down or up.
 * The server decides everything else (technique, ability, cost, cooldown, stun).
 */
public enum CastIntent {
    CAST_PRESS,
    CAST_RELEASE,
    ULTIMATE_PRESS,
    ULTIMATE_RELEASE,
    NEXT_ABILITY,
    PREVIOUS_ABILITY
}
