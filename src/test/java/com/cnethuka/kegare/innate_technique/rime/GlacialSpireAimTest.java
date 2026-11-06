package com.cnethuka.kegare.innate_technique.rime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Where Glacial Spire lands with the crosshair on a living thing. Everything stays in chunk (0,0) so it stays loaded. */
@ExtendWith(EphemeralTestServerProvider.class)
class GlacialSpireAimTest {
    private static final double EPS = 1.0e-6;

    private static Villager villager(ServerLevel level, double x, double y, double z) {
        Villager v = new Villager(EntityType.VILLAGER, level);
        v.setNoAi(true);
        v.setNoGravity(true);
        v.moveTo(x, y, z, 0f, 0f);
        level.addFreshEntity(v);
        return v;
    }

    private static ServerPlayer caster(MinecraftServer server, String name, double x, double y, double z) {
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        p.moveTo(x, y, z, -90f, 0f);
        p.setOnGround(true);
        MaliceService.refill(p);
        return p;
    }

    @Test
    void theHeadOfAVillagerOnFlatGroundIsAGoodAim(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 7, 8);
            ServerPlayer p = caster(server, "spire_head", 1.5, ground, 8.5);
            Villager target = villager(level, 7.5, ground, 8.5);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

            Optional<Vec3> at = GlacialSpireAbility.target(p);
            assertTrue(at.isPresent(), "ground is right under them");
            assertEquals(7.5, at.get().x, EPS, "under the target");
            assertEquals(ground, at.get().y, EPS, "on top of the ground");
            assertEquals(8.5, at.get().z, EPS, "under the target");

            // The real cast: released on that aim, it lands there and then stays there when the target walks off.
            InnateTechniqueService.select(p, 1);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            TestServer.advanceGameTime(server, Rime.GLACIAL_SPIRE.maxChargeTicks(p));
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            List<GlacialSpireEntity> spires = level.getEntitiesOfClass(GlacialSpireEntity.class, new AABB(p.blockPosition()).inflate(14));
            assertEquals(1, spires.size(), "the warning circle is down");
            GlacialSpireEntity spire = spires.get(0);
            assertEquals(7.5, spire.getX(), EPS);
            target.moveTo(12.5, ground, 12.5, 0f, 0f);
            spire.tick();
            assertEquals(7.5, spire.getX(), EPS, "it doesn't follow them");
            assertEquals(8.5, spire.getZ(), EPS, "it doesn't follow them");
            spire.discard();
            target.discard();
        });
    }

    @Test
    void theSkyStillHasNoGround(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 7, 8);
            ServerPlayer p = caster(server, "spire_sky", 1.5, ground, 8.5);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(1, 0.2, 0));
            assertFalse(GlacialSpireAbility.target(p).isPresent(), "nothing there");
        });
    }

    @Test
    void groundTooFarUnderATargetIsStillNoGround(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 7, 8);
            double high = ground + RimeConfig.SPIRE_RANGE.get() + 5;
            ServerPlayer p = caster(server, "spire_high", 1.5, high, 8.5);
            Villager target = villager(level, 7.5, high, 8.5);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            assertFalse(GlacialSpireAbility.target(p).isPresent(), "the ground is out of range");
            target.discard();
        });
    }

    @Test
    void onlyWhatTheCrosshairCanReachCounts(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 7, 8);
            ServerPlayer p = caster(server, "spire_hidden", 1.5, ground, 8.5);
            Villager target = villager(level, 7.5, ground, 8.5);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

            // Invisible: aiming at them mustn't give them away, so it's as if they weren't there.
            target.setInvisible(true);
            assertFalse(GlacialSpireAbility.target(p).isPresent(), "an invisible target isn't a target");
            target.setInvisible(false);

            // Behind a wall: the wall is what's aimed at, so the floor in front of it.
            BlockPos wall = BlockPos.containing(4.5, target.getEyeY(), 8.5);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            Optional<Vec3> at = GlacialSpireAbility.target(p);
            assertTrue(at.isPresent());
            assertTrue(at.get().x < 4.5, "in front of the wall, not under the target");
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            target.discard();
        });
    }
}
