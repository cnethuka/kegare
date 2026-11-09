package com.cnethuka.kegare.fists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeConfig;
import com.cnethuka.kegare.melee.MeleeLayer;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import com.cnethuka.kegare.player.BirthService;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Reinforced Fists (DESIGN §3.28): everyone's, the toggle and its costs, the bonuses, and the second impact. */
@ExtendWith(EphemeralTestServerProvider.class)
class FistsTest {
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

    /** Fists in slot 1, selected. */
    static void slot(ServerPlayer p) {
        assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(FistsIds.ABILITY)));
        InnateTechniqueService.select(p, 1);
    }

    static void turnOn(ServerPlayer p) {
        if (!ReinforcedFists.isOn(p)) CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        assertTrue(ReinforcedFists.isOn(p), "setup: Fists on");
    }

    static IronGolem golem(ServerLevel level, double x, double z) {
        IronGolem golem = new IronGolem(EntityType.IRON_GOLEM, level);
        golem.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), z, 0f, 0f);
        golem.setNoGravity(true);
        golem.setNoAi(true);
        level.addFreshEntity(golem);
        return golem;
    }

    static float melee(LivingEntity victim, ServerPlayer attacker, float amount) {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        victim.hurt(attacker.damageSources().playerAttack(attacker), amount);
        return before - victim.getHealth();
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    @Test
    void everyMaliceUserHasItAndSeveredNever(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "fists_everyone");
                assertTrue(SharedAbilityService.isUnlocked(p, FistsIds.ABILITY), "nothing to unlock");
                assertTrue(SharedAbilityService.unlockedIds(p).contains(FistsIds.ABILITY), "and it's in what they're sent");
                assertFalse(SharedAbilityService.unlock(p, Fists.REINFORCED_FISTS), "there's nothing to unlock");
                slot(p);
                assertSame(Fists.REINFORCED_FISTS, LoadoutService.abilityAt(p, 1), "in place of an ability");

                ServerPlayer severed = TestServer.damageable(server, "fists_severed");
                BirthService.rebirth(severed, OptionalInt.of(0));
                assertTrue(MaliceService.isSevered(severed), "setup: Severed");
                assertFalse(SharedAbilityService.isUnlocked(severed, FistsIds.ABILITY), "no Malice, no Fists");
                assertFalse(LoadoutService.edit(severed, LoadoutService.Action.SET, 1, 0, Optional.of(FistsIds.ABILITY)) == LoadoutResult.OK, "never on their bar");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theToggleCostsDrainsAndRearms(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "fists_toggle");
                slot(p);
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcedFists.isOn(p), "on");
                assertEquals(before - FistsConfig.ON_COST.get(), malice(p), EPS, "onCost up front");
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(before - FistsConfig.ON_COST.get() - FistsConfig.DRAIN.get(), malice(p), EPS, "a second's drain");

                // Burnout doesn't stop it (it's shared).
                Burnout.apply(p, 100);
                CastManager.tick(p);
                assertTrue(ReinforcedFists.isOn(p), "through Burnout");
                Burnout.clear(p);

                // Off, then the re-arm wait before it lights again.
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcedFists.isOn(p), "off");
                assertEquals(FistsConfig.ticks(FistsConfig.REARM), InnateTechniqueService.cooldownRemaining(p, Fists.REINFORCED_FISTS), "rearmCooldown");
                double paid = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(ReinforcedFists.isOn(p), "not yet");
                assertEquals(paid, malice(p), EPS, "and nothing paid");
                TestServer.advanceGameTime(server, FistsConfig.ticks(FistsConfig.REARM));
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(ReinforcedFists.isOn(p), "re-armed");

                // It runs out with the Malice.
                MaliceService.setCurrent(p, FistsConfig.DRAIN.get() / 40.0);
                CastManager.tick(p);
                assertFalse(ReinforcedFists.isOn(p), "out of Malice: off");

                // Leaving Technique Mode is the client's alone: nothing on the server turns it off. Death and logout end every toggle
                // (PlayerEvents skips the test's fake players, so that's checked live).
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void meleeHitsDealMoreAndTheEmptyHandMost(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = null;
            try {
                ServerPlayer p = player(server, "fists_bonus");
                slot(p); // before any hit: the bar can't change in a fight
                golem = golem(level, 4.5, 8.5);
                assertEquals(2f, melee(golem, p, 2f), EPS, "off: a normal hit");
                turnOn(p);
                assertEquals(2f * (1 + FistsConfig.FIST_BONUS.get()), melee(golem, p, 2f), EPS, "an empty hand: fistBonus");
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                assertEquals(2f * (1 + FistsConfig.WEAPON_BONUS.get()), melee(golem, p, 2f), EPS, "anything held: weaponBonus");
                assertTrue(FistsConfig.FIST_BONUS.get() > FistsConfig.WEAPON_BONUS.get(), "the fist's is bigger");
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // Not a melee hit: no bonus.
                golem.invulnerableTime = 0;
                float hp = golem.getHealth();
                golem.hurt(level.damageSources().thorns(p), 2f);
                assertEquals(2f, hp - golem.getHealth(), EPS, "thorns isn't a fist");
                // And nothing comes a moment later from a plain hit.
                assertEquals(0, SecondImpact.pending(), "no second impact without a Strike");
            } finally {
                if (golem != null) golem.discard();
                SecondImpact.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aStrikeWithFistsOnHitsAgainAMomentLater(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = null;
            try {
                ServerPlayer p = player(server, "fists_second");
                slot(p); // before any hit: the bar can't change in a fight
                golem = golem(level, 4.5, 8.5);

                // A Strike without Fists: nothing after it.
                ReinforcedStrike.earn(p);
                melee(golem, p, 2f);
                assertEquals(0, SecondImpact.pending(), "Fists off: no second impact");

                turnOn(p);
                ReinforcedStrike.earn(p);
                float strike = melee(golem, p, 2f);
                float expected = (float) (2f * (1 + FistsConfig.FIST_BONUS.get()) * (1 + MeleeConfig.STRIKE_BONUS.get()));
                assertEquals(expected, strike, EPS, "the Strike, with the fist's bonus");
                assertEquals(1, SecondImpact.pending(), "one coming");

                int delay = FistsConfig.ticks(FistsConfig.SECOND_IMPACT_DELAY);
                golem.invulnerableTime = 14; // what's left of the Strike's hurt cooldown by then
                float hp = golem.getHealth();
                TestServer.advanceGameTime(server, delay - 1);
                SecondImpact.tick(level);
                assertEquals(hp, golem.getHealth(), EPS, "not yet");
                TestServer.advanceGameTime(server, 1);
                SecondImpact.tick(level);
                assertEquals(expected * FistsConfig.SECOND_IMPACT_DAMAGE.get(), hp - golem.getHealth(), EPS, "secondImpactDamage of the Strike, through the cooldown");
                assertEquals(14, golem.invulnerableTime, "and the hurt cooldown isn't lengthened");
                assertEquals(0, SecondImpact.pending());
                assertTrue(SecondImpact.source(level, p).is(DamageTypeTags.NO_KNOCKBACK), "it never moves anyone (bosses)");
            } finally {
                if (golem != null) golem.discard();
                SecondImpact.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
