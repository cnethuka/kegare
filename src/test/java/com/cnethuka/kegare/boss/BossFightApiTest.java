package com.cnethuka.kegare.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.boss.BossEncounterEvent;
import com.cnethuka.kegare.api.boss.BossFightEndReason;
import com.cnethuka.kegare.api.boss.BossFightSnapshot;
import com.cnethuka.kegare.api.boss.BossFights;
import com.cnethuka.kegare.api.boss.BossMetric;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class BossFightApiTest extends BossTestSupport {
    @Test
    void phasesTypedMetricsAndSnapshotsAreDetached(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "api");
            ServerPlayer participant = player(server, "api_participant");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            fight.join(participant);
            BossFightSnapshot initial = fight.snapshot();
            ResourceLocation second = ResourceLocation.fromNamespaceAndPath("kegare_test", "second");
            TestServer.advanceGameTime(server, 5);
            fight.setPhase(second);
            fight.setPhase(second); // no duplicate history entry

            BossMetric<Long> count = BossMetric.longMetric(ResourceLocation.fromNamespaceAndPath("kegare_test", "count"));
            BossMetric<Double> adaptation = BossMetric.doubleMetric(ResourceLocation.fromNamespaceAndPath("kegare_test", "adaptation"));
            fight.increment(count, 2L);
            fight.set(count, 4L);
            fight.increment(count, 1L);
            fight.set(adaptation, 0.25);
            fight.increment(adaptation, 0.5);
            assertEquals(5L, fight.get(count));
            assertEquals(0.75, fight.get(adaptation), 1.0e-9);

            BossFightSnapshot current = fight.snapshot();
            assertEquals(PHASE, initial.phaseId());
            assertEquals(1, initial.phaseHistory().size(), "an old snapshot never changes");
            assertTrue(initial.participants().get(participant.getUUID()).firstActivityTick() == -1);
            assertEquals(second, current.phaseId());
            assertEquals(2, current.phaseHistory().size());
            assertEquals(5, current.phaseHistory().get(1).tick() - current.startTick());
            assertEquals(5L, current.customMetrics().get(count.id()));
            assertEquals(0.75, current.customMetrics().get(adaptation.id()));
            assertThrows(UnsupportedOperationException.class,
                    () -> current.phaseHistory().add(new BossFightSnapshot.PhaseChange(PHASE, 0)));
            assertThrows(UnsupportedOperationException.class, () -> current.customMetrics().clear());
            assertThrows(UnsupportedOperationException.class, () -> current.participants().clear());
            assertThrows(UnsupportedOperationException.class,
                    () -> current.participants().get(participant.getUUID()).damageByAbility().clear());

            BossFightSnapshot reset = BossFights.reset(boss).orElseThrow();
            assertEquals(BossFightEndReason.RESET, reset.endReason());
            assertTrue(reset.participants().containsKey(participant.getUUID()), "participants survive teardown in the report");
            assertFalse(fight.isActive());
            TestServer.removePlayers(server);
            assertTrue(fight.snapshot().participants().containsKey(participant.getUUID()),
                    "logout/removal never erases a participant from the detached report");
            assertTrue(BossFights.reset(boss).isEmpty());
        });
    }

    @Test
    void removalCancelsTheAttackAndEndsExactlyOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "removed");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            var attack = new com.cnethuka.kegare.api.boss.BossAttack() {
                int ends;
                @Override public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("kegare_test", "removed_attack"); }
                @Override public com.cnethuka.kegare.api.boss.BossAttackTiming timing() {
                    return new com.cnethuka.kegare.api.boss.BossAttackTiming(20, 20, 20, 20);
                }
                @Override public void onEnd(com.cnethuka.kegare.api.boss.BossAttackContext context,
                        com.cnethuka.kegare.api.boss.BossAttackEndReason reason) {
                    ends++;
                    assertEquals(com.cnethuka.kegare.api.boss.BossAttackEndReason.ENCOUNTER_ENDED, reason);
                }
            };
            fight.attacks().start(attack, null);
            boss.discard();
            assertEquals(BossFightEndReason.REMOVED, fight.snapshot().endReason());
            assertEquals(1, attack.ends);
            assertEquals(1, fight.snapshot().attacks().values().iterator().next().cancellations());
            assertTrue(BossFights.find(boss).isEmpty());
            BossRuntime.endAll(server, BossFightEndReason.SERVER_STOP);
            assertEquals(1, attack.ends, "later server shutdown does not report the old fight again");
        });
    }

    @Test
    void serverStopRejectsReentrantEncountersAndStillEndsEveryFight(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss first = boss(server, "server_stop_restart_a");
            WitherBoss second = boss(server, "server_stop_restart_b");
            BossFightImpl firstFight = (BossFightImpl) BossFights.start(first, PHASE);
            BossFightImpl secondFight = (BossFightImpl) BossFights.start(second, PHASE);
            int[] rejectedRestarts = {0};
            class RestartOnEnd {
                @SubscribeEvent public void ended(BossEncounterEvent.Ended event) {
                    if (!event.getEncounter().encounterId().equals(firstFight.id())) return;
                    assertThrows(IllegalStateException.class, () -> BossFights.start(first, PHASE));
                    rejectedRestarts[0]++;
                }
            }
            RestartOnEnd listener = new RestartOnEnd();
            NeoForge.EVENT_BUS.register(listener);
            try {
                BossRuntime.stop(server);
                assertEquals(1, rejectedRestarts[0]);
                assertFalse(firstFight.isActive());
                assertFalse(secondFight.isActive());
                assertTrue(BossFights.find(first).isEmpty());
                assertTrue(BossFights.find(second).isEmpty());
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
    }

    @Test
    void serverStopEndsEveryFightForThatServerOnlyOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "server_stop");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            BossRuntime.endAll(server, BossFightEndReason.SERVER_STOP);
            BossRuntime.endAll(server, BossFightEndReason.SERVER_STOP);
            assertEquals(BossFightEndReason.SERVER_STOP, fight.snapshot().endReason());
            assertTrue(BossFights.find(boss).isEmpty());
        });
    }
}
