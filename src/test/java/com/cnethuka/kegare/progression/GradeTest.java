package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Grade XP and promotions on the real paths. */
@ExtendWith(EphemeralTestServerProvider.class)
class GradeTest {
    private static final double EPS = 1.0e-3;

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer born(MinecraftServer server, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static void hit(ServerPlayer attacker, ServerPlayer victim, float amount) {
        victim.invulnerableTime = 0;
        assertTrue(victim.hurt(attacker.damageSources().playerAttack(attacker), amount), "setup: the hit lands");
    }

    @Test
    void aFightPaysBothPlayersAndALossStillPays(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer winner = born(server, "pay_winner");
            ServerPlayer loser = born(server, "pay_loser");
            hit(winner, loser, 5);
            hit(loser, winner, 2);
            FightTracker.onDeath(loser);
            assertEquals(5 * 2.0 + 40.0, GradeService.get(winner).xp(), EPS, "damage + win");
            assertEquals(2 * 2.0 + 15.0, GradeService.get(loser).xp(), EPS, "damage + loss");
        });
    }

    @Test
    void aSeveredPlayerEarnsTheSame(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer severed = TestServer.damageable(server, "pay_severed");
            server.overworld().addNewPlayer(severed);
            BirthService.rebirth(severed, OptionalInt.of(0));
            ServerPlayer other = born(server, "pay_other");
            hit(severed, other, 5);
            FightTracker.onDeath(other);
            // The same XP for the same damage; their Strength (DESIGN §3.27) makes the same swing deal more of it.
            assertEquals(5 * com.cnethuka.kegare.severed.SeveredConfig.MELEE_DAMAGE.get() * 2.0 + 40.0, GradeService.get(severed).xp(), EPS);
        });
    }

    @Test
    void repeatFightsAgainstTheSamePlayerGiveLess(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer a = born(server, "repeat_a");
            ServerPlayer b = born(server, "repeat_b");
            hit(a, b, 5);
            FightTracker.onDeath(b);
            double first = GradeService.get(a).xp();
            hit(a, b, 5);
            FightTracker.onDeath(b);
            double second = GradeService.get(a).xp() - first;
            assertEquals(first * 0.6, second, EPS, "the second fight within the hour");

            TestServer.advanceGameTime(server, 20 * 3600);
            double before = GradeService.get(a).xp();
            hit(a, b, 5);
            FightTracker.onDeath(b);
            assertEquals(first, GradeService.get(a).xp() - before, EPS, "a rolling hour later it's worth the full amount again");
        });
    }

    @Test
    void mobsGiveALittleUpToTheCap(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "mob_xp");
            Pig pig = EntityType.PIG.create(server.overworld());
            server.overworld().addFreshEntity(pig);
            assertTrue(pig.hurt(p.damageSources().playerAttack(p), 5f), "setup");
            assertEquals(5 * 0.1, GradeService.get(p).xp(), EPS, "a small amount per damage");
            for (int i = 0; i < 1000; i++) GradeService.onMobDamage(p, 10);
            assertEquals(40.0, GradeService.get(p).xp(), EPS, "capped per hour, so a farm can't replace fighting");
            TestServer.advanceGameTime(server, 20 * 3600);
            GradeService.onMobDamage(p, 10);
            assertEquals(41.0, GradeService.get(p).xp(), EPS);
            pig.discard();
        });
    }

    @Test
    void xpAloneStopsWhereAnAssessmentIsNeeded(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "xp_only");
            assertEquals(Grade.GRADE_4, Progression.getGrade(p), "everyone starts at Grade 4");
            GradeService.addXp(p, 199);
            assertEquals(Grade.GRADE_4, Progression.getGrade(p));
            GradeService.addXp(p, 1);
            assertEquals(Grade.GRADE_3, Progression.getGrade(p), "Grade 3 is XP only");
            GradeService.addXp(p, 500);
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "and so is Semi-Grade 2");
            GradeService.addXp(p, 100_000);
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "Grade 2 also takes an Assessment");
            assertEquals(1000.0, GradeService.get(p).xp(), EPS, "XP stops at the step's cost");

            GradeService.set(p, Grade.GRADE_1);
            GradeService.addXp(p, 1_000_000);
            assertEquals(Grade.GRADE_1, Progression.getGrade(p), "XP alone never reaches Special Grade");
            assertEquals(0.0, GradeService.get(p).xp(), EPS, "and does nothing here");
        });
    }

    @Test
    void xpCarriesAcrossAStep(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "xp_carry");
            GradeService.addXp(p, 250);
            assertEquals(Grade.GRADE_3, Progression.getGrade(p));
            assertEquals(50.0, GradeService.get(p).xp(), EPS, "what's left goes toward the next step");
        });
    }

    @Test
    void gradeNeverDropsOnDeathRerollOrRebirth(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "never_drops");
            GradeService.set(p, Grade.GRADE_2);
            GradeService.addXp(p, 10);
            FightTracker.onDeath(p);
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            BirthService.rebirth(p, OptionalInt.of(100));
            GradeService.addXp(p, 1);
            assertEquals(Grade.GRADE_2, Progression.getGrade(p));
            assertEquals(11.0, GradeService.get(p).xp(), EPS);
        });
    }
}
