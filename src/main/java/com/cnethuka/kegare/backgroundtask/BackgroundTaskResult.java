package com.cnethuka.kegare.backgroundtask;

import java.util.Locale;
import javax.annotation.Nullable;

/** Outcome of a Background Task request; every refusal has a message (action bar and the Sorcerer screen's status line). */
public enum BackgroundTaskResult {
    OK,
    SEVERED,
    IN_COMBAT,
    BAD_SLOT,
    EMPTY_SLOT,
    LOCKED,
    UNKNOWN;

    @Nullable
    public String translationKey() {
        return this == OK ? null : "message.kegare.background_task." + name().toLowerCase(Locale.ROOT);
    }
}
