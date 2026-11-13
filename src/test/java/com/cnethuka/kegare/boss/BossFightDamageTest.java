package com.cnethuka.kegare.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.boss.BossFightEndReason;
import com.cnethuka.kegare.api.boss.BossFights;
import com.cnethuka.kegare.progression.CastOrigin;
import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Snowball;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class BossFightDamageTest extends BossTestSupport {
    private static final ResourceLocation DELAYED = ResourceLocation.fromNamespaceAndPath("kegare_test", "delayed");

    @Test
    void directProjectileAndSummonHitsShareAbilityIdsWithoutCurrentTechniqueLookup(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "damage_credit");
            ServerPlayer owner = player(server, "damage_owner");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            assertTrue(hit(boss, owner.damageSources().playerAttack(owner), 2));

            Snowball projectile = new Snowball(server.overworld(), owner);
            projectile.setData(KegareAttachments.CAST_ORIGIN, new CastOrigin(owner.getUUID(), DELAYED));
            assertTrue(hit(boss, source(server.overworld(), projectile, owner), 3));

            Cow summon = track(new Cow(EntityType.COW, server.overworld()));
            summon.setData(KegareAttachments.CAST_ORIGIN, new CastOrigin(owner.getUUID(), DELAYED));
            assertTrue(hit(boss, source(server.overworld(), summon, summon), 4));

            var participant = fight.snapshot().participants().get(owner.getUUID());
            assertNotNull(participant, "player damage automatically joins the encounter");
            assertEquals(3, participant.damageToBoss().hits());
            assertEquals(9, participant.damageToBoss().raw(), 1.0e-6);
            assertEquals(7, participant.damageByAbility().get(DELAYED).raw(), 1.0e-6);
            assertEquals(2, participant.damageByAbility().get(DELAYED).hits());
            assertEquals(participant.firstActivityTick(), participant.lastActivityTick());
        });
    }

    @Test
    void simultaneousAttackersStaySeparate(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "simultaneous");
            ServerPlayer first = player(server, "simultaneous_a");
            ServerPlayer second = player(server, "simultaneous_b");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            assertTrue(hit(boss, first.damageSources().playerAttack(first), 3));
            assertTrue(hit(boss, second.damageSources().playerAttack(second), 5));

            assertEquals(3, fight.snapshot().participants().get(first.getUUID()).damageToBoss().raw(), 1.0e-6);
            assertEquals(5, fight.snapshot().participants().get(second.getUUID()).damageToBoss().raw(), 1.0e-6);
            assertEquals(8, fight.snapshot().incomingDamage().raw(), 1.0e-6);
            assertEquals(2, fight.snapshot().incomingDamage().hits());
            assertEquals(2, fight.snapshot().participants().size());
        });
    }

    @Test
    void nestedHitsUsingTheSameSourceAreMatchedByTheirDamageSequence(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "nested_damage");
            ServerPlayer attacker = player(server, "nested_damage_attacker");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            var source = attacker.damageSources().playerAttack(attacker);
            DamageContainer outer = new DamageContainer(source, 10);
            DamageContainer inner = new DamageContainer(source, 5);

            BossDamageTracker.pre(new LivingDamageEvent.Pre(boss, outer));
            boss.setHealth(boss.getHealth() - 10);
            BossDamageTracker.pre(new LivingDamageEvent.Pre(boss, inner));
            BossDamageTracker.post(new LivingDamageEvent.Post(boss, inner));
            boss.setHealth(boss.getHealth() - 5);
            BossDamageTracker.post(new LivingDamageEvent.Post(boss, outer));

            assertEquals(15, fight.snapshot().incomingDamage().raw(), 1.0e-6);
            assertEquals(15, fight.snapshot().incomingDamage().effective(), 1.0e-6);
            assertEquals(2, fight.snapshot().incomingDamage().hits());
            assertEquals(15, fight.snapshot().participants().get(attacker.getUUID()).damageToBoss().raw(), 1.0e-6);
        });
    }

    @Test
    void anAbortedDamageSequenceCannotClaimALaterHitAndIsClearedAfterTheTick(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "aborted_damage");
            ServerPlayer attacker = player(server, "aborted_damage_attacker");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            var source = attacker.damageSources().playerAttack(attacker);
            DamageContainer aborted = new DamageContainer(source, 40);
            DamageContainer current = new DamageContainer(source, 3);

            BossDamageTracker.pre(new LivingDamageEvent.Pre(boss, aborted));
            BossDamageTracker.pre(new LivingDamageEvent.Pre(boss, current));
            BossDamageTracker.post(new LivingDamageEvent.Post(boss, current));
            assertEquals(3, fight.snapshot().incomingDamage().raw(), 1.0e-6);
            assertEquals(1, fight.snapshot().incomingDamage().hits());

            BossRuntime.tick(server);
            BossDamageTracker.post(new LivingDamageEvent.Post(boss, aborted));
            assertEquals(1, fight.snapshot().incomingDamage().hits(), "stale captures are discarded at tick end");
        });
    }

    @Test
    void overkillIsHealthClampedAndDeathEndsAsDefeatedWithAKillingBlow(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            WitherBoss boss = boss(server, "overkill");
            ServerPlayer attacker = player(server, "overkill_attacker");
            BossFightImpl fight = (BossFightImpl) BossFights.start(boss, PHASE);
            boss.setHealth(3);
            assertTrue(hit(boss, attacker.damageSources().playerAttack(attacker), 50));

            var report = fight.snapshot();
            assertEquals(BossFightEndReason.DEFEATED, report.endReason());
            assertEquals(50, report.incomingDamage().raw(), 1.0e-6);
            assertEquals(3, report.incomingDamage().effective(), 1.0e-6);
            assertTrue(report.incomingDamage().postMitigation() >= report.incomingDamage().effective());
            assertTrue(report.endTick() >= report.startTick());
            assertTrue(report.durationTicks() >= 0);
            assertNotNull(report.killingBlow());
            assertEquals(attacker.getUUID(), report.killingBlow().playerId());
            assertTrue(BossFights.find(boss).isEmpty());
            assertTrue(BossFights.reset(boss).isEmpty(), "death cleanup is idempotent");
        });
    }
}
