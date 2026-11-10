package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The specials framework on the real cast pipeline: R resolves the selected ability's special, fizzles cost nothing,
 * requirements gate the cast (and are re-checked at a charge's release), and a special shares its base's cooldown. Rime's
 * real specials cover the plain cases; {@link SpecialFixtures} covers specials needing a toggle on or a trait, which no real
 * ability has.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SpecialsTest {
    /** Frost Shard, Splinter and the Lance leave entities in the shared test world; discard them so no other test counts them. */
    @org.junit.jupiter.api.AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            java.util.List<net.minecraft.world.entity.Entity> left = new java.util.ArrayList<>();
            server.overworld().getAllEntities().forEach(entity -> {
                if (entity != null && !(entity instanceof net.minecraft.world.entity.player.Player)) left.add(entity);
            });
            left.forEach(net.minecraft.world.entity.Entity::discard);
            TestServer.removePlayers(server);
        });
    }

    private static final double EPS = 1.0e-6;
    private static final int SHARD = 0, TOGGLE = 4, STEP = 5, LIFT = 6;

    @Test
    void realSpecialsShareTheirBasesCooldownAndAreListed(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "specials_real");
            BirthService.rebirth(p, OptionalInt.of(5000));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            MaliceService.refill(p);

            // Splinter puts Frost Shard on cooldown, and the other way round.
            InnateTechniqueService.select(p, SHARD);
            double before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before - MaliceService.castCostOf(p, Rime.SPLINTER.cost(p)), malice(p), EPS, "Splinter paid its own cost");
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD) > 0, "the special put its base on cooldown");
            assertEquals(InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD), InnateTechniqueService.cooldownRemaining(p, Rime.SPLINTER), "one shared cooldown");
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertEquals(before, malice(p), EPS, "Frost Shard is on the shared cooldown");
            InnateTechniqueService.clearCooldowns(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.SPLINTER) > 0, "Frost Shard put Splinter on cooldown");
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before, malice(p), EPS, "Splinter is on the shared cooldown");

            // The technique lists specials for lookups (HUD, sync, visuals).
            assertSame(Rime.SPLINTER, Rime.RIME.get().findAbility(Rime.SPLINTER.id()).orElseThrow());
            assertTrue(Rime.RIME.get().everyAbility().contains(Rime.GLACIAL_SPIRE));
            CastManager.stopAll(p, EndReason.CANCELLED);
        });
    }

    @Test
    void requirementsGateASpecial(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            SpecialFixtures.install();
            ServerPlayer p = TestServer.player(server, "specials");
            try {
                BirthService.rebirth(p, OptionalInt.of(5000));
                TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
                InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
                for (int i = 0; i < SpecialFixtures.ALL.size(); i++) {
                    Ability fixture = SpecialFixtures.ALL.get(i);
                    SharedAbilityService.unlock(p, fixture);
                    assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, TOGGLE + i, 0, Optional.of(fixture.id())), "setup");
                }
                MaliceService.refill(p);

                // No special defined: fizzle, nothing paid, no cooldown.
                InnateTechniqueService.select(p, TOGGLE);
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertNull(CastManager.state(p).casting(), "the toggle has no special: nothing starts");
                assertEquals(before, malice(p), EPS, "a fizzle costs nothing");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.TOGGLE));

                // Requirement unmet (it needs the toggle on): fizzle, nothing paid, no cooldown for either.
                InnateTechniqueService.select(p, STEP);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertEquals(before, malice(p), EPS, "an unmet requirement costs nothing");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.STEP));

                // Meet it: the toggle on, then the special casts, pays its own cost, and puts its base on cooldown too.
                InnateTechniqueService.select(p, TOGGLE);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(CastManager.isToggleActive(p, SpecialFixtures.TOGGLE), "setup: toggle on");
                InnateTechniqueService.select(p, STEP);
                before = malice(p);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertEquals(before - MaliceService.castCostOf(p, SpecialFixtures.STEP_SPECIAL_COST), malice(p), EPS, "the special paid its own cost");
                assertEquals(SpecialFixtures.STEP_COOLDOWN, InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.STEP), "its base on cooldown");
                assertEquals(SpecialFixtures.STEP_COOLDOWN, InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.STEP_SPECIAL), "one shared cooldown");

                // A trait requirement on a CHARGED special.
                InnateTechniqueService.clearCooldowns(p);
                InnateTechniqueService.select(p, LIFT);
                before = malice(p);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertNull(CastManager.state(p).casting(), "no trait: it fizzles");
                assertEquals(before, malice(p), EPS);

                // Tests share one server and any fake player's birth can roll the unique trait, so free it first.
                com.cnethuka.kegare.trait.TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
                assertEquals(TraitService.AddResult.ADDED, TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                CastState.Active charging = CastManager.state(p).casting();
                assertNotNull(charging, "with the trait, it charges");
                assertSame(SpecialFixtures.LIFT_SPECIAL, charging.ability());
                assertEquals(CastSlot.SPECIAL, charging.slot());
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
                assertNotNull(CastManager.state(p).casting(), "only R's release ends an R charge");

                // The requirement is re-checked at release: losing the trait mid-charge fizzles for free.
                TestServer.advanceGameTime(server, SpecialFixtures.LIFT_SPECIAL_CHARGE);
                TraitService.remove(p, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
                CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
                assertNull(CastManager.state(p).casting());
                assertEquals(before, malice(p), EPS, "lapsed requirement at release: nothing paid");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.LIFT), "and no cooldown");

                // With the trait kept, the release pays and starts its base's cooldown.
                TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                TestServer.advanceGameTime(server, SpecialFixtures.LIFT_SPECIAL_CHARGE);
                CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
                // castCostOf: a Six Eyes bearer wastes less energy (DESIGN §3.32).
                assertEquals(before - MaliceService.castCostOf(p, SpecialFixtures.LIFT_SPECIAL_COST), malice(p), EPS, "paid on release");
                assertTrue(InnateTechniqueService.cooldownRemaining(p, SpecialFixtures.LIFT) > 0, "the base shares the special's cooldown");
            } finally {
                TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
                CastManager.stopAll(p, EndReason.CANCELLED);
                SpecialFixtures.uninstall();
            }
        });
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
