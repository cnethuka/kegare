package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.player.BirthService;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The green orb: who rolls and when, where it appears, following it along its route, the unlock and its healing. */
@ExtendWith(EphemeralTestServerProvider.class)
class GreenOrbTest {
    @Test
    void whoRollsAndWhen(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_roller");

            // Above the health threshold: no roll.
            p.setHealth(15f);
            GreenOrbService.onHitByPlayer(p);
            assertEquals(Long.MIN_VALUE, SharedAbilityService.get(p).lastOrbRoll(), "healthy enough: no roll");
            // At or below it: a roll (chance decides the orb), then none until the cooldown passes.
            p.setHealth(9f);
            GreenOrbService.state(p).clearOrb();
            GreenOrbService.onHitByPlayer(p);
            long rolled = SharedAbilityService.get(p).lastOrbRoll();
            assertEquals(p.level().getGameTime(), rolled, "rolled");
            GreenOrbService.state(p).clearOrb();
            TestServer.advanceGameTime(server, 20);
            GreenOrbService.onHitByPlayer(p);
            assertEquals(rolled, SharedAbilityService.get(p).lastOrbRoll(), "one roll per cooldown");
            TestServer.advanceGameTime(server, RmtConfig.ticks(RmtConfig.ORB_ROLL_COOLDOWN));
            GreenOrbService.onHitByPlayer(p);
            assertTrue(SharedAbilityService.get(p).lastOrbRoll() > rolled, "rolls again after the cooldown");

            // Severed players never get it, forced or rolled.
            ServerPlayer severed = TestServer.player(server, "orb_severed");
            BirthService.rebirth(severed, OptionalInt.of(0));
            assertEquals(GreenOrbService.ForceResult.SEVERED, GreenOrbService.force(severed));
            severed.setHealth(4f);
            GreenOrbService.onHitByPlayer(severed);
            assertEquals(Long.MIN_VALUE, SharedAbilityService.get(severed).lastOrbRoll(), "no roll for Severed");
            assertFalse(GreenOrbService.state(severed).hasOrb());
        });
    }

    @Test
    void itStartsToOneSideOfTheCrosshairAndWaits(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_start");
            assertEquals(GreenOrbService.ForceResult.SPAWNED, GreenOrbService.force(p));
            RmtState state = GreenOrbService.state(p);
            Vec3 orb = state.orb();
            assertNotNull(orb);
            assertEquals(RmtConfig.ORB_DISTANCE.get(), orb.distanceTo(p.getEyePosition()), 0.05, "at the configured distance");
            double offCentre = Math.toDegrees(Math.acos(p.getLookAngle().dot(orb.subtract(p.getEyePosition()).normalize())));
            assertEquals(RmtConfig.ORB_SIDE_ANGLE.get(), offCentre, 0.5, "off to the side of the crosshair");
            assertEquals(RmtConfig.ORB_PATH_LEGS.get() + 1, state.path().points().size(), "the route's points");
            assertEquals(RmtConfig.ticks(RmtConfig.ORB_FOLLOW_SECONDS), state.followTotal());

            for (int i = 0; i < state.followTotal() + 5; i++) GreenOrbService.tick(p);
            assertEquals(0, state.followTicks(), "looking ahead doesn't set it off");
            assertEquals(orb, state.orb(), "it waits where it is");
            assertFalse(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "and never takes it in");
        });
    }

    @Test
    void routesDifferBetweenOrbs(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_routes");
            GreenOrbService.force(p);
            List<Vec3> first = GreenOrbService.state(p).path().points();
            GreenOrbService.force(p);
            assertTrue(!first.equals(GreenOrbService.state(p).path().points()), "a new route each time");
        });
    }

    @Test
    void followingItToTheEndTakesItIn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_finder");
            p.setHealth(9f);
            GreenOrbService.force(p);
            RmtState state = GreenOrbService.state(p);
            int total = state.followTotal();
            Vec3 start = state.orb();

            follow(p, total / 2);
            assertEquals(total / 2, state.followTicks(), "it moves on every tick they keep it in sight");
            assertTrue(state.orb().distanceTo(start) > 0.5, "and it really has moved");

            follow(p, total - total / 2 - 1);
            assertFalse(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "not yet: one tick short of the end");
            assertEquals(9f, p.getHealth(), 1e-4);
            follow(p, 1);
            assertTrue(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "taken in");
            assertEquals(9f + RmtConfig.ORB_UNLOCK_HEAL.get(), p.getHealth(), 1e-4, "real healing");
            assertFalse(state.hasOrb());

            // Never again.
            assertEquals(GreenOrbService.ForceResult.ALREADY_UNLOCKED, GreenOrbService.force(p));
            assertFalse(GreenOrbService.eligible(p));

            // The heal never goes above max health.
            ServerPlayer q = born(server, "orb_nearly_full");
            q.setHealth(q.getMaxHealth() - 1f);
            GreenOrbService.force(q);
            follow(q, GreenOrbService.state(q).followTotal());
            assertEquals(q.getMaxHealth(), q.getHealth(), 1e-4);
        });
    }

    /** The whole point: looking at where it started gets you nowhere, however long. */
    @Test
    void lookingAtItWithoutFollowingNeverTakesItIn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            for (int run = 0; run < 25; run++) { // it mustn't depend on the route
                ServerPlayer p = born(server, "orb_starer" + run);
                GreenOrbService.force(p);
                RmtState state = GreenOrbService.state(p);
                p.lookAt(EntityAnchorArgument.Anchor.EYES, state.orb());
                for (int i = 0; i < state.followTotal() * 10; i++) GreenOrbService.tick(p);
                assertFalse(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "run " + run);
                assertTrue(state.followTicks() < state.followTotal() / 2, "it never got far from their look: " + state.followTicks());
            }
        });
    }

    @Test
    void losingItSendsItBackAlongTheRoute(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = born(server, "orb_loser");
            GreenOrbService.force(p);
            RmtState state = GreenOrbService.state(p);
            int total = state.followTotal();
            int rewind = RmtConfig.ORB_FOLLOW_REWIND.get();
            Vec3 start = state.orb();

            follow(p, 30);
            lookAway(p, state);
            GreenOrbService.tick(p);
            assertEquals(30 - rewind, state.followTicks(), "back, faster than it came");

            // Caught again in time, it carries on from there rather than from the start.
            follow(p, 1);
            assertEquals(30 - rewind + 1, state.followTicks());

            // Lost for good, it's back where it started and can be tried again.
            for (int i = 0; i < 40; i++) {
                lookAway(p, state);
                GreenOrbService.tick(p);
            }
            assertEquals(0, state.followTicks());
            assertEquals(start, state.orb());
            assertTrue(state.hasOrb(), "still there");
            follow(p, total);
            assertTrue(SharedAbilityService.isUnlocked(p, RmtIds.ABILITY), "a second try counts");
        });
    }

    @Test
    void itFadesOnTimeButNeverUnderSomeoneFollowingIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            int lifetime = RmtConfig.ticks(RmtConfig.ORB_LIFETIME);

            ServerPlayer ignored = born(server, "orb_ignored");
            GreenOrbService.force(ignored);
            TestServer.advanceGameTime(server, lifetime - 1);
            GreenOrbService.tick(ignored);
            assertTrue(GreenOrbService.state(ignored).hasOrb(), "one tick left");
            TestServer.advanceGameTime(server, 1);
            GreenOrbService.tick(ignored);
            assertFalse(GreenOrbService.state(ignored).hasOrb(), "faded");

            // Started with one tick left, the follow still gets to finish, far past that.
            ServerPlayer late = born(server, "orb_late");
            GreenOrbService.force(late);
            int total = GreenOrbService.state(late).followTotal();
            TestServer.advanceGameTime(server, lifetime - 1);
            for (int i = 0; i < total; i++) {
                assertTrue(GreenOrbService.state(late).hasOrb(), "tick " + i);
                follow(late, 1);
                TestServer.advanceGameTime(server, 1);
            }
            assertTrue(SharedAbilityService.isUnlocked(late, RmtIds.ABILITY), "made it");

            // Lost that late, it's gone as soon as it has run back: no extra time for trying again.
            ServerPlayer lost = born(server, "orb_lost_late");
            GreenOrbService.force(lost);
            RmtState state = GreenOrbService.state(lost);
            TestServer.advanceGameTime(server, lifetime - 1);
            for (int i = 0; i < 12; i++) {
                follow(lost, 1);
                TestServer.advanceGameTime(server, 1);
            }
            for (int i = 0; i < 6 && state.hasOrb(); i++) {
                lookAway(lost, state);
                GreenOrbService.tick(lost);
                TestServer.advanceGameTime(server, 1);
            }
            assertFalse(state.hasOrb(), "faded");
            assertFalse(SharedAbilityService.isUnlocked(lost, RmtIds.ABILITY));
        });
    }

    @Test
    void theRouteStaysShortOfWalls(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            ServerPlayer p = born(server, "orb_walled");
            p.moveTo(0.5, 100, 0.5, -90f, 0f); // facing +X
            for (int y = 92; y <= 112; y++) {
                for (int z = -12; z <= 12; z++) level.setBlockAndUpdate(new BlockPos(2, y, z), Blocks.STONE.defaultBlockState());
            }
            try {
                for (int run = 0; run < 25; run++) {
                    GreenOrbService.force(p);
                    OrbPath path = GreenOrbService.state(p).path();
                    for (int i = 0; i <= 60; i++) {
                        assertTrue(path.at(i / 60.0).x < 2.0, "run " + run + " is in the wall at " + path.at(i / 60.0));
                    }
                }
                // Pressed right up against it, the look check still means something.
                p.moveTo(1.7, 100, 0.5, -90f, 0f);
                GreenOrbService.force(p);
                for (Vec3 point : GreenOrbService.state(p).path().points()) {
                    assertTrue(point.distanceTo(p.getEyePosition()) >= 0.74, "too close to the eyes: " + point);
                }
            } finally {
                for (int y = 92; y <= 112; y++) {
                    for (int z = -12; z <= 12; z++) level.setBlockAndUpdate(new BlockPos(2, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        });
    }

    @Test
    void thePayloadSurvivesTheWire() {
        OrbPath path = new OrbPath(List.of(new Vec3(1.5, 2.25, -3), new Vec3(4, 5, 6), new Vec3(-7, 8, 9.75)));
        for (OrbPayload sent : List.of(OrbPayload.show(path, 60), OrbPayload.progress(17), OrbPayload.hide(), OrbPayload.taken(new Vec3(1, 2, 3)))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            OrbPayload.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, OrbPayload.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    /** Eyes on the orb, tick by tick. */
    private static void follow(ServerPlayer p, int ticks) {
        RmtState state = GreenOrbService.state(p);
        for (int i = 0; i < ticks && state.hasOrb(); i++) {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, state.orb());
            GreenOrbService.tick(p);
        }
    }

    private static void lookAway(ServerPlayer p, RmtState state) {
        Vec3 away = p.getEyePosition().subtract(state.orb().subtract(p.getEyePosition()));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, away);
    }

    private static ServerPlayer born(MinecraftServer server, String name) {
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(500));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        p.moveTo(0.5, 100, 0.5, -90f, 0f); // open air: nothing between the eyes and the orb
        return p;
    }
}
