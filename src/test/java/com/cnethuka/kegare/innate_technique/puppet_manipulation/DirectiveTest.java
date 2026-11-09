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
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Landings;
import com.cnethuka.kegare.progression.ProgressionConfig;
import com.cnethuka.kegare.progression.RefinementService;
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
    private static Cow cow(ServerLevel level, double x, int y, double z) {
        Cow cow = new Cow(EntityType.COW, level);
        cow.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(cow);
        return cow;
    }

    private static void hitBy(ServerPlayer player, net.minecraft.world.entity.LivingEntity target) {
        target.invulnerableTime = 0;
        assertTrue(target.hurt(player.damageSources().playerAttack(player), 1f), "setup: the hit landed");
    }

    @Test
    void guardsGoForWhatTheirOwnerDamages(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "guard_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            ServerPlayer enemy = PossessionTest.damageable(server, "guard_enemy");
            level.addNewPlayer(enemy);
            enemy.moveTo(3.5, ground, 3.5, 0f, 0f);
            Cow first = cow(level, 2.5, ground, 0.5);
            Cow second = cow(level, -1.5, ground, 0.5);
            WitherBoss wither = EntityType.WITHER.create(level);
            wither.moveTo(3.5, ground, -3.5, 0f, 0f);
            wither.setNoAi(true);
            level.addFreshEntity(wither);
            net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level, 0.5, ground, 3.5);
            level.addFreshEntity(stand);
            try {
                PuppetEntity a = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity b = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);

                hitBy(owner, first);
                assertNull(a.getTarget(), "only Guard does this: they're following");

                DirectiveAbility.give(owner, PuppetOrder.GUARD, null);
                hitBy(owner, first);
                assertSame(first, a.getTarget(), "a mob the owner hit");
                assertSame(first, b.getTarget(), "both of them");

                hitBy(owner, second);
                assertSame(first, a.getTarget(), "one already chasing something keeps at it");

                a.setTarget(null);
                b.setTarget(null);
                enemy.invulnerableTime = 0;
                assertTrue(enemy.hurt(owner.damageSources().playerAttack(owner), 1f), "setup: the hit landed");
                assertSame(enemy, a.getTarget(), "a player the owner hit");

                // A technique of the owner's counts, however it was delivered.
                a.setTarget(null);
                b.setTarget(null);
                second.invulnerableTime = 0;
                assertTrue(second.hurt(CurioBursts.source(level, b, owner), 1f), "setup: the blast landed");
                assertSame(second, a.getTarget(), "their Curio Burst counts as them");

                // A puppet's own punch doesn't call the others over, and other people's hits are none of their business.
                a.setTarget(null);
                b.setTarget(null);
                first.invulnerableTime = 0;
                assertTrue(first.hurt(level.damageSources().mobAttack(b), 1f), "setup: the punch landed");
                assertNull(a.getTarget(), "a puppet's punch isn't the owner's doing");
                hitBy(enemy, second);
                assertNull(a.getTarget(), "someone else's hit isn't either");

                // Never their own, or a dummy.
                b.invulnerableTime = 0;
                b.hurt(owner.damageSources().playerAttack(owner), 1f);
                assertNull(a.getTarget(), "hitting your own puppet gives the others no ideas");
                Puppets.ownerDealtDamage(owner, stand);
                assertNull(a.getTarget(), "an armor stand isn't a target");
                Puppets.ownerDealtDamage(owner, owner);
                assertNull(a.getTarget(), "nor are you");

                // §4: a boss can be targeted and hurt, and is never moved.
                hitBy(owner, wither);
                assertSame(wither, a.getTarget(), "a boss the owner hit");
                wither.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                float health = wither.getHealth();
                a.moveTo(wither.getX() - 1.0, ground, wither.getZ(), -90f, 0f);
                wither.invulnerableTime = 0;
                assertTrue(a.doHurtTarget(wither), "the punch lands");
                assertTrue(wither.getHealth() < health, "and hurts it");
                assertEquals(net.minecraft.world.phys.Vec3.ZERO, wither.getDeltaMovement(), "but never pushes it");
            } finally {
                first.discard();
                second.discard();
                wither.discard();
                stand.discard();
                enemy.discard();
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void whatAGuardLandsEarnsItsOwnerRefinementUnderTheUsualRules(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "guard_refinement");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            Cow target = cow(level, 2.5, ground, 0.5);
            Cow other = cow(level, -1.5, ground, 0.5);
            try {
                assertEquals(0, RefinementService.level(owner), "a fresh Puppet player");
                PuppetEntity guard = PuppetTest.create(server, owner, 0);
                DirectiveAbility.give(owner, PuppetOrder.GUARD, null);
                hitBy(owner, target);
                assertEquals(0.0, RefinementService.get(owner).xp(), 1.0e-6, "a bare-handed hit earns nothing");
                assertSame(target, guard.getTarget());

                guard.moveTo(target.getX() - 1.0, ground, target.getZ(), -90f, 0f);
                target.invulnerableTime = 0;
                assertTrue(guard.doHurtTarget(target), "setup: the punch landed");
                double one = RefinementService.get(owner).xp();
                assertTrue(one > 0, "the guard's punch earned Refinement");

                target.invulnerableTime = 0;
                assertTrue(guard.doHurtTarget(target), "setup: the second punch landed");
                assertEquals(one, RefinementService.get(owner).xp(), 1.0e-6, "the repeat limit: no landing twice within a second");

                TestServer.advanceGameTime(server, (int) com.cnethuka.kegare.progression.ProgressionConfig.ticks(com.cnethuka.kegare.progression.ProgressionConfig.LANDING_REPEAT) + 1);
                target.invulnerableTime = 0;
                assertTrue(guard.doHurtTarget(target), "setup: the third punch landed");
                double two = RefinementService.get(owner).xp();
                assertTrue(two > one && two - one < one, "the same target is worth less each time: " + one + " then " + (two - one));

                other.invulnerableTime = 0;
                assertTrue(guard.doHurtTarget(other), "setup: the punch on a fresh target landed");
                assertEquals(one, RefinementService.get(owner).xp() - two, 1.0e-6, "a new target is worth full value");
            } finally {
                target.discard();
                other.discard();
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    /** A punch that lands and hurts, from next to the target, on a target topped up so it lasts. */
    private static void punch(PuppetEntity puppet, net.minecraft.world.entity.LivingEntity target) {
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        puppet.moveTo(target.getX() - 1.0, target.getY(), target.getZ(), -90f, 0f);
        assertTrue(puppet.doHurtTarget(target), "setup: the punch landed");
        assertTrue(target.getHealth() < target.getMaxHealth(), "setup: and hurt");
    }

    @Test
    void anIdleOwnerEarnsNothingFromTheirPuppets(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "idle_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            Cow target = cow(level, 2.5, ground, 0.5);
            Cow other = cow(level, -1.5, ground, 0.5);
            net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level, 0.5, ground, 3.5);
            level.addFreshEntity(stand);
            int repeat = (int) ProgressionConfig.ticks(ProgressionConfig.LANDING_REPEAT) + 1;
            int window = (int) ProgressionConfig.ticks(ProgressionConfig.OWNER_ACTIVE_WINDOW);
            try {
                assertEquals(30.0, ProgressionConfig.OWNER_ACTIVE_WINDOW.get(), "the default");
                PuppetEntity guard = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity spare = PuppetTest.create(server, owner, 0);
                DirectiveAbility.give(owner, PuppetOrder.GUARD, null);

                // They've done nothing yet.
                punch(guard, target);
                assertEquals(0.0, RefinementService.get(owner).xp(), 1.0e-6, "no hit of their own yet");
                assertEquals(0.0, GradeService.get(owner).xp(), 1.0e-6, "no Grade XP either");

                // A hit by hand opens the window, right up to its end.
                hitBy(owner, other);
                long hitAt = level.getGameTime();
                double xp = RefinementService.get(owner).xp();
                double grade = GradeService.get(owner).xp();
                TestServer.advanceGameTime(server, repeat);
                punch(guard, target);
                assertTrue(RefinementService.get(owner).xp() > xp, "within the window it earns");
                assertTrue(GradeService.get(owner).xp() > grade, "Grade XP too");
                TestServer.advanceGameTime(server, window - (int) (level.getGameTime() - hitAt));
                xp = RefinementService.get(owner).xp();
                punch(guard, target);
                assertTrue(RefinementService.get(owner).xp() > xp, "right to its last tick");

                // Past it, the puppet still fights and they earn nothing.
                TestServer.advanceGameTime(server, repeat);
                xp = RefinementService.get(owner).xp();
                grade = GradeService.get(owner).xp();
                punch(guard, target);
                assertEquals(xp, RefinementService.get(owner).xp(), 1.0e-6, "idle: no Refinement");
                assertEquals(grade, GradeService.get(owner).xp(), 1.0e-6, "idle: no Grade XP");

                // Left to itself it really does go on fighting.
                hitBy(owner, other); // sets the target going...
                guard.setTarget(null);
                guard.attack(target);
                TestServer.advanceGameTime(server, window + repeat); // ...and they stop
                target.setHealth(target.getMaxHealth());
                float health = target.getHealth();
                xp = RefinementService.get(owner).xp();
                for (int i = 0; i < 200 && target.getHealth() >= health; i++) {
                    tick(server, guard);
                    target.invulnerableTime = 0;
                }
                assertTrue(target.getHealth() < health, "the guard kept at it on its own");
                assertEquals(xp, RefinementService.get(owner).xp(), 1.0e-6, "and earned them nothing");

                // None of these are their landing a hit or an ability on something real.
                TestServer.advanceGameTime(server, window + repeat);
                owner.invulnerableTime = 0;
                assertTrue(owner.hurt(level.damageSources().mobAttack(other), 1f), "setup: being hit");
                spare.invulnerableTime = 0;
                spare.hurt(owner.damageSources().playerAttack(owner), 1f);
                Landings.land(owner, stand, PuppetManipulation.CURIO_BURST);
                Landings.land(owner, spare, PuppetManipulation.CURIO_BURST);
                punch(guard, target);
                Landings.usedWithoutTarget(owner, PuppetManipulation.DIRECTIVE);
                assertTrue(RefinementService.get(owner).xp() > xp, "an ability with no target earns for itself, used in a fight");
                TestServer.advanceGameTime(server, repeat);
                xp = RefinementService.get(owner).xp();
                punch(guard, target);
                assertEquals(xp, RefinementService.get(owner).xp(), 1.0e-6, "but being hit, hitting your own and that don't make you active");

                // An ability they land does, including one that goes off through a puppet.
                Landings.land(owner, other, PuppetManipulation.CURIO_BURST);
                TestServer.advanceGameTime(server, repeat);
                xp = RefinementService.get(owner).xp();
                punch(guard, target);
                assertTrue(RefinementService.get(owner).xp() > xp, "an ability landed on something real");

                TestServer.advanceGameTime(server, window + repeat);
                other.invulnerableTime = 0;
                assertTrue(other.hurt(CurioBursts.source(level, spare, owner), 1f), "setup: the blast landed");
                TestServer.advanceGameTime(server, repeat);
                xp = RefinementService.get(owner).xp();
                punch(guard, target);
                assertTrue(RefinementService.get(owner).xp() > xp, "their Curio Burst is their own doing");
            } finally {
                target.discard();
                other.discard();
                stand.discard();
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

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

                // Guard: a mob hurts the owner; the guards go for it, and punch it (barely). The owner has walked 10 blocks toward
                // the far puppet since, so it's put back out of Directive's range of where they stand now (it only passed before
                // because its chunk's entities were out of every search, TestServer.testAreaReady).
                far.moveTo(0.5, ground, owner.getZ() - 36, 0f, 0f);
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
