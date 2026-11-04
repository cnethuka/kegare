package com.cnethuka.kegare.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PityRollerTest {
    /** Spec defaults: Common 50, Rare 30, Epic 15, Legendary 5; soft pity after 30 (+5/roll); hard pity on roll 50. */
    private static final PityRoller.Settings DEFAULTS = new PityRoller.Settings(new int[]{50, 30, 15, 5}, 30, 5, 50);
    private static final boolean[] ALL = {true, true, true, true};
    private static final int LEG = InnateTechniqueRarity.LEGENDARY.ordinal();

    @Test
    void noSoftPityUpToRoll30() {
        // counter 29 means this is roll 30: still base weight
        assertEquals(5, PityRoller.effectiveWeights(DEFAULTS, 29, ALL)[LEG]);
        assertEquals(5, PityRoller.effectiveWeights(DEFAULTS, 0, ALL)[LEG]);
    }

    @Test
    void softPityAddsFivePerRollAfter30() {
        assertEquals(10, PityRoller.effectiveWeights(DEFAULTS, 30, ALL)[LEG]);  // roll 31
        assertEquals(15, PityRoller.effectiveWeights(DEFAULTS, 31, ALL)[LEG]);  // roll 32
        assertEquals(100, PityRoller.effectiveWeights(DEFAULTS, 48, ALL)[LEG]); // roll 49
        // other rarities untouched
        int[] w = PityRoller.effectiveWeights(DEFAULTS, 48, ALL);
        assertEquals(50, w[0]);
        assertEquals(30, w[1]);
        assertEquals(15, w[2]);
    }

    @Test
    void roll50IsGuaranteedLegendary() {
        assertFalse(PityRoller.isHardPity(DEFAULTS, 48)); // roll 49
        assertTrue(PityRoller.isHardPity(DEFAULTS, 49));  // roll 50
        // Even a random source that would always pick the first (Common) bucket must yield Legendary.
        Optional<PityRoller.Result> result = PityRoller.pick(DEFAULTS, 49, ALL, bound -> 0);
        assertTrue(result.isPresent());
        assertEquals(InnateTechniqueRarity.LEGENDARY, result.get().rarity());
        assertTrue(result.get().byHardPity());
    }

    @Test
    void rollsUntilSoftPityCountsTheBoostedRoll() {
        assertEquals(31, PityRoller.rollsUntilSoftPity(DEFAULTS, 0));  // roll 31 is the first boosted one
        assertEquals(2, PityRoller.rollsUntilSoftPity(DEFAULTS, 29));  // next is roll 30 (normal), then 31 (boosted)
        assertEquals(0, PityRoller.rollsUntilSoftPity(DEFAULTS, 30)); // next roll (31) is boosted: active
        assertEquals(0, PityRoller.rollsUntilSoftPity(DEFAULTS, 45));
    }

    @Test
    void rollsUntilHardPityCountsTheGuaranteedRoll() {
        assertEquals(50, PityRoller.rollsUntilHardPity(DEFAULTS, 0));  // "Legendary guaranteed in 50 rolls"
        assertEquals(1, PityRoller.rollsUntilHardPity(DEFAULTS, 49));  // next roll
        assertEquals(1, PityRoller.rollsUntilHardPity(DEFAULTS, 70));  // overdue (no Legendary was eligible)
        assertEquals(-1, PityRoller.rollsUntilHardPity(new PityRoller.Settings(new int[]{50, 30, 15, 5}, 30, 5, 0), 10));
    }

    @Test
    void counterResetsOnAnyLegendary() {
        assertEquals(0, PityRoller.nextCounter(12, InnateTechniqueRarity.LEGENDARY));
        assertEquals(0, PityRoller.nextCounter(49, InnateTechniqueRarity.LEGENDARY));
        assertEquals(13, PityRoller.nextCounter(12, InnateTechniqueRarity.EPIC));
    }

    @Test
    void exhaustivePickMatchesWeights() {
        // Feed every possible random value once: bucket sizes must equal the weights exactly.
        int[] weights = PityRoller.effectiveWeights(DEFAULTS, 0, ALL);
        int total = 0;
        for (int w : weights) total += w;
        Map<InnateTechniqueRarity, Integer> counts = new EnumMap<>(InnateTechniqueRarity.class);
        for (int value = 0; value < total; value++) {
            final int v = value;
            InnateTechniqueRarity r = PityRoller.pick(DEFAULTS, 0, ALL, bound -> v).orElseThrow().rarity();
            counts.merge(r, 1, Integer::sum);
        }
        assertEquals(50, counts.get(InnateTechniqueRarity.COMMON));
        assertEquals(30, counts.get(InnateTechniqueRarity.RARE));
        assertEquals(15, counts.get(InnateTechniqueRarity.EPIC));
        assertEquals(5, counts.get(InnateTechniqueRarity.LEGENDARY));
    }

    @Test
    void unavailableRaritiesAreSkipped() {
        boolean[] noEpic = {true, true, false, true};
        int[] w = PityRoller.effectiveWeights(DEFAULTS, 0, noEpic);
        assertEquals(0, w[InnateTechniqueRarity.EPIC.ordinal()]);
        for (int value = 0; value < 85; value++) {
            final int v = value;
            assertTrue(PityRoller.pick(DEFAULTS, 0, noEpic, bound -> v).orElseThrow().rarity() != InnateTechniqueRarity.EPIC);
        }
    }

    @Test
    void noLegendaryAvailableMeansNoPity() {
        boolean[] noLegendary = {true, true, true, false};
        PityRoller.Result r = PityRoller.pick(DEFAULTS, 49, noLegendary, bound -> 0).orElseThrow();
        assertEquals(InnateTechniqueRarity.COMMON, r.rarity());
        assertFalse(r.byHardPity());
        assertEquals(0, PityRoller.effectiveWeights(DEFAULTS, 60, noLegendary)[LEG]);
    }

    @Test
    void nothingAvailableIsEmpty() {
        assertTrue(PityRoller.pick(DEFAULTS, 0, new boolean[4], bound -> 0).isEmpty());
    }

    @Test
    void allZeroWeightsFallBackToUniform() {
        PityRoller.Settings zero = new PityRoller.Settings(new int[]{0, 0, 0, 0}, 1000, 0, 0);
        assertEquals(InnateTechniqueRarity.RARE, PityRoller.pick(zero, 0, ALL, bound -> 1).orElseThrow().rarity());
    }

    @Test
    void simulatedPlayersNeverExceedHardPity() {
        Random random = new Random(1234);
        int worst = 0;
        for (int player = 0; player < 20_000; player++) {
            int counter = 0;
            int rolls = 0;
            while (true) {
                rolls++;
                InnateTechniqueRarity r = PityRoller.pick(DEFAULTS, counter, ALL, random::nextInt).orElseThrow().rarity();
                counter = PityRoller.nextCounter(counter, r);
                if (r == InnateTechniqueRarity.LEGENDARY) break;
            }
            worst = Math.max(worst, rolls);
        }
        assertTrue(worst <= 50, "worst streak " + worst);
    }
}
