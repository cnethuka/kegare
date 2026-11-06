package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The fight bookkeeping and the win rule, with no game running. */
class FightTest {
    private static final UUID A = UUID.nameUUIDFromBytes("a".getBytes());
    private static final UUID B = UUID.nameUUIDFromBytes("b".getBytes());

    @Test
    void sidesAddUpAndTheLengthStopsAtTheLastHit() {
        Fight fight = new Fight(A, B, 100);
        fight.damage(A, 5, 120);
        fight.damage(B, 3, 140);
        fight.damage(A, 2, 160);
        assertEquals(7, fight.side(A).dealt());
        assertEquals(3, fight.side(B).dealt());
        assertSame(fight.side(B), fight.opponentOf(A));
        assertEquals(60, fight.lengthTicks(), "the quiet time after the last hit isn't part of the fight");
    }

    @Test
    void expiresOnlyAfterTheQuietTime() {
        Fight fight = new Fight(A, B, 0);
        fight.damage(A, 1, 100);
        assertFalse(fight.expired(699, 600));
        assertTrue(fight.expired(700, 600));
        fight.damage(B, 1, 690);
        assertFalse(fight.expired(700, 600), "any damage between them keeps it going");
    }

    @Test
    void aWinTakesAtLeastTheShare() {
        assertEquals(FightOutcome.WIN, Fight.outcomeOnOpponentDeath(25, 100, 0.25), "exactly the share counts");
        assertEquals(FightOutcome.SURVIVED, Fight.outcomeOnOpponentDeath(24.9, 100, 0.25));
        assertEquals(FightOutcome.WIN, Fight.outcomeOnOpponentDeath(1, 1, 0.25), "all the damage they took from players");
    }

    @Test
    void dealingNothingIsNeverAWin() {
        assertEquals(FightOutcome.SURVIVED, Fight.outcomeOnOpponentDeath(0, 0, 0.25));
        assertEquals(FightOutcome.SURVIVED, Fight.outcomeOnOpponentDeath(0, 50, 0.0));
    }
}
