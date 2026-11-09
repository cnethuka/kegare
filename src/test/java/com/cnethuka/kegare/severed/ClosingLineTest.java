package com.cnethuka.kegare.severed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.melee.MeleeConfig;
import com.cnethuka.kegare.melee.MeleeIntentPayload;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Closing Line (DESIGN §3.27): R while Blurring rushes in a straight line at where the target stood, up to one on a pillar when the
 * line is clear; arriving earns a Strike for its window, carrying the rush's speed; the HUD is told its cooldown; a wall mid-rush,
 * level or rising, is a crash; anything living in the path stops it; a swap ends it; a perfect parry of its Strike staggers harder;
 * a boss is struck but never moved.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ClosingLineTest {
    private static final double Y = 120;

    private static void face(ServerPlayer p, LivingEntity target) {
        Vec3 d = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
        p.setYHeadRot(yaw);
    }

    /**
     * The client carrying the rush out: {@code ticks} ticks of moving along its line at its speed (held at {@code stopAlong} along it,
     * where the game's collision would stop them), the server checking each.
     */
    private static void rush(MinecraftServer server, ServerPlayer p, double stopAlong, int ticks) {
        SeveredState state = Severed.state(p);
        Vec3 from = state.rushFrom, dir = state.rushDirection;
        double along = 0;
        for (int i = 0; i < ticks && ClosingLine.rushing(state); i++) {
            along = Math.min(stopAlong, along + state.rushSpeed);
            Vec3 at = from.add(dir.scale(along));
            p.setPos(at.x, at.y, at.z);
            Blur.tick(p, state);
            TestServer.advanceGameTime(server, 1);
        }
    }

    private static ServerPlayer blurring(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer s = SeveredBodyTest.severed(server, level, name);
        s.moveTo(0.5, Y, 0.5, -90f, 0f);
        Severed.toggleBlur(s);
        Blur.tick(s, Severed.state(s)); // where they are
        TestServer.advanceGameTime(server, 1);
        return s;
    }

    @Test
    void itRushesArrivesAndEarnsAStrikeCarryingItsSpeed(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            try {
                ServerPlayer s = blurring(server, level, "cl_rush");
                ServerPlayer target = SeveredBodyTest.maliceUser(server, level, "cl_rush_target");
                target.moveTo(12.5, Y, 0.5);
                face(s, target);
                CastManager.handleIntent(s, CastIntent.SPECIAL_PRESS); // R, while Blurring
                SeveredState state = Severed.state(s);
                assertTrue(ClosingLine.rushing(state), "rushing");
                double expected = 12.0 - ClosingLine.reachGap(s, target);
                assertEquals(expected, state.rushDistance, 1.0e-6, "to melee reach in front of where the target stood");
                assertEquals(0.0, state.rushDirection.y, 1.0e-9, "level ground, a level line");
                assertTrue(state.closingReadyAt > level.getGameTime(), "a cooldown");
                assertNotNull(state.sent);
                assertEquals(state.closingReadyAt, state.sent.closingReadyAt(), "the HUD's cue is told when it's ready");
                assertEquals(SeveredConfig.ticks(SeveredConfig.CLOSING_COOLDOWN.get()), state.sent.closingCooldown(), "and how long that is");
                // An admin's cooldown reset (/kegare cooldown reset) makes it ready, and the HUD is told at once.
                long readyAt = state.closingReadyAt;
                Severed.clearClosingLineCooldown(s);
                assertEquals(0, state.closingReadyAt, "ready");
                assertEquals(0, state.sent.closingReadyAt(), "the cue told");
                state.closingReadyAt = readyAt; // back as it was, for the rest of this test
                Blur.sync(s, state);

                rush(server, s, expected, 10);
                assertFalse(ClosingLine.rushing(state), "arrived");
                assertTrue(ReinforcedStrike.fromRush(s), "a Strike, from the rush");
                assertTrue(state.recoveryUntil > level.getGameTime(), "and the recovery with no Blur speed");
                assertEquals(SeveredConfig.ticks(SeveredConfig.CLOSING_STRIKE_WINDOW.get()), ReinforcedStrike.ticksLeft(s), 1, "for its window");

                // Its hit knocks harder than a plain Severed Strike: it carries the rush's speed.
                target.setDeltaMovement(Vec3.ZERO);
                target.hurt(s.damageSources().playerAttack(s), 1f);
                double rushed = target.getDeltaMovement().horizontalDistance();
                // A plain Severed Strike's push halves under the hit's own 0.4: what this one beats.
                double plain = MeleeConfig.SEVERED_STRIKE_KNOCKBACK.get() / 2 + 0.4;
                assertTrue(rushed > plain + 0.05, "knocked back harder than a plain Severed Strike: " + rushed + " against " + plain);

                // The window runs out.
                Severed.toggleBlur(s);
                Severed.toggleBlur(s);
                TestServer.advanceGameTime(server, SeveredConfig.ticks(SeveredConfig.CLOSING_COOLDOWN.get()) + 2);
                state.recoveryUntil = 0;
                s.moveTo(0.5, Y, 0.5);
                target.moveTo(12.5, Y, 0.5);
                face(s, target);
                Blur.tick(s, state);
                TestServer.advanceGameTime(server, 1);
                ClosingLine.request(s);
                rush(server, s, expected, 10);
                assertTrue(ReinforcedStrike.fromRush(s));
                TestServer.advanceGameTime(server, SeveredConfig.ticks(SeveredConfig.CLOSING_STRIKE_WINDOW.get()));
                assertFalse(ReinforcedStrike.isReady(s), "gone once its window is over");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void wallsCrashItLivingThingsStopItASwapEndsIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Pig pig = EntityType.PIG.create(level);
            BlockPos wall = new BlockPos(7, 120, 0);
            try {
                ServerPlayer target = SeveredBodyTest.maliceUser(server, level, "cl_wall_target");
                target.moveTo(13.5, Y + 4, 0.5); // seen over the wall, and the line rises toward them
                ServerPlayer s = blurring(server, level, "cl_wall");
                face(s, target);
                for (int i = 0; i < 3; i++) level.setBlock(wall.above(i), Blocks.STONE.defaultBlockState(), 3);
                ClosingLine.request(s);
                SeveredState state = Severed.state(s);
                assertTrue(ClosingLine.rushing(state), "it doesn't stop for walls: the wall stops it");
                assertTrue(state.rushDirection.y > 0.2, "rising: " + state.rushDirection);
                // Their feet meet the wall well under its top (more than a step), so the game's collision holds them at it.
                double atWall = (7.0 - 0.3 - 0.5) / state.rushDirection.x;
                assertTrue(state.rushFrom.y + state.rushDirection.y * atWall + s.maxUpStep() < Y + 3, "the wall is in the line's way");
                rush(server, s, atWall, 1); // the first tick, short of it
                // The next, the game's collision holds them at its face while the rise carries on up it (it moves them up before
                // across), to within a step of its top: still a wall, not a lip.
                s.setPos(7.0 - 0.3, Y + 3 - 0.4, 0.5);
                Blur.tick(s, state);
                TestServer.advanceGameTime(server, 1);
                assertFalse(ClosingLine.rushing(state), "stopped");
                assertTrue(CombatHelper.isStunned(s), "a wall crash: stunned");
                assertTrue(s.getHealth() < s.getMaxHealth(), "and hurt");
                assertFalse(ReinforcedStrike.isReady(s), "no Strike");
                for (int i = 0; i < 3; i++) level.setBlock(wall.above(i), Blocks.AIR.defaultBlockState(), 3);
                s.moveTo(0.5, Y, -30.5); // out of everyone's sight

                // Something living in the path: it stops short of it.
                ServerPlayer s2 = blurring(server, level, "cl_path");
                target.moveTo(14.5, Y, 0.5);
                pig.moveTo(6.5, Y, 0.5, 0f, 0f);
                pig.setNoGravity(true);
                level.addFreshEntity(pig);
                s2.moveTo(0.5, Y, 0.5);
                face(s2, target);
                Vec3 look = s2.getViewVector(1f);
                // Look over the pig at the target.
                s2.setXRot(s2.getXRot() - 3f);
                ClosingLine.request(s2);
                SeveredState state2 = Severed.state(s2);
                assertTrue(ClosingLine.rushing(state2), "rushing at " + look);
                assertEquals(6.0 - ClosingLine.reachGap(s2, pig), state2.rushDistance, 0.05, "stops in reach of what's in the way");
                assertEquals(target.getId(), state2.rushTarget, "rushing at the target, over the pig");
                pig.discard();

                // A swap mid-rush: moved off the line by something else, it's over.
                s2.moveTo(0.5, Y, 30.5);
                ServerPlayer s3 = blurring(server, level, "cl_swap");
                target.moveTo(14.5, Y, 0.5);
                face(s3, target);
                ClosingLine.request(s3);
                SeveredState state3 = Severed.state(s3);
                assertTrue(ClosingLine.rushing(state3));
                s3.setPos(0.5, Y, 8.5);
                Blur.tick(s3, state3);
                assertFalse(ClosingLine.rushing(state3), "swapped away: ended");
                assertFalse(ReinforcedStrike.isReady(s3), "no Strike");
            } finally {
                pig.discard();
                for (int i = 0; i < 3; i++) level.setBlock(wall.above(i), Blocks.AIR.defaultBlockState(), 3);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itLeapsUpToATargetOnAPillarWhenTheLineIsClear(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            BlockPos base = new BlockPos(10, 120, 0);
            try {
                for (int i = 0; i < 5; i++) level.setBlock(base.above(i), Blocks.STONE.defaultBlockState(), 3);
                ServerPlayer target = SeveredBodyTest.maliceUser(server, level, "cl_pillar_target");
                target.moveTo(10.5, Y + 5, 0.5); // on top of the pillar
                ServerPlayer s = blurring(server, level, "cl_pillar");
                face(s, target);
                ClosingLine.request(s);
                SeveredState state = Severed.state(s);
                assertTrue(ClosingLine.rushing(state), "rushing at someone up on a pillar");
                Vec3 line = target.position().subtract(s.position());
                assertEquals(line.length() - ClosingLine.reachGap(s, target), state.rushDistance, 1.0e-6, "in a straight line to melee reach of them");
                assertEquals(line.normalize().y, state.rushDirection.y, 1.0e-9, "up, the way to where they stand");
                // Nothing in the line's way: the pillar is beside its end, not across it.
                for (double t = 0; t <= state.rushDistance; t += 0.25) {
                    assertTrue(level.noCollision(s, s.getBoundingBox().move(state.rushDirection.scale(t))), "the line is clear at " + t);
                }

                rush(server, s, state.rushDistance, 10);
                assertFalse(ClosingLine.rushing(state), "arrived");
                assertFalse(CombatHelper.isStunned(s), "no crash");
                assertTrue(ReinforcedStrike.fromRush(s), "a Strike, from the rush");
                assertTrue(s.getY() > Y + 3, "up there with them, in the air (gravity has them from here)");
                assertTrue(s.getEyePosition().distanceTo(target.getBoundingBox().getCenter()) < 3.0, "within melee reach");
                // The rush's own end, its skid carrying them up against the pillar: not a wall crash.
                s.setPos(10.0 - 0.3, s.getY() - 0.1, 0.5);
                Blur.tick(s, state);
                TestServer.advanceGameTime(server, 1);
                assertFalse(CombatHelper.isStunned(s), "its own end at the pillar's side isn't a crash");
                assertTrue(state.blur, "still Blurring");
            } finally {
                for (int i = 0; i < 5; i++) level.setBlock(base.above(i), Blocks.AIR.defaultBlockState(), 3);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aParryCatchingItStaggersHarderAndBossesAreNeverMoved(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            server.setPvpAllowed(true);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer s = SeveredBodyTest.severed(server, level, "cl_parried");
                ServerPlayer defender = SeveredBodyTest.maliceUser(server, level, "cl_defender");
                s.moveTo(0.5, Y, 0.5);
                defender.moveTo(2.0, Y, 0.5);
                ReinforcedStrike.earnFromRush(s, 10, 0.4);
                MeleeIntentPayload.apply(defender, new MeleeIntentPayload(MeleeIntentPayload.Action.PARRY_PRESS, (byte) 0, (byte) 0));
                assertFalse(defender.hurt(s.damageSources().playerAttack(s), 4f), "parried");
                MobEffectInstance slow = s.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                assertNotNull(slow, "staggered");
                assertEquals(SeveredConfig.ticks(SeveredConfig.PARRY_STAGGER_SECONDS.get()), slow.getDuration(), "longer than a normal stagger");
                assertTrue(slow.getDuration() > Math.round(MeleeConfig.PARRY_STAGGER_SECONDS.get() * 20));
                assertEquals(SeveredConfig.PARRY_STAGGER_SLOWNESS.get().intValue(), slow.getAmplifier(), "and heavier");
                assertFalse(ReinforcedStrike.isReady(s), "the Strike was spent on it");

                // A boss: struck, never moved.
                s.removeAllEffects();
                wither.moveTo(3.5, Y, 4.5, 0f, 0f);
                wither.setNoGravity(true);
                level.addFreshEntity(wither);
                ReinforcedStrike.earnFromRush(s, 10, 0.4);
                float health = wither.getHealth();
                wither.setDeltaMovement(Vec3.ZERO);
                wither.hurt(s.damageSources().playerAttack(s), 4f);
                assertTrue(wither.getHealth() < health, "damaged");
                // Only the hit's own vanilla push (0.4): the Strike's knockback, its carried speed included, never reaches a boss.
                assertTrue(wither.getDeltaMovement().horizontalDistance() <= 0.4 + 1.0e-6, "never moved by the Strike");
            } finally {
                wither.discard();
                TestServer.removePlayers(server);
            }
        });
    }
}
