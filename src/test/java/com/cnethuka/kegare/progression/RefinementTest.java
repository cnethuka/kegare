package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Puppets;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Refinement, with and without a game running. */
@ExtendWith(EphemeralTestServerProvider.class)
class RefinementTest {
    private static final double EPS = 1.0e-6;
    private static final double BASE = 30.0;
    private static final double EXPONENT = 1.6;
    private static final RefinementMath.Settings S = new RefinementMath.Settings(1.0, 2.5, 3.0, 0.97, 0.2);

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
        return p;
    }

    @Test
    void theCurveRises() {
        assertEquals(0.0, RefinementMath.xpForLevel(0, BASE, EXPONENT), EPS);
        assertEquals(30.0, RefinementMath.xpForLevel(1, BASE, EXPONENT), EPS);
        for (int level = 1; level < 10; level++) {
            assertTrue(RefinementMath.xpForLevel(level + 1, BASE, EXPONENT) > RefinementMath.xpForLevel(level, BASE, EXPONENT));
        }
        assertEquals(121.0, RefinementMath.totalXp(2, BASE, EXPONENT), EPS, "the third ability");
        assertEquals(4012.0, RefinementMath.totalXp(9, BASE, EXPONENT), EPS, "the full kit");
    }

    @Test
    void specialsCountMoreAndPlayersCountMoreAndMobsStillCount() {
        double mobBase = RefinementMath.landingXp(S, false, false, 0, 1.0);
        assertEquals(1.0, mobBase, EPS);
        assertTrue(mobBase > 0, "a player can practice alone");
        assertEquals(2.5, RefinementMath.landingXp(S, true, false, 0, 1.0), EPS);
        assertEquals(3.0, RefinementMath.landingXp(S, false, true, 0, 1.0), EPS);
        assertEquals(7.5, RefinementMath.landingXp(S, true, true, 0, 1.0), EPS);
    }

    @Test
    void returnsDiminishPerTargetDownToTheFloor() {
        assertEquals(0.97, RefinementMath.landingXp(S, false, false, 1, 1.0), EPS);
        assertEquals(0.2, RefinementMath.landingXp(S, false, false, 500, 1.0), EPS);
    }

    @Test
    void theGradeMultiplierScalesIt() {
        assertEquals(2.0, RefinementMath.landingXp(S, false, false, 0, 2.0), EPS);
    }

    @Test
    void landingEarnsAndCastingIntoTheAirDoesnt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer caster = sorcerer(server, "air_or_not", Rime.RIME.get());
            caster.moveTo(0.5, 80, 0.5);
            InnateTechniqueService.select(caster, 0);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(0.0, RefinementService.get(caster).xp(), EPS, "casting at nothing gives nothing");

            Pig pig = EntityType.PIG.create(level);
            level.addFreshEntity(pig);
            Landings.land(caster, pig, Rime.FROST_SHARD);
            assertEquals(1.0, RefinementService.get(caster).xp(), EPS, "a mob counts");
            pig.discard();
        });
    }

    @Test
    void ownPuppetsGiveNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer owner = sorcerer(server, "own_puppet", PuppetManipulation.PUPPET_MANIPULATION.get());
            PuppetEntity puppet = Puppets.create(owner, PuppetTier.TAP);
            Landings.land(owner, puppet, PuppetManipulation.CURIO_BURST);
            assertEquals(0.0, RefinementService.get(owner).xp(), EPS);
        });
    }

    @Test
    void levelsUpAndCarriesOverAndStopsAtTheTop(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "levels", Rime.RIME.get());
            RefinementService.addXp(p, 29);
            assertEquals(0, RefinementService.level(p));
            RefinementService.addXp(p, 1);
            assertEquals(1, RefinementService.level(p));
            RefinementService.addXp(p, 91 + 5);
            assertEquals(2, RefinementService.level(p));
            assertEquals(5.0, RefinementService.get(p).xp(), EPS, "what's left carries over");
            assertEquals(2, RefinementService.peak(p));
            RefinementService.addXp(p, 1_000_000);
            assertEquals(10, RefinementService.level(p));
            assertEquals(10, RefinementService.peak(p));
        });
    }

    @Test
    void itNeverChangesCostOrCooldown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "only_unlocks", Rime.RIME.get());
            double cost = Rime.FROST_SHARD.cost(p);
            int cooldown = Rime.FROST_SHARD.cooldownTicks(p);
            RefinementService.setLevel(p, 10);
            assertEquals(cost, Rime.FROST_SHARD.cost(p), EPS);
            assertEquals(cooldown, Rime.FROST_SHARD.cooldownTicks(p));
        });
    }

    @Test
    void aRerollResetsItWhilePeakGradeStay(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "reroll_keeps", Rime.RIME.get());
            GradeService.set(p, Grade.GRADE_2);
            RefinementService.setLevel(p, 7);
            RefinementService.addXp(p, 3);
            InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
            assertEquals(0, RefinementService.level(p));
            assertEquals(0.0, RefinementService.get(p).xp(), EPS);
            assertEquals(7, RefinementService.peak(p));
            assertEquals(Grade.GRADE_2, Progression.getGrade(p));
        });
    }

    @Test
    void aHigherGradeRelearnsFaster(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer p = sorcerer(server, "faster", Rime.RIME.get());
            Pig one = EntityType.PIG.create(level);
            level.addFreshEntity(one);
            Landings.land(p, one, Rime.FROST_SHARD);
            assertEquals(1.0, RefinementService.get(p).xp(), EPS, "Grade 4 is x1");
            GradeService.set(p, Grade.GRADE_1);
            Pig two = EntityType.PIG.create(level);
            level.addFreshEntity(two);
            Landings.land(p, two, Rime.FROST_SHARD);
            assertEquals(1.0 + 2.0, RefinementService.get(p).xp(), EPS, "Grade 1 is x2");
            one.discard();
            two.discard();
        });
    }
}
