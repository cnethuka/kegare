package com.cnethuka.kegare.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.boss.BossAttack;
import com.cnethuka.kegare.api.boss.BossAttackEvent;
import com.cnethuka.kegare.api.boss.BossAttackContext;
import com.cnethuka.kegare.api.boss.BossAttackEndReason;
import com.cnethuka.kegare.api.boss.BossAttackStartResult;
import com.cnethuka.kegare.api.boss.BossAttackTiming;
import com.cnethuka.kegare.api.boss.BossFights;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Snowball;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class BossAttackTest extends BossTestSupport {
    private static final ResourceLocation ATTACK = ResourceLocation.fromNamespaceAndPath("kegare_test", "attack");

    @Test
    void timingCompletionCancellationAndPerAttackCooldown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss(server, "timing"), PHASE);
            List<String> calls = new ArrayList<>();
            BossAttack attack = new BossAttack() {
                @Override public ResourceLocation id() { return ATTACK; }
                @Override public BossAttackTiming timing() { return new BossAttackTiming(2, 2, 1, 4); }
                @Override public void onStart(BossAttackContext context) {
                    calls.add("start:" + context.state() + ":" + context.stateTicks());
                    assertEquals(fight.id(), context.encounter().id());
                    assertEquals(fight.level(), context.level());
                    assertEquals(fight.boss(), context.boss());
                }
                @Override public void onTick(BossAttackContext context) {
                    calls.add(context.state() + ":" + context.stateTicks() + ":" + context.attackTicks());
                }
                @Override public void onEnd(BossAttackContext context, BossAttackEndReason reason) {
                    calls.add("end:" + reason);
                }
            };

            assertEquals(BossAttackStartResult.STARTED, fight.attacks().start(attack, null));
            assertEquals(BossAttackStartResult.ATTACK_ALREADY_RUNNING, fight.attacks().start(attack, null));
            fight.attacks().tick(); // same game tick: no duplicate callback
            advance(server, fight, 1);
            advance(server, fight, 1);
            advance(server, fight, 1);
            advance(server, fight, 1);
            advance(server, fight, 1); // exactly at the end of recovery
            assertEquals(List.of("start:WINDUP:0", "WINDUP:1:1", "ACTIVE:0:2", "ACTIVE:1:3",
                    "RECOVERY:0:4", "end:COMPLETED"), calls);
            assertTrue(fight.attacks().activeAttack().isEmpty());
            assertEquals(4, fight.attacks().cooldownTicksRemaining(ATTACK));

            TestServer.advanceGameTime(server, 3);
            assertEquals(BossAttackStartResult.ON_COOLDOWN, fight.attacks().start(attack, null));
            TestServer.advanceGameTime(server, 1);
            assertEquals(BossAttackStartResult.STARTED, fight.attacks().start(attack, null));
            assertTrue(fight.attacks().cancel());
            assertFalse(fight.attacks().cancel());
            assertEquals("end:CANCELLED", calls.get(calls.size() - 1));
            assertEquals(1, fight.snapshot().attacks().get(ATTACK).completions());
            assertEquals(1, fight.snapshot().attacks().get(ATTACK).cancellations());
            assertEquals(2, fight.snapshot().attacks().get(ATTACK).starts());
        });
    }

    @Test
    void zeroLengthLeadingPhasesStartAtTheFirstRealPhase(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            assertThrows(IllegalArgumentException.class, () -> new BossAttackTiming(0, 0, 0, 0));
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss(server, "recovery_only"), PHASE);
            List<String> calls = new ArrayList<>();
            BossAttack attack = new BossAttack() {
                @Override public ResourceLocation id() { return ATTACK; }
                @Override public BossAttackTiming timing() { return new BossAttackTiming(0, 0, 2, 0); }
                @Override public void onStart(BossAttackContext context) {
                    calls.add("start:" + context.state() + ":" + context.stateTicks());
                }
                @Override public void onTick(BossAttackContext context) {
                    calls.add(context.state() + ":" + context.stateTicks());
                }
                @Override public void onEnd(BossAttackContext context, BossAttackEndReason reason) {
                    calls.add("end:" + reason);
                }
            };

            fight.attacks().start(attack, null);
            advance(server, fight, 1);
            advance(server, fight, 1);
            assertEquals(List.of("start:RECOVERY:0", "RECOVERY:1", "end:COMPLETED"), calls);
        });
    }

    @Test
    void cancellingFromTheStartedEventCannotRunOnStartAfterOnEnd(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss(server, "started_cancel"), PHASE);
            List<String> calls = new ArrayList<>();
            BossAttack attack = new BossAttack() {
                @Override public ResourceLocation id() { return ATTACK; }
                @Override public BossAttackTiming timing() { return new BossAttackTiming(1, 1, 1, 0); }
                @Override public void onStart(BossAttackContext context) { calls.add("start"); }
                @Override public void onEnd(BossAttackContext context, BossAttackEndReason reason) {
                    calls.add("end:" + reason);
                }
            };
            class CancelStarted {
                @SubscribeEvent public void started(BossAttackEvent.Started event) {
                    if (event.getAttack().attackId().equals(ATTACK)) fight.attacks().cancel();
                }
            }
            CancelStarted listener = new CancelStarted();
            NeoForge.EVENT_BUS.register(listener);
            try {
                assertEquals(BossAttackStartResult.STARTED, fight.attacks().start(attack, null));
                assertEquals(List.of("end:CANCELLED"), calls);
                assertTrue(fight.attacks().activeAttack().isEmpty());
                assertEquals(1, fight.snapshot().attacks().get(ATTACK).cancellations());
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
    }

    @Test
    void aFailedStartedListenerStillCancelsAndCleansUpTheAttack(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss(server, "started_failure"), PHASE);
            List<String> calls = new ArrayList<>();
            BossAttack attack = new BossAttack() {
                @Override public ResourceLocation id() { return ATTACK; }
                @Override public BossAttackTiming timing() { return new BossAttackTiming(1, 1, 1, 0); }
                @Override public void onStart(BossAttackContext context) { calls.add("start"); }
                @Override public void onEnd(BossAttackContext context, BossAttackEndReason reason) {
                    calls.add("end:" + reason);
                }
            };
            class FailStarted {
                @SubscribeEvent public void started(BossAttackEvent.Started event) {
                    if (event.getAttack().attackId().equals(ATTACK)) throw new IllegalStateException("test listener failure");
                }
            }
            FailStarted listener = new FailStarted();
            NeoForge.EVENT_BUS.register(listener);
            try {
                assertEquals(BossAttackStartResult.STARTED, fight.attacks().start(attack, null));
                assertEquals(List.of("end:CANCELLED"), calls);
                assertTrue(fight.attacks().activeAttack().isEmpty());
                assertEquals(1, fight.snapshot().attacks().get(ATTACK).cancellations());
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
    }

    @Test
    void bossOriginsCreditDirectProjectileAndSummonDamageToTheAttack(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "origins");
            ServerPlayer target = player(server, "origin_target");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            AtomicReference<BossAttackContext> context = new AtomicReference<>();
            BossAttack attack = new BossAttack() {
                @Override public ResourceLocation id() { return ATTACK; }
                @Override public BossAttackTiming timing() { return new BossAttackTiming(0, 20, 0, 0); }
                @Override public void onStart(BossAttackContext current) { context.set(current); }
            };
            assertEquals(BossAttackStartResult.STARTED, fight.attacks().start(attack, target));
            assertEquals(target, context.get().target());

            assertTrue(hit(target, target.damageSources().mobAttack(boss), 2));
            Snowball projectile = new Snowball(server.overworld(), boss);
            context.get().markOrigin(projectile);
            assertTrue(hit(target, source(server.overworld(), projectile, boss), 3));
            Cow summon = track(new Cow(EntityType.COW, server.overworld()));
            LivingEntity marked = context.get().markOrigin(summon);
            assertTrue(hit(target, source(server.overworld(), marked, marked), 4));

            var participant = fight.snapshot().participants().get(target.getUUID());
            assertEquals(3, participant.damageTakenFromBoss().hits());
            assertEquals(9, participant.damageTakenFromBoss().raw(), 1.0e-6);
            assertEquals(3, fight.snapshot().attacks().get(ATTACK).damage().hits());
            assertEquals(9, fight.snapshot().attacks().get(ATTACK).damage().raw(), 1.0e-6);
            assertEquals(9, fight.snapshot().outgoingDamage().raw(), 1.0e-6);
        });
    }
}
