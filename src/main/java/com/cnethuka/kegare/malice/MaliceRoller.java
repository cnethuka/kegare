package com.cnethuka.kegare.malice;

import java.util.function.IntUnaryOperator;

/**
 * Pure birth-roll math for Malice capacity. No Minecraft types, so it is covered by plain unit tests.
 * {@code random.applyAsInt(bound)} must return a uniform int in {@code [0, bound)}.
 */
public final class MaliceRoller {
    public record Result(MaliceTier tier, int capacity) {}

    private MaliceRoller() {}

    /**
     * @param tierWeights relative weight per {@link MaliceTier} ordinal (negative treated as 0)
     * @param tierBases   capacity base per {@link MaliceTier} ordinal ({@link MaliceTier#ZERO} is ignored, always 0)
     * @param variation   non-zero tiers get a uniform {@code [-variation, +variation]} offset
     */
    public static Result roll(int[] tierWeights, int[] tierBases, int variation, IntUnaryOperator random) {
        MaliceTier tier = pickTier(tierWeights, random);
        if (tier == MaliceTier.ZERO) return new Result(MaliceTier.ZERO, 0);
        int offset = variation > 0 ? random.applyAsInt(variation * 2 + 1) - variation : 0;
        // Non-zero tiers never land on 0: zero capacity is reserved for the ZERO tier (Severed).
        return new Result(tier, Math.max(1, tierBases[tier.ordinal()] + offset));
    }

    static MaliceTier pickTier(int[] tierWeights, IntUnaryOperator random) {
        MaliceTier[] tiers = MaliceTier.values();
        long total = 0;
        for (MaliceTier t : tiers) total += Math.max(0, tierWeights[t.ordinal()]);
        if (total <= 0) return MaliceTier.AVERAGE; // misconfigured: all weights zero
        int pick = random.applyAsInt((int) Math.min(Integer.MAX_VALUE, total));
        for (MaliceTier t : tiers) {
            pick -= Math.max(0, tierWeights[t.ordinal()]);
            if (pick < 0) return t;
        }
        return MaliceTier.AVERAGE;
    }

    /** Tier label for an admin-set capacity: ZERO for 0, otherwise the non-zero tier with the nearest base. */
    public static MaliceTier closestTier(int capacity, int[] tierBases) {
        if (capacity <= 0) return MaliceTier.ZERO;
        MaliceTier best = MaliceTier.LOW;
        int bestDistance = Integer.MAX_VALUE;
        for (MaliceTier t : MaliceTier.values()) {
            if (t == MaliceTier.ZERO) continue;
            int distance = Math.abs(tierBases[t.ordinal()] - capacity);
            if (distance < bestDistance) {
                best = t;
                bestDistance = distance;
            }
        }
        return best;
    }
}
