package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** "Bush Camper" and "Something's Missing". */
@ExtendWith(EphemeralTestServerProvider.class)
class SixEyesAdvancementsTest {
    private static boolean has(MinecraftServer server, ServerPlayer p, net.minecraft.resources.ResourceLocation id) {
        return TestServer.hasAdvancement(server, p, id);
    }

    private static void hit(LivingEntity victim, ServerPlayer attacker, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(attacker.damageSources().playerAttack(attacker), amount);
    }

    private static ServerPlayer victim(MinecraftServer server, String name) {
        ServerPlayer v = SightTest.player(server, name, 1000, 2.5, 2.5);
        v.setHealth(v.getMaxHealth());
        return v;
    }

    @Test
    void theirDisplays(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            DisplayInfo bush = server.getAdvancements().get(KegareAdvancements.BUSH_CAMPER).value().display().orElseThrow();
            assertEquals(AdvancementType.TASK, bush.getType());
            assertTrue(bush.isHidden(), "hidden");
            assertTrue(bush.shouldAnnounceChat(), "announced");
            DisplayInfo missing = server.getAdvancements().get(KegareAdvancements.SOMETHINGS_MISSING).value().display().orElseThrow();
            assertEquals(AdvancementType.GOAL, missing.getType());
            assertTrue(missing.isHidden(), "hidden");
            assertFalse(missing.shouldAnnounceChat(), "silent");
            assertEquals(KegareAdvancements.CLEAR_SIGHT, server.getAdvancements().get(KegareAdvancements.SOMETHINGS_MISSING).value().parent().orElseThrow(), "the Six Eyes tab");
        });
    }

    @Test
    void stealingAKillAndWhatIsnt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            server.setPvpAllowed(true);
            try {
                ServerPlayer worker = SightTest.player(server, "bush_worker", 1000, 0.5, 0.5);

                // 80% by another player, 20% and the killing blow by you. Severed count too.
                ServerPlayer camper = SightTest.player(server, "bush_camper", 0, 4.5, 0.5);
                ServerPlayer v1 = victim(server, "bush_v1");
                hit(v1, worker, 16f);
                hit(v1, camper, 4f);
                assertTrue(v1.isDeadOrDying(), "setup: dead");
                assertTrue(has(server, camper, KegareAdvancements.BUSH_CAMPER), "stolen");
                assertFalse(has(server, worker, KegareAdvancements.BUSH_CAMPER), "not for the one robbed");

                // Dealing more than 25% of it isn't stealing.
                ServerPlayer fair = SightTest.player(server, "bush_fair", 1000, 4.5, 4.5);
                ServerPlayer v2 = victim(server, "bush_v2");
                hit(v2, worker, 14f);
                hit(v2, fair, 6f);
                assertTrue(v2.isDeadOrDying(), "setup: dead");
                assertFalse(has(server, fair, KegareAdvancements.BUSH_CAMPER), "30% is your own kill");

                // Nobody else at 75%: the rest is shared.
                ServerPlayer other = SightTest.player(server, "bush_other", 1000, 8.5, 0.5);
                ServerPlayer shared = SightTest.player(server, "bush_shared", 1000, 8.5, 4.5);
                ServerPlayer v3 = victim(server, "bush_v3");
                hit(v3, worker, 8f);
                hit(v3, other, 8f);
                hit(v3, shared, 4f);
                assertTrue(v3.isDeadOrDying(), "setup: dead");
                assertFalse(has(server, shared, KegareAdvancements.BUSH_CAMPER), "no one at 75%");

                // Outside the window it doesn't count.
                ServerPlayer late = SightTest.player(server, "bush_late", 1000, 12.5, 0.5);
                ServerPlayer v4 = victim(server, "bush_v4");
                hit(v4, worker, 16f);
                TestServer.advanceGameTime(server, 20 * 31);
                hit(v4, late, 4f);
                assertTrue(v4.isDeadOrDying(), "setup: dead");
                assertFalse(has(server, late, KegareAdvancements.BUSH_CAMPER), "that was over 30 s ago");

                // It must be your killing blow.
                ServerPlayer nibbler = SightTest.player(server, "bush_nibble", 1000, 12.5, 4.5);
                ServerPlayer v5 = victim(server, "bush_v5");
                hit(v5, nibbler, 4f);
                hit(v5, worker, 16f);
                assertTrue(v5.isDeadOrDying(), "setup: dead");
                assertFalse(has(server, nibbler, KegareAdvancements.BUSH_CAMPER), "the worker landed it");

                // A mob's blow steals nothing, and a mob as the victim counts for nothing.
                Zombie zombie = new Zombie(EntityType.ZOMBIE, server.overworld());
                zombie.moveTo(16, 120, 0, 0, 0);
                zombie.setNoAi(true);
                zombie.setNoGravity(true);
                server.overworld().addFreshEntity(zombie);
                ServerPlayer mobber = SightTest.player(server, "bush_mobber", 1000, 16.5, 4.5);
                hit(zombie, worker, 16f);
                hit(zombie, mobber, 4f);
                zombie.discard();
                assertFalse(has(server, mobber, KegareAdvancements.BUSH_CAMPER), "mobs don't count");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void readingAVoidForTheFirstTime(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "missing_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer plain = SightTest.player(server, "missing_plain", 1000, 0.5, 6.5);
                ServerPlayer severed = SightTest.player(server, "missing_void", 0, -8.5, 0.5);
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f); // at the plain one
                Reads.build(bearer);
                assertFalse(has(server, bearer, KegareAdvancements.SOMETHINGS_MISSING), "someone's there");
                bearer.moveTo(0.5, 120, 0.5, 90f, 0f); // at the void
                Reads.build(bearer);
                assertTrue(has(server, bearer, KegareAdvancements.SOMETHINGS_MISSING), "nothing there");

                // Without the eyes, nothing to read.
                assertFalse(has(server, plain, KegareAdvancements.SOMETHINGS_MISSING));
                assertTrue(severed.isAlive());
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }
}
