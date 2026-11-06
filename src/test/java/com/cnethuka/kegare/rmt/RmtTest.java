package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Reverse Malice Technique as a shared ability: unlock rules, loadout, healing, cooldown, interruption. */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtTest {
    private static final double EPS = 1.0e-4;

    /** The player doesn't move for it: every phase is effects only, and the channel still has its aura. */
    @Test
    void itHasNoPlayerAnimation() {
        ReverseMaliceTechnique rmt = new ReverseMaliceTechnique();
        for (VisualPhase phase : VisualPhase.values()) {
            assertTrue(rmt.visuals().get(phase).animation().isEmpty(), phase.name());
        }
        assertEquals(java.util.Optional.of(RmtIds.FX_CHANNEL_AURA), rmt.visuals().get(VisualPhase.CHANNEL_LOOP).effect());
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir"), "src/main/resources/assets/kegare/player_animations/rmt.json"))
                || java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir"), "../src/main/resources/assets/kegare/player_animations/rmt.json")),
                "and its file is gone");
    }

    @Test
    void reverseMaliceTechnique(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ReverseMaliceTechnique rmt = Rmt.REVERSE_MALICE_TECHNIQUE;

            // Severed players can never unlock it.
            ServerPlayer severed = TestServer.player(server, "rmt_severed");
            BirthService.rebirth(severed, OptionalInt.of(0));
            assertTrue(MaliceService.isSevered(severed), "setup: Severed");
            assertFalse(SharedAbilityService.unlock(severed, rmt));

            ServerPlayer p = TestServer.player(server, "rmt");
            BirthService.rebirth(p, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            clearCombat(server, p);

            // Locked: it can't go on the bar.
            assertEquals(LoadoutResult.LOCKED, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(rmt.id())));
            assertTrue(SharedAbilityService.unlock(p, rmt));

            // Swap a Rime ability for it.
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(rmt.id())));
            assertSame(rmt, LoadoutService.abilityAt(p, 1));
            assertFalse(LoadoutService.holds(p, Rime.GLACIAL_LANCE.id()), "Glacial Lance left the bar");
            InnateTechniqueService.select(p, 1);

            // Full health: refused, nothing paid.
            MaliceService.refill(p);
            p.setHealth(p.getMaxHealth());
            double before = malice(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNull(CastManager.state(p).casting());
            assertEquals(before, malice(p), EPS);

            // No special: R fizzles at no cost.
            p.setHealth(6f);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertNull(CastManager.state(p).casting());
            assertEquals(before, malice(p), EPS);

            // Hold: start cost, then heal at the end of each full second while Malice drains.
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNotNull(CastManager.state(p).casting(), "channeling");
            assertEquals(before - RmtConfig.COST.get(), malice(p), EPS, "start cost");
            for (int i = 0; i < 19; i++) CastManager.tick(p);
            assertEquals(6f, p.getHealth(), 1e-4, "no heal before a full second");
            CastManager.tick(p);
            assertEquals(6f + RmtConfig.HEAL_PER_SECOND.get(), p.getHealth(), 1e-4, "healed after one second");
            assertEquals(before - RmtConfig.COST.get() - RmtConfig.UPKEEP.get(), malice(p), 1e-3, "one second of upkeep");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, rmt), "no cooldown while held");

            // Release: stops, cooldown starts.
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertEquals(RmtConfig.ticks(RmtConfig.COOLDOWN), InnateTechniqueService.cooldownRemaining(p, rmt), "cooldown starts on release");

            // Freezing interrupts it like any channel (and the cooldown starts).
            InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            p.setHealth(6f);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNotNull(CastManager.state(p).casting());
            FreezeService.addStacks(p, RimeConfig.FREEZE_MAX_STACKS.get(), null);
            assertTrue(FreezeService.isFrozen(p), "setup: frozen");
            assertNull(CastManager.state(p).casting(), "the freeze interrupted the heal");
            assertTrue(InnateTechniqueService.cooldownRemaining(p, rmt) > 0);
            FreezeService.clear(p);
            CombatHelper.clearStun(p);

            // Fully healed: the channel ends on its own.
            InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            p.setHealth(p.getMaxHealth() - 1f);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            for (int i = 0; i < 20; i++) CastManager.tick(p);
            assertEquals(p.getMaxHealth(), p.getHealth(), 1e-4, "never above max health");
            assertNull(CastManager.state(p).casting(), "nothing left to heal: done");

            // A reroll resets the loadout but keeps the unlock; locking it again takes it off the bar.
            InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.KINETIC.get());
            assertFalse(LoadoutService.holds(p, rmt.id()), "reset to the new default");
            assertTrue(SharedAbilityService.isUnlocked(p, rmt.id()), "unlock kept");
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 0, 0, Optional.of(rmt.id())));
            assertTrue(SharedAbilityService.lock(p, rmt));
            assertFalse(LoadoutService.holds(p, rmt.id()), "locked again: off the bar");
            CastManager.stopAll(p, EndReason.CANCELLED);
        });
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    private static void clearCombat(MinecraftServer server, ServerPlayer p) {
        TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);
    }
}
