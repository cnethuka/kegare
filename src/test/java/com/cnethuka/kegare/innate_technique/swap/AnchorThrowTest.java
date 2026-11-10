package com.cnethuka.kegare.innate_technique.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Anchor's token leaves the throwing hand at the same speed and lands where a throw from the eyes would (DESIGN §3.19). */
@ExtendWith(EphemeralTestServerProvider.class)
class AnchorThrowTest {
    private static final int Y = 120, Z = 200; // a floor and a wall in the air, away from the other tests
    private static final int WALL_X = 12;

    @Test
    void theTokenLeavesTheHandAndLandsWhereItWouldHave(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getChunk(0, Z >> 4);
            level.getChunk(WALL_X >> 4, Z >> 4);
            ServerPlayer caster = TestServer.player(server, "anchor_thrower");
            level.addNewPlayer(caster);
            build(level, Blocks.STONE.defaultBlockState());
            try {
                // Level at the wall, up a little, down at it, down at the floor close by and steeply at the floor at your feet.
                for (float pitch : new float[] {0f, -10f, 20f, 45f, 60f}) {
                    stand(caster, pitch);
                    AnchorEntity old = thrown(caster), now = thrown(caster);
                    Vec3 velocity = old.getDeltaMovement();
                    AnchorThrow.fromHand(caster, now, 400);

                    Vec3 middle = now.position().add(0, now.getBbHeight() / 2.0, 0).subtract(caster.getEyePosition());
                    assertEquals(AnchorThrow.HAND_RIGHT, middle.z, 1.0e-6, pitch + "°: out at the right (facing +X, right is +Z)");
                    assertEquals(AnchorThrow.HAND_UP, middle.y, 1.0e-6, pitch + "°: below the eyes");
                    assertEquals(AnchorThrow.HAND_FORWARD, middle.x, 1.0e-6, pitch + "°: in front");
                    assertEquals(velocity.length(), now.getDeltaMovement().length(), 1.0e-5, pitch + "°: the same speed");

                    Vec3 was = land(old), is = land(now);
                    assertTrue(was.distanceTo(is) <= AnchorThrow.SAME_SPOT, pitch + "°: lands where it would have: " + was + " vs " + is);
                }

                // The hand inside a block: it falls back to a throw from the eyes.
                stand(caster, 0f);
                BlockPos inHand = BlockPos.containing(AnchorThrow.hand(caster));
                level.setBlockAndUpdate(inHand, Blocks.STONE.defaultBlockState());
                AnchorEntity blocked = thrown(caster);
                Vec3 start = blocked.position(), velocity = blocked.getDeltaMovement();
                AnchorThrow.fromHand(caster, blocked, 400);
                assertEquals(start, blocked.position(), "the hand in a block: from the eyes");
                assertEquals(velocity, blocked.getDeltaMovement(), "the hand in a block: the old aim");
                level.setBlockAndUpdate(inHand, Blocks.AIR.defaultBlockState());
            } finally {
                build(level, Blocks.AIR.defaultBlockState());
                caster.discard();
            }
        });
    }

    /** A floor under the thrower and a wall ahead (+X). */
    private static void build(ServerLevel level, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(-2, Y - 1, Z - 6, WALL_X - 1, Y - 1, Z + 6)) level.setBlockAndUpdate(p, state);
        for (BlockPos p : BlockPos.betweenClosed(WALL_X, Y - 1, Z - 6, WALL_X, Y + 6, Z + 6)) level.setBlockAndUpdate(p, state);
    }

    private static void stand(ServerPlayer caster, float pitch) {
        caster.moveTo(0.5, Y, Z + 0.5, -90f, pitch); // facing +X
        caster.setYHeadRot(-90f);
        caster.setYBodyRot(-90f);
    }

    /** A token shot from the eyes the usual way, without the random spread, so two throws are the same throw. */
    private static AnchorEntity thrown(ServerPlayer caster) {
        AnchorEntity token = new AnchorEntity(caster, caster.level(), 400);
        token.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0f, 1.5f, 0f);
        return token;
    }

    private static Vec3 land(AnchorEntity token) {
        for (int i = 0; i < 200 && !token.isStuck(); i++) token.tick();
        assertTrue(token.isStuck(), "it lands");
        return token.position();
    }
}
