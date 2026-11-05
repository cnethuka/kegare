package com.cnethuka.kegare.innate_technique.rime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Rime's specials on the real cast pipeline (throwaway server, flat world). */
@ExtendWith(EphemeralTestServerProvider.class)
class RimeSpecialsTest {
    private static final double EPS = 1.0e-6;

    @Test
    void specials(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);

            ServerPlayer p = TestServer.player(server, "rime_specials");
            BirthService.rebirth(p, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            p.moveTo(0.5, ground, 0.5, 0f, 0f);
            p.setOnGround(true);
            MaliceService.refill(p);

            // ---- Splinter: three real shard entities, sharing Frost Shard's cooldown.
            InnateTechniqueService.select(p, 0);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            List<SplinterShardEntity> shards = level.getEntitiesOfClass(SplinterShardEntity.class, p.getBoundingBox().inflate(8));
            assertEquals(SplinterAbility.SHARDS, shards.size(), "three shards");
            assertEquals(RimeConfig.ticks(RimeConfig.SPLINTER_COOLDOWN), InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD),
                    "Splinter starts the shared cooldown with its own length");
            shards.forEach(e -> e.discard());

            // ---- Glacial Spire: looking at the sky, the release fizzles for free.
            MaliceService.refill(p);
            InnateTechniqueService.select(p, 1);
            p.setXRot(-90f); // straight up
            double before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertNotNull(CastManager.state(p).casting(), "charging");
            TestServer.advanceGameTime(server, Rime.GLACIAL_SPIRE.maxChargeTicks(p));
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertEquals(before, malice(p), EPS, "no ground in range: nothing paid");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Rime.GLACIAL_LANCE), "and no cooldown");

            // Aimed down at the ground: a spire marker appears there, then erupts and launches what stands on it.
            p.setXRot(90f);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            TestServer.advanceGameTime(server, Rime.GLACIAL_SPIRE.maxChargeTicks(p));
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertEquals(before - Rime.GLACIAL_SPIRE.cost(p), malice(p), 1e-3, "paid on release");
            List<GlacialSpireEntity> spires = level.getEntitiesOfClass(GlacialSpireEntity.class, new AABB(p.blockPosition()).inflate(4));
            assertEquals(1, spires.size(), "the spire marker");
            GlacialSpireEntity spire = spires.get(0);
            Cow cow = new Cow(EntityType.COW, level);
            cow.moveTo(spire.getX() + 0.3, spire.getY(), spire.getZ(), 0f, 0f);
            level.addFreshEntity(cow);
            spire.tickCount = RimeConfig.ticks(RimeConfig.SPIRE_DELAY);
            spire.tick();
            assertEquals(RimeConfig.SPIRE_FREEZE_STACKS.get(), FreezeService.stacks(cow), "erupted: freeze stacks");
            assertEquals(RimeConfig.SPIRE_MAX_LAUNCH.get(), cow.getDeltaMovement().y, 1e-3, "launched (full charge)");
            spire.tickCount += RimeConfig.ticks(RimeConfig.SPIRE_STANDS_SECONDS);
            spire.tick();
            assertTrue(spire.isRemoved(), "the spike shatters and is gone: no blocks left behind");
            FreezeService.clear(cow);

            // ---- Ice Path: lays a trail under the slider that slows anyone else on it, then melts.
            InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            InnateTechniqueService.select(p, 2);
            p.setXRot(0f);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertNotNull(CastManager.state(p).casting(), "sliding");
            CastManager.tick(p);
            assertTrue(IcePaths.points(level, p.getUUID()) > 0, "ice laid under the feet");
            cow.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
            while (level.getGameTime() % 4 != 0) TestServer.advanceGameTime(server, 1);
            IcePaths.tick(level);
            assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "slowed on the trail");
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.FROST_FIELD) > 0, "shared cooldown with Frost Field");
            TestServer.advanceGameTime(server, RimeConfig.ticks(RimeConfig.PATH_TRAIL_SECONDS) + 1);
            IcePaths.tick(level);
            assertEquals(0, IcePaths.points(level, p.getUUID()), "the trail melted");

            // ---- Shatter Shell, shell on: cheap, uses the shell up, freezes and knocks back.
            InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            InnateTechniqueService.select(p, 3);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(Rime.RIME_SHELL.isActive(p), "setup: shell on");
            cow.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0f, 0f);
            cow.setDeltaMovement(0, 0, 0);
            double withShell = Rime.SHATTER_SHELL.cost(p);
            assertEquals(RimeConfig.SHATTER_COST.get() * RimeEnvironment.evaluate(p).multiplier(), withShell, 1e-6);
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before - withShell, malice(p), 1e-3, "paid the with-shell cost");
            assertFalse(Rime.RIME_SHELL.isActive(p), "the shell was used up");
            assertEquals(RimeConfig.SHATTER_FREEZE_STACKS.get(), FreezeService.stacks(cow), "freeze stacks");
            assertTrue(cow.getDeltaMovement().x > 0, "knocked away from the caster");
            assertEquals(RimeConfig.ticks(RimeConfig.SHATTER_COOLDOWN), InnateTechniqueService.cooldownRemaining(p, Rime.RIME_SHELL),
                    "both share one cooldown");

            // Shell off: the higher cost.
            InnateTechniqueService.clearCooldowns(p);
            MaliceService.refill(p);
            double noShell = Rime.SHATTER_SHELL.cost(p);
            assertEquals(RimeConfig.SHATTER_COST_NO_SHELL.get() * RimeEnvironment.evaluate(p).multiplier(), noShell, 1e-6);
            before = malice(p);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before - noShell, malice(p), 1e-3, "paid the without-shell cost");

            cow.discard();
            CastManager.stopAll(p, com.cnethuka.kegare.innatetechnique.EndReason.CANCELLED);
        });
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
