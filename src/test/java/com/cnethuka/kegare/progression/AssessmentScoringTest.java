package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The score and the requirements, with no game running. */
class AssessmentScoringTest {
    private static final double EPS = 1.0e-6;
    private static final AssessmentScoring.Settings S = new AssessmentScoring.Settings(
            new AssessmentScoring.Weights(50, 25, 15, 10), new AssessmentScoring.TechniqueWeights(40, 25, 10, 25),
            0.6, 0.5, 0.6, 3, 80, 0.08, 0.7, 0.8, 0.1, 0.3, 0.02, 4);

    private static final Set<String> FULL_BASE = Set.of("a", "b", "c", "d", "e");
    private static final Set<String> FULL_SPECIALS = Set.of("ra", "rb", "rc", "rd", "re");

    /** Landings a second apart. */
    private static List<Fight.Use> landings(String... ids) {
        List<Fight.Use> out = new ArrayList<>();
        long tick = 0;
        for (String id : ids) {
            out.add(new Fight.Use(id, id.startsWith("r"), tick));
            tick += 20;
        }
        return out;
    }

    @Test
    void repeatingOneMoveScoresLow() {
        double spam = AssessmentScoring.techniqueUse(landings("a", "a", "a", "a", "a", "a", "a", "a"), FULL_BASE, FULL_SPECIALS, S);
        double varied = AssessmentScoring.techniqueUse(landings("a", "b", "ra", "c", "rb", "a", "rc", "b"), FULL_BASE, FULL_SPECIALS, S);
        assertTrue(spam < 0.1, "one move out of a full kit: " + spam);
        assertEquals(1.0, varied, EPS, "three of each, mixed and combined: full marks");
        assertTrue(spam < varied / 5);
    }

    @Test
    void lockedAbilitiesDontLowerTheScore() {
        Set<String> levelZero = Set.of("a", "b");
        double fresh = AssessmentScoring.techniqueUse(landings("a", "b", "a", "b"), levelZero, Set.of(), S);
        assertEquals(1.0, fresh, EPS, "both of the two it has, combined: as good as it gets");
        double sameMovesFullKit = AssessmentScoring.techniqueUse(landings("a", "b", "a", "b"), FULL_BASE, FULL_SPECIALS, S);
        assertTrue(sameMovesFullKit < fresh, "the same two moves out of a full kit are worth less");
    }

    @Test
    void landingAnAbilityYouNoLongerHaveDoesntCount() {
        double locked = AssessmentScoring.techniqueUse(landings("zzz", "a"), Set.of("a", "b"), Set.of(), S);
        double unlocked = AssessmentScoring.techniqueUse(landings("b", "a"), Set.of("a", "b"), Set.of(), S);
        assertTrue(locked < unlocked);
    }

    @Test
    void mixingAndCombosCountSeparately() {
        Set<String> base = Set.of("a");
        Set<String> specials = Set.of("ra");
        double both = AssessmentScoring.techniqueUse(landings("a", "ra"), base, specials, S);
        double onlyBase = AssessmentScoring.techniqueUse(landings("a"), base, specials, S);
        assertTrue(both > onlyBase, "landing the special too is worth more");
    }

    @Test
    void combosTakeDifferentAbilitiesInsideTheWindow() {
        assertEquals(0, AssessmentScoring.combos(landings("a", "a", "a"), 80), "the same move isn't a combination");
        assertEquals(2, AssessmentScoring.combos(landings("a", "b", "c"), 80));
        List<Fight.Use> slow = List.of(new Fight.Use("a", false, 0), new Fight.Use("b", false, 81));
        assertEquals(0, AssessmentScoring.combos(slow, 80), "too far apart");
    }

    @Test
    void efficiencyIsDamagePerBaseMalice() {
        assertEquals(0.5, AssessmentScoring.efficiency(4, 100, S), EPS);
        assertEquals(1.0, AssessmentScoring.efficiency(8, 100, S), EPS);
        assertEquals(1.0, AssessmentScoring.efficiency(800, 100, S), EPS, "capped");
        assertEquals(0.0, AssessmentScoring.efficiency(50, 0, S), EPS, "nothing cast: nothing to rate");
    }

    @Test
    void baseCostUndoesTheSurroundings() {
        assertEquals(40.0, Landings.baseCost(32.0, 0.8), EPS, "cheaper in the cold, same base");
        assertEquals(40.0, Landings.baseCost(50.0, 1.25), EPS, "dearer in the heat, same base");
        assertEquals(AssessmentScoring.efficiency(4, Landings.baseCost(32.0, 0.8), S),
                AssessmentScoring.efficiency(4, Landings.baseCost(50.0, 1.25), S), EPS, "so the score can't tell the difference");
    }

    @Test
    void performanceIsTheShareOfTheDamageTraded() {
        assertEquals(0.5 / 0.7, AssessmentScoring.performance(10, 10, S), EPS);
        assertEquals(1.0, AssessmentScoring.performance(21, 9, S), EPS, "a 70% share of the trade: full marks");
        assertEquals(0.0, AssessmentScoring.performance(0, 0, S), EPS);
    }

    @Test
    void opponentCountsForMoreAboveAndAlmostNothingFarBelow() {
        assertEquals(0.8, AssessmentScoring.opponent(Grade.GRADE_3, Grade.GRADE_3, S), EPS);
        assertEquals(0.9, AssessmentScoring.opponent(Grade.GRADE_3, Grade.SEMI_GRADE_2, S), EPS);
        assertEquals(1.0, AssessmentScoring.opponent(Grade.GRADE_4, Grade.SPECIAL_GRADE, S), EPS);
        assertEquals(0.5, AssessmentScoring.opponent(Grade.GRADE_3, Grade.GRADE_4, S), EPS);
        assertEquals(0.02, AssessmentScoring.opponent(Grade.SPECIAL_GRADE, Grade.GRADE_4, S), EPS);
    }

    @Test
    void ninetyIsReachableWithoutAWinOrAStrongerOpponent() {
        // Lost on the damage trade (dealing only 2 for every 3 taken), against an equal, perfect otherwise.
        double score = AssessmentScoring.score(1.0, 1.0, AssessmentScoring.performance(20, 30, S),
                AssessmentScoring.opponent(Grade.GRADE_1, Grade.GRADE_1, S), S);
        assertTrue(score >= 90.0, "got " + score);
    }

    @Test
    void severedWeightsAreRescaled() {
        assertEquals(100.0, AssessmentScoring.severedScore(1, 1, 1, S), EPS);
        assertEquals(100.0 * 50 / 75, AssessmentScoring.severedScore(1, 0, 0, S), EPS);
        assertEquals(100.0 * 15 / 75, AssessmentScoring.severedScore(0, 1, 0, S), EPS);
        assertEquals(100.0 * 10 / 75, AssessmentScoring.severedScore(0, 0, 1, S), EPS);
    }

    @Test
    void severedCanReachNinetyOnMeleeVariety() {
        List<Fight.Use> moves = landings("sword", "sword_critical", "axe", "axe_sprint", "sword", "fist");
        assertEquals(1.0, AssessmentScoring.meleeVariety(moves, S), EPS);
        double score = AssessmentScoring.severedScore(1.0, AssessmentScoring.performance(20, 25, S),
                AssessmentScoring.opponent(Grade.GRADE_1, Grade.GRADE_1, S), S);
        assertTrue(score >= 90.0, "got " + score);
        assertTrue(AssessmentScoring.meleeVariety(landings("fist", "fist", "fist", "fist"), S) < 0.2, "one plain punch over and over");
    }

    // ---- requirements ----

    private static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());
    private static final UUID C = UUID.nameUUIDFromBytes("c".getBytes());
    private static final AssessmentRules.Requirement GRADE_2 = new AssessmentRules.Requirement(50, 5, 2, 2, 40);

    private static List<AssessmentRules.Entry> history(double score, UUID... opponents) {
        List<AssessmentRules.Entry> out = new ArrayList<>();
        for (UUID opponent : opponents) out.add(new AssessmentRules.Entry(score, score, opponent, 0));
        return out;
    }

    private static List<AssessmentRules.Entry> history(double score, double technique, UUID... opponents) {
        List<AssessmentRules.Entry> out = new ArrayList<>();
        for (UUID opponent : opponents) out.add(new AssessmentRules.Entry(score, technique, opponent, 0));
        return out;
    }

    @Test
    void averagesOverTheLastFights() {
        List<AssessmentRules.Entry> history = new ArrayList<>(history(0, A, A, A));
        history.addAll(history(60, A, B, A, B, A));
        AssessmentRules.Status status = AssessmentRules.evaluate(history, GRADE_2, 2, false);
        assertEquals(60.0, status.average(), EPS, "only the last five count");
        assertEquals(2, status.opponents());
        assertTrue(status.met());
    }

    @Test
    void oneFriendCantCarryIt() {
        assertFalse(AssessmentRules.evaluate(history(100, A, A, A, A, A), GRADE_2, 2, false).met(), "five perfect fights with one friend");
        assertTrue(AssessmentRules.evaluate(history(100, A, A, A, A, B), GRADE_2, 2, false).met());
        AssessmentRules.Requirement grade1 = new AssessmentRules.Requirement(70, 10, 3, 5, 60);
        assertFalse(AssessmentRules.evaluate(history(100, A, B, A, B, A, B, A, B, A, B), grade1, 5, false).met(), "two friends isn't three");
        assertTrue(AssessmentRules.evaluate(history(100, A, B, A, B, A, B, A, B, A, C), grade1, 5, false).met());
    }

    @Test
    void takesEnoughFightsAndTheAverage() {
        assertFalse(AssessmentRules.evaluate(history(100, A, B, A, B), GRADE_2, 2, false).met(), "only four fights");
        assertFalse(AssessmentRules.evaluate(history(49.9, A, B, A, B, A), GRADE_2, 2, false).met());
        assertTrue(AssessmentRules.evaluate(history(50, A, B, A, B, A), GRADE_2, 2, false).met(), "exactly the average");
    }

    /** The real fights this gate was set from: spam scored 45.6 with Technique use 5, a mix 52.3 with 43, varied play 72.4 with 83. */
    @Test
    void spamCantQualifyHoweverWellItTrades() {
        assertFalse(AssessmentRules.evaluate(history(60, 5, A, B, A, B, A), GRADE_2, 2, false).met(), "a high enough average, on one move");
        assertFalse(AssessmentRules.evaluate(history(100, 39.9, A, B, A, B, A), GRADE_2, 2, false).met(), "even a perfect average needs the Technique use");
        assertTrue(AssessmentRules.evaluate(history(52.3, 43, A, B, A, B, A), GRADE_2, 2, false).met());
        assertTrue(AssessmentRules.evaluate(history(72.4, 83, A, B, A, B, A), GRADE_2, 2, false).met(), "the varied fight passes Grade 2");
        assertTrue(AssessmentRules.evaluate(history(50, 40, A, B, A, B, A), GRADE_2, 2, false).met(), "exactly the minimum");
    }

    @Test
    void theTechniqueMinimumIsAnAverageOfTheSameFights() {
        List<AssessmentRules.Entry> history = new ArrayList<>(history(60, 0, A, A, A));
        history.addAll(history(60, 20, A, B));
        history.addAll(history(60, 60, A, B, A));
        AssessmentRules.Status status = AssessmentRules.evaluate(history, GRADE_2, 2, false);
        assertEquals(44.0, status.technique(), EPS, "only the last five count: an off fight or two doesn't sink it");
        assertTrue(status.met());
    }

    @Test
    void severedPlayersAreHeldToItToo() {
        assertFalse(AssessmentRules.evaluate(history(60, 5, A, B, A, B, A), GRADE_2, 0, true).met(), "the same hit, over and over");
        assertTrue(AssessmentRules.evaluate(history(60, 40, A, B, A, B, A), GRADE_2, 0, true).met());
    }

    @Test
    void peakRefinementMattersExceptForSevered() {
        List<AssessmentRules.Entry> history = history(100, A, B, A, B, A);
        assertFalse(AssessmentRules.evaluate(history, GRADE_2, 1, false).met());
        assertTrue(AssessmentRules.evaluate(history, GRADE_2, 2, false).met());
        assertTrue(AssessmentRules.evaluate(history, GRADE_2, 0, true).met(), "Severed players have no Refinement and skip it");
    }
}
