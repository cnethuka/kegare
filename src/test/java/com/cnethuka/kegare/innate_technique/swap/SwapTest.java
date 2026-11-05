package com.cnethuka.kegare.innate_technique.swap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Swap: targeting, Positions, the SwapTargetEvent gate, stunned targets, Switchback's return and its cancelling. */
@ExtendWith(EphemeralTestServerProvider.class)
class SwapTest {
    private static final double EPS = 1.0e-4;
    private static final double Y = 120; // in the open air above the flat world: nothing blocks line of sight

    @Test
    void positionsAndSwitchback(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0); // loads the chunk: area queries only see entities in loaded chunks
            ServerPlayer caster = TestServer.player(server, "swap_caster");
            ServerPlayer target = TestServer.player(server, "swap_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            BirthService.rebirth(caster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(caster, Swap.SWAP.get());
            InnateTechniqueService.select(caster, 0);

            // Positions: places and facing trade, both fall distances reset; cost paid, cooldown started.
            face(caster, target);
            caster.fallDistance = 6f;
            target.fallDistance = 9f;
            Vec3 casterAt = caster.position();
            Vec3 targetAt = target.position();
            float casterYaw = caster.getYRot();
            float targetYaw = target.getYRot();
            double before = malice(caster);
            assertTrue(Swap.holds(caster), "setup: holds Swap");
            assertEquals(Swap.POSITIONS, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(caster, 0), "setup: Positions in slot 1");
            assertTrue(level.getEntities(caster, caster.getBoundingBox().inflate(20)).contains(target), "setup: the target is in the level's entity lists");
            assertTrue(SwapTargeting.lookedAt(caster).isPresent(), "setup: the target is in sight");
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before - SwapConfig.POSITIONS_COST.get(), malice(caster), EPS, "cast went off");
            assertEquals(targetAt, caster.position(), "caster moved to the target");
            assertEquals(casterAt, target.position(), "target moved to the caster");
            assertEquals(targetYaw, caster.getYRot(), EPS, "facing traded");
            assertEquals(casterYaw, target.getYRot(), EPS, "facing traded");
            assertEquals(0f, caster.fallDistance, EPS);
            assertEquals(0f, target.fallDistance, EPS);
            assertEquals(before - SwapConfig.POSITIONS_COST.get(), malice(caster), EPS, "cost");
            assertTrue(InnateTechniqueService.cooldownRemaining(caster, Swap.POSITIONS) > 0, "cooldown");

            // Nobody in sight: fizzle, nothing paid, no cooldown.
            InnateTechniqueService.clearCooldowns(caster);
            caster.moveTo(caster.getX(), caster.getY(), caster.getZ(), caster.getYRot() + 90f, 0f);
            caster.setYHeadRot(caster.getYRot());
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before, malice(caster), EPS, "no target: free");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.POSITIONS), "no target: no cooldown");

            // SwapTargetEvent cancelled: fizzle with the Malice spent and no cooldown; nobody moves.
            face(caster, target);
            SwapDebug.setUnswappable(target.getUUID(), true);
            casterAt = caster.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(casterAt, caster.position(), "refused: nobody moved");
            assertEquals(before - SwapConfig.POSITIONS_COST.get(), malice(caster), EPS, "refused: Malice spent");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.POSITIONS), "refused: no cooldown");
            SwapDebug.setUnswappable(target.getUUID(), false);

            // A stunned target can still be swapped...
            CombatHelper.stun(target, 100);
            casterAt = caster.position();
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(casterAt, target.position(), "stunned target swapped");
            CombatHelper.clearStun(target);
            // ...but a stunned caster can't cast.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            CombatHelper.stun(caster, 100);
            casterAt = caster.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(casterAt, caster.position(), "stunned caster: nothing");
            assertEquals(before, malice(caster), EPS);
            CombatHelper.clearStun(caster);

            // Switchback: the swap, then the swap back once the delay is up (shared cooldown with Positions).
            face(caster, target);
            casterAt = caster.position();
            targetAt = target.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(targetAt, caster.position(), "switchback: swapped");
            assertEquals(before - SwapConfig.SWITCHBACK_COST.get(), malice(caster), EPS, "switchback cost");
            assertTrue(InnateTechniqueService.cooldownRemaining(caster, Swap.POSITIONS) > 0, "shared cooldown");
            assertTrue(Switchback.isPending(caster));
            TestServer.advanceGameTime(server, SwapConfig.ticks(SwapConfig.SWITCHBACK_DELAY) - 2);
            Switchback.tick(server);
            assertEquals(targetAt, caster.position(), "not yet");
            TestServer.advanceGameTime(server, 2);
            Switchback.tick(server);
            assertEquals(casterAt, caster.position(), "swapped back");
            assertEquals(targetAt, target.position(), "swapped back");
            assertFalse(Switchback.isPending(caster));

            // Called off when the target dies (or logs out, or changes dimension).
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            casterAt = caster.position();
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            Vec3 afterSwap = caster.position();
            Switchback.cancelInvolving(target);
            TestServer.advanceGameTime(server, SwapConfig.ticks(SwapConfig.SWITCHBACK_DELAY) + 1);
            Switchback.tick(server);
            assertEquals(afterSwap, caster.position(), "called off: no swap back");
            assertFalse(Switchback.isPending(caster));

            for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
        });
    }

    /** Puts the two players 8 blocks apart in the air, the caster looking straight at the target. */
    static void face(ServerPlayer caster, ServerPlayer target) {
        caster.moveTo(0.5, Y, 0.5, -90f, 0f); // facing +X
        target.moveTo(8.5, Y, 0.5, 90f, 0f);  // facing -X
        caster.setYHeadRot(-90f); // a living entity looks where its head points
        target.setYHeadRot(90f);
        MaliceService.refill(caster);
    }

    static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
