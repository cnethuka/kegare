package com.cnethuka.kegare.innatetechnique;

/**
 * The only thing a client may send about casting: which key went down or up.
 * The server decides everything else (innate technique, ability, cost, cooldown, stun).
 */
public enum CastIntent {
    /** The selected ability's base version (right-click in Technique Mode, or the Cast key). */
    CAST_PRESS,
    CAST_RELEASE,
    /** The selected ability's special (R). */
    SPECIAL_PRESS,
    SPECIAL_RELEASE,
    ULTIMATE_PRESS,
    ULTIMATE_RELEASE
}
