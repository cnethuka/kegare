package com.cnethuka.kegare.innatetechnique;

/** Outcome of the shared cast checks. Non-OK results map to an action-bar message (lang key). */
public enum CastResult {
    OK(null),
    /** Silently ignored (dead, spectator, not born yet). */
    IGNORED(null),
    SEVERED("message.kegare.cast.severed"),
    NO_INNATE_TECHNIQUE("message.kegare.cast.no_innate_technique"),
    NO_ULTIMATE("message.kegare.cast.no_ultimate"),
    BUSY("message.kegare.cast.busy"),
    STUNNED("message.kegare.cast.stunned"),
    ON_COOLDOWN("message.kegare.cast.on_cooldown"),
    NOT_ENOUGH_MALICE("message.kegare.cast.not_enough_malice"),
    BLOCKED("message.kegare.cast.blocked"),
    CHARGE_TOO_SHORT("message.kegare.cast.charge_too_short");

    private final String translationKey;

    CastResult(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
