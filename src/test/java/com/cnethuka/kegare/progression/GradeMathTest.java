package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ladder and the XP rules, with no game running. */
class GradeMathTest {
    private static final double EPS = 1.0e-9;
    private static final GradeMath.Settings S = new GradeMath.Settings(2.0, 40.0, 15.0, 20.0, 0.6, 0.05, 0.35, 0.02, 0.5, 3.0, 1.0);

    @Test
    void theLadderRunsInOrderAndEndsAtSpecialGrade() {
        assertEquals(List.of(Grade.GRADE_4, Grade.GRADE_3, Grade.SEMI_GRADE_2, Grade.GRADE_2, Grade.SEMI_GRADE_1, Grade.GRADE_1,
                Grade.SPECIAL_GRADE), List.of(Grade.values()));
        Grade grade = Grade.GRADE_4;
        int steps = 0;
        while (grade.next().isPresent()) {
            grade = grade.next().get();
            steps++;
        }
        assertEquals(6, steps);
        assertEquals(Grade.SPECIAL_GRADE, grade);
        assertEquals(Grade.GRADE_2, Grade.byId("grade_2").orElseThrow());
    }

    @Test
    void whatEachPromotionTakes() {
        assertTrue(Grade.GRADE_3.takesXp() && !Grade.GRADE_3.takesAssessment());
        assertTrue(Grade.SEMI_GRADE_2.takesXp() && !Grade.SEMI_GRADE_2.takesAssessment());
        assertTrue(Grade.GRADE_2.takesXp() && Grade.GRADE_2.takesAssessment());
        assertTrue(Grade.SEMI_GRADE_1.takesXp() && !Grade.SEMI_GRADE_1.takesAssessment());
        assertTrue(Grade.GRADE_1.takesXp() && Grade.GRADE_1.takesAssessment());
        assertTrue(!Grade.SPECIAL_GRADE.takesXp() && Grade.SPECIAL_GRADE.takesAssessment(), "Assessment only, never XP alone");
    }

    @Test
    void diminishingReturnsFallPerEarlierFightAndStopAtTheFloor() {
        assertEquals(1.0, GradeMath.diminishing(0, 0.6, 0.05), EPS);
        assertEquals(0.6, GradeMath.diminishing(1, 0.6, 0.05), EPS);
        assertEquals(0.36, GradeMath.diminishing(2, 0.6, 0.05), EPS);
        assertEquals(0.05, GradeMath.diminishing(50, 0.6, 0.05), EPS);
        for (int n = 0; n < 5; n++) {
            assertTrue(GradeMath.diminishing(n + 1, 0.6, 0.05) < GradeMath.diminishing(n, 0.6, 0.05), "each fight gives less");
        }
    }

    @Test
    void theLedgerForgetsWhatLeavesTheWindow() {
        UUID id = UUID.nameUUIDFromBytes("x".getBytes());
        OpponentLedger ledger = new OpponentLedger();
        assertEquals(0, ledger.count(id, 0, 100));
        ledger.record(id, 0, 100);
        ledger.record(id, 50, 100);
        assertEquals(2, ledger.count(id, 99, 100));
        assertEquals(1, ledger.count(id, 100, 100), "the first has left the rolling window");
        assertEquals(0, ledger.count(id, 151, 100));
    }

    @Test
    void aFarWeakerOpponentIsWorthAlmostNothing() {
        assertEquals(1.0, GradeMath.weakerOpponent(0, 0.35, 0.02), EPS);
        assertEquals(0.65, GradeMath.weakerOpponent(1, 0.35, 0.02), EPS);
        assertEquals(0.30, GradeMath.weakerOpponent(2, 0.35, 0.02), EPS);
        assertEquals(0.02, GradeMath.weakerOpponent(6, 0.35, 0.02), EPS);
        double even = GradeMath.fightXp(S, FightOutcome.WIN, 20, 0, 0, 0);
        double far = GradeMath.fightXp(S, FightOutcome.WIN, 20, 0, 0, -6);
        assertTrue(far < even * 0.03, "beating someone far below you: almost nothing");
    }

    @Test
    void beatingSomeoneAboveYouPaysABonus() {
        assertEquals(40.0, GradeMath.fightXp(S, FightOutcome.WIN, 0, 0, 0, 0), EPS);
        assertEquals(60.0, GradeMath.fightXp(S, FightOutcome.WIN, 0, 0, 0, 1), EPS, "one Grade above");
        assertEquals(120.0, GradeMath.fightXp(S, FightOutcome.WIN, 0, 0, 0, 6), EPS, "capped");
        assertEquals(15.0, GradeMath.fightXp(S, FightOutcome.LOSS, 0, 0, 0, 3), EPS, "only a win gets the bonus");
    }

    @Test
    void losingStillGivesXpAndLeavingGivesOnlyTheDamage() {
        assertEquals(15.0 + 10.0, GradeMath.fightXp(S, FightOutcome.LOSS, 5, 20, 0, 0), EPS);
        assertEquals(10.0, GradeMath.fightXp(S, FightOutcome.LEFT, 5, 20, 0, 0), EPS);
    }

    @Test
    void surviveTakesARealExchange() {
        assertEquals(20.0 + 2.0, GradeMath.fightXp(S, FightOutcome.SURVIVED, 1, 1, 0, 0), EPS);
        assertEquals(2.0, GradeMath.fightXp(S, FightOutcome.SURVIVED, 1, 0, 0, 0), EPS, "took nothing: nothing survived");
        assertEquals(0.0, GradeMath.fightXp(S, FightOutcome.SURVIVED, 0, 1, 0, 0), EPS, "dealt nothing");
    }

    @Test
    void repeatFightsShrinkEverything() {
        assertEquals(0.6 * (40.0 + 2.0), GradeMath.fightXp(S, FightOutcome.WIN, 1, 0, 1, 0), EPS);
        assertFalse(GradeMath.fightXp(S, FightOutcome.WIN, 20, 0, 3, 0) >= GradeMath.fightXp(S, FightOutcome.WIN, 20, 0, 2, 0));
    }
}
