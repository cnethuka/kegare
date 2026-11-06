package com.cnethuka.kegare.progression;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/**
 * What a shared or system unlock asks for: any of a minimum Grade, a minimum Refinement and Insight per category.
 * Everything asked for must be met.
 *
 * @param loweredBy the Insight category that lowers the Refinement needed, if one matches this unlock
 * @param tiedToTechnique it belongs to the technique it was earned on: a reroll takes it, and lowering Refinement can.
 *                        Otherwise it stays once earned
 */
public record UnlockRequirements(Grade grade, int refinement, Map<InsightCategory, Integer> insight, @Nullable InsightCategory loweredBy, boolean tiedToTechnique) {
    public static Builder builder() {
        return new Builder();
    }

    /** The Refinement needed once Insight has lowered it. {@code rate} is levels per point of matching Insight. */
    public int effectiveRefinement(ToIntFunction<InsightCategory> insightOf, double rate) {
        if (loweredBy == null) return refinement;
        return Math.max(0, refinement - (int) Math.floor(insightOf.applyAsInt(loweredBy) * rate));
    }

    public boolean met(Grade playerGrade, int playerRefinement, ToIntFunction<InsightCategory> insightOf, double rate) {
        if (!playerGrade.atLeast(grade) || playerRefinement < effectiveRefinement(insightOf, rate)) return false;
        for (Map.Entry<InsightCategory, Integer> needed : insight.entrySet()) {
            if (insightOf.applyAsInt(needed.getKey()) < needed.getValue()) return false;
        }
        return true;
    }

    public static final class Builder {
        private Grade grade = Grade.GRADE_4;
        private int refinement;
        private final Map<InsightCategory, Integer> insight = new EnumMap<>(InsightCategory.class);
        @Nullable
        private InsightCategory loweredBy;
        private boolean tiedToTechnique;

        private Builder() {}

        public Builder grade(Grade minimum) {
            this.grade = minimum;
            return this;
        }

        public Builder refinement(int minimum) {
            this.refinement = minimum;
            return this;
        }

        public Builder insight(InsightCategory category, int minimum) {
            this.insight.put(category, minimum);
            return this;
        }

        /** Insight in this category lowers the Refinement needed, at the configured rate. */
        public Builder loweredBy(InsightCategory category) {
            this.loweredBy = category;
            return this;
        }

        /** It belongs to the technique it was earned on, so a reroll takes it. */
        public Builder tiedToTechnique() {
            this.tiedToTechnique = true;
            return this;
        }

        public UnlockRequirements build() {
            return new UnlockRequirements(grade, refinement, Map.copyOf(insight), loweredBy, tiedToTechnique);
        }
    }
}
