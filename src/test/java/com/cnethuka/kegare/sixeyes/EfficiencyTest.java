package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.backgroundtask.BackgroundRmt;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskConfig;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskResult;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.reinforcement.ReinforcementConfig;
import com.cnethuka.kegare.reinforcement.ReinforcementIds;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.rmt.RmtConfig;
import com.cnethuka.kegare.rmt.RmtFatigue;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.rmt.RmtOutput;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Efficiency, Attunement and RMT output (DESIGN §3.32). */
@ExtendWith(EphemeralTestServerProvider.class)
class EfficiencyTest {
    private static final double EPS = 1.0e-3;

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    @Test
    void theCurveNearsFreeAndNeverGetsThereEachStepAddingLess() {
        double previous = Efficiency.costFactor(Efficiency.x(0, 0));
        assertTrue(previous < 1.0, "some waste gone from the first moment");
        double lastGain = Double.MAX_VALUE;
        for (int g = 1; g <= 6; g++) {
            double now = Efficiency.costFactor(Efficiency.x(g, 0));
            double gain = previous - now;
            assertTrue(gain > 0 && gain < lastGain, "each Grade adds, and less than the last: step " + g);
            lastGain = gain;
            previous = now;
        }
        lastGain = Double.MAX_VALUE;
        previous = Efficiency.costFactor(Efficiency.x(6, 0));
        for (double a = 500; a <= 20_000; a += 500) {
            double now = Efficiency.costFactor(Efficiency.x(6, a));
            double gain = previous - now;
            assertTrue(gain > 0 && gain < lastGain, "each step of Attunement adds, and less than the last: " + a);
            lastGain = gain;
            previous = now;
        }
        assertTrue(Efficiency.costFactor(Efficiency.x(6, 1.0e300)) > 0, "never free, however far they go");
    }

    @Test
    void everyCostFallsAndACastStillCostsAtLeastOne(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "eff_costs", 20000, 0.5, 0.5);
            try {
                SightTest.eyes(server, p, true);
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                Unlocks.grant(p, BackgroundRmt.ID);
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(ReinforcementIds.ABILITY)));
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 2, 0, Optional.of(RmtIds.ABILITY)));
                double factor = Efficiency.costFactor(p);
                assertTrue(factor < 1.0, "a new bearer already wastes less");

                // A cast's start cost, and a toggle's upkeep.
                InnateTechniqueService.select(p, 1);
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - ReinforcementConfig.ON_COST.get() * factor, malice(p), EPS, "start cost");
                before = malice(p);
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(before - ReinforcementConfig.DRAIN.get() * factor, malice(p), EPS, "upkeep");
                CastManager.stopAll(p, EndReason.CANCELLED);
                InnateTechniqueService.clearCooldowns(p);

                // A channel (RMT).
                InnateTechniqueService.select(p, 2);
                before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - RmtConfig.COST.get() * factor, malice(p), EPS, "RMT's start");
                before = malice(p);
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(before - RmtConfig.UPKEEP.get() * factor, malice(p), EPS, "RMT's upkeep");
                CastManager.stopAll(p, EndReason.CANCELLED);
                InnateTechniqueService.clearCooldowns(p);

                // A Background Task.
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
                BackgroundTaskService.tick(p);
                before = malice(p);
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(before - BackgroundTaskConfig.RMT_DRAIN.get() * factor, malice(p), EPS, "a Background Task's drain");
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);

                // However attuned, a cast costs at least 1; and it's never free. (A cheap cast: the curve is too slow to take the shipped
                // 40 below 1 at any Attunement a player could reach.)
                GradeService.set(p, Grade.SPECIAL_GRADE);
                Attunement.set(p, 1.0e15);
                double onCost = ReinforcementConfig.ON_COST.get();
                ReinforcementConfig.ON_COST.set(10.0);
                try {
                    assertTrue(10.0 * Efficiency.costFactor(p) < 1.0, "setup: below 1 by the curve");
                    InnateTechniqueService.select(p, 1);
                    before = malice(p);
                    CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                    assertEquals(before - 1.0, malice(p), EPS, "at least 1 Malice");
                    before = malice(p);
                    for (int i = 0; i < 20; i++) CastManager.tick(p);
                    assertTrue(malice(p) < before, "upkeep is still paid");
                    CastManager.stopAll(p, EndReason.CANCELLED);
                } finally {
                    ReinforcementConfig.ON_COST.set(onCost);
                }
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void eyewearAndStrainNeverTouchItAndNonBearersPayInFull(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "eff_wear", 1000, 0.5, 0.5);
            try {
                ServerPlayer plain = SightTest.player(server, "eff_plain", 1000, 4.5, 0.5);
                assertEquals(40.0, MaliceService.costOf(plain, 40.0), EPS, "without the eyes, in full");
                SightTest.eyes(server, p, true);
                double factor = Efficiency.costFactor(p);
                StrainTest.wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                assertEquals(factor, Efficiency.costFactor(p), EPS, "a blindfold changes nothing");
                StrainTest.wear(p, new ItemStack(KegareItems.TINTED_GLASSES.get()));
                assertEquals(factor, Efficiency.costFactor(p), EPS, "nor glasses");
                StrainTest.wear(p, ItemStack.EMPTY);
                Strain.set(p, Strain.MAX);
                assertEquals(factor, Efficiency.costFactor(p), EPS, "nor max Strain");
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void attunementGrowsWithUseAndIsLostWithTheEyes(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "att_bearer", 1000, 0.5, 0.5);
            ServerPlayer heir = SightTest.player(server, "att_heir", 1000, -6.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "att_target", 1000, 0.5, 6.5);
                SightTest.eyes(server, p, true);
                assertEquals(0, Attunement.get(p), EPS, "a new bearer starts out clumsy");

                // A second in a fight.
                for (int i = 0; i < 20; i++) {
                    CombatHelper.markCombat(p);
                    Attunement.tick(p);
                }
                assertEquals(SixEyesConfig.ATTUNEMENT_FIGHT.get(), Attunement.get(p), EPS, "fighting while holding them");

                // A full-detail Read of someone: once a minute each.
                double before = Attunement.get(p);
                Reads.build(p);
                assertEquals(before + SixEyesConfig.ATTUNEMENT_READ.get(), Attunement.get(p), EPS, "a full-detail Read");
                before = Attunement.get(p);
                p.moveTo(0.5, 120, 0.5, 180f, 0f);
                Reads.build(p);
                p.moveTo(0.5, 120, 0.5, 0f, 0f);
                Reads.build(p);
                assertEquals(before, Attunement.get(p), EPS, "the same one again so soon counts for nothing");

                // A pin: only of something real, once a minute each.
                before = Attunement.get(p);
                Attunement.pinned(p, target.getId());
                assertEquals(before + SixEyesConfig.ATTUNEMENT_PIN.get(), Attunement.get(p), EPS, "a pin");
                Attunement.pinned(p, target.getId());
                assertEquals(before + SixEyesConfig.ATTUNEMENT_PIN.get(), Attunement.get(p), EPS, "not twice");
                Attunement.pinned(p, 999_999);
                assertEquals(before + SixEyesConfig.ATTUNEMENT_PIN.get(), Attunement.get(p), EPS, "nor of nothing");
                Attunement.pinned(heir, target.getId());
                assertEquals(0, Attunement.get(heir), EPS, "nor without the eyes");

                // Passed on: all gone, and the heir starts from nothing.
                double factor = Efficiency.costFactor(p);
                Attunement.set(p, 5000);
                assertTrue(Efficiency.costFactor(p) < factor, "setup: it was worth something");
                SightTest.eyes(server, p, false);
                assertEquals(0, Attunement.get(p), EPS, "lost with the eyes");
                SightTest.eyes(server, heir, true);
                assertEquals(0, Attunement.get(heir), EPS, "the next bearer starts clumsy");
            } finally {
                SightTest.eyes(server, heir, false);
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void rmtOutputGrowsPastTheNormalTopAndFatigueStillApplies(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "eff_rmt", 1000, 0.5, 0.5);
            try {
                GradeService.set(p, Grade.SPECIAL_GRADE);
                com.cnethuka.kegare.progression.RefinementService.setLevel(p, com.cnethuka.kegare.progression.RefinementService.maxLevel());
                double normalTop = RmtOutput.of(p);
                assertEquals(1.6, normalTop, 0.01, "setup: the normal top");
                SightTest.eyes(server, p, true);
                assertEquals(normalTop * Efficiency.rmtFactor(p), RmtOutput.of(p), EPS, "the eyes raise it");
                assertTrue(RmtOutput.of(p) > normalTop, "beyond 1.6");
                double fresh = RmtOutput.of(p);
                Attunement.set(p, 5000);
                assertTrue(RmtOutput.of(p) > fresh, "and it grows with Attunement");

                double full = RmtOutput.of(p);
                RmtFatigue.set(p, 0.5);
                assertEquals(full * 0.5, RmtOutput.of(p), EPS, "injury fatigue still applies");
                RmtFatigue.set(p, 0);
                assertFalse(Double.isInfinite(RmtOutput.of(p)));
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }
}
