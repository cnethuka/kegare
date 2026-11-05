package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 2: Directive's four orders (and what each does) and Focus, on players' and mobs' targets. */
@ExtendWith(EphemeralTestServerProvider.class)
class DirectiveTest {
    @Test
    void ordersAndFocus(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "directive_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            Cow cow = new Cow(EntityType.COW, level);
            try {
                PuppetEntity a = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity b = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity far = PuppetTest.create(server, owner, 0);
                far.moveTo(0.5, ground, 40.5, 0f, 0f); // out of Directive's 32 blocks
                assertEquals(PuppetOrder.FOLLOW, a.order(), "new puppets follow");
                InnateTechniqueService.select(owner, 1);

                // Follow -> Stay -> Guard, for every puppet in range; the far one doesn't hear it.
                directive(owner);
                assertEquals(PuppetOrder.STAY, owner.getData(PuppetRegistry.OWNER.get()).order(), "the owner's order");
                assertEquals(PuppetOrder.STAY, a.order());
                assertEquals(PuppetOrder.STAY, b.order());
                assertEquals(PuppetOrder.FOLLOW, far.order(), "out of range: unchanged");
                directive(owner);
                assertEquals(PuppetOrder.GUARD, a.order());

                // Go needs a block under the crosshair; looking at the sky skips it.
                owner.setXRot(-80f);
                directive(owner);
                assertEquals(PuppetOrder.FOLLOW, a.order(), "no block to go to: Go skipped");
                directive(owner); // Stay
                directive(owner); // Guard
                owner.setXRot(30f); // the ground a few blocks ahead
                directive(owner);
                assertEquals(PuppetOrder.GO, a.order());
                BlockPos to = a.goTarget();
                assertNotNull(to, "Go has a destination");
                assertEquals(ground, to.getY(), "on top of the ground");
                for (int i = 0; i < 160 && a.distanceToSqr(to.getX() + 0.5, to.getY(), to.getZ() + 0.5) > 1.44; i++) tick(server, a);
                assertTrue(a.distanceToSqr(to.getX() + 0.5, to.getY(), to.getZ() + 0.5) <= 1.44, "walked there: at " + a.position()
                        + " to " + to + " nav done " + a.getNavigation().isDone() + " path " + a.getNavigation().getPath()
                        + " owner at " + owner.position() + " spinning " + a.isSpinning());

                // New puppets take the current order.
                InnateTechniqueService.select(owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity late = PuppetTest.create(server, owner, 0);
                assertEquals(PuppetOrder.GO, late.order(), "a new puppet takes the current order");
                late.discard();

                // Stay: it stays put while the owner walks off. Follow: it comes after them.
                InnateTechniqueService.select(owner, 1);
                DirectiveAbility.give(owner, PuppetOrder.STAY, null);
                b.moveTo(owner.getX() + 1, ground, owner.getZ(), 0f, 0f);
                double bx = b.getX();
                owner.moveTo(owner.getX(), ground, owner.getZ() + 10, 0f, 0f);
                for (int i = 0; i < 60; i++) tick(server, b);
                assertEquals(bx, b.getX(), 0.3, "Stay: it stays");
                DirectiveAbility.give(owner, PuppetOrder.FOLLOW, null);
                for (int i = 0; i < 120; i++) tick(server, b);
                assertTrue(b.distanceTo(owner) < 4.0, "Follow: it caught up (" + b.distanceTo(owner) + ")");

                // Guard: a mob hurts the owner; the guards go for it, and punch it (barely).
                DirectiveAbility.give(owner, PuppetOrder.GUARD, null);
                cow.moveTo(owner.getX() + 2, ground, owner.getZ(), 0f, 0f);
                level.addFreshEntity(cow);
                owner.invulnerableTime = 0;
                assertTrue(owner.hurt(level.damageSources().mobAttack(cow), 1f), "setup: the cow's hit landed");
                assertSame(cow, b.getTarget(), "the guard goes for the cow");
                assertNull(far.getTarget(), "the far puppet doesn't (out of range)");
                float cowHealth = cow.getHealth();
                for (int i = 0; i < 120 && cow.getHealth() >= cowHealth; i++) {
                    tick(server, b);
                    cow.invulnerableTime = 0;
                }
                assertTrue(cow.getHealth() < cowHealth, "the guard landed a punch");
                assertTrue(cowHealth - cow.getHealth() <= PuppetConfig.MELEE_DAMAGE.get() + 1e-3, "a weak one");

                // Pathing stops about a block short of a target: a half-size puppet still reaches it from there.
                b.moveTo(cow.getX(), ground, cow.getZ() + 1.8, 0f, 0f);
                assertTrue(b.isWithinMeleeAttackRange(cow), "reaches a cow from where pathing leaves it");
                b.moveTo(cow.getX(), ground, cow.getZ() + 2.6, 0f, 0f);
                assertFalse(b.isWithinMeleeAttackRange(cow), "but not from further");

                // A new order calls off the chase.
                DirectiveAbility.give(owner, PuppetOrder.STAY, null);
                assertNull(b.getTarget(), "new order: chase over");

                // Focus: every puppet in range swarms what the owner looks at.
                InnateTechniqueService.clearCooldowns(owner);
                MaliceService.refill(owner);
                cow.moveTo(owner.getX() + 6, ground, owner.getZ(), 0f, 0f);
                PossessionTest.aimAt(owner, cow);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertSame(cow, a.getTarget(), "Focus: a swarms the cow");
                assertSame(cow, b.getTarget(), "and so does b");
                assertNull(far.getTarget(), "the far one doesn't hear it");

                // Bosses can be targeted (and damaged); never the owner.
                WitherBoss wither = EntityType.WITHER.create(level);
                wither.moveTo(owner.getX() + 3, ground, owner.getZ(), 0f, 0f);
                wither.setNoAi(true);
                level.addFreshEntity(wither);
                assertTrue(a.attack(wither), "a boss is a fair target");
                wither.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                float witherHealth = wither.getHealth();
                a.moveTo(wither.getX() - 1.0, ground, wither.getZ(), -90f, 0f);
                assertTrue(a.doHurtTarget(wither), "the punch lands on the boss");
                assertTrue(wither.getHealth() < witherHealth, "and hurts it");
                assertEquals(net.minecraft.world.phys.Vec3.ZERO, wither.getDeltaMovement(), "but never pushes it");
                wither.discard();
                assertFalse(a.attack(owner), "never the owner");
                assertFalse(a.attack(b), "nor a puppet of the same owner");
            } finally {
                cow.discard();
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void focusOnAPlayerAndOfflineOwners(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            server.setPvpAllowed(true);
            ServerPlayer owner = PossessionTest.damageable(server, "focus_owner");
            ServerPlayer victim = PossessionTest.damageable(server, "focus_victim");
            level.addNewPlayer(owner);
            level.addNewPlayer(victim);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            try {
                PuppetEntity puppet = PuppetTest.create(server, owner, PuppetConfig.ticks(PuppetConfig.CREATION_FULL_CHARGE));
                victim.moveTo(owner.getX() + 6, ground, owner.getZ(), 90f, 0f);
                victim.setHealth(20f);
                InnateTechniqueService.select(owner, 1);
                PossessionTest.aimAt(owner, victim);
                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertSame(victim, puppet.getTarget(), "Focus works on players");
                puppet.moveTo(victim.getX() - 0.6, ground, victim.getZ(), -90f, 0f);
                for (int i = 0; i < 60 && victim.getHealth() >= 20f; i++) {
                    tick(server, puppet);
                    victim.invulnerableTime = 0;
                }
                assertTrue(victim.getHealth() < 20f, "and the puppets hit them");

                // The owner goes offline: their puppets idle and never attack.
                level.removePlayerImmediately(owner, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                puppet.tick();
                assertNull(puppet.getTarget(), "owner offline: no target");
                assertFalse(puppet.attack(victim), "and can't be given one");
                level.addNewPlayer(owner);
            } finally {
                level.removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    /** One server tick for one entity: the world's clock moves too (AI goals time some checks by it). */
    static void tick(MinecraftServer server, Entity entity) {
        TestServer.advanceGameTime(server, 1);
        entity.tick();
    }

    private static void directive(ServerPlayer owner) {
        InnateTechniqueService.clearCooldowns(owner);
        MaliceService.refill(owner);
        CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
    }

    static List<PuppetEntity> puppets(ServerLevel level, ServerPlayer owner) {
        return PuppetTest.puppetsOf(level, owner);
    }
}
