package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 7: all eight advancements, and the mastery ones ignoring mobs. */
@ExtendWith(EphemeralTestServerProvider.class)
class PuppetAdvancementsTest {
    @Test
    void advancements(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            server.setPvpAllowed(true);
            ServerPlayer owner = PossessionTest.damageable(server, "adv_owner");
            ServerPlayer other = PossessionTest.damageable(server, "adv_other");
            level.addNewPlayer(owner);
            level.addNewPlayer(other);
            PossessionTest.prepare(owner);
            owner.moveTo(8.5, ground, 8.5, -90f, 0f);
            owner.setYHeadRot(-90f);
            other.moveTo(12.5, ground, 12.5, 0f, 0f);
            Cow cow = new Cow(EntityType.COW, level);
            try {
                // Pulling Strings: the first puppet.
                assertFalse(has(server, owner, PuppetAdvancements.PULLING_STRINGS));
                PuppetEntity first = PossessionTest.puppetInFront(server, owner, 0);
                assertTrue(has(server, owner, PuppetAdvancements.PULLING_STRINGS), "Pulling Strings");

                // Nobody Home (a mob's hit doesn't count) and Out of Body.
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertTrue(has(server, owner, PuppetAdvancements.OUT_OF_BODY), "Out of Body");
                cow.moveTo(owner.getX(), ground, owner.getZ(), 0f, 0f);
                level.addFreshEntity(cow);
                PossessionTest.shellOf(level, owner).hurt(level.damageSources().mobAttack(cow), 1f);
                assertFalse(has(server, owner, PuppetAdvancements.NOBODY_HOME), "a mob's hit doesn't count");
                InnateTechniqueService.clearCooldowns(owner);
                PossessionTest.aimAt(owner, first);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                PossessionTest.shellOf(level, owner).hurt(level.damageSources().playerAttack(other), 1f);
                assertTrue(has(server, owner, PuppetAdvancements.NOBODY_HOME), "Nobody Home");
                owner.setHealth(20f);

                // Little Me: the final hit on a player while possessing (a mob doesn't count).
                InnateTechniqueService.clearCooldowns(owner);
                PossessionTest.aimAt(owner, first);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                cow.setHealth(1f);
                cow.invulnerableTime = 0;
                cow.hurt(level.damageSources().playerAttack(owner), 5f);
                assertFalse(has(server, owner, PuppetAdvancements.LITTLE_ME), "killing a mob doesn't count");
                other.setHealth(1f);
                other.invulnerableTime = 0;
                other.hurt(level.damageSources().playerAttack(owner), 5f);
                assertTrue(has(server, owner, PuppetAdvancements.LITTLE_ME), "Little Me");
                Possession.release(owner, Possession.Return.RETURNED);
                other.setHealth(20f);
                other.revive();
                owner.setHealth(20f);

                // Who's Real?: a player hits the Stand-In (a mob doesn't count).
                InnateTechniqueService.clearCooldowns(owner);
                InnateTechniqueService.select(owner, 3);
                PossessionTest.aimAt(owner, first);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertTrue(first.isStandIn());
                first.invulnerableTime = 0;
                first.hurt(level.damageSources().mobAttack(cow), 0.5f);
                assertFalse(has(server, owner, PuppetAdvancements.WHOS_REAL), "a mob's hit doesn't count");
                first.invulnerableTime = 0;
                first.hurt(level.damageSources().playerAttack(other), 0.5f);
                assertTrue(has(server, owner, PuppetAdvancements.WHOS_REAL), "Who's Real?");
                first.endStandIn();

                // Curiosity Killed: a Curio Burst kills a player (killing a mob doesn't count).
                InnateTechniqueService.select(owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity bomb = PossessionTest.puppetInFront(server, owner, 0);
                cow.moveTo(bomb.getX() + 0.5, ground, bomb.getZ(), 0f, 0f);
                cow.setHealth(1f);
                cow.invulnerableTime = 0;
                CurioBursts.detonate(bomb, owner);
                assertFalse(has(server, owner, PuppetAdvancements.CURIOSITY_KILLED), "a mob doesn't count");
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, owner.getBoundingBox().inflate(16))
                        .forEach(Entity::discard); // the cow's drops (other tests share this world)
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity bomb2 = PossessionTest.puppetInFront(server, owner, 0);
                other.moveTo(bomb2.getX() + 0.5, ground, bomb2.getZ(), 0f, 0f);
                other.setHealth(1f);
                other.invulnerableTime = 0;
                CurioBursts.detonate(bomb2, owner);
                assertTrue(has(server, owner, PuppetAdvancements.CURIOSITY_KILLED), "Curiosity Killed");
                other.revive();
                other.setHealth(20f);

                // Decoy: a player kills your puppet while you're more than 16 blocks away (not when you're close).
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity near = PossessionTest.puppetInFront(server, owner, 0);
                near.invulnerableTime = 0;
                near.hurt(level.damageSources().playerAttack(other), 50f);
                assertFalse(has(server, owner, PuppetAdvancements.DECOY), "too close to count");
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity far = PossessionTest.puppetInFront(server, owner, 0);
                owner.moveTo(owner.getX(), ground, owner.getZ() - PuppetAdvancements.DECOY_DISTANCE - 4, 0f, 0f);
                far.invulnerableTime = 0;
                far.hurt(level.damageSources().playerAttack(other), 50f);
                assertTrue(has(server, owner, PuppetAdvancements.DECOY), "Decoy");
                owner.moveTo(8.5, ground, 8.5, -90f, 0f);

                // Kuru Kuru: ten puppets spinning at once, reachable under the default cap.
                assertTrue(PuppetAdvancements.KURU_KURU_PUPPETS <= PuppetConfig.MAX_PUPPETS.getDefault(), "Kuru Kuru fits under the cap");
                DirectiveAbility.give(owner, PuppetOrder.STAY, null);
                List<PuppetEntity> ten = new ArrayList<>();
                InnateTechniqueService.select(owner, 0);
                for (int i = 0; i < PuppetAdvancements.KURU_KURU_PUPPETS; i++) {
                    InnateTechniqueService.clearCooldowns(owner);
                    MaliceService.refill(owner);
                    PuppetEntity p = PuppetTest.create(server, owner, 0);
                    p.moveTo(2.5 + (i % 5) * 2, ground, 2.5 + (i / 5) * 2, 0f, 0f);
                    ten.add(p);
                }
                PuppetAdvancements.checkSpinning(server);
                assertFalse(has(server, owner, PuppetAdvancements.KURU_KURU), "not spinning yet");
                for (int t = 0; t <= PuppetConfig.ticks(PuppetConfig.SPIN_DELAY) + 2; t++) ten.forEach(p -> DirectiveTest.tick(server, p));
                assertTrue(ten.stream().allMatch(PuppetEntity::isSpinning), "all ten spinning");
                PuppetAdvancements.checkSpinning(server);
                assertTrue(has(server, owner, PuppetAdvancements.KURU_KURU), "Kuru Kuru");
            } finally {
                cow.discard();
                level.removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    private static boolean has(MinecraftServer server, ServerPlayer player, net.minecraft.resources.ResourceLocation id) {
        return TestServer.hasAdvancement(server, player, id);
    }
}
