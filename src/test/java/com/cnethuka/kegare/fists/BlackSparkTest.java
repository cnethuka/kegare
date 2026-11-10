package com.cnethuka.kegare.fists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Black Spark (DESIGN §3.28): the try, the secret offset, the window's factors, the power with no cap, bosses, and the first Insight. */
@ExtendWith(EphemeralTestServerProvider.class)
class BlackSparkTest {
    private static final double EPS = 1.0e-3;

    /** A swing at {@code target} and a hit of {@code damage}, the way a real attack goes: the swing first, then the damage. */
    static float swing(ServerPlayer attacker, LivingEntity target, float damage) {
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(attacker, target));
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(attacker.damageSources().playerAttack(attacker), damage);
        return before - target.getHealth();
    }

    static <T extends LivingEntity> T place(ServerLevel level, T mob, double x, double z) {
        mob.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), z, 0f, 0f);
        mob.setNoGravity(true);
        if (mob instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    @Test
    void thePowerIsCanonsExponentWithNoCap() {
        double scale = 4, exponent = 2.5;
        assertEquals(Math.pow(2, 1.5), BlackSpark.multiplier(0, scale, exponent), 1.0e-9, "s = 2: about 2.8x");
        assertEquals(Math.pow(3, 1.5), BlackSpark.multiplier(4, scale, exponent), 1.0e-9, "s = 3: about 5.2x");
        assertEquals(8.0, BlackSpark.multiplier(8, scale, exponent), 1.0e-9, "s = 4 (a full netherite sword): 8x");
        assertEquals(Math.pow(252, 1.5), BlackSpark.multiplier(1000, scale, exponent), 1.0e-6, "no cap");
        // Damage is d x s^1.5 with s = 2 + d/4 (it equals s^2.5 only where d = s), so check that directly.
        for (double d : new double[] {1, 3, 7.2, 9.6, 14.4}) {
            assertEquals(d * Math.pow(2 + d / 4, 1.5), d * BlackSpark.multiplier(d, scale, exponent), 1.0e-9);
        }
    }

    @Test
    void theOffsetIsNeverSent() {
        List<String> spark = Arrays.stream(BlackSparkPayload.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(List.of("at", "direction", "color", "attackerId", "victimId"), spark, "where, which way, the color and who: nothing of the roll");
        List<String> fists = Arrays.stream(FistsPayload.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(List.of("entityId", "color", "flags"), fists);
        assertTrue(BlackSpark.lands(30, 20, 25) && !BlackSpark.lands(19, 20, 25) && !BlackSpark.lands(46, 20, 25), "[offset, offset + window]");
    }

    @Test
    void theWindowsFactorsEachMoveItTheRightWay(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = null;
            try {
                ServerPlayer p = FistsTest.player(server, "spark_window");
                ServerPlayer weaker = FistsTest.player(server, "spark_weaker");
                ServerPlayer stronger = FistsTest.player(server, "spark_stronger");
                MaliceService.adminSetCapacity(weaker, 200);
                MaliceService.adminSetCapacity(stronger, 5000);
                ServerPlayer severed = TestServer.damageable(server, "spark_severed");
                BirthService.rebirth(severed, OptionalInt.of(0));
                golem = place(level, new IronGolem(EntityType.IRON_GOLEM, level), 4.5, 8.5);

                // Your Malice: fuller is wider.
                MaliceService.refill(p);
                double full = SparkWindow.malice(p);
                MaliceService.setCurrent(p, 100);
                assertTrue(SparkWindow.malice(p) < full, "emptier: narrower");
                MaliceService.refill(p);

                // Your opponent's: stronger is narrower; a mob or a Severed player has none.
                assertTrue(SparkWindow.opponent(p, stronger) < SparkWindow.opponent(p, p), "stronger: narrower");
                assertTrue(SparkWindow.opponent(p, weaker) > SparkWindow.opponent(p, p), "weaker: wider");
                assertEquals(1.0, SparkWindow.opponent(p, p), EPS, "an equal: no change");
                assertEquals(SparkWindow.opponent(p, golem), SparkWindow.opponent(p, severed), EPS, "a mob and a Severed player alike");
                assertTrue(SparkWindow.opponent(p, severed) > 1.0, "and they can be hit");

                // Your body.
                double healthy = SparkWindow.body(p);
                p.setHealth(4f);
                assertTrue(SparkWindow.body(p) < healthy, "hurt: narrower");
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFoodLevel(2);
                assertTrue(SparkWindow.body(p) < healthy, "hungry: narrower");
                p.getFoodData().setFoodLevel(20);
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                assertTrue(SparkWindow.body(p) < healthy, "Weakness: narrower");
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                assertTrue(SparkWindow.body(p) < healthy, "Slowness: narrower");
                p.removeAllEffects();
                FreezeService.addStacks(p, 2, null);
                assertTrue(SparkWindow.body(p) < healthy, "Freeze stacks: narrower");
                FreezeService.clear(p);
                assertEquals(healthy, SparkWindow.body(p), EPS);

                // Temperature and humidity: small nudges, mild and dry the widest (an assumption).
                double mild = SparkWindow.weather(0.8, 0.0, false, false);
                assertEquals(1.0, mild, EPS);
                assertTrue(SparkWindow.weather(2.0, 0.0, false, false) < mild, "a desert: narrower");
                assertTrue(SparkWindow.weather(-0.5, 0.0, false, false) < mild, "the cold: narrower");
                assertTrue(SparkWindow.weather(0.8, 0.9, false, false) < mild, "humid: narrower");
                assertTrue(SparkWindow.weather(0.8, 0.4, true, false) < SparkWindow.weather(0.8, 0.4, false, false), "rain: narrower");
                assertTrue(SparkWindow.weather(0.8, 0.4, true, true) < SparkWindow.weather(0.8, 0.4, true, false), "a storm: narrower still");
                assertTrue(SparkWindow.weather(2.0, 1.0, true, true) > 0.85, "never more than a nudge");

                // Your Grade: higher is wider, never enough to guarantee it.
                double grade4 = SparkWindow.grade(p);
                GradeService.set(p, Grade.SPECIAL_GRADE);
                assertTrue(SparkWindow.grade(p) > grade4, "Special Grade: wider");
                SparkWindow best = SparkWindow.of(p, severed, true);
                assertTrue(best.ms() <= FistsConfig.MAX_WINDOW_SHARE.get() * FistsConfig.JITTER_MS.get() + EPS, "capped below the jitter: never certain");
                GradeService.set(p, Grade.GRADE_4);

                // The zone: wider.
                assertTrue(SparkWindow.of(p, golem, true).ms() > SparkWindow.of(p, golem, false).ms(), "in the zone: wider");
            } finally {
                if (golem != null) golem.discard();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aTryRightAfterFistsGoOnAndItsPower(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = null;
            WitherBoss boss = null;
            try {
                ServerPlayer p = FistsTest.player(server, "spark_try");
                FistsTest.slot(p);
                golem = place(level, new IronGolem(EntityType.IRON_GOLEM, level), 4.5, 8.5);
                double insight = Insight.get(p, InsightCategory.OUTPUT);

                // Fists off: no try at all.
                BlackSpark.testOffset = 0;
                assertEquals(1f, swing(p, golem, 1f), EPS, "a plain punch");

                // On, and a hit straight after, with the spark sitting at 0: a Black Spark on a bare-fisted punch.
                FistsTest.turnOn(p);
                float fist = (float) (1 + FistsConfig.FIST_BONUS.get());
                float sparked = swing(p, golem, 1f);
                assertEquals(fist * BlackSpark.multiplier(fist), sparked, EPS, "a punch with Fists, raised to the power");
                assertEquals(insight + 5, Insight.get(p, InsightCategory.OUTPUT), EPS, "first_black_spark: Output 5");

                // Only the first hit after turning on is a try.
                golem.setDeltaMovement(Vec3.ZERO);
                BlackSpark.testOffset = 0;
                assertEquals(fist, swing(p, golem, 1f), EPS, "the second hit: just Fists");

                // A fresh turn-on, and a miss: the gap (a fraction of a ms here) is before where the spark sat.
                CastOff.off(p);
                BlackSpark.testOffset = 100;
                assertEquals(fist, swing(p, golem, 1f), EPS, "missed");

                // A second one never pays the Insight again.
                CastOff.off(p);
                BlackSpark.testOffset = 0;
                swing(p, golem, 1f);
                assertEquals(insight + 5, Insight.get(p, InsightCategory.OUTPUT), EPS, "once ever");

                // With a Strike about to go off, the Strike's bonus counts on the scale.
                CastOff.off(p);
                BlackSpark.testOffset = 0;
                ReinforcedStrike.earn(p);
                float strike = swing(p, golem, 1f);
                double withStrike = fist * (1 + com.cnethuka.kegare.melee.MeleeConfig.STRIKE_BONUS.get());
                assertEquals(fist * BlackSpark.multiplier(withStrike) * (1 + com.cnethuka.kegare.melee.MeleeConfig.STRIKE_BONUS.get()), strike, 0.01,
                        "the Strike's hit on the scale, and its own multiplier after the defences as ever");
                SecondImpact.clear();

                // A boss takes it and is never moved.
                boss = place(level, new WitherBoss(EntityType.WITHER, level), 2.5, 11.5);
                boss.setInvulnerableTicks(0);
                boss.setDeltaMovement(Vec3.ZERO);
                CastOff.off(p);
                BlackSpark.testOffset = 0;
                float before = boss.getHealth();
                swing(p, boss, 1f);
                BlackSpark.knockBack();
                assertTrue(before - boss.getHealth() > fist * 2, "the boss took a Black Spark");
                assertEquals(0.4, boss.getDeltaMovement().horizontalDistance(), 1.0e-3, "and got none of its knockback, only what any hit does");

                // Forced (for filming): the next melee hit, whatever the timing.
                BlackSpark.force(p);
                golem.setHealth(golem.getMaxHealth());
                assertEquals(fist * BlackSpark.multiplier(fist), swing(p, golem, 1f), EPS, "forced");

                // It throws them back (a golem doesn't budge for anyone; a zombie does).
                net.minecraft.world.entity.monster.Zombie zombie = place(level, new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE, level), 4.5, 6.5);
                try {
                    BlackSpark.force(p);
                    swing(p, zombie, 1f);
                    BlackSpark.knockBack(); // the end of the tick
                    assertTrue(zombie.isAlive(), "setup: it lived");
                    assertTrue(zombie.getDeltaMovement().horizontalDistance() > 0.9, "thrown back hard enough to slam: " + zombie.getDeltaMovement());
                    assertEquals(0.4 / 2 + FistsConfig.KNOCKBACK.get(), zombie.getDeltaMovement().horizontalDistance(), 0.02, "on top of the hit's own");
                } finally {
                    zombie.discard();
                }
            } finally {
                if (golem != null) golem.discard();
                if (boss != null) boss.discard();
                BlackSpark.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    /** Fists off and on again, past the re-arm wait: a fresh try. */
    static final class CastOff {
        static void off(ServerPlayer p) {
            if (ReinforcedFists.isOn(p)) com.cnethuka.kegare.innatetechnique.CastManager.handleIntent(p, com.cnethuka.kegare.innatetechnique.CastIntent.CAST_PRESS);
            com.cnethuka.kegare.innatetechnique.InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            FistsTest.turnOn(p);
        }
    }
}
