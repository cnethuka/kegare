package com.cnethuka.kegare.malice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaliceRollerTest {
    /** Spec defaults: Zero 0 (2%), Low 250 (30%), Average 600 (45%), High 1500 (20%), Vast 10000 (3%). */
    private static final int[] WEIGHTS = {2, 30, 45, 20, 3};
    private static final int[] BASES = {0, 250, 600, 1500, 10_000};
    private static final double VARIATION = 10.0;

    @Test
    void exhaustiveTierPickMatchesWeights() {
        Map<MaliceTier, Integer> counts = new EnumMap<>(MaliceTier.class);
        for (int value = 0; value < 100; value++) {
            final int v = value;
            counts.merge(MaliceRoller.pickTier(WEIGHTS, bound -> v), 1, Integer::sum);
        }
        assertEquals(2, counts.get(MaliceTier.ZERO));
        assertEquals(30, counts.get(MaliceTier.LOW));
        assertEquals(45, counts.get(MaliceTier.AVERAGE));
        assertEquals(20, counts.get(MaliceTier.HIGH));
        assertEquals(3, counts.get(MaliceTier.VAST));
    }

    @Test
    void zeroTierHasZeroCapacityAndNoVariation() {
        // First random call picks the tier (0 lands in ZERO); no variation is applied.
        MaliceRoller.Result result = MaliceRoller.roll(WEIGHTS, BASES, VARIATION, bound -> 0);
        assertEquals(MaliceTier.ZERO, result.tier());
        assertEquals(0, result.capacity());
    }

    /** Every offset the roll can give, for each tier: exactly the base, plus or minus 10% of that tier's own base. */
    @Test
    void variationIsAPercentOfEachTiersBase() {
        assertCapacityRange(MaliceTier.LOW, 225, 275);
        assertCapacityRange(MaliceTier.AVERAGE, 540, 660);
        assertCapacityRange(MaliceTier.HIGH, 1350, 1650);
        assertCapacityRange(MaliceTier.VAST, 9000, 11_000);
    }

    private static void assertCapacityRange(MaliceTier tier, int min, int max) {
        int[] onlyThisTier = new int[MaliceTier.values().length];
        onlyThisTier[tier.ordinal()] = 1;
        int width = max - min + 1;
        for (int offset = 0; offset < width; offset++) {
            final int o = offset;
            // bound 1 picks the tier; the second call has to ask for exactly the range's width
            MaliceRoller.Result r = MaliceRoller.roll(onlyThisTier, BASES, VARIATION, bound -> {
                if (bound == 1) return 0;
                assertEquals(width, bound, tier + ": a uniform pick over the whole range");
                return o;
            });
            assertEquals(tier, r.tier());
            assertEquals(min + offset, r.capacity());
        }
    }

    @Test
    void noVariationMeansExactlyTheBase() {
        int[] onlyHigh = {0, 0, 0, 1, 0};
        assertEquals(1500, MaliceRoller.roll(onlyHigh, BASES, 0.0, bound -> 0).capacity());
    }

    @Test
    void nonZeroTierNeverRollsZero() {
        int[] onlyLow = {0, 1, 0, 0, 0};
        int[] tinyBases = {0, 3, 100, 150, 250};
        // The worst the config allows: 100% of the base, at the lowest offset, would land exactly on 0.
        assertEquals(1, MaliceRoller.roll(onlyLow, tinyBases, 100.0, bound -> 0).capacity());
        assertEquals(1, MaliceRoller.roll(onlyLow, BASES, 100.0, bound -> 0).capacity());
    }

    @Test
    void closestTierLabelsAdminCapacity() {
        assertEquals(MaliceTier.ZERO, MaliceRoller.closestTier(0, BASES));
        assertEquals(MaliceTier.LOW, MaliceRoller.closestTier(200, BASES));
        assertEquals(MaliceTier.AVERAGE, MaliceRoller.closestTier(700, BASES));
        assertEquals(MaliceTier.HIGH, MaliceRoller.closestTier(5000, BASES));
        assertEquals(MaliceTier.VAST, MaliceRoller.closestTier(9999, BASES));
    }
}
