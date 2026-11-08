package com.cnethuka.kegare.severed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.progression.FightTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Severed's advancements (DESIGN §3.27): three different Malice users killed in time, and coming back from three hearts to kill the
 * same player before the fight ends; mobs never count, a totem disqualifies, and a non-Severed player never earns either.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SeveredAdvancementsTest {
    private static final double Y = 120;

    private static boolean has(MinecraftServer server, ServerPlayer p, net.minecraft.resources.ResourceLocation id) {
        return TestServer.hasAdvancement(server, p, id);
    }

    /** {@code killer} kills {@code victim} with a hit (then the victim is stood back up for the next test step). */
    private static void kill(ServerPlayer killer, LivingEntity victim) {
        victim.invulnerableTime = 0;
        victim.hurt(killer.damageSources().playerAttack(killer), 1000f);
        victim.setHealth(victim.getMaxHealth());
    }

    @Test
    void howCruel(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            List<Pig> pigs = new ArrayList<>();
            try {
                ServerPlayer s = SeveredBodyTest.severed(server, level, "adv_cruel");
                ServerPlayer m1 = SeveredBodyTest.maliceUser(server, level, "adv_cruel_a");
                ServerPlayer m2 = SeveredBodyTest.maliceUser(server, level, "adv_cruel_b");
                ServerPlayer m3 = SeveredBodyTest.maliceUser(server, level, "adv_cruel_c");
                ServerPlayer other = SeveredBodyTest.severed(server, level, "adv_cruel_severed");
                for (ServerPlayer p : List.of(s, m1, m2, m3, other)) p.moveTo(0.5, Y, 0.5);
                for (int i = 0; i < 3; i++) {
                    Pig pig = EntityType.PIG.create(level);
                    pig.moveTo(2.5, Y, 2.5 + i);
                    level.addFreshEntity(pig);
                    pigs.add(pig);
                    kill(s, pig);
                }
                assertFalse(has(server, s, SeveredAdvancements.HOW_CRUEL), "mobs don't count");
                kill(s, other);
                kill(s, m1);
                kill(s, m1);
                kill(s, m1);
                assertFalse(has(server, s, SeveredAdvancements.HOW_CRUEL), "the same player thrice, and a Severed one, aren't three Malice users");
                kill(s, m2);
                TestServer.advanceGameTime(server, SeveredConfig.ticks(SeveredConfig.CRUEL_WINDOW.get()) + 20);
                kill(s, m3);
                assertFalse(has(server, s, SeveredAdvancements.HOW_CRUEL), "not within the window");
                kill(s, m1);
                kill(s, m2);
                assertTrue(has(server, s, SeveredAdvancements.HOW_CRUEL), "three different Malice users in time");

                // A Malice user never earns it.
                kill(m1, m2);
                kill(m1, m3);
                kill(m1, s);
                assertFalse(has(server, m1, SeveredAdvancements.HOW_CRUEL), "not Severed");
            } finally {
                pigs.forEach(Pig::discard);
                FightTracker.clear();
                SeveredAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void justGettingStarted(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            Pig pig = EntityType.PIG.create(level);
            try {
                ServerPlayer s = SeveredBodyTest.severed(server, level, "adv_started");
                ServerPlayer m = SeveredBodyTest.maliceUser(server, level, "adv_started_m");
                ServerPlayer bystander = SeveredBodyTest.maliceUser(server, level, "adv_started_b");
                for (ServerPlayer p : List.of(s, m, bystander)) p.moveTo(0.5, Y, 0.5);
                float low = (float) (SeveredConfig.STARTED_HEARTS.get() * 2);

                // A mob bringing them low doesn't count; nor does killing someone they weren't low against.
                pig.moveTo(2.5, Y, 2.5);
                level.addFreshEntity(pig);
                s.setHealth(low - 1);
                s.hurt(s.damageSources().mobAttack(pig), 1f);
                kill(s, pig);
                bystander.hurt(bystander.damageSources().playerAttack(s), 1f); // a fight with the bystander, never low in it
                kill(s, bystander);
                assertFalse(has(server, s, SeveredAdvancements.JUST_GETTING_STARTED), "no fight with a Malice user brought them low");

                // Brought to the line by a Malice user, then that player killed while the fight is on.
                s.setHealth(s.getMaxHealth());
                s.invulnerableTime = 0;
                m.hurt(m.damageSources().playerAttack(s), 1f); // the fight starts
                s.setHealth(low + 2);
                s.invulnerableTime = 0;
                s.hurt(s.damageSources().playerAttack(m), 4f); // down to the line in it
                assertTrue(s.getHealth() <= low, "setup: low");
                s.setHealth(s.getMaxHealth()); // golden apples and healing are fine
                kill(s, m);
                assertTrue(has(server, s, SeveredAdvancements.JUST_GETTING_STARTED), "Our fight is just getting started!");

                // A totem in that fight disqualifies it.
                ServerPlayer s2 = SeveredBodyTest.severed(server, level, "adv_started_totem");
                ServerPlayer m2 = SeveredBodyTest.maliceUser(server, level, "adv_started_m2");
                s2.moveTo(0.5, Y, 0.5);
                m2.moveTo(0.5, Y, 0.5);
                m2.hurt(m2.damageSources().playerAttack(s2), 1f);
                s2.setHealth(low + 2);
                s2.invulnerableTime = 0;
                s2.hurt(s2.damageSources().playerAttack(m2), 4f);
                NeoForge.EVENT_BUS.post(new LivingUseTotemEvent(s2, s2.damageSources().playerAttack(m2), new ItemStack(Items.TOTEM_OF_UNDYING), InteractionHand.OFF_HAND));
                s2.setHealth(s2.getMaxHealth());
                kill(s2, m2);
                assertFalse(has(server, s2, SeveredAdvancements.JUST_GETTING_STARTED), "a totem used in the fight");

                // The fight over before the kill: no.
                ServerPlayer s3 = SeveredBodyTest.severed(server, level, "adv_started_late");
                ServerPlayer m3 = SeveredBodyTest.maliceUser(server, level, "adv_started_m3");
                s3.moveTo(0.5, Y, 0.5);
                m3.moveTo(0.5, Y, 0.5);
                m3.hurt(m3.damageSources().playerAttack(s3), 1f);
                s3.setHealth(low + 2);
                s3.invulnerableTime = 0;
                s3.hurt(s3.damageSources().playerAttack(m3), 4f);
                FightTracker.onLogout(m3); // the fight ends
                s3.setHealth(s3.getMaxHealth());
                kill(s3, m3);
                assertFalse(has(server, s3, SeveredAdvancements.JUST_GETTING_STARTED), "not before the fight ended");

                // A Malice user never earns it.
                ServerPlayer m4 = SeveredBodyTest.maliceUser(server, level, "adv_started_m4");
                m4.moveTo(0.5, Y, 0.5);
                bystander.hurt(bystander.damageSources().playerAttack(m4), 1f);
                m4.setHealth(low + 2);
                m4.invulnerableTime = 0;
                m4.hurt(m4.damageSources().playerAttack(bystander), 4f);
                kill(m4, bystander);
                assertFalse(has(server, m4, SeveredAdvancements.JUST_GETTING_STARTED), "not Severed");
            } finally {
                pig.discard();
                FightTracker.clear();
                SeveredAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
