package com.cnethuka.kegare.backgroundtask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.fists.Fists;
import com.cnethuka.kegare.fists.FistsConfig;
import com.cnethuka.kegare.fists.FistsIds;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.reinforcement.Reinforcement;
import com.cnethuka.kegare.reinforcement.ReinforcementConfig;
import com.cnethuka.kegare.reinforcement.ReinforcementIds;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.rmt.RmtConfig;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Sustained Flow (DESIGN §3.31): every active toggle's upkeep, never a start cost, a channel or another Background Task. */
@ExtendWith(EphemeralTestServerProvider.class)
class SustainedFlowTest {
    private static final double EPS = 1.0e-3;

    static ServerPlayer player(MinecraftServer server, String name, net.minecraft.core.Holder<com.cnethuka.kegare.innatetechnique.InnateTechnique> technique) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(3000));
        InnateTechniqueService.setInnateTechnique(p, technique.value());
        MaliceService.refill(p);
        p.moveTo(0.5, 120, 0.5, -90f, 0f);
        return p;
    }

    private static void flow(ServerPlayer p, boolean on) {
        Unlocks.grant(p, SustainedFlow.ID);
        if (BackgroundTaskService.slot(p, 1).task().isEmpty()) assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 1, SustainedFlow.ID));
        assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.setOn(p, 1, on));
        BackgroundTaskService.tick(p);
        assertEquals(on, BackgroundTaskService.isRunning(p, SustainedFlow.ID), "setup: Sustained Flow " + (on ? "on" : "off"));
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    @Test
    void unlocksAtRefinement5AndStaysThroughARerollAndNotForTheSevered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "flow_unlock", Rime.RIME);
                RefinementService.setLevel(p, 4);
                assertFalse(Unlocks.has(p, SustainedFlow.ID), "Refinement 4: locked");
                RefinementService.setLevel(p, 5);
                assertTrue(Unlocks.has(p, SustainedFlow.ID), "Refinement 5: unlocked");
                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
                assertEquals(0, RefinementService.level(p), "setup: a reroll resets Refinement");
                assertTrue(Unlocks.has(p, SustainedFlow.ID), "kept once earned, even after a reroll");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void everyToggleDrainsLessWhileItRuns(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                double keep = 1.0 - BackgroundTaskConfig.FLOW_REDUCTION.get();
                ServerPlayer rime = player(server, "flow_rime", Rime.RIME);
                ServerPlayer time = player(server, "flow_time", TimeManipulation.TIME_MANIPULATION);
                Ability[][] toggles = {{Fists.REINFORCED_FISTS, Reinforcement.REINFORCEMENT, Rime.RIME_SHELL}, {TimeManipulation.ACCELERATE}};
                ServerPlayer[] players = {rime, time};
                for (int i = 0; i < players.length; i++) {
                    ServerPlayer p = players[i];
                    for (Ability toggle : toggles[i]) assertEquals(CastType.TOGGLE, toggle.castType(), "setup: " + toggle.id() + " is a toggle");
                    flow(p, false);
                    for (Ability toggle : toggles[i]) {
                        assertEquals(toggle.upkeepPerSecond(p), CastManager.toggleUpkeep(p, toggle), EPS, toggle.id() + ": in full without it");
                    }
                    flow(p, true);
                    for (Ability toggle : toggles[i]) {
                        assertEquals(toggle.upkeepPerSecond(p) * keep, CastManager.toggleUpkeep(p, toggle), EPS, toggle.id() + ": flowReduction off");
                    }
                }
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theRealDrainNeverAStartCostAChannelOrATask(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                double keep = 1.0 - BackgroundTaskConfig.FLOW_REDUCTION.get();
                double flowDrain = BackgroundTaskConfig.FLOW_DRAIN.get();
                ServerPlayer p = player(server, "flow_drain", Rime.RIME);
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(FistsIds.ABILITY)));
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 2, 0, Optional.of(ReinforcementIds.ABILITY)));
                flow(p, true);

                // Start costs in full.
                double before = malice(p);
                InnateTechniqueService.select(p, 1);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - FistsConfig.ON_COST.get(), malice(p), EPS, "Fists' onCost in full");
                before = malice(p);
                InnateTechniqueService.select(p, 2);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(before - ReinforcementConfig.ON_COST.get(), malice(p), EPS, "Reinforcement's onCost in full");

                // A second with both on and the flow running: their upkeep 30% less, the flow's own drain on top.
                before = malice(p);
                for (int i = 0; i < 20; i++) {
                    CastManager.tick(p);
                    BackgroundTaskService.tick(p);
                }
                assertEquals(before - (FistsConfig.DRAIN.get() + ReinforcementConfig.DRAIN.get()) * keep - flowDrain, malice(p), EPS,
                        "the toggles' upkeep less flowReduction, plus its own drain");
                InnateTechniqueService.select(p, 1);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                InnateTechniqueService.select(p, 2);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);

                // Another Background Task pays in full.
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                Unlocks.grant(p, BackgroundRmt.ID);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
                BackgroundTaskService.tick(p);
                before = malice(p);
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(before - BackgroundTaskConfig.RMT_DRAIN.get() - flowDrain, malice(p), EPS, "Background RMT's drain untouched");
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);

                // A channel isn't a toggle: RMT's upkeep in full.
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 3, 0, Optional.of(RmtIds.ABILITY)));
                InnateTechniqueService.select(p, 3);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                before = malice(p);
                for (int i = 0; i < 20; i++) {
                    CastManager.tick(p);
                    BackgroundTaskService.tick(p);
                }
                assertEquals(before - RmtConfig.UPKEEP.get() - flowDrain, malice(p), EPS, "the channel's upkeep untouched");
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
