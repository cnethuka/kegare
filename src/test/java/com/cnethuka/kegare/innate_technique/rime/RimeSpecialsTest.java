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

            // Shattered Pressure: three players caught by one Shatter Shell (two aren't enough). NeoForge's fake
            // players ignore advancements, so this caster keeps real ones.
            ServerPlayer shatterCaster = withRealAdvancements(server, level, "rime_shatter_caster");
            BirthService.rebirth(shatterCaster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(shatterCaster, Rime.RIME.get());
            InnateTechniqueService.select(shatterCaster, 3);
            shatterCaster.moveTo(0.5, ground, 0.5, 0f, 0f);
            MaliceService.refill(shatterCaster);
            ServerPlayer a = TestServer.player(server, "rime_target_a");
            ServerPlayer b = TestServer.player(server, "rime_target_b");
            ServerPlayer c = TestServer.player(server, "rime_target_c");
            // Fake players aren't in the level until added, and area queries only see entities that are.
            for (ServerPlayer t : List.of(a, b, c)) level.addNewPlayer(t);
            a.moveTo(shatterCaster.getX() + 2, shatterCaster.getY(), shatterCaster.getZ(), 0f, 0f);
            b.moveTo(shatterCaster.getX() - 2, shatterCaster.getY(), shatterCaster.getZ(), 0f, 0f);
            c.moveTo(shatterCaster.getX(), shatterCaster.getY(), shatterCaster.getZ() + 40, 0f, 0f); // out of range for the first burst
            CastManager.handleIntent(shatterCaster, CastIntent.SPECIAL_PRESS);
            assertEquals(RimeConfig.SHATTER_FREEZE_STACKS.get(), FreezeService.stacks(a), "setup: the burst reached the players");
            assertEquals(0, FreezeService.stacks(c), "setup: the far one was out of range");
            assertFalse(hasAdvancement(server, shatterCaster, RimeAdvancements.SHATTERED_PRESSURE), "two players: not yet");
            InnateTechniqueService.clearCooldowns(shatterCaster);
            MaliceService.refill(shatterCaster);
            for (ServerPlayer t : List.of(a, b, c)) FreezeService.clear(t);
            a.moveTo(shatterCaster.getX() + 2, shatterCaster.getY(), shatterCaster.getZ(), 0f, 0f);
            b.moveTo(shatterCaster.getX() - 2, shatterCaster.getY(), shatterCaster.getZ(), 0f, 0f);
            c.moveTo(shatterCaster.getX(), shatterCaster.getY(), shatterCaster.getZ() + 2, 0f, 0f);
            CastManager.handleIntent(shatterCaster, CastIntent.SPECIAL_PRESS);
            for (ServerPlayer t : List.of(a, b, c)) {
                assertEquals(RimeConfig.SHATTER_FREEZE_STACKS.get(), FreezeService.stacks(t), "setup: the second burst reached " + t.getGameProfile().getName());
            }
            assertTrue(Rime.holds(shatterCaster), "setup: the caster holds Rime");
            assertTrue(hasAdvancement(server, shatterCaster, RimeAdvancements.SHATTERED_PRESSURE), "three players in one burst");
            for (ServerPlayer t : List.of(a, b, c)) level.removePlayerImmediately(t, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

            cow.discard();
            CastManager.stopAll(p, com.cnethuka.kegare.innatetechnique.EndReason.CANCELLED);
        });
    }

    private static ServerPlayer withRealAdvancements(MinecraftServer server, ServerLevel level, String name) {
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(("kegare-test:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(uuid, name)) {
            @Override
            public boolean isFakePlayer() {
                return false; // NeoForge gives fake players throwaway advancements; this one keeps real ones
            }
        };
    }

    private static boolean hasAdvancement(MinecraftServer server, ServerPlayer p, net.minecraft.resources.ResourceLocation id) {
        var holder = server.getAdvancements().get(id);
        assertNotNull(holder, "advancement " + id + " is loaded");
        return p.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
