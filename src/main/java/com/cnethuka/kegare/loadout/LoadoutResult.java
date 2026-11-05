package com.cnethuka.kegare.loadout;

import java.util.Locale;
import javax.annotation.Nullable;

/** Outcome of a loadout edit; every refusal has an action-bar / Loadout-page message. */
public enum LoadoutResult {
    OK,
    IN_COMBAT,
    CASTING,
    SEVERED,
    NO_INNATE_TECHNIQUE,
    ULTIMATE_FIXED,
    BAD_SLOT,
    LOCKED,
    UNAVAILABLE;

    @Nullable
    public String translationKey() {
        return this == OK ? null : "message.kegare.loadout." + name().toLowerCase(Locale.ROOT);
    }
}
