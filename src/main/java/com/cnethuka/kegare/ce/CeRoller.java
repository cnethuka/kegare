package com.cnethuka.kegare.ce;

import java.util.function.IntUnaryOperator;

/**
 * Pure birth-roll math for ce capacity. No Minecraft types, so it is covered by plain unit tests.
 * {@code random.applyAsInt(bound)} must return a uniform int in {@code [0, bound)}.
 */
public final class CeRoller {
    public record Result(CeTier tier, int capacity) {}

    private CeRoller() {}

    /**
     * @param tierWeights relative weight per {@link CeTier} ordinal (negative treated as 0)
     * @param tierBases   capacity base per {@link CeTier} ordinal ({@link CeTier#ZERO} is ignored, always 0)
     * @param variation   non-zero tiers get a uniform {@code [-variation, +variation]} offset
     */
    public static Result roll(int[] tierWeights, int[] tierBases, int variation, IntUnaryOperator random) {
        CeTier tier = pickTier(tierWeights, random);
        if (tier == CeTier.ZERO) return new Result(CeTier.ZERO, 0);
        int offset = variation > 0 ? random.applyAsInt(variation * 2 + 1) - variation : 0;
        // Non-zero tiers never land on 0: zero capacity is reserved for the ZERO tier (Severed).
        return new Result(tier, Math.max(1, tierBases[tier.ordinal()] + offset));
    }

    static CeTier pickTier(int[] tierWeights, IntUnaryOperator random) {
        CeTier[] tiers = CeTier.values();
        long total = 0;
        for (CeTier t : tiers) total += Math.max(0, tierWeights[t.ordinal()]);
        if (total <= 0) return CeTier.AVERAGE; // misconfigured: all weights zero
        int pick = random.applyAsInt((int) Math.min(Integer.MAX_VALUE, total));
        for (CeTier t : tiers) {
            pick -= Math.max(0, tierWeights[t.ordinal()]);
            if (pick < 0) return t;
        }
        return CeTier.AVERAGE;
    }

    /** Tier label for an admin-set capacity: ZERO for 0, otherwise the non-zero tier with the nearest base. */
    public static CeTier closestTier(int capacity, int[] tierBases) {
        if (capacity <= 0) return CeTier.ZERO;
        CeTier best = CeTier.LOW;
        int bestDistance = Integer.MAX_VALUE;
        for (CeTier t : CeTier.values()) {
            if (t == CeTier.ZERO) continue;
            int distance = Math.abs(tierBases[t.ordinal()] - capacity);
            if (distance < bestDistance) {
                best = t;
                bestDistance = distance;
            }
        }
        return best;
    }
}
