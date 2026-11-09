package com.cnethuka.kegare.backgroundtask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.reinforcement.AuraPayload;
import com.cnethuka.kegare.reinforcement.Reinforcement;
import com.cnethuka.kegare.reinforcement.ReinforcementAbility;
import com.cnethuka.kegare.reinforcement.ReinforcementConfig;
import com.cnethuka.kegare.reinforcement.ReinforcementIds;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Background Reinforcement (DESIGN §3.31): Grade 3, a share of the ability's strength, never stacking with it. */
@ExtendWith(EphemeralTestServerProvider.class)
class BackgroundReinforcementTest {
    private static final double EPS = 1.0e-3;

    static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(0.5, 120, 0.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static float taken(ServerPlayer p, DamageSource source) {
        p.invulnerableTime = 0;
        p.setHealth(p.getMaxHealth());
        float before = p.getHealth();
        p.hurt(source, 4f);
        return before - p.getHealth();
    }

    @Test
    void unlocksAtGrade3AndStays(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "bgreinf_grade");
                assertFalse(Unlocks.has(p, BackgroundReinforcement.ID), "Grade 4: locked");
                assertEquals(BackgroundTaskResult.LOCKED, BackgroundTaskService.set(p, 0, BackgroundReinforcement.ID));
                GradeService.set(p, Grade.GRADE_3);
                assertTrue(Unlocks.has(p, BackgroundReinforcement.ID), "Grade 3: unlocked");
                InnateTechniqueService.setInnateTechnique(p, com.cnethuka.kegare.innate_technique.swap.Swap.SWAP.get());
                assertTrue(Unlocks.has(p, BackgroundReinforcement.ID), "kept through a reroll");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aShareOfTheAbilityNeverStackingWithIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "bgreinf_strength");
                ServerPlayer caster = player(server, "bgreinf_caster");
                double kb = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                double s = BackgroundTaskConfig.REINFORCEMENT_STRENGTH.get();
                assertTrue(BackgroundTaskConfig.REINFORCEMENT_DRAIN.get() < ReinforcementConfig.DRAIN.get(), "a smaller drain");

                // Slots first: neither the bar nor the tasks change once the hits below have put them in a fight.
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(ReinforcementIds.ABILITY)));
                Unlocks.grant(p, BackgroundReinforcement.ID);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundReinforcement.ID));
                double malice = MaliceService.get(p).current();
                for (int i = 0; i < 20; i++) BackgroundTaskService.tick(p);
                assertEquals(malice - BackgroundTaskConfig.REINFORCEMENT_DRAIN.get(), MaliceService.get(p).current(), EPS, "its drain");
                assertEquals(s, Reinforcement.strength(p), EPS, "backgroundStrength");
                assertEquals(kb + ReinforcementConfig.KNOCKBACK_RESISTANCE.get() * s, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS,
                        "that share of the knockback resistance");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_PHYSICAL.get() * s), taken(p, p.damageSources().generic()), EPS,
                        "of the physical reduction");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_TECHNIQUE.get() * s), taken(p, RimeDamage.source(p.level(), caster, caster)),
                        EPS, "of the technique reduction");
                int look = AuraPayload.FULL | AuraPayload.CALM; // (the hits above put them in a fight, which only adds FIGHT)
                assertEquals(AuraPayload.CALM, com.cnethuka.kegare.reinforcement.AuraTellAccess.flags(p) & look, "the calm aura, shown to everyone");

                // The ability on as well: the stronger applies, they don't stack.
                InnateTechniqueService.select(p, 1);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcementAbility.isOn(p), "setup: the ability on");
                BackgroundTaskService.tick(p);
                assertEquals(1.0, Reinforcement.strength(p), EPS, "the ability's, not more");
                assertEquals(kb + ReinforcementConfig.KNOCKBACK_RESISTANCE.get(), p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS, "no stacking");
                assertEquals(4f * (1 - ReinforcementConfig.REDUCTION_PHYSICAL.get()), taken(p, p.damageSources().generic()), EPS, "no stacking");
                assertEquals(AuraPayload.FULL, com.cnethuka.kegare.reinforcement.AuraTellAccess.flags(p) & look, "the full aura");

                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcementAbility.isOn(p), "the ability off");
                assertEquals(kb + ReinforcementConfig.KNOCKBACK_RESISTANCE.get() * s, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS,
                        "back to the background's");
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);
                assertEquals(0.0, Reinforcement.strength(p), EPS, "off: nothing");
                assertEquals(kb, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), EPS, "and no knockback resistance");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
