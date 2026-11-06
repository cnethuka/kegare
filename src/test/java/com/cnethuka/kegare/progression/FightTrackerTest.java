package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.Puppets;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.player.BirthService;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Fight tracking on the real damage path. */
@ExtendWith(EphemeralTestServerProvider.class)
class FightTrackerTest {
    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private static void hit(ServerPlayer attacker, ServerPlayer victim, float amount) {
        victim.invulnerableTime = 0;
        assertTrue(victim.hurt(attacker.damageSources().playerAttack(attacker), amount), "setup: the hit lands");
    }

    private static ServerPlayer born(MinecraftServer server, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        p.setHealth(p.getMaxHealth());
        return p;
    }

    @Test
    void tracksDamageBothWaysAndEndsOnTheTimeout(MinecraftServer server) throws Exception {
        List<FightReport> reports = new ArrayList<>();
        FightTracker.Listener listener = reports::add;
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            FightTracker.clear();
            FightTracker.addListener(listener);
            try {
                ServerPlayer a = born(server, "fight_a");
                ServerPlayer b = born(server, "fight_b");
                hit(a, b, 4);
                hit(b, a, 2);
                Fight fight = FightTracker.between(a, b);
                assertNotNull(fight, "damage between two players starts a fight");
                assertEquals(4, fight.side(a.getUUID()).dealt(), 1.0e-3);
                assertEquals(2, fight.side(b.getUUID()).dealt(), 1.0e-3);

                TestServer.advanceGameTime(server, 20 * 29);
                FightTracker.endExpired(server);
                assertNotNull(FightTracker.between(a, b), "still on at 29 quiet seconds");
                hit(a, b, 1);
                TestServer.advanceGameTime(server, 20 * 29);
                FightTracker.endExpired(server);
                assertNotNull(FightTracker.between(a, b), "any damage resets the clock");
                TestServer.advanceGameTime(server, 20);
                FightTracker.endExpired(server);
                assertNull(FightTracker.between(a, b), "over after 30 quiet seconds");

                assertEquals(2, reports.size(), "one report per player");
                reports.forEach(r -> assertEquals(FightOutcome.SURVIVED, r.outcome()));
            } finally {
                FightTracker.removeListener(listener);
            }
        });
    }

    @Test
    void selfDamageOwnPuppetsAndTheEnvironmentNeverCount(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            FightTracker.clear();
            ServerPlayer a = born(server, "never_a");
            InnateTechniqueService.setInnateTechnique(a, PuppetManipulation.PUPPET_MANIPULATION.get());

            a.hurt(a.damageSources().playerAttack(a), 2);
            a.invulnerableTime = 0;
            a.hurt(a.damageSources().fall(), 2);
            assertTrue(FightTracker.of(a).isEmpty(), "yourself and the environment are never a fight");

            PuppetEntity puppet = Puppets.create(a, PuppetTier.TAP);
            assertNotNull(puppet);
            assertTrue(Attribution.isOwn(a, puppet), "a puppet belongs to its owner");
            assertTrue(!Attribution.isRealTarget(a, puppet), "and isn't a target for them");
            assertTrue(!Attribution.isRealTarget(a, a), "nor are you");
        });
    }

    @Test
    void aPuppetsHitIsItsOwners(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            FightTracker.clear();
            ServerPlayer owner = born(server, "puppet_owner");
            ServerPlayer enemy = born(server, "puppet_enemy");
            InnateTechniqueService.setInnateTechnique(owner, PuppetManipulation.PUPPET_MANIPULATION.get());
            PuppetEntity puppet = Puppets.create(owner, PuppetTier.TAP);
            assertTrue(enemy.hurt(enemy.damageSources().mobAttack(puppet), 3), "setup");
            Fight fight = FightTracker.between(owner, enemy);
            assertNotNull(fight, "the owner is in the fight, not the puppet");
            assertEquals(3, fight.side(owner.getUUID()).dealt(), 1.0e-3);
        });
    }

    @Test
    void aDeathEndsEveryFightAndOnlyTheShareWins(MinecraftServer server) throws Exception {
        List<FightReport> reports = new ArrayList<>();
        FightTracker.Listener listener = reports::add;
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            FightTracker.clear();
            FightTracker.addListener(listener);
            try {
                ServerPlayer big = born(server, "death_big");
                ServerPlayer small = born(server, "death_small");
                ServerPlayer dead = born(server, "death_dead");
                hit(big, dead, 16);
                hit(small, dead, 2);
                FightTracker.onDeath(dead);
                assertTrue(FightTracker.of(dead).isEmpty(), "every fight of theirs is over");

                FightOutcome bigGot = reports.stream().filter(r -> r.player() == big).findFirst().orElseThrow().outcome();
                FightOutcome smallGot = reports.stream().filter(r -> r.player() == small).findFirst().orElseThrow().outcome();
                assertEquals(FightOutcome.WIN, bigGot, "16 of 18");
                assertEquals(FightOutcome.SURVIVED, smallGot, "2 of 18 is under a quarter");
                assertEquals(2, reports.stream().filter(r -> r.player() == dead && r.outcome() == FightOutcome.LOSS).count());
            } finally {
                FightTracker.removeListener(listener);
            }
        });
    }

    @Test
    void aLogoutEndsTheFight(MinecraftServer server) throws Exception {
        List<FightReport> reports = new ArrayList<>();
        FightTracker.Listener listener = reports::add;
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            FightTracker.clear();
            FightTracker.addListener(listener);
            try {
                ServerPlayer a = born(server, "logout_a");
                ServerPlayer b = born(server, "logout_b");
                hit(a, b, 3);
                FightTracker.onLogout(b);
                assertNull(FightTracker.between(a, b));
                assertEquals(FightOutcome.LEFT, reports.stream().filter(r -> r.player() == b).findFirst().orElseThrow().outcome());
                assertEquals(FightOutcome.SURVIVED, reports.stream().filter(r -> r.player() == a).findFirst().orElseThrow().outcome());
            } finally {
                FightTracker.removeListener(listener);
            }
        });
    }
}
