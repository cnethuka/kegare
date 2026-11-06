package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Firsts pay once, and everything the spec wires up today pays where config says. */
@ExtendWith(EphemeralTestServerProvider.class)
class InsightTest {
    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name, InnateTechnique technique) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, technique);
        return p;
    }

    @Test
    void aFirstPaysOnceEver(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "pays_once", Rime.RIME.get());
            assertTrue(Insight.recordFirst(p, "some_first", InsightCategory.WARD, 4));
            assertFalse(Insight.recordFirst(p, "some_first", InsightCategory.WARD, 4));
            assertFalse(Insight.recordFirst(p, "some_first", InsightCategory.OUTPUT, 9), "the key pays once, whatever it asks for");
            assertEquals(4, Insight.get(p, InsightCategory.WARD));
            assertEquals(0, Insight.get(p, InsightCategory.OUTPUT));
            assertTrue(Insight.recordFirst(p, "another_first", InsightCategory.WARD, 1));
            assertEquals(5, Insight.get(p, InsightCategory.WARD));
        });
    }

    @Test
    void eachTechniqueThatHitsYouPaysOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer victim = sorcerer(server, "hit_victim", Rime.RIME.get());
            ServerPlayer rime = sorcerer(server, "hit_rime", Rime.RIME.get());
            ServerPlayer swap = sorcerer(server, "hit_swap", Swap.SWAP.get());
            Landings.land(rime, victim, Rime.FROST_SHARD);
            assertEquals(1, Insight.get(victim, InsightCategory.WARD), "first time hit by Rime");
            TestServer.advanceGameTime(server, 40);
            Landings.land(rime, victim, Rime.GLACIAL_LANCE);
            assertEquals(1, Insight.get(victim, InsightCategory.WARD), "Rime again pays nothing");
            Landings.land(swap, victim, Swap.POSITIONS);
            assertEquals(2, Insight.get(victim, InsightCategory.WARD), "Swap is a new technique");
            assertEquals(0, Insight.get(rime, InsightCategory.WARD), "hitting pays nothing");
        });
    }

    @Test
    void theFirstFightAgainstALegendaryHolderPaysOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            InnateTechnique legendary = KegareRegistries.INNATE_TECHNIQUE.stream()
                    .filter(t -> t.rarity() == InnateTechniqueRarity.LEGENDARY).findFirst().orElseThrow();
            ServerPlayer p = sorcerer(server, "fights_legend", Rime.RIME.get());
            ServerPlayer holder = sorcerer(server, "the_legend", legendary);
            ServerPlayer plain = sorcerer(server, "not_legend", Rime.RIME.get());

            FightTracker.damage(p, plain, 1f);
            assertEquals(0, Insight.get(p, InsightCategory.OUTPUT), "not Legendary");
            FightTracker.damage(p, holder, 1f);
            assertEquals(5, Insight.get(p, InsightCategory.OUTPUT));

            FightTracker.clear();
            FightTracker.damage(p, holder, 1f);
            assertEquals(5, Insight.get(p, InsightCategory.OUTPUT), "once ever");
        });
    }

    @Test
    void theHooksForLaterSystemsPayOnceEachIntoTheirConfiguredCategory(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "hooks", Rime.RIME.get());
            InnateTechnique rime = Rime.RIME.get();
            assertTrue(InsightHooks.survivedStandstill(p));
            assertFalse(InsightHooks.survivedStandstill(p));
            assertEquals(5, Insight.get(p, InsightCategory.WARD));
            assertTrue(InsightHooks.hitByMaximumOutput(p, rime));
            assertFalse(InsightHooks.hitByMaximumOutput(p, rime));
            assertEquals(5, Insight.get(p, InsightCategory.OUTPUT));
            assertTrue(InsightHooks.caughtInDominion(p, rime));
            assertTrue(InsightHooks.clashedWithDominion(p, rime));
            assertTrue(InsightHooks.bookChapter(p, "one"));
            assertTrue(InsightHooks.bookChapter(p, "two"));
            assertFalse(InsightHooks.bookChapter(p, "one"));
            assertEquals(5 + 5 + 2 + 2, Insight.get(p, InsightCategory.DOMINION));
        });
    }

    @Test
    void whichCategoryASourcePaysIsConfig(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "hooks_config", Rime.RIME.get());
            ProgressionConfig.INSIGHT_CATEGORY.get(InsightSource.SURVIVED_STANDSTILL).set(InsightCategory.DOMINION);
            try {
                InsightHooks.survivedStandstill(p);
                assertEquals(5, Insight.get(p, InsightCategory.DOMINION));
                assertEquals(0, Insight.get(p, InsightCategory.WARD));
            } finally {
                ProgressionConfig.INSIGHT_CATEGORY.get(InsightSource.SURVIVED_STANDSTILL).set(InsightCategory.WARD);
            }
        });
    }

    @Test
    void itSurvivesARerollAndDoesntTouchTechniqueUnlockLevels(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = sorcerer(server, "keeps_insight", Rime.RIME.get());
            Insight.add(p, InsightCategory.OUTPUT, 500);
            int before = TechniqueUnlocks.requiredLevel(p, Rime.SHATTER_SHELL);
            assertEquals(7, before, "Insight does not change extension technique unlock levels");
            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
            assertEquals(500, Insight.get(p, InsightCategory.OUTPUT));
        });
    }
}
