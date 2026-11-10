package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * RMT and Output Insight (DESIGN §3.17, §3.21): the green orb's chance rises with it, up to its cap; and three sources each pay once
 * ever: unlocking RMT, healing yourself out of 3 hearts with it and living 10 s, and being healed by another player's.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtInsightTest {
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        Insight.reset(p);
        p.moveTo(0.5, 120, 0.5);
        return p;
    }

    private static int output(ServerPlayer p) {
        return Insight.get(p, InsightCategory.OUTPUT);
    }

    @Test
    void theOrbsChanceRisesWithOutputInsight(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                ServerPlayer p = player(server, server.overworld(), "rmt_orb_chance");
                double base = RmtConfig.ORB_CHANCE.get();
                assertEquals(base, GreenOrbService.chance(p), 1e-9, "no Insight: the base chance");
                Insight.add(p, InsightCategory.OUTPUT, 10);
                assertEquals(base + 10 * RmtConfig.ORB_INSIGHT_BONUS.get(), GreenOrbService.chance(p), 1e-9, "+0.5% a point");
                Insight.add(p, InsightCategory.OUTPUT, 1000);
                assertEquals(RmtConfig.ORB_CHANCE_CAP.get(), GreenOrbService.chance(p), 1e-9, "never past the cap");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void unlockedBeforeItPaidIsPaidOnLogin(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            try {
                ServerPlayer p = player(server, server.overworld(), "rmt_paid_late");
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                Insight.reset(p); // as if unlocked before unlocking paid anything
                assertTrue(SharedAbilityService.isUnlocked(p, Rmt.REVERSE_MALICE_TECHNIQUE.id()), "setup: unlocked");
                assertEquals(0, output(p), "setup: never paid");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
                assertEquals(5, output(p), "paid on login");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
                assertEquals(5, output(p), "once");
                ServerPlayer locked = player(server, server.overworld(), "rmt_never_unlocked");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(locked));
                assertEquals(0, output(locked), "nothing without RMT");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void eachSourcePaysOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                // unlocked_rmt: however it's unlocked; once.
                ServerPlayer p = player(server, level, "rmt_insight");
                assertTrue(SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE));
                assertEquals(5, output(p), "unlocked_rmt: Output 5");
                SharedAbilityService.lock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                assertEquals(5, output(p), "only once");

                // rmt_survived_low: at 3 hearts during their own channel, healed above, alive 10 s later.
                LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(Rmt.REVERSE_MALICE_TECHNIQUE.id()));
                InnateTechniqueService.select(p, 1);
                MaliceService.refill(p);
                p.setHealth(5f);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                for (int i = 0; i < 30; i++) CastManager.tick(p);
                assertTrue(p.getHealth() > RmtConfig.LOW_HEALTH.get(), "setup: healed out of it");
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
                TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.SURVIVE_SECONDS) - 1);
                RmtFatigue.tick(p);
                assertEquals(5, output(p), "not yet: 10 s to live through");
                TestServer.advanceGameTime(server, 1);
                RmtFatigue.tick(p);
                assertEquals(10, output(p), "rmt_survived_low: Output 5");

                InnateTechniqueService.clearCooldowns(p);
                MaliceService.refill(p);
                p.setHealth(5f);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                for (int i = 0; i < 30; i++) CastManager.tick(p);
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
                TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.SURVIVE_SECONDS));
                RmtFatigue.tick(p);
                assertEquals(10, output(p), "only once");

                // Healed out of it but dead before the 10 s: nothing.
                ServerPlayer q = player(server, level, "rmt_insight_dies");
                SharedAbilityService.unlock(q, Rmt.REVERSE_MALICE_TECHNIQUE);
                LoadoutService.edit(q, LoadoutService.Action.SET, 1, 0, Optional.of(Rmt.REVERSE_MALICE_TECHNIQUE.id()));
                InnateTechniqueService.select(q, 1);
                MaliceService.refill(q);
                q.setHealth(5f);
                CastManager.handleIntent(q, CastIntent.CAST_PRESS);
                for (int i = 0; i < 30; i++) CastManager.tick(q);
                CastManager.handleIntent(q, CastIntent.CAST_RELEASE);
                q.die(q.damageSources().generic());
                TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.SURVIVE_SECONDS));
                RmtFatigue.tick(q);
                assertFalse(Insight.hasFirst(q, "rmt_survived_low"), "died first: it doesn't count");

                // healed_by_other: once (called when another player's RMT heals them).
                int before = output(q);
                assertTrue(InsightHooks.healedByOther(q));
                assertFalse(InsightHooks.healedByOther(q), "only once");
                assertEquals(before + 3, output(q), "healed_by_other: Output 3");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
