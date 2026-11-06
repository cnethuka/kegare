package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Puppets;
import com.cnethuka.kegare.innate_technique.rime.FrostShardEntity;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The shared hit path on real casts: what lands, what doesn't, and that every technique reaches it. */
@ExtendWith(EphemeralTestServerProvider.class)
class LandingsTest {
    private static final double Y = 80;

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name, InnateTechnique technique) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, technique);
        MaliceService.refill(p);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** Stands them 6 blocks apart, the caster looking straight at the target. */
    private static void face(ServerPlayer caster, ServerPlayer target) {
        caster.moveTo(0.5, Y, 0.5, -90f, 0f);
        caster.setYHeadRot(-90f);
        target.moveTo(6.5, Y, 0.5, 90f, 0f);
    }

    private static List<String> landed(ServerPlayer caster, ServerPlayer target) {
        Fight fight = FightTracker.between(caster, target);
        return fight == null ? List.of() : fight.side(caster.getUUID()).abilities().stream().map(Fight.Use::id).toList();
    }

    @Test
    void aProjectileLandsForTheAbilityThatFiredIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer caster = sorcerer(server, "shard_caster", Rime.RIME.get());
            ServerPlayer target = sorcerer(server, "shard_target", Rime.RIME.get());
            face(caster, target);
            InnateTechniqueService.select(caster, 0);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertTrue(landed(caster, target).isEmpty(), "casting isn't landing");

            FrostShardEntity shard = level.getEntitiesOfClass(FrostShardEntity.class, new AABB(-16, 60, -16, 16, 100, 16)).get(0);
            assertTrue(target.hurt(RimeDamage.source(level, shard, caster), 3f), "setup: the shard hits");
            assertEquals(List.of(Rime.FROST_SHARD.id().toString()), landed(caster, target), "traced through the shard to its ability");
            shard.discard();
        });
    }

    @Test
    void aPuppetsHitLandsForPuppetCreationAndHittingYourOwnEarnsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer owner = sorcerer(server, "landing_owner", PuppetManipulation.PUPPET_MANIPULATION.get());
            ServerPlayer enemy = sorcerer(server, "landing_enemy", Rime.RIME.get());
            face(owner, enemy);
            // Spawned the way the cast spawns it: inside the cast's scope.
            PuppetEntity[] holder = new PuppetEntity[1];
            CastScope.run(owner, PuppetManipulation.PUPPET_CREATION, () -> holder[0] = Puppets.create(owner, PuppetTier.TAP));
            PuppetEntity puppet = holder[0];
            assertTrue(puppet.hasData(com.cnethuka.kegare.registry.KegareAttachments.CAST_ORIGIN), "what a cast spawns is tagged");
            assertTrue(enemy.hurt(enemy.damageSources().mobAttack(puppet), 2f), "setup");
            assertEquals(List.of(PuppetManipulation.PUPPET_CREATION.id().toString()), landed(owner, enemy));
            assertEquals(0.0, RefinementService.get(owner).xp(), 1.0e-6, "on the fight's record, but the owner has done nothing to earn from it");

            puppet.invulnerableTime = 0;
            puppet.hurt(owner.damageSources().playerAttack(owner), 1f);
            assertEquals(0.0, GradeService.get(owner).xp(), 1.0e-6, "your own puppet gives nothing");
            Landings.land(owner, puppet, PuppetManipulation.CURIO_BURST);
            assertEquals(1, landed(owner, enemy).size(), "nor does an ability landed on it");
        });
    }

    @Test
    void swapsLandWithoutDealingDamage(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "swap_caster", Swap.SWAP.get());
            ServerPlayer target = sorcerer(server, "swap_target", Rime.RIME.get());
            face(caster, target);
            InnateTechniqueService.select(caster, 0); // Positions
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(List.of(Swap.POSITIONS.id().toString()), landed(caster, target), "no damage, but it landed: a fight is open");
            assertEquals(0.0, FightTracker.between(caster, target).side(caster.getUUID()).dealt());
        });
    }

    @Test
    void anAirCastLandsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "air_caster", Swap.SWAP.get());
            caster.moveTo(0.5, Y, 0.5);
            double before = MaliceService.get(caster).current();
            InnateTechniqueService.select(caster, 2); // Anchor: no target of its own, thrown with nobody around
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertTrue(MaliceService.get(caster).current() < before, "setup: it went off");
            assertTrue(FightTracker.of(caster).isEmpty());
        });
    }

    @Test
    void aNoTargetAbilityCountsOnlyInARealFight(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "notarget_caster", Swap.SWAP.get());
            ServerPlayer enemy = sorcerer(server, "notarget_enemy", Rime.RIME.get());
            caster.moveTo(0.5, Y, 0.5);
            enemy.moveTo(6.5, Y, 0.5);
            assertTrue(enemy.hurt(enemy.damageSources().playerAttack(caster), 1f), "setup: they've traded a hit");
            InnateTechniqueService.select(caster, 2); // Anchor
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(List.of(Swap.ANCHOR.id().toString()), landed(caster, enemy), "used in a fight, it counts against that opponent");
        });
    }

    @Test
    void sharedAbilitiesNeverLand(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "shared_caster", Rime.RIME.get());
            ServerPlayer enemy = sorcerer(server, "shared_enemy", Rime.RIME.get());
            var rmt = com.cnethuka.kegare.loadout.SharedAbilities.get(RmtIds.ABILITY).orElseThrow();
            Landings.land(caster, enemy, rmt);
            assertNull(FightTracker.between(caster, enemy));
        });
    }

    private static void assertNull(Object o) {
        assertFalse(o != null, "expected nothing");
    }

    @Test
    void anAbilityOfAnotherTechniqueDoesntCount(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "other_caster", Rime.RIME.get());
            ServerPlayer enemy = sorcerer(server, "other_enemy", Rime.RIME.get());
            Landings.land(caster, enemy, Swap.POSITIONS);
            assertNull(FightTracker.between(caster, enemy));
        });
    }

    @Test
    void theSameAbilityOnTheSameTargetCountsOncePerSecond(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "repeat_caster", Rime.RIME.get());
            ServerPlayer enemy = sorcerer(server, "repeat_enemy", Rime.RIME.get());
            Landings.land(caster, enemy, Rime.SPLINTER);
            Landings.land(caster, enemy, Rime.SPLINTER);
            Landings.land(caster, enemy, Rime.SPLINTER);
            assertEquals(1, landed(caster, enemy).size(), "one volley");
            TestServer.advanceGameTime(server, 20);
            Landings.land(caster, enemy, Rime.SPLINTER);
            assertEquals(2, landed(caster, enemy).size());
        });
    }

    @Test
    void anEffectPutOnSomeoneDuringACastLands(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer caster = sorcerer(server, "effect_caster", Rime.RIME.get());
            Pig pig = EntityType.PIG.create(level);
            level.addFreshEntity(pig);
            ServerPlayer enemy = sorcerer(server, "effect_enemy", Rime.RIME.get());
            CastScope.run(caster, Rime.FROST_FIELD, () -> enemy.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20), caster));
            assertEquals(List.of(Rime.FROST_FIELD.id().toString()), landed(caster, enemy));
            CastScope.run(caster, Rime.SHATTER_SHELL, () -> enemy.knockback(1.0, 1.0, 0.0));
            assertEquals(2, landed(caster, enemy).size(), "so does knocking them back");
            pig.discard();
        });
    }

    @Test
    void openingCastsCountTowardTheFightTheyStart(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer caster = sorcerer(server, "open_caster", Rime.RIME.get());
            ServerPlayer enemy = sorcerer(server, "open_enemy", Rime.RIME.get());
            face(caster, enemy);
            InnateTechniqueService.select(caster, 0);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS); // Frost Shard, before any fight
            assertTrue(enemy.hurt(enemy.damageSources().playerAttack(caster), 1f), "setup");
            Fight fight = FightTracker.between(caster, enemy);
            assertNotNull(fight);
            assertEquals(40.0, fight.side(caster.getUUID()).baseMalice(), 1.0e-6, "the shard that opened it, at base cost");
        });
    }
}
