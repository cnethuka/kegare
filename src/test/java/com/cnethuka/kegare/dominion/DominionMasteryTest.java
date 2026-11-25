package com.cnethuka.kegare.dominion;

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
 * How long a Dominion holds before it breaks (DESIGN §3.35): its base duration scaled by its caster's mastery, their Grade and Refinement
 * (a full Dominion's technique's own, an Incomplete one's Peak), and its time running out breaking it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class DominionMasteryTest {
    private static final double EPS = 1.0e-9;

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    @Test
    void masteryIsGradeAndRefinementWeighedTogether(MinecraftServer server) { // the server: its config loaded
        assertEquals(0.0, DominionMastery.mastery(Grade.GRADE_4, 0), EPS, "none at Grade 4 with no Refinement");
        assertEquals(1.0, DominionMastery.mastery(Grade.SPECIAL_GRADE, RefinementService.maxLevel()), EPS, "all of it at the top of both");
        assertEquals(0.5, DominionMastery.mastery(Grade.SPECIAL_GRADE, 0), EPS, "Grade is half of it");
        assertEquals(0.5, DominionMastery.mastery(Grade.GRADE_4, RefinementService.maxLevel()), EPS, "Refinement the other half");
        assertEquals(1.0, DominionMastery.mastery(Grade.SPECIAL_GRADE, RefinementService.maxLevel() + 5), EPS, "never past the top");
        assertEquals(DominionConfig.MASTERY_LEAST_SHARE.get(), DominionMastery.share(0), EPS);
        assertEquals(DominionConfig.MASTERY_MOST_SHARE.get(), DominionMastery.share(1), EPS);
        assertTrue(DominionMastery.share(0.4) < DominionMastery.share(0.6), "the more of it, the longer");
    }

    @Test
    void anIncompleteDominionHoldsLongerForAHigherGradeAndPeakRefinement(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer novice = DominionTest.at(server, "mastery_novice", 8, 8);
                ServerPlayer learned = DominionTest.at(server, "mastery_learned", 80, 8);
                ServerPlayer master = DominionTest.at(server, "mastery_master", 160, 8);
                int base = ticks(DominionConfig.INCOMPLETE_DURATION.get());
                assertEquals(Math.round(base * DominionConfig.MASTERY_LEAST_SHARE.get()), IncompleteDominions.spec(novice).durationTicks(),
                        "Grade 4 with no Refinement: the least");

                GradeService.set(learned, Grade.SEMI_GRADE_2);
                RefinementService.setLevel(learned, 6);
                int atUnlock = IncompleteDominions.spec(learned).durationTicks();
                assertEquals(Math.round(base * DominionMastery.share(0.5 * 2 / 6.0 + 0.5 * 6 / (double) RefinementService.maxLevel())), atUnlock,
                        "where it's learned (Semi-Grade 2, Peak 6): about its base");

                GradeService.set(master, Grade.SPECIAL_GRADE);
                RefinementService.setLevel(master, RefinementService.maxLevel());
                assertEquals(Math.round(base * DominionConfig.MASTERY_MOST_SHARE.get()), IncompleteDominions.spec(master).durationTicks(),
                        "Special Grade at the top Refinement: the most");

                RefinementService.onTechniqueChanged(master); // a reroll: Refinement starts over, Peak doesn't
                assertEquals(Math.round(base * DominionConfig.MASTERY_MOST_SHARE.get()), IncompleteDominions.spec(master).durationTicks(),
                        "Peak Refinement: a reroll takes none of it away");

                DominionMeter.set(learned, 100);
                DominionEntity d = Dominions.open(learned, IncompleteDominions.spec(learned), CastIntent.CAST_RELEASE);
                assertNotNull(d, "setup: it opens");
                assertEquals(atUnlock, d.duration(), "the barrier holds that long");
            } finally {
                DominionTest.cleanUp(server);
            }
        });
    }

    @Test
    void aFullDominionRidesOnItsTechniquesOwnRefinement(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DominionTest.at(server, "mastery_full", 8, 8);
                int base = DominionTest.TEST.durationTicks(p);
                GradeService.set(p, Grade.SPECIAL_GRADE);
                RefinementService.setLevel(p, RefinementService.maxLevel());
                assertEquals(Math.round(base * DominionConfig.MASTERY_MOST_SHARE.get()), FullDominions.spec(DominionTest.TEST, p).durationTicks(),
                        "an addon's own duration, scaled the same way");
                RefinementService.onTechniqueChanged(p);
                assertEquals(Math.round(base * DominionMastery.share(0.5)), FullDominions.spec(DominionTest.TEST, p).durationTicks(),
                        "a new technique's Refinement starts over, and with it the half of mastery it makes up");
            } finally {
                DominionTest.cleanUp(server);
            }
        });
    }

    @Test
    void itBreaksWhenItsTimeIsUp() {
        assertEquals(DominionNet.Moment.BROKE, Dominions.momentFor(Dominions.EndReason.EXPIRED), "held as long as it could be: it breaks");
        assertEquals(DominionNet.Moment.BROKE, Dominions.momentFor(Dominions.EndReason.BROKEN));
        assertEquals(DominionNet.Moment.BROKE, Dominions.momentFor(Dominions.EndReason.CLASH_LOST));
        assertEquals(DominionNet.Moment.ENDED, Dominions.momentFor(Dominions.EndReason.COMMAND), "ended by command: it goes out quietly");
        assertEquals(DominionNet.Moment.ENDED, Dominions.momentFor(Dominions.EndReason.CASTER_GONE));
    }
}
