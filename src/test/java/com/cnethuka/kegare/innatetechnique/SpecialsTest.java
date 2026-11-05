package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.test.TestAbilities;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The specials framework on the real cast pipeline: R resolves the selected ability's special, fizzles cost nothing,
 * requirements gate the cast (and are re-checked at a charge's release), and a special shares its base's cooldown.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SpecialsTest {
    private static final double EPS = 1.0e-6;

    @Test
    void specials(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "specials");
            BirthService.rebirth(p, OptionalInt.of(500));
            TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
            InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.APEX.get()); // Dash, Burst, Hover, Aura
            MaliceService.refill(p);

            // No special defined: fizzle, nothing paid, no cooldown.
            InnateTechniqueService.select(p, 1); // Burst
            double before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertNull(CastManager.state(p).casting(), "Burst has no special: nothing starts");
            assertEquals(before, malice(p), EPS, "a fizzle costs nothing");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TestAbilities.BURST));

            // Requirement unmet (Blink needs Aura on): fizzle, nothing paid, no cooldown for either.
            InnateTechniqueService.select(p, 0); // Dash
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before, malice(p), EPS, "an unmet requirement costs nothing");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TestAbilities.DASH));

            // Meet it: Aura on, then Blink casts, pays its own cost, and puts Dash on cooldown too.
            InnateTechniqueService.select(p, 3);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(CastManager.isToggleActive(p, TestAbilities.AURA), "setup: Aura on");
            InnateTechniqueService.select(p, 0);
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before - KegareServerConfig.BLINK_COST.get(), malice(p), EPS, "Blink paid its own cost");
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.DASH) > 0, "the special put its base on cooldown");
            assertEquals(InnateTechniqueService.cooldownRemaining(p, TestAbilities.DASH),
                    InnateTechniqueService.cooldownRemaining(p, TestAbilities.BLINK), "one shared cooldown");

            // The base is blocked by the shared cooldown (no cost) ...
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertEquals(before, malice(p), EPS, "Dash is on the shared cooldown");
            // ... and the other way round: the base puts its special on cooldown.
            InnateTechniqueService.clearCooldowns(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.BLINK) > 0, "Dash put Blink on cooldown");
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before, malice(p), EPS, "Blink is on the shared cooldown");

            // A trait requirement on a CHARGED special (Updraft, special of Hover).
            InnateTechniqueService.clearCooldowns(p);
            InnateTechniqueService.select(p, 2); // Hover
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertNull(CastManager.state(p).casting(), "no trait: Updraft fizzles");
            assertEquals(before, malice(p), EPS);

            assertEquals(TraitService.AddResult.ADDED, TraitService.add(p, KegareTraits.LENSING_EYES.get(), Trait.AddReason.ADMIN));
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            CastState.Active charging = CastManager.state(p).casting();
            assertNotNull(charging, "with the trait, Updraft charges");
            assertSame(TestAbilities.UPDRAFT, charging.ability());
            assertEquals(CastSlot.SPECIAL, charging.slot());
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNotNull(CastManager.state(p).casting(), "only R's release ends an R charge");

            // The requirement is re-checked at release: losing the trait mid-charge fizzles for free.
            TestServer.advanceGameTime(server, TestAbilities.UPDRAFT.maxChargeTicks(p));
            TraitService.remove(p, KegareTraits.LENSING_EYES.get(), Trait.RemoveReason.ADMIN);
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertEquals(before, malice(p), EPS, "lapsed requirement at release: nothing paid");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TestAbilities.HOVER), "and no cooldown");

            // With the trait kept, the release pays and starts Hover's cooldown.
            TraitService.add(p, KegareTraits.LENSING_EYES.get(), Trait.AddReason.ADMIN);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            TestServer.advanceGameTime(server, TestAbilities.UPDRAFT.maxChargeTicks(p));
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertEquals(before - KegareServerConfig.UPDRAFT_COST.get(), malice(p), EPS, "Updraft paid on release");
            assertTrue(InnateTechniqueService.cooldownRemaining(p, TestAbilities.HOVER) > 0, "Hover shares Updraft's cooldown");

            // The technique lists specials for lookups (HUD, sync, visuals).
            assertSame(TestAbilities.BLINK, TestInnateTechniques.APEX.get().findAbility(TestAbilities.BLINK.id()).orElseThrow());
            assertTrue(TestInnateTechniques.APEX.get().everyAbility().contains(TestAbilities.UPDRAFT));

            TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
            CastManager.stopAll(p, EndReason.CANCELLED);
        });
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
