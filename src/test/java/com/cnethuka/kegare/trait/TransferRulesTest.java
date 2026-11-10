package com.cnethuka.kegare.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Who may inherit six_eyes, and the inactivity rule. */
class TransferRulesTest {
    private static final UUID BEARER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KILLER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SEVERED = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static TransferRules.Candidate c(UUID id) {
        return new TransferRules.Candidate(id, false);
    }

    private static TransferRules.Candidate severed(UUID id) {
        return new TransferRules.Candidate(id, true);
    }

    @Test
    void previousBearerAndSeveredAreNeverEligible() {
        List<TransferRules.Candidate> online = List.of(c(BEARER), c(KILLER), c(ALICE), severed(SEVERED));
        assertEquals(List.of(KILLER, ALICE), TransferRules.eligibleHeirs(online, BEARER));
        assertFalse(TransferRules.isEligibleHeir(c(BEARER), BEARER));
        assertFalse(TransferRules.isEligibleHeir(severed(SEVERED), BEARER));
        assertTrue(TransferRules.isEligibleHeir(c(ALICE), BEARER));
    }

    @Test
    void randomModeOnlyEverPicksEligiblePlayers() {
        List<TransferRules.Candidate> online = List.of(c(BEARER), c(KILLER), c(ALICE), severed(SEVERED));
        Set<UUID> picked = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            final int index = i;
            picked.add(TransferRules.pickHeir(TransferMode.RANDOM, online, BEARER, KILLER, bound -> index).orElseThrow());
        }
        assertEquals(Set.of(KILLER, ALICE), picked);
    }

    @Test
    void killerModePrefersAnEligibleKiller() {
        List<TransferRules.Candidate> online = List.of(c(BEARER), c(ALICE), c(KILLER));
        // random would pick index 0 (ALICE), but KILLER mode wins
        assertEquals(Optional.of(KILLER), TransferRules.pickHeir(TransferMode.KILLER, online, BEARER, KILLER, bound -> 0));
    }

    @Test
    void killerModeFallsBackToRandomWhenKillerIneligibleOrAbsent() {
        // Severed killer
        List<TransferRules.Candidate> severedKiller = List.of(c(BEARER), c(ALICE), severed(KILLER));
        assertEquals(Optional.of(ALICE), TransferRules.pickHeir(TransferMode.KILLER, severedKiller, BEARER, KILLER, bound -> 0));
        // Killer offline (not in the online list)
        List<TransferRules.Candidate> killerOffline = List.of(c(BEARER), c(ALICE));
        assertEquals(Optional.of(ALICE), TransferRules.pickHeir(TransferMode.KILLER, killerOffline, BEARER, KILLER, bound -> 0));
        // No killer at all (release / inactivity)
        assertEquals(Optional.of(ALICE), TransferRules.pickHeir(TransferMode.KILLER, killerOffline, BEARER, null, bound -> 0));
    }

    @Test
    void nobodyEligibleMeansItStaysUnclaimed() {
        List<TransferRules.Candidate> online = List.of(c(BEARER), severed(SEVERED));
        assertTrue(TransferRules.pickHeir(TransferMode.RANDOM, online, BEARER, null, bound -> 0).isEmpty());
        assertTrue(TransferRules.pickHeir(TransferMode.KILLER, List.of(), BEARER, KILLER, bound -> 0).isEmpty());
    }

    @Test
    void inactivityRuleIsOffAtZeroAndInclusiveAtTheLimit() {
        long day = 86_400_000L;
        assertFalse(TransferRules.isInactive(0, 365 * day, 0));
        assertFalse(TransferRules.isInactive(0, 7 * day - 1, 7));
        assertTrue(TransferRules.isInactive(0, 7 * day, 7));
        assertTrue(TransferRules.isInactive(0, day / 2, 0.5)); // fractional days allowed
    }
}
