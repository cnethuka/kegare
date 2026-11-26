package com.cnethuka.kegare.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.RefinementService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * How long a Domain holds before it breaks (DESIGN §3.35): its base duration scaled by its caster's mastery, their Grade and Refinement
 * (a Domain Expansion's technique's own, an Incomplete one's Peak), and its time running out breaking it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class DomainMasteryTest {
    private static final double EPS = 1.0e-9;

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    @Test
    void masteryIsGradeAndRefinementWeighedTogether(MinecraftServer server) { // the server: its config loaded
        assertEquals(0.0, DomainMastery.mastery(Grade.GRADE_4, 0), EPS, "none at Grade 4 with no Refinement");
        assertEquals(1.0, DomainMastery.mastery(Grade.SPECIAL_GRADE, RefinementService.maxLevel()), EPS, "all of it at the top of both");
        assertEquals(0.5, DomainMastery.mastery(Grade.SPECIAL_GRADE, 0), EPS, "Grade is half of it");
        assertEquals(0.5, DomainMastery.mastery(Grade.GRADE_4, RefinementService.maxLevel()), EPS, "Refinement the other half");
        assertEquals(1.0, DomainMastery.mastery(Grade.SPECIAL_GRADE, RefinementService.maxLevel() + 5), EPS, "never past the top");
        assertEquals(DomainConfig.MASTERY_LEAST_SHARE.get(), DomainMastery.share(0), EPS);
        assertEquals(DomainConfig.MASTERY_MOST_SHARE.get(), DomainMastery.share(1), EPS);
        assertTrue(DomainMastery.share(0.4) < DomainMastery.share(0.6), "the more of it, the longer");
    }

    @Test
    void anIncompleteDomainHoldsLongerForAHigherGradeAndPeakRefinement(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer novice = DomainTest.at(server, "mastery_novice", 8, 8);
                ServerPlayer learned = DomainTest.at(server, "mastery_learned", 80, 8);
                ServerPlayer master = DomainTest.at(server, "mastery_master", 160, 8);
                int base = ticks(DomainConfig.INCOMPLETE_DURATION.get());
                assertEquals(Math.round(base * DomainConfig.MASTERY_LEAST_SHARE.get()), IncompleteDomains.spec(novice).durationTicks(),
                        "Grade 4 with no Refinement: the least");

                GradeService.set(learned, Grade.SEMI_GRADE_2);
                RefinementService.setLevel(learned, 6);
                int atUnlock = IncompleteDomains.spec(learned).durationTicks();
                assertEquals(Math.round(base * DomainMastery.share(0.5 * 2 / 6.0 + 0.5 * 6 / (double) RefinementService.maxLevel())), atUnlock,
                        "where it's learned (Semi-Grade 2, Peak 6): about its base");

                GradeService.set(master, Grade.SPECIAL_GRADE);
                RefinementService.setLevel(master, RefinementService.maxLevel());
                assertEquals(Math.round(base * DomainConfig.MASTERY_MOST_SHARE.get()), IncompleteDomains.spec(master).durationTicks(),
                        "Special Grade at the top Refinement: the most");

                RefinementService.onTechniqueChanged(master); // a reroll: Refinement starts over, Peak doesn't
                assertEquals(Math.round(base * DomainConfig.MASTERY_MOST_SHARE.get()), IncompleteDomains.spec(master).durationTicks(),
                        "Peak Refinement: a reroll takes none of it away");

                DomainMeter.set(learned, 100);
                DomainEntity d = Domains.open(learned, IncompleteDomains.spec(learned), CastIntent.CAST_RELEASE);
                assertNotNull(d, "setup: it opens");
                assertEquals(atUnlock, d.duration(), "the barrier holds that long");
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void aFullDomainRidesOnItsTechniquesOwnRefinement(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "mastery_full", 8, 8);
                int base = DomainTest.TEST.durationTicks(p);
                GradeService.set(p, Grade.SPECIAL_GRADE);
                RefinementService.setLevel(p, RefinementService.maxLevel());
                assertEquals(Math.round(base * DomainConfig.MASTERY_MOST_SHARE.get()), DomainExpansions.spec(DomainTest.TEST, p).durationTicks(),
                        "an addon's own duration, scaled the same way");
                RefinementService.onTechniqueChanged(p);
                assertEquals(Math.round(base * DomainMastery.share(0.5)), DomainExpansions.spec(DomainTest.TEST, p).durationTicks(),
                        "a new technique's Refinement starts over, and with it the half of mastery it makes up");
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void itBreaksWhenItsTimeIsUp() {
        assertEquals(DomainNet.Moment.BROKE, Domains.momentFor(Domains.EndReason.EXPIRED), "held as long as it could be: it breaks");
        assertEquals(DomainNet.Moment.BROKE, Domains.momentFor(Domains.EndReason.BROKEN));
        assertEquals(DomainNet.Moment.BROKE, Domains.momentFor(Domains.EndReason.CLASH_LOST));
        assertEquals(DomainNet.Moment.ENDED, Domains.momentFor(Domains.EndReason.COMMAND), "ended by command: it goes out quietly");
        assertEquals(DomainNet.Moment.ENDED, Domains.momentFor(Domains.EndReason.CASTER_GONE));
    }
}
