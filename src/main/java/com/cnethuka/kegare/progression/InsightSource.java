package com.cnethuka.kegare.progression;

import java.util.Locale;

/** Everything that pays Insight, with the category and amount it pays by default. Which category each one pays is config. */
public enum InsightSource {
    HIT_BY_TECHNIQUE(InsightCategory.WARD, 1),
    FIRST_LEGENDARY_FIGHT(InsightCategory.OUTPUT, 5),
    SURVIVED_STANDSTILL(InsightCategory.WARD, 5),
    HIT_BY_MAXIMUM_OUTPUT(InsightCategory.OUTPUT, 5),
    CAUGHT_IN_DOMINION(InsightCategory.DOMINION, 5),
    CLASHED_WITH_DOMINION(InsightCategory.DOMINION, 5),
    BOOK_CHAPTER(InsightCategory.DOMINION, 2);

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
