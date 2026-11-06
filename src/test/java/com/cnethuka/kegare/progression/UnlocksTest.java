package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The unlock framework, on entries that only exist here. Nothing real is registered yet. */
@ExtendWith(EphemeralTestServerProvider.class)
class UnlocksTest {
    private static final double RATE = 0.1;
    private final List<ResourceLocation> registered = new ArrayList<>();

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            registered.forEach(Unlocks::unregister);
            registered.clear();
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private ResourceLocation entry(String name, UnlockRequirements requirements) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Kegare.MODID, "test/" + name);
        Unlocks.register(id, requirements);
        registered.add(id);
        return id;
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        return p;
    }

    private static int effective(UnlockRequirements r, int insight) {
        return r.effectiveRefinement(c -> insight, RATE);
    }

    @Test
    void matchingInsightLowersTheRefinementNeeded() {
        UnlockRequirements lowered = UnlockRequirements.builder().refinement(8).loweredBy(InsightCategory.DOMINION).build();
        assertEquals(8, effective(lowered, 0));
        assertEquals(8, effective(lowered, 9), "it rounds down");
        assertEquals(7, effective(lowered, 10));
        assertEquals(5, effective(lowered, 30));
        assertEquals(0, effective(lowered, 500), "never below 0");

        UnlockRequirements unrelated = UnlockRequirements.builder().refinement(8).build();
        assertEquals(8, effective(unrelated, 500), "an unlock nothing matches isn't lowered");

        // Only the matching category counts.
        assertEquals(8, lowered.effectiveRefinement(c -> c == InsightCategory.WARD ? 500 : 0, RATE));
    }

    @Test
    void everythingAskedForHasToBeMet() {
        UnlockRequirements r = UnlockRequirements.builder().grade(Grade.GRADE_2).refinement(3).insight(InsightCategory.WARD, 5).build();
        assertTrue(r.met(Grade.GRADE_2, 3, c -> 5, RATE));
        assertTrue(r.met(Grade.SPECIAL_GRADE, 10, c -> 50, RATE));
        assertFalse(r.met(Grade.SEMI_GRADE_2, 3, c -> 5, RATE), "Grade");
        assertFalse(r.met(Grade.GRADE_2, 2, c -> 5, RATE), "Refinement");
        assertFalse(r.met(Grade.GRADE_2, 3, c -> 4, RATE), "Insight");
        assertFalse(r.met(Grade.GRADE_2, 3, c -> c == InsightCategory.OUTPUT ? 5 : 0, RATE), "Insight of the wrong kind");
        assertEquals(Map.of(InsightCategory.WARD, 5), r.insight());
    }

    @Test
    void nothingRealIsRegistered() {
        assertTrue(Unlocks.registered().isEmpty(), "Maximum Output, Cradle, Threshold and the rest plug in when they're built");
    }

    @Test
    void registeringTwiceIsABug(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation id = entry("twice", UnlockRequirements.builder().build());
            assertThrows(IllegalArgumentException.class, () -> Unlocks.register(id, UnlockRequirements.builder().build()));
        });
    }

    @Test
    void itReevaluatesWhenAnyOfTheThreeChanges(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation byGrade = entry("by_grade", UnlockRequirements.builder().grade(Grade.GRADE_3).build());
            ResourceLocation byRefinement = entry("by_refinement", UnlockRequirements.builder().refinement(3).build());
            ResourceLocation byInsight = entry("by_insight", UnlockRequirements.builder().insight(InsightCategory.OUTPUT, 5).build());
            ServerPlayer p = sorcerer(server, "reevaluates");
            assertFalse(Unlocks.has(p, byGrade));
            assertFalse(Unlocks.has(p, byRefinement));
            assertFalse(Unlocks.has(p, byInsight));

            GradeService.addXp(p, 200);
            assertEquals(Grade.GRADE_3, Progression.getGrade(p));
            assertTrue(Unlocks.has(p, byGrade), "a promotion earned by XP");
            RefinementService.addXp(p, 295);
            assertEquals(3, RefinementService.level(p));
            assertTrue(Unlocks.has(p, byRefinement), "a level earned by XP");
            assertTrue(Insight.recordFirst(p, "unlocks_test", InsightCategory.OUTPUT, 5));
            assertTrue(Unlocks.has(p, byInsight), "a first");
        });
    }

    @Test
    void insightCanBeWhatFinishesARefinementUnlock(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation id = entry("lowered", UnlockRequirements.builder().refinement(4).loweredBy(InsightCategory.DOMINION).build());
            ServerPlayer p = sorcerer(server, "lowered");
            RefinementService.setLevel(p, 2);
            assertFalse(Unlocks.has(p, id));
            Insight.add(p, InsightCategory.DOMINION, 19);
            assertFalse(Unlocks.has(p, id), "19 Insight is 1 level");
            Insight.add(p, InsightCategory.DOMINION, 1);
            assertTrue(Unlocks.has(p, id), "20 Insight is 2 levels");
        });
    }

    @Test
    void aRerollTakesOnlyWhatsTiedToTheTechnique(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation tied = entry("tied", UnlockRequirements.builder().refinement(3).tiedToTechnique().build());
            ResourceLocation stays = entry("stays", UnlockRequirements.builder().refinement(3).build());
            ResourceLocation grade = entry("grade", UnlockRequirements.builder().grade(Grade.GRADE_3).build());
            ResourceLocation insight = entry("insight", UnlockRequirements.builder().insight(InsightCategory.WARD, 1).build());
            ServerPlayer p = sorcerer(server, "reroll_unlocks");
            RefinementService.setLevel(p, 3);
            GradeService.set(p, Grade.GRADE_3);
            Insight.add(p, InsightCategory.WARD, 1);
            assertTrue(Unlocks.has(p, tied));

            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
            assertFalse(Unlocks.has(p, tied), "per technique: lost");
            assertTrue(Unlocks.has(p, stays), "the entry isn't flagged");
            assertTrue(Unlocks.has(p, grade), "Grade unlocks stay");
            assertTrue(Unlocks.has(p, insight), "Insight unlocks stay");

            RefinementService.setLevel(p, 3);
            assertTrue(Unlocks.has(p, tied), "earned again on the new technique");
        });
    }

    @Test
    void loweringRefinementTakesTiedUnlocks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation tied = entry("tied_lower", UnlockRequirements.builder().refinement(3).tiedToTechnique().build());
            ResourceLocation stays = entry("stays_lower", UnlockRequirements.builder().refinement(3).build());
            ServerPlayer p = sorcerer(server, "lower_unlocks");
            RefinementService.setLevel(p, 5);
            RefinementService.setLevel(p, 1);
            assertFalse(Unlocks.has(p, tied));
            assertTrue(Unlocks.has(p, stays));
        });
    }

    @Test
    void listenersHearEachUnlockOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ResourceLocation id = entry("heard", UnlockRequirements.builder().refinement(1).build());
            List<ResourceLocation> heard = new ArrayList<>();
            Unlocks.addListener((player, unlocked) -> {
                if (unlocked.equals(id)) heard.add(unlocked);
            });
            ServerPlayer p = sorcerer(server, "heard");
            RefinementService.setLevel(p, 1);
            RefinementService.setLevel(p, 2);
            Unlocks.evaluate(p);
            assertEquals(List.of(id), heard);
        });
    }
}
