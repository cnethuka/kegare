package com.cnethuka.kegare.severed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A Severed player's body (DESIGN §3.27): Strength on their melee hits only, the Toughness split (physical, technique, falls) with the
 * void, /kill and starving untouched, Stamina on normal drain and healing, and Senses (who's sensed, through anything, and never for
 * anyone who isn't Severed).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SeveredBodyTest {
    private static final double Y = 120;
    private static final float EPS = 1.0e-3f;

    static ServerPlayer severed(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(0));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    static ServerPlayer maliceUser(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static float took(LivingEntity victim, DamageSource source, float amount) {
        victim.setHealth(victim.getMaxHealth());
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
        return victim.getMaxHealth() - victim.getHealth();
    }

    @Test
    void strengthAndToughness(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            try {
                ServerPlayer s = severed(server, level, "sev_body");
                ServerPlayer m = maliceUser(server, level, "sev_body_other");
                s.moveTo(0.5, Y, 0.5);
                m.moveTo(2.5, Y, 0.5);
                assertTrue(Severed.is(s) && !Severed.is(m), "setup");
                float strength = SeveredConfig.MELEE_DAMAGE.get().floatValue();

                // Strength: a Severed hand hits harder; a Malice user's doesn't; a technique source isn't melee.
                assertEquals(4f * strength, took(m, s.damageSources().playerAttack(s), 4f), EPS, "Severed melee");
                ServerPlayer m2 = maliceUser(server, level, "sev_body_third");
                m2.moveTo(4.5, Y, 0.5);
                assertEquals(4f, took(m, m.damageSources().playerAttack(m2), 4f), EPS, "a Malice user's hit is plain");

                // Toughness: physical, technique and falls each have their share; the void, /kill and starving are untouched.
                assertEquals(8f * SeveredConfig.TAKEN_PHYSICAL.get().floatValue(), took(s, s.damageSources().playerAttack(m), 8f), EPS, "a hit");
                assertEquals(8f * SeveredConfig.TAKEN_PHYSICAL.get().floatValue(), took(s, s.damageSources().inFire(), 8f), EPS, "fire is physical");
                assertEquals(10f * SeveredConfig.TAKEN_TECHNIQUE.get().floatValue(), took(s, RimeDamage.source(level, m, m), 10f), EPS, "a technique");
                assertEquals(10f * SeveredConfig.TAKEN_FALL.get().floatValue(), took(s, s.damageSources().fall(), 10f), EPS, "a fall");
                assertTrue(SeveredConfig.TAKEN_FALL.get() < SeveredConfig.TAKEN_PHYSICAL.get()
                        && SeveredConfig.TAKEN_PHYSICAL.get() < SeveredConfig.TAKEN_TECHNIQUE.get(), "falls least, then hits, then techniques");
                assertEquals(10f, took(s, s.damageSources().fellOutOfWorld(), 10f), EPS, "the void");
                assertEquals(10f, took(s, s.damageSources().genericKill(), 10f), EPS, "/kill");
                assertEquals(10f, took(s, s.damageSources().starve(), 10f), EPS, "starving");
                assertEquals(8f, took(m, m.damageSources().fall(), 8f), EPS, "a Malice user takes it all");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void staminaOnNormalDrainAndHealing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            boolean regen = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
            try {
                ServerPlayer s = severed(server, level, "sev_stamina");
                ServerPlayer m = maliceUser(server, level, "sev_stamina_other");
                float share = SeveredConfig.EXHAUSTION.get().floatValue();
                for (ServerPlayer p : List.of(s, m)) p.getFoodData().setExhaustion(0f);
                s.causeFoodExhaustion(1f);
                m.causeFoodExhaustion(1f);
                assertEquals(share, s.getFoodData().getExhaustionLevel(), EPS, "sprinting, jumping, attacking, mining: less");
                assertEquals(1f, m.getFoodData().getExhaustionLevel(), EPS, "a Malice user feels it all");

                // Healing from a full food bar: the saturation it burns costs them less too.
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, server);
                for (ServerPlayer p : List.of(s, m)) {
                    p.getFoodData().setFoodLevel(20);
                    p.getFoodData().setSaturation(5f);
                    p.getFoodData().setExhaustion(0f);
                    p.setHealth(10f);
                    for (int i = 0; i < 10; i++) p.getFoodData().tick(p);
                }
                assertTrue(s.getHealth() > 10f && m.getHealth() > 10f, "both healed: healing stays vanilla");
                assertEquals(m.getFoodData().getExhaustionLevel() * share, s.getFoodData().getExhaustionLevel(), EPS, "healing's drain, cut");
            } finally {
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(regen, server);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void sensesFindEveryBodyInRangeOnlyForTheSevered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Pig near = EntityType.PIG.create(level), hidden = EntityType.PIG.create(level), far = EntityType.PIG.create(level);
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            try {
                ServerPlayer s = severed(server, level, "sev_senses");
                ServerPlayer m = maliceUser(server, level, "sev_senses_other");
                ServerPlayer watcher = maliceUser(server, level, "sev_senses_spectator");
                double range = SeveredConfig.SENSES_RANGE.get();
                s.moveTo(0.5, Y, 0.5);
                m.moveTo(6.5, Y, 0.5);
                watcher.moveTo(3.5, Y, 3.5);
                watcher.setGameMode(GameType.SPECTATOR);
                near.moveTo(0.5, Y, 8.5);
                hidden.moveTo(7.5, Y, 7.5);
                hidden.setInvisible(true); // what Invisibility sets (the world doesn't tick here to set it)
                far.moveTo(0.5, Y, 0.5 + range + 2);
                stand.moveTo(2.5, Y, 2.5);
                for (Entity e : List.of(near, hidden, far, stand)) {
                    e.setNoGravity(true);
                    level.addFreshEntity(e);
                }
                List<LivingEntity> sensed = Senses.sensed(s);
                assertTrue(sensed.contains(m), "a player, standing still");
                assertTrue(sensed.contains(near), "a mob");
                assertTrue(sensed.contains(hidden) && hidden.isInvisible(), "an invisible one too");
                assertFalse(sensed.contains(far), "out of range");
                assertFalse(sensed.contains(stand), "not an armor stand");
                assertFalse(sensed.contains(watcher), "not a spectator");
                assertFalse(sensed.contains(s), "not themself");
                assertEquals(m, sensed.get(0), "nearest first");

                near.moveTo(0.5, Y, 12.5); // moving: still there
                assertTrue(Senses.sensed(s).contains(near), "moving");
                near.moveTo(0.5, Y, 0.5 + range + 4);
                assertFalse(Senses.sensed(s).contains(near), "gone out of range");

                assertTrue(Senses.sensed(m).isEmpty(), "never for a Malice user");
            } finally {
                for (Entity e : List.of(near, hidden, far, stand)) e.discard();
                TestServer.removePlayers(server);
            }
        });
    }
}
