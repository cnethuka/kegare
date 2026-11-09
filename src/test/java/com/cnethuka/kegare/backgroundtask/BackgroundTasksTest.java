package com.cnethuka.kegare.backgroundtask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.mojang.serialization.JsonOps;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The Background Task framework and slots (DESIGN §3.31), with test-only tasks nobody earns on their own. */
@ExtendWith(EphemeralTestServerProvider.class)
class BackgroundTasksTest {
    private static final double EPS = 1.0e-6;

    /** A task that counts its ticks, pauses on request and drains 2 a second. */
    static final class TestTask extends BackgroundTask {
        int ticks, stops;
        boolean pause;

        TestTask(String path) {
            super(Kegare.id(path));
        }

        @Override public UnlockRequirements requirements() { return UnlockRequirements.builder().grantedOnly().build(); }
        @Override public double drainPerSecond(ServerPlayer player) { return 2.0; }
        @Override public boolean paused(ServerPlayer player, boolean wasPaused) { return pause; }
        @Override public void tick(ServerPlayer player) { ticks++; }
        @Override public void stopped(ServerPlayer player) { stops++; }
    }

    static final TestTask A = new TestTask("test_background_a");
    static final TestTask B = new TestTask("test_background_b");
    static final TestTask C = new TestTask("test_background_c");

    /** The test tasks exist only while a test runs: nothing else ever sees them. */
    @org.junit.jupiter.api.BeforeEach
    void registerTasks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (TestTask t : new TestTask[] {A, B, C}) BackgroundTasks.register(t);
        });
    }

    @org.junit.jupiter.api.AfterEach
    void unregisterTasks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (TestTask t : new TestTask[] {A, B, C}) BackgroundTasks.unregister(t.id());
        });
    }

    static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        for (TestTask t : new TestTask[] {A, B, C}) Unlocks.grant(p, t.id());
        return p;
    }

    private static void eyes(MinecraftServer server, ServerPlayer p, boolean on) {
        if (on) {
            TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            assertEquals(TraitService.AddResult.ADDED, TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
        } else {
            TraitService.remove(p, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
        }
    }

    @Test
    void twoSlotsThreeWithTheEyesNoneSevered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "bg_slots");
            try {
                assertEquals(2, BackgroundTaskService.slotCount(p), "every Malice user: 2");
                assertEquals(BackgroundTaskResult.BAD_SLOT, BackgroundTaskService.set(p, 2, A.id()), "no third slot");
                eyes(server, p, true);
                assertEquals(3, BackgroundTaskService.slotCount(p), "the Six Eyes: 3, from the start");
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 2, A.id()), "the third slot at once");

                ServerPlayer s = TestServer.damageable(server, "bg_severed");
                BirthService.rebirth(s, OptionalInt.of(0));
                assertEquals(0, BackgroundTaskService.slotCount(s), "Severed: none");
                assertEquals(BackgroundTaskResult.SEVERED, BackgroundTaskService.set(s, 0, A.id()), "and nothing to put in");
                assertEquals(0, BackgroundTaskSync.build(s).slotCount(), "no Background view");
            } finally {
                eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void losingTheEyesSwitchesOffTheThirdAndKeepsIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "bg_eyes");
            try {
                eyes(server, p, true);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 2, C.id()));
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isRunning(p, C.id()), "running in the third slot");

                eyes(server, p, false);
                int stops = C.stops;
                BackgroundTaskService.tick(p);
                assertEquals(2, BackgroundTaskService.slotCount(p), "back to 2");
                assertFalse(BackgroundTaskService.isRunning(p, C.id()), "the third slot's task stopped");
                assertEquals(stops + 1, C.stops, "and was told so");
                assertEquals(C.id(), BackgroundTaskService.slot(p, 2).task().orElseThrow(), "kept saved");
                assertFalse(BackgroundTaskService.slot(p, 2).on(), "switched off");

                eyes(server, p, true);
                assertEquals(3, BackgroundTaskService.slotCount(p), "the eyes again: the third slot at once");
                assertEquals(C.id(), BackgroundTaskService.slot(p, 2).task().orElseThrow(), "with its task");
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.setOn(p, 2, true));
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isRunning(p, C.id()), "running again");
            } finally {
                eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void slotsChangeOutOfAFightOnlySwitchingAnyTime(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "bg_combat");
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, A.id()));
                assertTrue(BackgroundTaskService.slot(p, 0).on(), "placed means on");
                CombatHelper.markCombat(p);
                assertEquals(BackgroundTaskResult.IN_COMBAT, BackgroundTaskService.set(p, 1, B.id()), "no slot changes mid-fight");
                assertEquals(BackgroundTaskResult.IN_COMBAT, BackgroundTaskService.set(p, 0, null), "nor emptying one");
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.setOn(p, 0, false), "switching off mid-fight");
                BackgroundTaskService.tick(p);
                assertFalse(BackgroundTaskService.isRunning(p, A.id()), "off");
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.setOn(p, 0, true), "and on again");
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isRunning(p, A.id()), "on");
                assertEquals(BackgroundTaskResult.EMPTY_SLOT, BackgroundTaskService.setOn(p, 1, true), "an empty slot has nothing to switch");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void lockedTasksDrainsPausesAndMoves(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "bg_run");
                Unlocks.revoke(p, B.id());
                assertEquals(BackgroundTaskResult.LOCKED, BackgroundTaskService.set(p, 0, B.id()), "locked: refused");

                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, A.id()));
                double before = MaliceService.get(p).current();
                int ticks = A.ticks;
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(before - 2.0, MaliceService.get(p).current(), 1e-3, "a second's drain");
                assertEquals(ticks + 20, A.ticks, "its effect every tick");

                A.pause = true;
                before = MaliceService.get(p).current();
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isPaused(p, A.id()), "paused by its own rule");
                assertEquals(before, MaliceService.get(p).current(), EPS, "paying nothing");
                A.pause = false;
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isRunning(p, A.id()), "resumes on its own");

                MaliceService.setCurrent(p, 0.01);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isPaused(p, A.id()), "can't pay: paused");
                MaliceService.refill(p);
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundTaskService.isRunning(p, A.id()), "and back once it can");

                // A task placed in another slot moves there, swapping with what was there.
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 1, C.id()));
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 1, A.id()));
                assertEquals(A.id(), BackgroundTaskService.slot(p, 1).task().orElseThrow(), "moved");
                assertEquals(C.id(), BackgroundTaskService.slot(p, 0).task().orElseThrow(), "swapped");
            } finally {
                A.pause = false;
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void savedThroughRerollsAndDeathAndOnlyEverSentToTheirOwner(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "bg_saved");
            try {
                eyes(server, p, true);
                BackgroundTaskService.set(p, 0, A.id());
                BackgroundTaskService.set(p, 2, B.id());
                BackgroundTaskService.setOn(p, 2, false);
                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get()); // a reroll
                assertEquals(A.id(), BackgroundTaskService.slot(p, 0).task().orElseThrow(), "kept through a reroll");

                // What's saved (and copied on death) comes back the same.
                var json = BackgroundTaskData.CODEC.encodeStart(JsonOps.INSTANCE, BackgroundTaskService.data(p)).getOrThrow();
                BackgroundTaskData back = BackgroundTaskData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                for (int i = 0; i < BackgroundTaskService.MAX_SLOTS; i++) assertEquals(BackgroundTaskService.slot(p, i), back.slot(i), "slot " + i);

                // The count is private: only the player themself is ever sent it.
                ServerPlayer q = player(server, "bg_other");
                BackgroundTaskSync.clear();
                BackgroundTaskService.tick(p);
                BackgroundTaskService.tick(q);
                assertEquals(java.util.Set.of(p.getUUID(), q.getUUID()), BackgroundTaskSync.sent().keySet(), "each sent their own");
                assertEquals(3, BackgroundTaskSync.sent().get(p.getUUID()).slotCount(), "the bearer's 3, to the bearer");
                assertEquals(2, BackgroundTaskSync.sent().get(q.getUUID()).slotCount(), "2 to the other");
                assertEquals(3, BackgroundTaskSync.sent().get(p.getUUID()).slots().size(), "slots as many as they have");
            } finally {
                eyes(server, p, false);
                BackgroundTaskSync.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
