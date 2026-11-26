package com.cnethuka.kegare.progression;

import java.util.Locale;

/** Everything that pays Insight, with the category and amount it pays by default. Which category each one pays is config. */
public enum InsightSource {
    HIT_BY_TECHNIQUE(InsightCategory.WARD, 1),
    FIRST_LEGENDARY_FIGHT(InsightCategory.OUTPUT, 5),
    SURVIVED_STANDSTILL(InsightCategory.WARD, 5),
    CAUGHT_IN_DOMAIN(InsightCategory.DOMAIN, 5),
    CLASHED_WITH_DOMAIN(InsightCategory.DOMAIN, 5),
    UNLOCKED_RMT(InsightCategory.OUTPUT, 5),
    RMT_SURVIVED_LOW(InsightCategory.OUTPUT, 5),
    HEALED_BY_OTHER(InsightCategory.OUTPUT, 3),
    FIRST_BLACK_SPARK(InsightCategory.OUTPUT, 5);

    final InsightCategory defaultCategory;
    final int defaultAmount;

    InsightSource(InsightCategory defaultCategory, int defaultAmount) {
        this.defaultCategory = defaultCategory;
        this.defaultAmount = defaultAmount;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
