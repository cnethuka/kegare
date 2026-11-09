package com.cnethuka.kegare.progression;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/**
 * What a shared or system unlock asks for: any of a minimum Grade, a minimum Refinement and Insight per category.
 * Everything asked for must be met.
 *
 * @param loweredBy the Insight category that lowers the Refinement needed, if one matches this unlock
 * @param tiedToTechnique it belongs to the technique it was earned on: a reroll takes it, and lowering Refinement can.
 *                        Otherwise it stays once earned
 * @param condition anything else it needs, read when it's checked (so a config number counts as it stands now)
 * @param grantedOnly meeting it only makes a player eligible: it's given by whatever system teaches it ({@link Unlocks#grant})
 */
public record UnlockRequirements(Grade grade, int refinement, Map<InsightCategory, Integer> insight, @Nullable InsightCategory loweredBy, boolean tiedToTechnique,
                                 @Nullable Predicate<Player> condition, boolean grantedOnly) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The Refinement needed once Insight has lowered it. Insight speeds an unlock up but never removes the requirement: it takes off at
     * most {@code cap} of it, rounded down, and a requirement of 1 or more stays at 1 or more whatever {@code cap} is.
     *
     * @param rate levels per point of matching Insight
     * @param cap the most it can take off, as a share of the requirement
     */
    public int effectiveRefinement(ToIntFunction<InsightCategory> insightOf, double rate, double cap) {
        if (loweredBy == null || refinement <= 0) return refinement;
        int lowered = (int) Math.floor(Math.min(insightOf.applyAsInt(loweredBy) * rate, refinement * cap));
        return Math.max(1, refinement - Math.max(0, lowered));
    }

    /** As {@link #met(Grade, int, ToIntFunction, double, double)}, and the condition too. */
    public boolean met(Player player, Grade playerGrade, int playerRefinement, ToIntFunction<InsightCategory> insightOf, double rate, double cap) {
        return met(playerGrade, playerRefinement, insightOf, rate, cap) && (condition == null || condition.test(player));
    }

    public boolean met(Grade playerGrade, int playerRefinement, ToIntFunction<InsightCategory> insightOf, double rate, double cap) {
        if (!playerGrade.atLeast(grade) || playerRefinement < effectiveRefinement(insightOf, rate, cap)) return false;
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
        @Nullable
        private Predicate<Player> condition;
        private boolean grantedOnly;

        private Builder() {}

        /** Anything else it needs, read when it's checked. */
        public Builder condition(Predicate<Player> condition) {
            this.condition = condition;
            return this;
        }

        /** Meeting it makes a player eligible; it's given by {@link Unlocks#grant}, never on its own. */
        public Builder grantedOnly() {
            this.grantedOnly = true;
            return this;
        }

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
            return new UnlockRequirements(grade, refinement, Map.copyOf(insight), loweredBy, tiedToTechnique, condition, grantedOnly);
        }
    }
}
