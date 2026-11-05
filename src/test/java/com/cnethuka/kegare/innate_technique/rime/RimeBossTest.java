package com.cnethuka.kegare.innate_technique.rime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * DESIGN §4's boss rule for Rime: bosses take Rime damage, but never get Freeze stacks or a freeze, are never launched
 * by Glacial Spire and never knocked back by Shatter Shell. A cow next to them shows the same casts do work on mobs.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RimeBossTest {
    private static final double EPS = 1.0e-6;

    @Test
    void bossesAreDamagedButNeverMovedOrDisabled(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer p = TestServer.player(server, "rime_boss_caster");
            BirthService.rebirth(p, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            p.moveTo(0.5, ground, 0.5, 0f, 0f);
            WitherBoss wither = EntityType.WITHER.create(level);
            Cow cow = new Cow(EntityType.COW, level);
            try {
                assertTrue(CombatHelper.isBoss(wither), "setup: the wither is a boss (c:bosses)");
                assertTrue(CombatHelper.isBoss(EntityType.ENDER_DRAGON.create(level)), "the ender dragon too");
                assertFalse(CombatHelper.isBoss(cow), "a cow is not");
                wither.setNoAi(true);
                wither.moveTo(3.5, ground, 0.5, 0f, 0f);
                level.addFreshEntity(wither);
                cow.moveTo(0.5, ground, 3.5, 0f, 0f);
                level.addFreshEntity(cow);

                // Rime damage lands on a boss...
                float health = wither.getHealth();
                assertTrue(wither.hurt(RimeDamage.source(level, p, p), 5f), "Rime damage hurts a boss");
                assertTrue(wither.getHealth() < health, "it lost health");
                // ...but Freeze stacks never stick and it never freezes solid.
                FreezeService.addStacks(wither, RimeConfig.FREEZE_MAX_STACKS.get(), p);
                assertEquals(0, FreezeService.stacks(wither), "no Freeze stacks on a boss");
                assertFalse(FreezeService.isFrozen(wither), "never frozen");
                assertFalse(CombatHelper.isStunned(wither), "never stunned");

                // Glacial Spire: the boss takes the damage, the cow on the same spike is launched, the boss is not.
                GlacialSpireEntity spire = new GlacialSpireEntity(level, p, new Vec3(3.5, ground, 0.5), 1f);
                level.addFreshEntity(spire);
                cow.moveTo(3.8, ground, 0.5, 0f, 0f);
                wither.setDeltaMovement(Vec3.ZERO);
                cow.setDeltaMovement(Vec3.ZERO);
                wither.invulnerableTime = 0;
                health = wither.getHealth();
                spire.tickCount = RimeConfig.ticks(RimeConfig.SPIRE_DELAY);
                spire.tick();
                assertTrue(wither.getHealth() < health, "the spike damages the boss");
                assertEquals(0.0, wither.getDeltaMovement().y, EPS, "the boss is not launched");
                assertEquals(0, FreezeService.stacks(wither), "and gets no stacks");
                assertEquals(RimeConfig.SPIRE_MAX_LAUNCH.get(), cow.getDeltaMovement().y, 1e-3, "the cow next to it is launched");
                spire.discard();
                FreezeService.clear(cow);

                // Shatter Shell: the cow is knocked back and frozen a little; the boss doesn't move and gets nothing.
                InnateTechniqueService.select(p, 3);
                MaliceService.refill(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(Rime.RIME_SHELL.isActive(p), "setup: shell on");
                wither.moveTo(2.5, ground, 0.5, 0f, 0f);
                cow.moveTo(0.5, ground, 2.5, 0f, 0f);
                wither.setDeltaMovement(Vec3.ZERO);
                cow.setDeltaMovement(Vec3.ZERO);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertEquals(Vec3.ZERO, wither.getDeltaMovement(), "the boss is not knocked back");
                assertEquals(0, FreezeService.stacks(wither), "and gets no stacks");
                assertTrue(cow.getDeltaMovement().lengthSqr() > 0.01, "the cow is knocked back");
                assertEquals(RimeConfig.SHATTER_FREEZE_STACKS.get(), FreezeService.stacks(cow), "and gets its stacks");
            } finally {
                wither.discard();
                cow.discard();
                CastManager.stopAll(p, EndReason.CANCELLED);
            }
        });
    }
}
