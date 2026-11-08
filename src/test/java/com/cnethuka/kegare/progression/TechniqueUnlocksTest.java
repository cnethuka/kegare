package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastResult;
import com.cnethuka.kegare.innatetechnique.CastSlot;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Extension technique unlocks, with the requirement on as it is by default. */
@ExtendWith(EphemeralTestServerProvider.class)
class TechniqueUnlocksTest {
    private static final double EPS = 1.0e-6;

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ProgressionConfig.REQUIRE_REFINEMENT.set(false);
            ProgressionConfig.UNLOCK_OVERRIDES.set(List.of());
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer fresh(MinecraftServer server, String name, InnateTechnique technique) {
        TestServer.ensureOverworld(server);
        ProgressionConfig.REQUIRE_REFINEMENT.set(true);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, technique);
        MaliceService.refill(p);
        return p;
    }

    private static List<ResourceLocation> bar(ServerPlayer p) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Optional<ResourceLocation> slot : LoadoutService.slots(p)) slot.ifPresent(out::add);
        return out;
    }

    @Test
    void theDefaultTableMatchesTheSpec() {
        assertEquals(List.of(0, 0, 2, 4, 6), List.of(UnlockTable.ability(0), UnlockTable.ability(1), UnlockTable.ability(2), UnlockTable.ability(3), UnlockTable.ability(4)));
        assertEquals(List.of(1, 3, 5, 7, 9), List.of(UnlockTable.special(0), UnlockTable.special(1), UnlockTable.special(2), UnlockTable.special(3), UnlockTable.special(4)));
        assertEquals(8, UnlockTable.ULTIMATE);
    }

    @Test
    void everyRealTechniqueUnlocksAsSpecified(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            InnateTechnique rime = Rime.RIME.get();
            assertEquals(0, rime.unlockLevel(Rime.FROST_SHARD));
            assertEquals(0, rime.unlockLevel(Rime.GLACIAL_LANCE));
            assertEquals(1, rime.unlockLevel(Rime.SPLINTER));
            assertEquals(2, rime.unlockLevel(Rime.FROST_FIELD));
            assertEquals(3, rime.unlockLevel(Rime.GLACIAL_SPIRE));
            assertEquals(4, rime.unlockLevel(Rime.RIME_SHELL));
            assertEquals(5, rime.unlockLevel(Rime.ICE_PATH));
            assertEquals(7, rime.unlockLevel(Rime.SHATTER_SHELL));
            assertTrue(rime.ultimateLocked(), "stays locked until Dominions exist");

            InnateTechnique swap = Swap.SWAP.get();
            assertEquals(0, swap.unlockLevel(Swap.POSITIONS));
            assertEquals(0, swap.unlockLevel(Swap.AWARENESS));
            assertEquals(1, swap.unlockLevel(Swap.SWITCHBACK));
            assertEquals(2, swap.unlockLevel(Swap.ANCHOR));
            assertEquals(3, swap.unlockLevel(Swap.MIMIC));
            assertEquals(4, swap.unlockLevel(Swap.EXCHANGE));
            assertEquals(5, swap.unlockLevel(Swap.SEND_OFF));
            assertEquals(7, swap.unlockLevel(Swap.MOMENTUM_EXCHANGE));
            assertEquals(8, swap.unlockLevel(Swap.TOTAL_SWAP));

            InnateTechnique puppets = PuppetManipulation.PUPPET_MANIPULATION.get();
            assertEquals(0, puppets.unlockLevel(PuppetManipulation.PUPPET_CREATION));
            assertEquals(0, puppets.unlockLevel(PuppetManipulation.DIRECTIVE));
            assertEquals(1, puppets.unlockLevel(PuppetManipulation.POSSESS));
            assertEquals(2, puppets.unlockLevel(PuppetManipulation.CURIO_BURST));
            assertEquals(3, puppets.unlockLevel(PuppetManipulation.FOCUS));
            assertEquals(4, puppets.unlockLevel(PuppetManipulation.UNDERSTUDY));
            assertEquals(5, puppets.unlockLevel(PuppetManipulation.CHAIN_BURST));
            assertEquals(6, puppets.unlockLevel(PuppetManipulation.STRINGS), "Reel In and Tie come with it");
            assertEquals(7, puppets.unlockLevel(PuppetManipulation.STAND_IN));
            assertEquals(9, puppets.unlockLevel(PuppetManipulation.ZIP_LEASH), "Zip and Leash");
            assertTrue(puppets.ultimateLocked());

            InnateTechnique time = TimeManipulation.TIME_MANIPULATION.get();
            assertEquals(0, time.unlockLevel(TimeManipulation.POINT_MAKER));
            assertEquals(0, time.unlockLevel(TimeManipulation.ACCELERATE));
            assertEquals(1, time.unlockLevel(TimeManipulation.REWIND));
            assertEquals(2, time.unlockLevel(TimeManipulation.DECELERATE));
            assertEquals(3, time.unlockLevel(TimeManipulation.FAST_FORWARD));
            assertEquals(5, time.unlockLevel(TimeManipulation.STASIS_FIELD));
            assertEquals(8, time.unlockLevel(TimeManipulation.STANDSTILL), "Standstill: the ultimate's level, like Total Swap");
            assertFalse(time.ultimateLocked());

            assertEquals(0, TestInnateTechniques.APEX.get().unlockLevel(TestInnateTechniques.APEX.get().abilities().get(3)), "test techniques: everything");
            assertFalse(InnateTechnique.builder(InnateTechniqueRarity.COMMON).ability(Rime.FROST_SHARD).build().hasUnlockLevels(), "what datagen refuses");
            assertTrue(InnateTechnique.builder(InnateTechniqueRarity.COMMON).ability(Rime.FROST_SHARD).testContent().build().hasUnlockLevels());
        });
    }

    @Test
    void aFreshPlayerHasTheLevelZeroKitAndUnlocksTheRestInOrder(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_rime", Rime.RIME.get());
            assertEquals(List.of(Rime.FROST_SHARD.id(), Rime.GLACIAL_LANCE.id()), bar(p));
            RefinementService.setLevel(p, 1);
            assertEquals(2, bar(p).size(), "level 1 is a special, which has no slot of its own");
            assertTrue(TechniqueUnlocks.isUnlocked(p, Rime.SPLINTER));
            RefinementService.setLevel(p, 2);
            assertEquals(List.of(Rime.FROST_SHARD.id(), Rime.GLACIAL_LANCE.id(), Rime.FROST_FIELD.id()), bar(p), "ability 3 goes into the first empty slot");
            RefinementService.setLevel(p, 4);
            assertEquals(4, bar(p).size());
            assertTrue(TechniqueUnlocks.isUnlocked(p, Rime.GLACIAL_SPIRE), "skipping levels unlocks what they skipped");
        });
    }

    @Test
    void anUnlockGoesToTheFirstEmptySlotAndWaitsIfThereIsNone(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_slots", Swap.SWAP.get());
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.CLEAR, 0, 0, Optional.empty()));
            RefinementService.setLevel(p, 2);
            assertEquals(Optional.of(Swap.ANCHOR.id()), LoadoutService.slots(p).get(0), "slot 1 was the first empty one");
        });
    }

    @Test
    void lockedCastsAreFreeAndSayWhatTheyNeed(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "locked_cast", Rime.RIME.get());
            InnateTechniqueService.select(p, 0); // Frost Shard, unlocked; its special isn't
            double malice = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(malice, MaliceService.get(p).current(), EPS, "no cost");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD), "no cooldown");
            assertNull(CastManager.state(p).casting());
            assertEquals(CastResult.REFINEMENT_LOCKED, CastManager.validate(p,
                    new AbilityContext(p, Rime.RIME.get(), Rime.SPLINTER, CastSlot.SPECIAL, 1f, 0)));
            assertEquals(1, TechniqueUnlocks.requiredLevel(p, Rime.SPLINTER));

            RefinementService.setLevel(p, 1);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertTrue(MaliceService.get(p).current() < malice, "unlocked: it casts");
        });
    }

    @Test
    void aLockedUltimateCastsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "locked_ult", Swap.SWAP.get());
            double malice = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.ULTIMATE_PRESS);
            assertEquals(malice, MaliceService.get(p).current(), EPS);
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Swap.TOTAL_SWAP));
            assertEquals(8, TechniqueUnlocks.requiredLevel(p, Swap.TOTAL_SWAP));
        });
    }

    @Test
    void theLoadoutRefusesLockedAbilities(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "locked_bar", Rime.RIME.get());
            assertEquals(LoadoutResult.LOCKED, LoadoutService.edit(p, LoadoutService.Action.SET, 5, 0, Optional.of(Rime.FROST_FIELD.id())));
            assertEquals(2, bar(p).size());
            RefinementService.setLevel(p, 2);
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 5, 0, Optional.of(Rime.FROST_FIELD.id())));
        });
    }

    @Test
    void sharedAbilitiesArentGatedAndARerollKeepsThemUnlocked(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_reroll", Rime.RIME.get());
            Ability rmt = SharedAbilities.get(RmtIds.ABILITY).orElseThrow();
            SharedAbilityService.unlock(p, rmt);
            assertTrue(TechniqueUnlocks.isUnlocked(p, rmt), "Refinement 0, and RMT doesn't care");
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 8, 0, Optional.of(rmt.id())));

            RefinementService.setLevel(p, 7);
            assertEquals(5, bar(p).size());
            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
            assertEquals(List.of(Swap.POSITIONS.id(), Swap.AWARENESS.id()), bar(p), "the new technique's level 0 kit");
            assertTrue(SharedAbilityService.isUnlocked(p, rmt.id()), "shared abilities stay unlocked");
            assertEquals(0, RefinementService.level(p));
        });
    }

    @Test
    void loweringRefinementRemovesWhatItLocks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_lower", Rime.RIME.get());
            RefinementService.setLevel(p, 4);
            assertEquals(4, bar(p).size());
            InnateTechniqueService.select(p, 3); // Rime Shell
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(CastManager.isToggleActive(p, Rime.RIME_SHELL), "setup");

            RefinementService.setLevel(p, 1);
            assertEquals(List.of(Rime.FROST_SHARD.id(), Rime.GLACIAL_LANCE.id()), bar(p));
            assertFalse(CastManager.isToggleActive(p, Rime.RIME_SHELL), "and it stops running");

            RefinementService.setLevel(p, 2);
            assertEquals(3, bar(p).size(), "unlocked again: back on the bar");
        });
    }

    @Test
    void requireRefinementFalseUnlocksEverything(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_casual", Rime.RIME.get());
            assertEquals(2, bar(p).size());
            ProgressionConfig.REQUIRE_REFINEMENT.set(false);
            assertTrue(TechniqueUnlocks.isUnlocked(p, Rime.SHATTER_SHELL));
            InnateTechniqueService.setInnateTechnique(p, PuppetManipulation.PUPPET_MANIPULATION.get());
            assertEquals(5, bar(p).size(), "the whole technique");
            ProgressionConfig.REQUIRE_REFINEMENT.set(true);
            LoadoutService.validate(p);
            assertEquals(2, bar(p).size(), "turned back on: the login repair takes the locked ones off");
        });
    }

    @Test
    void configOverridesTheDefinition(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ProgressionConfig.UNLOCK_OVERRIDES.set(List.of("kegare:frost_field=0", "kegare:splinter=9"));
            ServerPlayer p = fresh(server, "kit_override", Rime.RIME.get());
            assertEquals(List.of(Rime.FROST_SHARD.id(), Rime.GLACIAL_LANCE.id(), Rime.FROST_FIELD.id()), bar(p));
            assertEquals(9, TechniqueUnlocks.requiredLevel(p, Rime.SPLINTER));
        });
    }

    @Test
    void aLowerTopLevelLocksNothingForever(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_low_top", PuppetManipulation.PUPPET_MANIPULATION.get());
            ProgressionConfig.REFINEMENT_MAX_LEVEL.set(5);
            try {
                assertEquals(5, TechniqueUnlocks.requiredLevel(p, PuppetManipulation.ZIP_LEASH));
                RefinementService.setLevel(p, 5);
                assertTrue(TechniqueUnlocks.isUnlocked(p, PuppetManipulation.ZIP_LEASH));
            } finally {
                ProgressionConfig.REFINEMENT_MAX_LEVEL.set(10);
            }
        });
    }

    @Test
    void testTechniquesAreAllUnlocked(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = fresh(server, "kit_test", TestInnateTechniques.APEX.get());
            assertEquals(4, bar(p).size());
            TestInnateTechniques.APEX.get().everyAbility().forEach(a -> assertTrue(TechniqueUnlocks.isUnlocked(p, a)));
        });
    }
}
