package com.cnethuka.kegare.progression;

import java.util.Locale;

/** The three separate kinds of Insight. What a player has learned of each is only ever shown to them. */
public enum InsightCategory {
    DOMINION,
    WARD,
    OUTPUT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "insight.kegare." + id();
    }
}
