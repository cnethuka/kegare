package com.cnethuka.kegare.reinforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.fists.Fists;
import com.cnethuka.kegare.fists.FistsConfig;
import com.cnethuka.kegare.fists.FistsIds;
import com.cnethuka.kegare.fists.ReinforcedFists;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.AbilityCategory;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Reinforcement (DESIGN §3.16): everyone's, Malice category, the toggle and its costs, both reductions, knockback, with Fists. */
@ExtendWith(EphemeralTestServerProvider.class)
class ReinforcementTest {
    private static final double EPS = 1.0e-3;

    static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(2.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), 8.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** Reinforcement in slot {@code slot}, selected. */
    static void slot(ServerPlayer p, int slot) {
        assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, slot, 0, Optional.of(ReinforcementIds.ABILITY)));
        InnateTechniqueService.select(p, slot);
    }

    static void turnOn(ServerPlayer p) {
        if (!ReinforcementAbility.isOn(p)) CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        assertTrue(ReinforcementAbility.isOn(p), "setup: Reinforcement on");
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    /** What a hit of {@code amount} of this kind took off. */
    private static float taken(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float amount) {
        p.invulnerableTime = 0;
        p.setHealth(p.getMaxHealth());
        float before = p.getHealth();
        p.hurt(source, amount);
        return before - p.getHealth();
    }

    @Test
    void everyMaliceUserHasItInTheMaliceTabAndSeveredNever(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "reinf_everyone");
                assertSame(AbilityCategory.MALICE, Reinforcement.REINFORCEMENT.category(), "a Malice ability");
                assertTrue(AbilityCategory.MALICE.abilities(null).contains(Reinforcement.REINFORCEMENT), "under the Malice tab");
                assertTrue(SharedAbilityService.isUnlocked(p, ReinforcementIds.ABILITY), "nothing to unlock");
                assertTrue(SharedAbilityService.unlockedIds(p).contains(ReinforcementIds.ABILITY), "and it's in what they're sent");
                slot(p, 1);
                assertSame(Reinforcement.REINFORCEMENT, LoadoutService.abilityAt(p, 1), "on the bar");

                ServerPlayer severed = TestServer.damageable(server, "reinf_severed");
                BirthService.rebirth(severed, OptionalInt.of(0));
                assertTrue(MaliceService.isSevered(severed), "setup: Severed");
                assertFalse(SharedAbilityService.isUnlocked(severed, ReinforcementIds.ABILITY), "no Malice, no Reinforcement");
                assertFalse(LoadoutService.edit(severed, LoadoutService.Action.SET, 1, 0, Optional.of(ReinforcementIds.ABILITY)) == LoadoutResult.OK,
                        "never on their bar");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theToggleCostsDrainsAndRearms(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "reinf_toggle");
                slot(p, 1);
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcementAbility.isOn(p), "on");
                assertEquals(before - ReinforcementConfig.ON_COST.get(), malice(p), EPS, "onCost up front");
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(before - ReinforcementConfig.ON_COST.get() - ReinforcementConfig.DRAIN.get(), malice(p), EPS, "a second's drain");

                Burnout.apply(p, 100);
                CastManager.tick(p);
                assertTrue(ReinforcementAbility.isOn(p), "through Burnout");
                Burnout.clear(p);

                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcementAbility.isOn(p), "off");
                assertEquals(ReinforcementConfig.ticks(ReinforcementConfig.REARM), InnateTechniqueService.cooldownRemaining(p, Reinforcement.REINFORCEMENT),
                        "rearmCooldown");
                double paid = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcementAbility.isOn(p), "not yet");
                assertEquals(paid, malice(p), EPS, "and nothing paid");
                TestServer.advanceGameTime(server, ReinforcementConfig.ticks(ReinforcementConfig.REARM));
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcementAbility.isOn(p), "re-armed");

                MaliceService.setCurrent(p, ReinforcementConfig.DRAIN.get() / 40.0);
                CastManager.tick(p);
                assertFalse(ReinforcementAbility.isOn(p), "out of Malice: off");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void lessDamageOfBothKindsAndLessKnockback(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "reinf_reduce");
                ServerPlayer caster = player(server, "reinf_caster");
                slot(p, 1);
                double kb = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                float physical = taken(p, p.damageSources().generic(), 4f);
                float technique = taken(p, RimeDamage.source(p.level(), caster, caster), 4f);
                assertEquals(4f, physical, EPS, "off: all of it");
                assertEquals(4f, technique, EPS, "off: all of it");

                turnOn(p);
                assertEquals(kb + ReinforcementConfig.KNOCKBACK_RESISTANCE.get(), p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS,
                        "knockbackResistance: force tolerance");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_PHYSICAL.get()), taken(p, p.damageSources().generic(), 4f), EPS,
                        "reductionPhysical");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_TECHNIQUE.get()), taken(p, RimeDamage.source(p.level(), caster, caster), 4f),
                        EPS, "reductionTechnique");
                assertEquals(4f, taken(p, p.damageSources().fellOutOfWorld(), 4f), EPS, "the void is untouched");

                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcementAbility.isOn(p), "off");
                assertEquals(kb, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS, "the knockback resistance goes with it");
                assertEquals(4f, taken(p, p.damageSources().generic(), 4f), EPS, "and the reduction");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itRunsWithFistsAndAddsNoDamage(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = new IronGolem(EntityType.IRON_GOLEM, level);
            golem.moveTo(4.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), 8.5, 0f, 0f);
            golem.setNoGravity(true);
            golem.setNoAi(true);
            level.addFreshEntity(golem);
            try {
                ServerPlayer p = player(server, "reinf_fists");
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 2, 0, Optional.of(FistsIds.ABILITY)));
                slot(p, 1);
                turnOn(p);
                golem.invulnerableTime = 0;
                float hp = golem.getHealth();
                golem.hurt(p.damageSources().playerAttack(p), 2f);
                assertEquals(2f, hp - golem.getHealth(), EPS, "Reinforcement adds no damage");

                InnateTechniqueService.select(p, 2);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcedFists.isOn(p), "Fists on");
                assertTrue(ReinforcementAbility.isOn(p), "and Reinforcement still on: both run");
                golem.invulnerableTime = 0;
                hp = golem.getHealth();
                golem.hurt(p.damageSources().playerAttack(p), 2f);
                assertEquals(2f * (1 + FistsConfig.FIST_BONUS.get()), hp - golem.getHealth(), EPS, "Fists' bonus, nothing more");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_PHYSICAL.get()), taken(p, p.damageSources().generic(), 4f), EPS,
                        "and the reduction with both on");
                double malice = malice(p);
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(malice - FistsConfig.DRAIN.get() - ReinforcementConfig.DRAIN.get(), malice(p), EPS, "both drain");
            } finally {
                golem.discard();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theAuraShowsToEveryoneFullerInAFight(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "reinf_aura");
                slot(p, 1);
                assertEquals(0, AuraTell.flags(p), "off: nothing");
                turnOn(p);
                assertEquals(AuraPayload.FULL, AuraTell.flags(p), "on: the full aura");
                AuraTell.sync(p);
                assertEquals(AuraPayload.FULL, AuraTell.sentTo(p, p), "sent to them");
                CombatHelper.markCombat(p);
                assertEquals(AuraPayload.FULL | AuraPayload.FIGHT, AuraTell.flags(p), "in a fight: fuller");
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(0, AuraTell.flags(p), "off again: nothing");
            } finally {
                AuraTell.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
