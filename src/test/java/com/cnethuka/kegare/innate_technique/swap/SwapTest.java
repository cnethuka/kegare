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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
            loadArena(level);
            ServerPlayer caster = TestServer.player(server, "swap_caster");
            ServerPlayer target = TestServer.player(server, "swap_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            try {
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

            } finally {
                for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void disguises(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer caster = TestServer.player(server, "disguise_caster");
            ServerPlayer target = TestServer.player(server, "disguise_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            try {
            BirthService.rebirth(caster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(caster, Swap.SWAP.get());
            InnateTechniqueService.select(caster, 1);
            assertEquals(Swap.AWARENESS, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(caster, 1), "setup: Awareness in slot 2");

            // Awareness: each looks like the other; the API sees through it.
            face(caster, target);
            double before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before - SwapConfig.AWARENESS_COST.get(), malice(caster), EPS, "cost");
            assertTrue(Disguises.isDisguised(caster));
            assertEquals(target.getUUID(), Disguises.getDisguisedAs(caster).orElseThrow(), "caster looks like the target");
            assertEquals(caster.getUUID(), Disguises.getDisguisedAs(target).orElseThrow(), "target looks like the caster");
            assertEquals(caster.getUUID(), Disguises.getRealIdentity(caster));
            assertEquals(Disguises.Kind.AWARENESS, Disguises.get(caster).orElseThrow().kind());
            Vec3 at = caster.position();
            assertEquals(at, caster.position(), "visual only: nobody moved");

            // It ends on its own after the duration.
            TestServer.advanceGameTime(server, SwapConfig.ticks(SwapConfig.AWARENESS_DURATION) - 1);
            Disguises.tick(server);
            assertTrue(Disguises.isDisguised(caster), "not yet");
            TestServer.advanceGameTime(server, 1);
            Disguises.tick(server);
            assertFalse(Disguises.isDisguised(caster), "expired");
            assertFalse(Disguises.isDisguised(target), "expired");

            // Mimic: one way only, shares Awareness' cooldown.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(target.getUUID(), Disguises.getDisguisedAs(caster).orElseThrow(), "mimic: caster looks like the target");
            assertFalse(Disguises.isDisguised(target), "mimic: the target stays as themselves");
            assertTrue(InnateTechniqueService.cooldownRemaining(caster, Swap.AWARENESS) > 0, "shared cooldown");
            // The face's owner dies (or logs out, or leaves): it ends.
            Disguises.endInvolving(target);
            assertFalse(Disguises.isDisguised(caster), "ended with the target");

            // Refused by SwapTargetEvent: no disguise, Malice spent, no cooldown.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            SwapDebug.setUnswappable(target.getUUID(), true);
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertFalse(Disguises.isDisguised(caster));
            assertEquals(before - SwapConfig.AWARENESS_COST.get(), malice(caster), EPS, "refused: Malice spent");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.AWARENESS), "refused: no cooldown");
            SwapDebug.setUnswappable(target.getUUID(), false);

            } finally {
                for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void anchor(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer caster = TestServer.player(server, "anchor_caster");
            ServerPlayer target = TestServer.player(server, "anchor_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            try {
            BirthService.rebirth(caster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(caster, Swap.SWAP.get());
            InnateTechniqueService.select(caster, 2);
            assertEquals(Swap.ANCHOR, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(caster, 2), "setup: Anchor in slot 3");
            Vec3 spot = new Vec3(3.5, Y, 6.5);

            // Send Off with no anchor out: fizzles at no cost.
            face(caster, target);
            double before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(before, malice(caster), EPS, "no anchor: free");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.ANCHOR), "no anchor: no cooldown");

            // First cast: throw (throw cost, the short throw cooldown).
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before - SwapConfig.ANCHOR_THROW_COST.get(), malice(caster), EPS, "throw went off");
            assertTrue(Anchors.has(caster), "thrown");
            assertEquals(before - SwapConfig.ANCHOR_THROW_COST.get(), malice(caster), EPS, "throw cost");
            assertEquals(SwapConfig.ticks(SwapConfig.ANCHOR_THROW_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, Swap.ANCHOR), "throw cooldown");

            // Second cast: the caster lands on the anchor, which is used up; the full cooldown starts.
            AnchorEntity anchor = Anchors.get(caster).orElseThrow();
            anchor.setPos(spot.x, spot.y, spot.z);
            InnateTechniqueService.clearCooldowns(caster);
            caster.fallDistance = 7f;
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(spot, caster.position(), "recalled onto the anchor");
            assertEquals(0f, caster.fallDistance, EPS, "fall distance reset");
            assertTrue(anchor.isRemoved(), "used up");
            assertFalse(Anchors.has(caster));
            assertEquals(before - SwapConfig.ANCHOR_RECALL_COST.get(), malice(caster), EPS, "recall cost");
            assertEquals(SwapConfig.ticks(SwapConfig.ANCHOR_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, Swap.ANCHOR), "full cooldown");

            // Send Off: the target goes to the anchor instead; the caster stays; shared cooldown.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            anchor = Anchors.get(caster).orElseThrow();
            anchor.setPos(spot.x, spot.y, spot.z);
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            Vec3 casterAt = caster.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(spot, target.position(), "the target was sent to the anchor");
            assertEquals(casterAt, caster.position(), "the caster stayed");
            assertTrue(anchor.isRemoved(), "Send Off used it up");
            assertEquals(before - SwapConfig.SEND_OFF_COST.get(), malice(caster), EPS, "send off cost");
            assertEquals(SwapConfig.ticks(SwapConfig.ANCHOR_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, Swap.ANCHOR), "shared cooldown");

            // An anchor nobody uses times out.
            InnateTechniqueService.clearCooldowns(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            anchor = Anchors.get(caster).orElseThrow();
            anchor.tickCount = SwapConfig.ticks(SwapConfig.ANCHOR_LIFETIME);
            anchor.tick();
            assertTrue(anchor.isRemoved(), "timed out");
            assertFalse(Anchors.has(caster));

            } finally {
                for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void exchange(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer caster = TestServer.player(server, "exchange_caster");
            ServerPlayer target = TestServer.player(server, "exchange_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            try {
            BirthService.rebirth(caster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(caster, Swap.SWAP.get());
            InnateTechniqueService.select(caster, 3);
            assertEquals(Swap.EXCHANGE, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(caster, 3), "setup: Exchange in slot 4");
            caster.removeAllEffects();
            target.removeAllEffects();

            // Nothing to swap: free fizzle. Infinite effects never count.
            face(caster, target);
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobEffectInstance.INFINITE_DURATION, 3));
            double before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before, malice(caster), EPS, "nothing to swap: free");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.EXCHANGE));
            caster.removeAllEffects();

            // The caster's worst negative goes; the target's strongest positive comes back (level and time kept).
            caster.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
            caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 2));
            CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
            assertEquals(before - SwapConfig.EXCHANGE_COST.get(), malice(caster), EPS, "cost");
            assertFalse(caster.hasEffect(MobEffects.POISON), "gave Poison away");
            assertTrue(caster.hasEffect(MobEffects.WEAKNESS), "kept the lesser negative");
            assertEquals(1, target.getEffect(MobEffects.POISON).getAmplifier(), "Poison II carried over");
            assertEquals(200, target.getEffect(MobEffects.POISON).getDuration(), "duration carried over");
            assertFalse(target.hasEffect(MobEffects.DAMAGE_BOOST), "Strength taken");
            assertTrue(target.hasEffect(MobEffects.MOVEMENT_SPEED), "the lesser positive stays");
            assertEquals(2, caster.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier(), "Strength III carried over");
            assertEquals(100, caster.getEffect(MobEffects.DAMAGE_BOOST).getDuration());

            // Momentum Exchange: velocities and fall distances trade; nobody moves.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            caster.setKnownMovement(new Vec3(0, -1.5, 0));
            target.setKnownMovement(new Vec3(0.4, 0, 0));
            caster.fallDistance = 10f;
            target.fallDistance = 0f;
            Vec3 casterAt = caster.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
            assertEquals(before - SwapConfig.MOMENTUM_COST.get(), malice(caster), EPS, "momentum cost");
            assertEquals(new Vec3(0.4, 0, 0), caster.getDeltaMovement(), "caster took the target's velocity");
            assertEquals(new Vec3(0, -1.5, 0), target.getDeltaMovement(), "target took the caster's velocity");
            assertEquals(0f, caster.fallDistance, EPS, "fall distance traded");
            assertEquals(10f, target.fallDistance, EPS, "fall distance traded");
            assertEquals(casterAt, caster.position(), "nobody moved");
            assertTrue(InnateTechniqueService.cooldownRemaining(caster, Swap.EXCHANGE) > 0, "shared cooldown");
            } finally {
                for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void totalSwap(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer caster = TestServer.player(server, "total_caster");
            ServerPlayer target = TestServer.player(server, "total_target");
            level.addNewPlayer(caster);
            level.addNewPlayer(target);
            try {
            BirthService.rebirth(caster, OptionalInt.of(1000));
            InnateTechniqueService.setInnateTechnique(caster, Swap.SWAP.get());
            assertEquals(Swap.TOTAL_SWAP, Swap.SWAP.get().ultimate().orElseThrow(), "setup: Total Swap is the ultimate");

            // Too close: free fizzle.
            face(caster, target);
            target.moveTo(caster.getX() + 2, caster.getY(), caster.getZ(), 90f, 0f);
            double before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
            assertEquals(before, malice(caster), EPS, "too close: free");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, Swap.TOTAL_SWAP));

            // 4/20 and 18/20 become 18 and 4; places swap; each looks like the other.
            face(caster, target);
            caster.setHealth(4f);
            target.setHealth(18f);
            Vec3 casterAt = caster.position();
            Vec3 targetAt = target.position();
            before = malice(caster);
            CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
            assertEquals(before - SwapConfig.TOTAL_SWAP_COST.get(), malice(caster), EPS, "cost");
            assertEquals(18f, caster.getHealth(), EPS, "caster got the target's share");
            assertEquals(4f, target.getHealth(), EPS, "target got the caster's share");
            assertEquals(targetAt, caster.position(), "positions swapped");
            assertEquals(casterAt, target.position(), "positions swapped");
            assertEquals(target.getUUID(), Disguises.getDisguisedAs(caster).orElseThrow(), "disguised as each other");
            assertEquals(caster.getUUID(), Disguises.getDisguisedAs(target).orElseThrow(), "disguised as each other");
            assertEquals(Disguises.Kind.TOTAL_SWAP, Disguises.get(caster).orElseThrow().kind());
            assertEquals(SwapConfig.ticks(SwapConfig.TOTAL_SWAP_COOLDOWN), InnateTechniqueService.cooldownRemaining(caster, Swap.TOTAL_SWAP));
            // Cast below 30%: Identity Crisis is on the clock for 30 s.
            assertTrue(SwapTracker.identityCrisisPending(caster));
            TestServer.advanceGameTime(server, (int) SwapAdvancements.IDENTITY_CRISIS_SURVIVE_TICKS);
            SwapTracker.tick(server);
            assertFalse(SwapTracker.identityCrisisPending(caster), "30 s survived");
            Disguises.endInvolving(caster);

            // Shares, not points: each gets the other's percentage of their OWN max health.
            InnateTechniqueService.clearCooldowns(caster);
            face(caster, target);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40.0);
            caster.setHealth(4f);   // 20%
            target.setHealth(30f);  // 75%
            CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
            assertEquals(15f, caster.getHealth(), EPS, "75% of 20");
            assertEquals(8f, target.getHealth(), EPS, "20% of 40");
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20.0);
            Disguises.endInvolving(caster);
            } finally {
                for (ServerPlayer p : new ServerPlayer[] {caster, target}) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    /** Loads the test chunk: area queries only see entities in loaded chunks. */
    static void loadArena(ServerLevel level) {
        level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
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
