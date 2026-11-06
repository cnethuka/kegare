package com.cnethuka.kegare.progression;

/** Grade XP, without a game running. {@link GradeService} feeds it from the config. */
public final class GradeMath {
    /**
     * @param xpPerDamage XP per point of damage dealt to the opponent
     * @param decay what each earlier fight against the same player in the window multiplies this one by
     * @param decayFloor the least diminishing returns go down to
     * @param belowPerStep lost per Grade the opponent is below you
     * @param belowFloor the least a far weaker opponent is worth
     * @param winBonusPerStep extra win XP per Grade the opponent is above you
     * @param winBonusMax the most win XP can be multiplied by
     * @param surviveMinDamage to survive a fight you must both deal and take this much: a tap isn't a fight
     */
    public record Settings(double xpPerDamage, double winXp, double lossXp, double surviveXp, double decay, double decayFloor,
                           double belowPerStep, double belowFloor, double winBonusPerStep, double winBonusMax, double surviveMinDamage) {}

    private GradeMath() {}

    /** Each earlier meeting in the window makes this one worth less. Shared by Grade (per fight) and Refinement (per hit). */
    public static double diminishing(int earlier, double decay, double floor) {
        return Math.max(floor, Math.pow(decay, Math.max(0, earlier)));
    }

    /** What beating someone {@code stepsBelow} Grades below you is worth, down to almost nothing. Not below: 1. */
    public static double weakerOpponent(int stepsBelow, double perStep, double floor) {
        return stepsBelow <= 0 ? 1.0 : Math.max(floor, 1.0 - stepsBelow * perStep);
    }

    /** The win XP multiplier for beating someone {@code stepsAbove} Grades above you. Not above: 1. */
    public static double strongerOpponentWin(int stepsAbove, double perStep, double max) {
        return stepsAbove <= 0 ? 1.0 : Math.min(max, 1.0 + stepsAbove * perStep);
    }

    /**
     * XP for one fight.
     *
     * @param earlierFights fights against this opponent already in the window
     * @param stepsAboveMe how far above you the opponent's Grade is (negative: below)
     */
    public static double fightXp(Settings s, FightOutcome outcome, double dealt, double taken, int earlierFights, int stepsAboveMe) {
        double outcomeXp = switch (outcome) {
            case WIN -> s.winXp * strongerOpponentWin(stepsAboveMe, s.winBonusPerStep, s.winBonusMax);
            case LOSS -> s.lossXp;
            case SURVIVED -> dealt >= s.surviveMinDamage && taken >= s.surviveMinDamage ? s.surviveXp : 0.0;
            case LEFT -> 0.0;
        };
        double xp = dealt * s.xpPerDamage + outcomeXp;
        return xp * diminishing(earlierFights, s.decay, s.decayFloor) * weakerOpponent(-stepsAboveMe, s.belowPerStep, s.belowFloor);
    }
}
