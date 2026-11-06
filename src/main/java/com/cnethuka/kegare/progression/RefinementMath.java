package com.cnethuka.kegare.progression;

/** The Refinement curve and what one landing is worth, with no game running. */
public final class RefinementMath {
    /**
     * @param baseXp a landed base ability or ultimate; @param specialXp a landed special
     * @param playerMultiplier a hit on a player, against 1 for a mob
     * @param decay what each earlier landing on the same target in the window multiplies this one by
     */
    public record Settings(double baseXp, double specialXp, double playerMultiplier, double decay, double floor) {}

    private RefinementMath() {}

    /** The XP to go from {@code level - 1} to {@code level}. */
    public static double xpForLevel(int level, double base, double exponent) {
        return level <= 0 ? 0.0 : Math.round(base * Math.pow(level, exponent));
    }

    /** The total XP from 0 to {@code level}. */
    public static double totalXp(int level, double base, double exponent) {
        double total = 0;
        for (int l = 1; l <= level; l++) total += xpForLevel(l, base, exponent);
        return total;
    }

    /**
     * One landing's XP.
     *
     * @param earlier landings on this same target already in the window
     * @param gradeMultiplier from the caster's Grade: an experienced sorcerer re-learns faster
     */
    public static double landingXp(Settings s, boolean special, boolean onPlayer, int earlier, double gradeMultiplier) {
        return (special ? s.specialXp : s.baseXp) * (onPlayer ? s.playerMultiplier : 1.0)
                * GradeMath.diminishing(earlier, s.decay, s.floor) * gradeMultiplier;
    }
}
