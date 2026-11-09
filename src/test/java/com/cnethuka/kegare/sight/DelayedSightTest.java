package com.cnethuka.kegare.sight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.severed.Senses;
import com.cnethuka.kegare.severed.SensedPositionsPayload;
import com.cnethuka.kegare.severed.Severed;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Too fast for the eye (DESIGN §3.30): who sees the image, who sees the real position, the ease, mobs, and hits on the image measured. */
@ExtendWith(EphemeralTestServerProvider.class)
class DelayedSightTest {
    private static final double Y = 120;
    /** Blur's top speed and Accelerate's sprint, in blocks a tick (DESIGN §3.27: Blur 14 b/s, Accelerate 11.2). */
    private static final double BLUR_SPEED = 0.7, ACCELERATE_SPEED = 0.56;

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, int capacity) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static ServerPlayer blurring(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer s = player(server, level, name, 0);
        s.moveTo(0.5, Y, 0.5, -90f, 0f);
        Severed.toggleBlur(s);
        assertTrue(Severed.blurring(s), "setup: Blurring");
        return s;
    }

    private static ServerPlayer accelerating(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = player(server, level, name, 3000);
        InnateTechniqueService.setInnateTechnique(p, TimeManipulation.TIME_MANIPULATION.get());
        MaliceService.refill(p);
        p.moveTo(0.5, Y, 0.5, -90f, 0f);
        InnateTechniqueService.select(p, 1);
        CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        assertTrue(TimeManipulation.ACCELERATE.isActive(p), "setup: Accelerating");
        return p;
    }

    /** A tick: the mover goes {@code step} along x, then the end of the server tick. */
    private static void step(MinecraftServer server, ServerPlayer mover, double step) {
        mover.setPos(mover.getX() + step, mover.getY(), mover.getZ());
        DelayedSight.tick(server);
        TestServer.advanceGameTime(server, 1);
    }

    private static void run(MinecraftServer server, ServerPlayer mover, double speed, int ticks) {
        for (int i = 0; i < ticks; i++) step(server, mover, speed);
    }

    @Test
    void othersSeeABlurringPlayerBehindAndTheRightOnesSeeTheRealPosition(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                ServerPlayer s = blurring(server, level, "sight_blur");
                ServerPlayer viewer = player(server, level, "sight_viewer", 1000);
                ServerPlayer six = player(server, level, "sight_six", 1000);
                com.cnethuka.kegare.trait.TraitSuccession.release(server, KegareTraits.SIX_EYES.get()); // unique: free it first
                assertEquals(TraitService.AddResult.ADDED, TraitService.add(six, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN), "setup: Six Eyes");
                ServerPlayer senser = player(server, level, "sight_senser", 0);
                viewer.moveTo(0.5, Y, 10.5);
                six.moveTo(0.5, Y, 12.5);
                senser.moveTo(0.5, Y, 14.5);

                run(server, s, BLUR_SPEED, 20);
                assertTrue(DelayedSight.hidden(s));
                assertEquals(SightConfig.BLUR_DELAY.get(), DelayedSight.delayMs(s), 1.0e-6, "eased in to blurDelay");
                double behind = SightConfig.BLUR_DELAY.get() / 50.0 * BLUR_SPEED;
                assertEquals(s.getX() - behind, DelayedSight.seenAt(viewer, s).x, 1.0e-6, "a normal viewer: where they were 150 ms ago");
                assertEquals(s.getX(), DelayedSight.seenAt(s, s).x, 1.0e-9, "themself: the real one");
                assertEquals(s.getX(), DelayedSight.seenAt(six, s).x, 1.0e-9, "a Six Eyes bearer: the real one");
                assertEquals(s.getX() - behind, DelayedSight.seenAt(senser, s).x, 1.0e-6, "a Severed player's eyes: the image");
                List<SensedPositionsPayload.Entry> sensed = Senses.realPositions(senser, List.of(s.getId()));
                assertEquals(1, sensed.size(), "but their Senses get the real position");
                assertEquals(s.position(), sensed.get(0).pos());
                assertTrue(Senses.realPositions(six, List.of(s.getId())).isEmpty(), "nothing extra for someone who sees it anyway");

                // A viewer who starts watching now is told the image from the first packet.
                ClientboundAddEntityPacket spawn = (ClientboundAddEntityPacket) DelayedSight.spawnPacket(s, viewer);
                assertNotNull(spawn);
                assertEquals(s.getX() - behind, spawn.getX(), 1.0e-6, "spawned at the image");
                assertNull(DelayedSight.spawnPacket(s, six), "vanilla's own for a Six Eyes bearer");
                assertTrue(DelayedSight.isMovement(DelayedSight.teleport(s.getId(), Vec3.ZERO, 0f, 0f, true)), "what the tracker holds back");

                // Faster is further behind: Accelerate is 100 ms, at its own speed.
                ServerPlayer a = accelerating(server, level, "sight_accel");
                a.moveTo(0.5, Y, 4.5);
                run(server, a, ACCELERATE_SPEED, 20);
                double aBehind = SightConfig.ACCELERATE_DELAY.get() / 50.0 * ACCELERATE_SPEED;
                assertEquals(a.getX() - aBehind, DelayedSight.seenAt(viewer, a).x, 1.0e-6, "Accelerate: 100 ms behind");
                assertTrue(behind > aBehind, "Blur is further behind: faster, and longer");
            } finally {
                com.cnethuka.kegare.trait.TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
                DelayedSight.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itEasesBackWithNoSnapAndNothingIsDelayedOtherwise(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                ServerPlayer walker = player(server, level, "sight_walker", 1000);
                ServerPlayer viewer = player(server, level, "sight_viewer2", 1000);
                viewer.moveTo(0.5, Y, 10.5);
                walker.moveTo(0.5, Y, 4.5);
                run(server, walker, 0.28, 10); // a sprint, neither state on
                assertFalse(DelayedSight.hidden(walker), "nothing delayed");
                assertEquals(walker.position(), DelayedSight.seenAt(viewer, walker));
                assertNull(DelayedSight.spawnPacket(walker, viewer));

                ServerPlayer s = blurring(server, level, "sight_ease");
                run(server, s, BLUR_SPEED, 20);
                Severed.toggleBlur(s);
                assertFalse(Severed.blurring(s), "setup: Blur off");
                int ease = SightConfig.EASE_TICKS.get();
                double last = DelayedSight.seenAt(viewer, s).x;
                double most = 0;
                for (int i = 0; i < ease; i++) {
                    step(server, s, BLUR_SPEED);
                    double now = DelayedSight.seenAt(viewer, s).x;
                    most = Math.max(most, now - last);
                    assertTrue(now >= last, "never backwards");
                    last = now;
                }
                assertFalse(DelayedSight.hidden(s), "caught up after easeTicks");
                assertEquals(s.getX(), DelayedSight.seenAt(viewer, s).x, 1.0e-9);
                double catchUp = BLUR_SPEED * (1 + SightConfig.BLUR_DELAY.get() / 50.0 / ease);
                assertTrue(most <= catchUp + 1.0e-6, "no snap: never more than " + catchUp + " a tick, was " + most);
            } finally {
                DelayedSight.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void mobsGoForWhereTheyWereAndBossesForWhereTheyAre(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            double ground = Y; // paths need ground: a floor of its own, clear of anything other tests built
            for (int x = -4; x <= 24; x++) {
                for (int z = -6; z <= 10; z++) level.setBlock(BlockPos.containing(x, Y - 1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            }
            server.setPvpAllowed(true);
            Zombie zombie = EntityType.ZOMBIE.create(level);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer s = blurring(server, level, "sight_mobs");
                s.moveTo(0.5, ground, 0.5);
                run(server, s, BLUR_SPEED, 20);
                Vec3 image = DelayedSight.seenAt(zombie, s);
                assertTrue(image.distanceTo(s.position()) > 2.0, "setup: the image is well behind");
                for (var mob : List.of(zombie, wither)) {
                    mob.setNoAi(true);
                    mob.setNoGravity(true);
                    mob.moveTo(image.x, ground, image.z + 6);
                    mob.setOnGround(true); // a ground mob only paths from the ground
                    level.addFreshEntity(mob);
                }
                assertTrue(DelayedSight.misleads(zombie, s));
                assertFalse(DelayedSight.misleads(wither, s), "a boss sees the real position");

                Path path = zombie.getNavigation().createPath(s, 0);
                assertNotNull(path);
                assertEquals(BlockPos.containing(image), path.getTarget(), "a mob paths to where they were");
                Path bossPath = wither.getNavigation().createPath(s, 0);
                if (bossPath != null) assertEquals(BlockPos.containing(s.position()), bossPath.getTarget(), "a boss, to where they are");

                zombie.getLookControl().setLookAt(s, 30f, 30f);
                assertEquals(image.x, zombie.getLookControl().getWantedX(), 1.0e-6, "and turns to where they were");
                wither.getLookControl().setLookAt(s, 30f, 30f);
                assertEquals(s.getX(), wither.getLookControl().getWantedX(), 1.0e-6);

                // Next to the image: it swings, and misses, since they've gone. Next to them: it doesn't swing at all.
                zombie.moveTo(image.x, ground, image.z + 0.9);
                assertTrue(zombie.isWithinMeleeAttackRange(s), "in reach of where it sees them");
                float health = s.getHealth();
                assertFalse(zombie.doHurtTarget(s), "the swing at where they were misses");
                assertEquals(health, s.getHealth(), 1.0e-6);
                zombie.moveTo(s.getX(), ground, s.getZ() + 0.9);
                assertFalse(zombie.isWithinMeleeAttackRange(s), "right by the real one, it sees nothing to swing at");
            } finally {
                for (int x = -4; x <= 24; x++) {
                    for (int z = -6; z <= 10; z++) level.setBlock(BlockPos.containing(x, Y - 1, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                zombie.discard();
                wither.discard();
                DelayedSight.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    /**
     * Hits on the image, measured (DESIGN §3.30): judged against the real position, with no reach widened. A melee swing aimed at the image at
     * 2, 3 and 4 blocks, with the mover running across, away and toward; an arrow from 12 blocks aimed at the image, and one led ahead.
     */
    @Test
    void hitsOnTheImageMeasured(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            StringBuilder report = new StringBuilder("Hits on the image:\n");
            try {
                ServerPlayer attacker = player(server, level, "sight_attacker", 1000);
                for (String who : List.of("Blur", "Accelerate")) {
                    boolean blur = who.equals("Blur");
                    double speed = blur ? BLUR_SPEED : ACCELERATE_SPEED;
                    double lag = (blur ? SightConfig.BLUR_DELAY.get() : SightConfig.ACCELERATE_DELAY.get()) / 50.0 * speed;
                    ServerPlayer mover = blur ? blurring(server, level, "sight_m_blur") : accelerating(server, level, "sight_m_accel");
                    // Melee: the attacker's client lets them swing at what's under the crosshair within their reach (3 blocks); the server
                    // takes it if the real body is within that reach plus 1 (ServerGamePacketListenerImpl.handleInteract).
                    for (double d : new double[] {2, 3, 4}) {
                        for (String way : List.of("across", "away", "toward")) {
                            Vec3 run = switch (way) {
                                case "across" -> new Vec3(1, 0, 0);
                                case "away" -> new Vec3(0, 0, 1);
                                default -> new Vec3(0, 0, -1);
                            };
                            attacker.moveTo(0.5, Y, 0.5, 0f, 0f);
                            Vec3 eye = attacker.getEyePosition();
                            AABB imageBox = new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3).move(0.5, Y, 0.5 + d); // d blocks apart, feet to feet
                            AABB realBox = imageBox.move(run.scale(lag));
                            boolean canSwing = imageBox.distanceToSqr(eye) < attacker.entityInteractionRange() * attacker.entityInteractionRange();
                            boolean lands = canSwing && attacker.canInteractWithEntity(realBox, 1.0);
                            report.append(String.format(Locale.ROOT, "  %s, melee at the image %.0f blocks, running %s: %s%n", who, d, way,
                                    !canSwing ? "out of reach (can't swing)" : lands ? "lands" : "misses (the real body is out of reach)"));
                        }
                    }
                    // Arrows: a full-power shot from 12 blocks across their run, at the image's chest, and one led ahead.
                    for (boolean led : new boolean[] {false, true}) {
                        DelayedSight.clear();
                        mover.moveTo(0.5, Y, 20.5, -90f, 0f);
                        mover.setHealth(mover.getMaxHealth());
                        mover.invulnerableTime = 0;
                        run(server, mover, speed, 12); // up to speed, the image eased in
                        attacker.moveTo(mover.getX() + 4, Y, 8.5, 0f, 0f);
                        Vec3 aim = DelayedSight.seenAt(attacker, mover).add(0, 1.2, 0);
                        double travel = aim.distanceTo(attacker.getEyePosition()) / 3.0;
                        if (led) aim = aim.add(speed * (lag / speed + travel), 0, 0);
                        Arrow arrow = new Arrow(level, attacker, new ItemStack(Items.ARROW), null);
                        Vec3 dir = aim.subtract(arrow.position());
                        arrow.shoot(dir.x, dir.y, dir.z, 3.0f, 0f);
                        level.addFreshEntity(arrow);
                        float before = mover.getHealth();
                        boolean hit = false;
                        for (int t = 0; t < 20 && !hit && !arrow.isRemoved(); t++) {
                            step(server, mover, speed);
                            arrow.tick();
                            hit = mover.getHealth() < before;
                        }
                        arrow.discard();
                        report.append(String.format(Locale.ROOT, "  %s, arrow %s: %s%n", who, led ? "led ahead of the image" : "aimed at the image",
                                hit ? "hits" : "misses"));
                    }
                }
                System.out.print(report);
                assertEquals(3.0, attacker.entityInteractionRange(), 1.0e-9, "no reach widened");
            } finally {
                DelayedSight.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
