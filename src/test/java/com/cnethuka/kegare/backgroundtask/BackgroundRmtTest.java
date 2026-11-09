package com.cnethuka.kegare.backgroundtask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeConfig;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeStops;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.reinforcement.AuraTell;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.rmt.RmtConfig;
import com.cnethuka.kegare.rmt.RmtFatigue;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.rmt.RmtOutput;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Background RMT (DESIGN §3.31). */
@ExtendWith(EphemeralTestServerProvider.class)
class BackgroundRmtTest {
    private static final double EPS = 1.0e-3;

    static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(1000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(0.5, 120, 0.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** RMT and Background RMT unlocked, Background RMT in slot 1, on. */
    static ServerPlayer running(MinecraftServer server, String name) {
        ServerPlayer p = player(server, name);
        SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
        Unlocks.grant(p, BackgroundRmt.ID);
        assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
        BackgroundTaskService.tick(p);
        assertTrue(BackgroundRmt.running(p), "setup: running");
        return p;
    }

    private static void share(ServerPlayer p, double share) {
        MaliceService.setCurrent(p, MaliceService.get(p).capacity() * share);
    }

    @Test
    void unlocksAt500HealedWithRmtAndAwardsAlwaysOn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer q = player(server, "bgrmt_no_rmt");
                RmtHealed.set(q, 600);
                assertFalse(Unlocks.has(q, BackgroundRmt.ID), "RMT itself first");

                ServerPlayer p = player(server, "bgrmt_unlock");
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                RmtHealed.set(p, 499.9);
                assertFalse(Unlocks.has(p, BackgroundRmt.ID), "499.9: not yet");
                assertFalse(TestServer.hasAdvancement(server, p, KegareAdvancements.ALWAYS_ON), "no Always On");

                // Healing with the channel counts toward it, and the heal that crosses 500 unlocks it.
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(RmtIds.ABILITY)));
                InnateTechniqueService.select(p, 1);
                p.setHealth(4f);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                for (int i = 0; i < 5; i++) CastManager.tick(p);
                assertTrue(RmtHealed.get(p) > 499.9, "the channel's healing counted: " + RmtHealed.get(p));
                assertTrue(Unlocks.has(p, BackgroundRmt.ID), "unlocked");
                assertTrue(TestServer.hasAdvancement(server, p, KegareAdvancements.ALWAYS_ON), "Always On");
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);

                // Kept once earned, and saved with the count.
                assertTrue(RmtHealed.get(p) >= BackgroundTaskConfig.RMT_HEAL_TO_UNLOCK.get());

                ServerPlayer s = TestServer.damageable(server, "bgrmt_severed");
                BirthService.rebirth(s, OptionalInt.of(0));
                RmtHealed.set(s, 1000);
                assertFalse(Unlocks.has(s, BackgroundRmt.ID), "Severed never");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void healsAtItsShareOfRmtOutputWithFatigueAndAtFullHealthNeverAbsorption(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = running(server, "bgrmt_rate");
                p.setHealth(10f);
                double perSecond = RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(p) * BackgroundTaskConfig.RMT_OUTPUT.get();
                assertEquals(RmtConfig.HEAL_PER_SECOND.get() / 6.0, perSecond, 0.01, "about 1 health every 3 s at an output of 1");
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(10f + perSecond, p.getHealth(), EPS, "a second's healing");

                RmtFatigue.set(p, 0.5);
                double tired = RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(p) * BackgroundTaskConfig.RMT_OUTPUT.get();
                assertEquals(perSecond * 0.5, tired, EPS, "injury fatigue applies");
                float before = p.getHealth();
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(before + tired, p.getHealth(), EPS, "at the tired rate");
                RmtFatigue.set(p, 0);

                // At full health it keeps running and draining, ready for the next hit; never absorption.
                p.setHealth(p.getMaxHealth());
                double malice = MaliceService.get(p).current();
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "still running at full health");
                assertEquals(malice - BackgroundTaskConfig.RMT_DRAIN.get(), MaliceService.get(p).current(), EPS, "and draining");
                assertEquals(0f, p.getAbsorptionAmount(), EPS, "never absorption");
                p.setHealth(p.getMaxHealth() - 0.01f);
                BackgroundTaskService.tick(p);
                assertEquals(p.getMaxHealth(), p.getHealth(), EPS, "a hit is healed as it lands, never above max");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void slowsRegenAndPausesBelow20ResumingAbove30(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = running(server, "bgrmt_pause");
                share(p, 0.5);
                double before = MaliceService.get(p).current();
                MaliceService.tickRegen(p);
                double slowed = MaliceService.get(p).current() - before;
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);
                before = MaliceService.get(p).current();
                MaliceService.tickRegen(p);
                double normal = MaliceService.get(p).current() - before;
                assertEquals(normal * BackgroundTaskConfig.RMT_REGEN_FACTOR.get(), slowed, 1e-6, "regenFactor while it runs");
                BackgroundTaskService.setOn(p, 0, true);

                share(p, 0.19);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isPaused(p, BackgroundRmt.ID), "below 20%: paused");
                share(p, 0.25);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isPaused(p, BackgroundRmt.ID), "25%: still waiting");
                share(p, 0.31);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "above 30%: resumed on its own");
                share(p, 0.25);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "25% on the way down: still running");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void stunsAndBurnoutDontStopItAndItShortensBurnout(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = running(server, "bgrmt_stun");
                CombatHelper.stun(p, 100);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "stunned: still running");
                Burnout.apply(p, 100);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "burnt out: still running");
                assertEquals((int) Math.round(100 * BackgroundTaskConfig.RMT_BURNOUT_FACTOR.get()), Burnout.remaining(p), "Burnout shortened");
                Burnout.clear(p);

                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);
                Burnout.apply(p, 100);
                assertEquals(100, Burnout.remaining(p), "not running: Burnout as it was");
                Burnout.clear(p);
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void onlyTheSixEyesSeeTheShimmer(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer eyes = null;
            try {
                ServerPlayer p = running(server, "bgrmt_seen");
                ServerPlayer plain = player(server, "bgrmt_plain");
                eyes = player(server, "bgrmt_eyes");
                TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
                assertEquals(TraitService.AddResult.ADDED, TraitService.add(eyes, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
                assertFalse(AuraTell.shimmerSeenBy(plain, p), "nobody else can tell");
                assertTrue(AuraTell.shimmerSeenBy(eyes, p), "the Six Eyes see a faint green shimmer");
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);
                assertFalse(AuraTell.shimmerSeenBy(eyes, p), "nothing to see when it's off");
            } finally {
                if (eyes != null) TraitService.remove(eyes, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
                TestServer.removePlayers(server);
            }
        });
    }
}
