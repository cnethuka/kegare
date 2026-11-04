package com.cnethuka.kegare.malice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaliceRollerTest {
    /** Spec defaults: Zero 0 (2%), Low 60 (25%), Average 100 (45%), High 150 (20%), Vast 250 (8%). */
    private static final int[] WEIGHTS = {2, 25, 45, 20, 8};
    private static final int[] BASES = {0, 60, 100, 150, 250};

    @Test
    void exhaustiveTierPickMatchesWeights() {
        Map<MaliceTier, Integer> counts = new EnumMap<>(MaliceTier.class);
        for (int value = 0; value < 100; value++) {
            final int v = value;
            counts.merge(MaliceRoller.pickTier(WEIGHTS, bound -> v), 1, Integer::sum);
        }
        assertEquals(2, counts.get(MaliceTier.ZERO));
        assertEquals(25, counts.get(MaliceTier.LOW));
        assertEquals(45, counts.get(MaliceTier.AVERAGE));
        assertEquals(20, counts.get(MaliceTier.HIGH));
        assertEquals(8, counts.get(MaliceTier.VAST));
    }

    @Test
    void zeroTierHasZeroCapacityAndNoVariation() {
        // First random call picks the tier (0 lands in ZERO); no variation is applied.
        MaliceRoller.Result result = MaliceRoller.roll(WEIGHTS, BASES, 10, bound -> 0);
        assertEquals(MaliceTier.ZERO, result.tier());
        assertEquals(0, result.capacity());
    }

    @Test
    void variationStaysWithinPlusMinusTen() {
        int[] onlyAverage = {0, 0, 1, 0, 0};
        for (int offset = 0; offset <= 20; offset++) {
            final int o = offset;
            // bound 1 picks the tier, bound 21 picks the offset
            MaliceRoller.Result r = MaliceRoller.roll(onlyAverage, BASES, 10, bound -> bound == 21 ? o : 0);
            assertEquals(MaliceTier.AVERAGE, r.tier());
            assertEquals(90 + offset, r.capacity());
        }
    }

    @Test
    void nonZeroTierNeverRollsZero() {
        int[] onlyLow = {0, 1, 0, 0, 0};
        int[] tinyBases = {0, 3, 100, 150, 250};
        MaliceRoller.Result r = MaliceRoller.roll(onlyLow, tinyBases, 10, bound -> 0); // 3 - 10 -> clamped to 1
        assertEquals(MaliceTier.LOW, r.tier());
        assertTrue(r.capacity() >= 1);
    }

    @Test
    void closestTierLabelsAdminCapacity() {
        assertEquals(MaliceTier.ZERO, MaliceRoller.closestTier(0, BASES));
        assertEquals(MaliceTier.LOW, MaliceRoller.closestTier(55, BASES));
        assertEquals(MaliceTier.AVERAGE, MaliceRoller.closestTier(110, BASES));
        assertEquals(MaliceTier.HIGH, MaliceRoller.closestTier(170, BASES));
        assertEquals(MaliceTier.VAST, MaliceRoller.closestTier(9999, BASES));
    }
}
