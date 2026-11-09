package com.cnethuka.kegare.innate_technique.time_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.backgroundtask.BackgroundRmt;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskResult;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskService;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Background RMT (DESIGN §3.31) in a Standstill (§3.26): paused while time is stopped for them, resumed with it. */
@ExtendWith(EphemeralTestServerProvider.class)
class BackgroundRmtStandstillTest {
    private static final double EPS = 1.0e-3;

    static ServerPlayer running(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(1000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(0.5, 120, 0.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
        Unlocks.grant(p, BackgroundRmt.ID);
        assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
        BackgroundTaskService.tick(p);
        assertTrue(BackgroundRmt.running(p), "setup: running");
        return p;
    }

    @Test
    void pausedWhileFrozenInAStandstill(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = server.overworld();
            try {
                ServerPlayer p = running(server, "bgrmt_frozen");
                ServerPlayer caster = TestServer.damageable(server, "bgrmt_caster");
                level.addNewPlayer(caster);
                BirthService.rebirth(caster, OptionalInt.of(3000));
                TraitService.removeAll(caster, Trait.RemoveReason.ADMIN);
                InnateTechniqueService.setInnateTechnique(caster, TimeManipulation.TIME_MANIPULATION.get());
                MaliceService.refill(caster);
                caster.moveTo(3.5, 120, 0.5, 90f, 0f);
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                assertTrue(TimeStops.active(level), "setup: time stands still");
                assertTrue(TimeStops.frozen(p), "setup: frozen");
                p.setHealth(10f);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isPaused(p, BackgroundRmt.ID), "paused while time is stopped for them");
                assertEquals(10f, p.getHealth(), EPS, "no healing");

                TestServer.advanceGameTime(server, TimeConfig.ticks(TimeConfig.STANDSTILL_SECONDS));
                TimeStops.tick(level);
                assertFalse(TimeStops.active(level), "setup: time runs again");
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "resumes with it");
            } finally {
                TimeStops.clear();
                TestServer.removePlayers(server);
            }
        });
    }

}
