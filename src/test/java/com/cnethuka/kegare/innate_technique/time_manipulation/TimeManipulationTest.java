package com.cnethuka.kegare.innate_technique.time_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightSource;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Time Manipulation (DESIGN §3.26): Point Maker's mark, return and heal; Rewind on players and mobs, with no history and refused;
 * Accelerate's cooldown rate and Fast Forward (never the ultimate, Burnout or a shared ability); Decelerate's hit; Stasis Field's slow
 * and its projectiles; Standstill's freeze, held damage as one hit, its cap, immunity, early endings and a frozen player leaving; and
 * bosses, which take the damage but are never moved, slowed or stopped.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TimeManipulationTest {
    private static final double EPS = 1.0e-4;
    private static final double Y = 120; // in the open air above the flat world

    private static ServerPlayer holder(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(2000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, TimeManipulation.TIME_MANIPULATION.get());
        p.setHealth(p.getMaxHealth());
        MaliceService.refill(p);
        return p;
    }

    private static ServerPlayer bystander(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static void lookAt(ServerPlayer p, Vec3 at) {
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
        p.setYHeadRot(yaw);
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    private static void reset(MinecraftServer server) {
        TimeStops.clear();
        StasisFields.clear();
        PointMarks.clearAll();
        PositionHistory.clear();
        CooldownRates.clear();
        TestServer.removePlayers(server);
    }

    @Test
    void theReturnHealsAShareOfMaxHealthButNeverPastTheMark() {
        assertEquals(6f, PointMarks.heal(20f, 8f, 20f, 0.3), EPS, "20 -> 8: back to 14");
        assertEquals(2f, PointMarks.heal(20f, 18f, 20f, 0.3), EPS, "20 -> 18: back to 20, no more");
        assertEquals(0f, PointMarks.heal(20f, 20f, 20f, 0.3), EPS, "nothing lost, nothing healed");
        assertEquals(0f, PointMarks.heal(14f, 20f, 20f, 0.3), EPS, "healthier than at the mark: never takes any away");
    }

    @Test
    void pointMakerMarksThenReturns(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            try {
                ServerPlayer p = holder(server, level, "tm_point");
                assertEquals(20f, p.getMaxHealth(), EPS, "setup: plain max health");
                InnateTechniqueService.select(p, 0);
                assertEquals(TimeManipulation.POINT_MAKER, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(p, 0), "setup: slot 1");
                BlockPos block = new BlockPos(4, 117, 0);
                level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                p.moveTo(0.5, Y, 0.5, 0f, 0f);
                lookAt(p, new Vec3(4.5, 118.0, 0.5));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000));

                // The mark: paid, no cooldown yet, the mark out.
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(PointMarks.has(p), "marked");
                assertEquals(before - TimeConfig.MARK_COST.get(), malice(p), EPS, "the mark's cost");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER), "marking starts nothing");
                float yaw = p.getYRot(), pitch = p.getXRot();

                // Somewhere else, hurt to 8 and poisoned: the return puts it right.
                p.moveTo(10.5, Y, 10.5, 45f, 30f);
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                p.setHealth(8f);
                before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(PointMarks.has(p), "the mark is used");
                assertEquals(new Vec3(4.5, 118.0, 0.5), p.position(), "back on top of the marked block");
                assertEquals(yaw, p.getYRot(), EPS, "facing as at the mark");
                assertEquals(pitch, p.getXRot(), EPS);
                assertFalse(p.hasEffect(MobEffects.POISON), "what came since is shed");
                assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "what was there at the mark stays");
                assertEquals(14f, p.getHealth(), EPS, "20 -> 8 comes back to 14");
                assertEquals(before - TimeConfig.RETURN_COST.get(), malice(p), EPS, "Malice: only the cost, never put back");
                assertTrue(InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER) > 0, "the return starts the cooldown");

                // 20 -> 18: back to 20, never past it.
                InnateTechniqueService.clearCooldowns(p);
                p.setHealth(20f);
                p.moveTo(0.5, Y, 0.5, 0f, 0f);
                lookAt(p, new Vec3(4.5, 118.0, 0.5));
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                p.setHealth(18f);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertEquals(20f, p.getHealth(), EPS, "20 -> 18 comes back to 20");

                // A mark left to run out is gone, and that starts the cooldown.
                InnateTechniqueService.clearCooldowns(p);
                p.moveTo(0.5, Y, 0.5, 0f, 0f);
                lookAt(p, new Vec3(4.5, 118.0, 0.5));
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(PointMarks.has(p));
                TestServer.advanceGameTime(server, TimeConfig.ticks(TimeConfig.MARK_SECONDS));
                PointMarks.tick(p);
                assertFalse(PointMarks.has(p), "ran out");
                assertTrue(InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER) > 0, "running out starts the cooldown");

                // Nothing in sight to mark: free.
                InnateTechniqueService.clearCooldowns(p);
                p.moveTo(0.5, Y, 0.5, 0f, -60f);
                p.setYHeadRot(0f);
                before = malice(p);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(PointMarks.has(p));
                assertEquals(before, malice(p), EPS, "no block: nothing paid");
            } finally {
                level.setBlock(new BlockPos(4, 117, 0), Blocks.AIR.defaultBlockState(), 3);
                reset(server);
            }
        });
    }

    /** {@code ticks} of history: the target walks +X from {@code fromX} at 0.1 blocks a tick (staying in the loaded chunk). */
    private static void walk(MinecraftServer server, Entity target, double fromX, int ticks) {
        for (int i = 0; i < ticks; i++) {
            target.moveTo(fromX + i * 0.1, Y, 0.5, target.getYRot(), 0f);
            PositionHistory.tick(server);
            TestServer.advanceGameTime(server, 1);
        }
    }

    @Test
    void rewindSnapsPlayersAndMobsBack(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Consumer<TimeTargetEvent> refuse = e -> e.setCanceled(true);
            Pig pig = EntityType.PIG.create(level);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer caster = holder(server, level, "tm_rewind");
                ServerPlayer target = bystander(server, level, "tm_rewind_target");
                caster.moveTo(0.5, Y, 0.5, 0f, 0f);
                InnateTechniqueService.select(caster, 0);
                int ticks = TimeConfig.ticks(TimeConfig.REWIND_SECONDS);

                // A player running away is snapped back to where it was 3 seconds ago; the cost paid, Point Maker's cooldown started.
                walk(server, target, 8.5, ticks + 6);
                Vec3 then = new Vec3(8.5 + 6 * 0.1, Y, 0.5);
                lookAt(caster, target.position().add(0, 1, 0));
                assertEquals(target, TimeTargeting.lookedAtLiving(caster).orElse(null), "setup: the target in sight");
                assertTrue(PositionHistory.path(target, ticks).isPresent(), "setup: 3 s of history");
                double before = malice(caster);
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(then.x, target.getX(), 1.0e-6, "where it was 3 s ago");
                assertEquals(before - TimeConfig.REWIND_COST.get(), malice(caster), EPS, "Rewind's cost");
                assertTrue(InnateTechniqueService.cooldownRemaining(caster, TimeManipulation.POINT_MAKER) > 0, "shares Point Maker's cooldown");
                target.moveTo(0.5, Y, 40.5); // out of the way

                // A mob, followed because it's near a Time Manipulation holder.
                InnateTechniqueService.clearCooldowns(caster);
                pig.moveTo(8.5, Y, 0.5, 0f, 0f);
                pig.setNoGravity(true);
                level.addFreshEntity(pig);
                walk(server, pig, 8.5, ticks + 4);
                lookAt(caster, pig.position().add(0, 0.4, 0));
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(8.5 + 4 * 0.1, pig.getX(), 1.0e-6, "the mob went back too");

                // Within a block of where it was 3 seconds ago (standing still): nothing would happen, so a free fizzle, told so.
                InnateTechniqueService.clearCooldowns(caster);
                for (int i = 0; i < ticks + 3; i++) {
                    pig.moveTo(8.5 + (i % 2) * 0.3, Y, 0.5, 0f, 0f); // shuffling on the spot
                    PositionHistory.tick(server);
                    TestServer.advanceGameTime(server, 1);
                }
                lookAt(caster, pig.position().add(0, 0.4, 0));
                before = malice(caster);
                Vec3 still = pig.position();
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(still, pig.position(), "it stays");
                assertEquals(before, malice(caster), EPS, "already there: free");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, TimeManipulation.POINT_MAKER), "and no cooldown");
                assertEquals("message.kegare.time.already_there",
                        ((net.minecraft.network.chat.contents.TranslatableContents) TimeManipulation.REWIND.blockedMessage(caster).getContents()).getKey(),
                        "\"X was already there.\"");

                // No history (it's only just been seen): fizzles, nothing paid, no cooldown.
                InnateTechniqueService.clearCooldowns(caster);
                PositionHistory.clear();
                before = malice(caster);
                Vec3 at = pig.position();
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(at, pig.position(), "nowhere to go back to");
                assertEquals(before, malice(caster), EPS, "free");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, TimeManipulation.POINT_MAKER));

                // TimeTargetEvent cancelled: fizzles with the Malice spent; nobody moves.
                walk(server, pig, 8.5, ticks + 4);
                NeoForge.EVENT_BUS.addListener(refuse);
                at = pig.position();
                before = malice(caster);
                lookAt(caster, pig.position().add(0, 0.4, 0));
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(at, pig.position(), "refused: it stays");
                assertEquals(before - TimeConfig.REWIND_COST.get(), malice(caster), EPS, "refused: Malice spent");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(caster, TimeManipulation.POINT_MAKER), "refused: no cooldown");
                NeoForge.EVENT_BUS.unregister(refuse);
                pig.discard();

                // A boss is never moved: free fizzle.
                wither.moveTo(7.5, Y, 0.5, 0f, 0f);
                wither.setNoGravity(true);
                level.addFreshEntity(wither);
                walk(server, wither, 7.5, ticks + 4);
                lookAt(caster, wither.position().add(0, 1.5, 0));
                at = wither.position();
                before = malice(caster);
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertEquals(at, wither.position(), "bosses stay put");
                assertEquals(before, malice(caster), EPS, "free");
            } finally {
                NeoForge.EVENT_BUS.unregister(refuse);
                pig.discard();
                wither.discard();
                reset(server);
            }
        });
    }

    @Test
    void accelerateAndFastForwardRunTheOtherClocks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                ServerPlayer p = holder(server, level, "tm_accelerate");
                p.moveTo(0.5, Y, 0.5, 0f, 0f);
                InnateTechniqueService.select(p, 1);
                assertEquals(TimeManipulation.ACCELERATE, com.cnethuka.kegare.loadout.LoadoutService.abilityAt(p, 1), "setup: slot 2");
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertTrue(TimeManipulation.ACCELERATE.isActive(p), "on");
                assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed");
                assertNotNull(p.getAttribute(Attributes.ATTACK_SPEED).getModifier(AccelerateAbility.ATTACK_SPEED), "faster attacks");

                // Decelerate's cooldown runs half again as fast: 40 ticks take 60 off it.
                InnateTechniqueService.startCooldown(p, TimeManipulation.DECELERATE);
                int start = InnateTechniqueService.cooldownRemaining(p, TimeManipulation.DECELERATE);
                for (int i = 0; i < 40; i++) {
                    CooldownRates.tick(p);
                    TestServer.advanceGameTime(server, 1);
                }
                assertEquals(start - 60, InnateTechniqueService.cooldownRemaining(p, TimeManipulation.DECELERATE), 1, "1.5x");

                // Fast Forward: 6 s off every other cooldown of the technique; never the ultimate's or a shared ability's.
                InnateTechniqueService.startCooldown(p, TimeManipulation.POINT_MAKER);
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                InnateTechniqueService.startCooldown(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                int pm = InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER);
                int dec = InnateTechniqueService.cooldownRemaining(p, TimeManipulation.DECELERATE);
                int ult = InnateTechniqueService.cooldownRemaining(p, TimeManipulation.STANDSTILL);
                int rmt = InnateTechniqueService.cooldownRemaining(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                int ff = TimeConfig.ticks(TimeConfig.FAST_FORWARD_SECONDS);
                double before = malice(p);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertEquals(before - TimeConfig.FAST_FORWARD_COST.get(), malice(p), EPS, "Fast Forward's cost");
                assertEquals(pm - ff, InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER), "Point Maker: 6 s sooner");
                assertEquals(Math.max(0, dec - ff), InnateTechniqueService.cooldownRemaining(p, TimeManipulation.DECELERATE), "Decelerate: 6 s sooner");
                assertEquals(ult, InnateTechniqueService.cooldownRemaining(p, TimeManipulation.STANDSTILL), "never the ultimate's");
                assertEquals(rmt, InnateTechniqueService.cooldownRemaining(p, Rmt.REVERSE_MALICE_TECHNIQUE), "never a shared ability's");
                assertFalse(TimeManipulation.ACCELERATE.isActive(p), "Accelerate switched off");
                assertEquals(TimeConfig.ticks(TimeConfig.FAST_FORWARD_COOLDOWN), InnateTechniqueService.cooldownRemaining(p, TimeManipulation.ACCELERATE),
                        "and on their shared cooldown");
                assertEquals(1.0, CooldownRates.multiplier(p, TimeManipulation.DECELERATE.cooldownKey()), EPS, "the rate went with it");

                // Never Burnout: it isn't a cooldown.
                Burnout.apply(p, 300);
                AccelerateAbility.fastForward(p, TimeManipulation.TIME_MANIPULATION.get(), 120);
                assertEquals(300, Burnout.remaining(p), "Burnout untouched");
                Burnout.clear(p);
            } finally {
                reset(server);
            }
        });
    }

    @Test
    void decelerateSlowsAndTheEventGuardsTheCooldownSlow(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            Consumer<TimeTargetEvent> refuse = e -> e.setCanceled(true);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer caster = holder(server, level, "tm_decelerate");
                ServerPlayer target = bystander(server, level, "tm_decelerate_target");
                caster.moveTo(0.5, Y, 0.5, 0f, 0f);
                target.moveTo(4.5, Y, 0.5, 0f, 0f);
                InnateTechniqueService.startCooldown(target, Swap.POSITIONS);
                DecelerateEntity hand = new DecelerateEntity(level, caster);

                DecelerateEntity.strike(hand, caster, target, TimeConfig.DECELERATE_DAMAGE.get().floatValue());
                assertEquals(20f - TimeConfig.DECELERATE_DAMAGE.get().floatValue(), target.getHealth(), EPS, "the damage");
                assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Slowness");
                assertTrue(target.hasEffect(MobEffects.DIG_SLOWDOWN), "Mining Fatigue");
                assertEquals(TimeConfig.DECELERATE_COOLDOWN_RATE.get(), CooldownRates.multiplier(target, Swap.POSITIONS.cooldownKey()), EPS,
                        "their cooldowns at half speed");
                int start = InnateTechniqueService.cooldownRemaining(target, Swap.POSITIONS);
                for (int i = 0; i < 20; i++) {
                    CooldownRates.tick(target);
                    TestServer.advanceGameTime(server, 1);
                }
                assertEquals(start - 10, InnateTechniqueService.cooldownRemaining(target, Swap.POSITIONS), 1, "20 ticks take only 10 off");

                // TimeTargetEvent cancelled: the slows land, the cooldown slow doesn't.
                CooldownRates.clear();
                target.removeAllEffects();
                target.invulnerableTime = 0;
                NeoForge.EVENT_BUS.addListener(refuse);
                DecelerateEntity.strike(hand, caster, target, 1f);
                assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
                assertEquals(1.0, CooldownRates.multiplier(target, Swap.POSITIONS.cooldownKey()), EPS, "refused: cooldowns untouched");
                NeoForge.EVENT_BUS.unregister(refuse);

                // A boss takes the damage, never the slow.
                wither.moveTo(8.5, Y, 0.5, 0f, 0f);
                wither.setNoGravity(true);
                level.addFreshEntity(wither);
                float health = wither.getHealth();
                wither.invulnerableTime = 0;
                DecelerateEntity.strike(hand, caster, wither, 3f);
                assertTrue(wither.getHealth() < health, "the boss is hurt");
                assertFalse(wither.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and never slowed");

                // The cast throws a real projectile.
                InnateTechniqueService.select(caster, 2);
                CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
                List<DecelerateEntity> thrown = level.getEntitiesOfClass(DecelerateEntity.class, caster.getBoundingBox().inflate(8));
                assertEquals(1, thrown.size(), "one clock hand in the air");
                assertEquals(TimeConfig.DECELERATE_SPEED.get(), thrown.get(0).getDeltaMovement().length(), 0.05, "at its speed");
                thrown.forEach(Entity::discard);
            } finally {
                NeoForge.EVENT_BUS.unregister(refuse);
                wither.discard();
                reset(server);
            }
        });
    }

    @Test
    void stasisFieldSlowsWhatsInsideAndLetsProjectilesGo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Pig pig = EntityType.PIG.create(level);
            WitherBoss wither = EntityType.WITHER.create(level);
            Snowball a = new Snowball(level, 8.5, Y, 0.5), b = new Snowball(level, 8.5, Y, 1.5);
            try {
                ServerPlayer caster = holder(server, level, "tm_stasis");
                caster.moveTo(0.5, Y, 0.5, 0f, 0f);
                Vec3 centre = new Vec3(8.5, Y, 0.5);
                StasisFields.open(caster, centre, 4.0, 100);
                pig.moveTo(9.5, Y, 0.5, 0f, 0f);
                wither.moveTo(6.5, Y, 2.5, 0f, 0f);
                for (Entity e : List.of(pig, wither, a, b)) {
                    e.setNoGravity(true);
                    level.addFreshEntity(e);
                }
                a.setDeltaMovement(1, 0, 0);
                b.setDeltaMovement(0, 0, -1);
                StasisFields.tick(level);
                assertEquals(0.05, a.getDeltaMovement().x, 1.0e-6, "a projectile inside crawls, same way");
                assertEquals(-0.05, b.getDeltaMovement().z, 1.0e-6);
                MobEffectInstance slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                assertNotNull(slow, "heavy Slowness inside");
                assertEquals(TimeConfig.STASIS_SLOWNESS.get(), slow.getAmplifier());
                assertFalse(wither.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "never a boss");

                // Out of the field: its own speed back.
                a.setPos(14.5, Y, 0.5);
                StasisFields.tick(level);
                assertEquals(new Vec3(1, 0, 0), a.getDeltaMovement(), "restored on leaving");
                assertTrue(StasisFields.slowed(b), "the other still crawls");

                // The field ends: everything in it goes again.
                TestServer.advanceGameTime(server, 100);
                StasisFields.tick(level);
                assertEquals(new Vec3(0, 0, -1), b.getDeltaMovement(), "restored when it ends");
                assertEquals(0, StasisFields.count());
            } finally {
                for (Entity e : List.of(pig, wither, a, b)) e.discard();
                reset(server);
            }
        });
    }

    private static boolean ticks(Entity e) {
        return !NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(e)).isCanceled();
    }

    @Test
    void standstillFreezesHoldsTheDamageAndLetsItLandAsOneHit(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            Pig pig = EntityType.PIG.create(level);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer caster = holder(server, level, "tm_stop");
                ServerPlayer immune = holder(server, level, "tm_stop_immune");
                ServerPlayer target = bystander(server, level, "tm_stop_target");
                caster.moveTo(0.5, Y, 0.5, 0f, 0f);
                immune.moveTo(4.5, Y, 4.5, 0f, 0f);
                target.moveTo(6.5, Y, 0.5, 0f, 0f);
                pig.moveTo(8.5, Y, 2.5, 0f, 0f);
                wither.moveTo(12.5, Y, 0.5, 0f, 0f);
                for (Entity e : List.of(pig, wither)) {
                    e.setNoGravity(true);
                    level.addFreshEntity(e);
                }

                // Cast: paid, on cooldown, time stops.
                double before = malice(caster);
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                assertTrue(TimeStops.active(level), "time stands still");
                assertEquals(before - TimeConfig.STANDSTILL_COST.get(), malice(caster), EPS, "its cost");
                assertTrue(InnateTechniqueService.cooldownRemaining(caster, TimeManipulation.STANDSTILL) > 0, "its cooldown");
                assertTrue(TimeStops.frozen(target) && TimeStops.frozen(pig), "the player and the mob are frozen");
                assertFalse(TimeStops.frozen(caster), "not the caster");
                assertFalse(TimeStops.frozen(immune), "nor another Time Manipulation holder");
                assertFalse(TimeStops.frozen(wither), "nor a boss");
                assertFalse(ticks(pig), "a frozen mob's tick is skipped");
                assertTrue(ticks(caster) && ticks(wither), "the caster and the boss tick on");

                // A frozen player does nothing, and is put back if they move.
                assertTrue(NeoForge.EVENT_BUS.post(new AttackEntityEvent(target, pig)).isCanceled(), "no attacks");
                assertTrue(com.cnethuka.kegare.combat.CombatHelper.isStunned(target), "stunned: no casting");
                target.setPos(7.5, Y, 0.5);
                TimeStops.tick(level);
                assertEquals(new Vec3(6.5, Y, 0.5), target.position(), "held where they stood");

                // Damage to the frozen is held; between the caster and the immune it lands at once.
                assertFalse(target.hurt(caster.damageSources().playerAttack(caster), 4f), "held");
                assertFalse(target.hurt(caster.damageSources().playerAttack(caster), 5f), "held");
                assertFalse(pig.hurt(caster.damageSources().playerAttack(caster), 2f), "held");
                assertEquals(20f, target.getHealth(), EPS, "nothing yet");
                assertTrue(immune.hurt(caster.damageSources().playerAttack(caster), 3f), "immune: at once");
                assertEquals(17f, immune.getHealth(), EPS);

                // No second stop while one stands, not even by the immune.
                MaliceService.refill(immune);
                before = malice(immune);
                CastManager.handleIntent(immune, CastIntent.ULTIMATE_PRESS);
                assertEquals(before, malice(immune), EPS, "refused, free");
                assertEquals(caster, TimeStops.caster(level));

                // Something the caster throws flies its first few ticks, then hangs.
                Snowball thrown = new Snowball(level, caster);
                thrown.setDeltaMovement(1, 0, 0);
                level.addFreshEntity(thrown);
                for (int i = 0; i < TimeConfig.STANDSTILL_FLIGHT_TICKS.get(); i++) assertTrue(ticks(thrown), "flies, tick " + i);
                assertFalse(ticks(thrown), "then hangs");
                thrown.discard();

                // Time resumes: one hit each for what was held, the caster burnt out, the survivors noted.
                TestServer.advanceGameTime(server, TimeConfig.ticks(TimeConfig.STANDSTILL_SECONDS));
                TimeStops.tick(level);
                assertFalse(TimeStops.active(level), "over");
                assertEquals(11f, target.getHealth(), EPS, "4 + 5 as one hit");
                assertEquals(pig.getMaxHealth() - 2f, pig.getHealth(), EPS, "the mob's too");
                assertFalse(com.cnethuka.kegare.combat.CombatHelper.isStunned(target), "let go");
                assertEquals(TimeConfig.ticks(TimeConfig.STANDSTILL_BURNOUT), Burnout.remaining(caster), "then Burnout");
                assertTrue(Insight.hasFirst(target, InsightSource.SURVIVED_STANDSTILL.id()), "survived it");
                assertTrue(ticks(pig), "everything moves again");
                Burnout.clear(caster);

                // The one hit is capped.
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
                target.setHealth(100f);
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 30f);
                target.hurt(caster.damageSources().playerAttack(caster), 30f);
                TestServer.advanceGameTime(server, 60);
                TimeStops.tick(level);
                assertEquals(100f - TimeConfig.STANDSTILL_DAMAGE_CAP.get().floatValue(), target.getHealth(), EPS, "at most damageCap");

                // A frozen player who logs out takes what was held for them there and then, as the one hit (capped), and is let go;
                // the stop goes on without them.
                float cap = TimeConfig.STANDSTILL_DAMAGE_CAP.get().floatValue();
                target.setHealth(100f);
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 30f);
                target.hurt(caster.damageSources().playerAttack(caster), 30f);
                TimeStops.onLeaving(target);
                assertEquals(100f - cap, target.getHealth(), EPS, "landed as they left, as one capped hit");
                assertFalse(com.cnethuka.kegare.combat.CombatHelper.isStunned(target), "let go");
                assertTrue(TimeStops.active(level), "it stands for the rest");
                TestServer.advanceGameTime(server, 60);
                TimeStops.tick(level);
                assertEquals(100f - cap, target.getHealth(), EPS, "nothing lands twice");

                // Sent to another level: the same, before they go.
                target.setHealth(100f);
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 10f);
                NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(target, Level.NETHER));
                assertEquals(90f, target.getHealth(), EPS, "landed before the trip");
                TestServer.advanceGameTime(server, 60);
                TimeStops.tick(level);
                assertEquals(90f, target.getHealth(), EPS, "nothing lands twice");

                // A frozen player who dies during it has nothing left to land.
                target.setHealth(100f);
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 10f);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(target, caster.damageSources().generic()));
                TestServer.advanceGameTime(server, 60);
                TimeStops.tick(level);
                assertEquals(100f, target.getHealth(), EPS, "dropped");

                // The caster dies: it ends early, and what was held lands.
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 10f);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(caster, caster.damageSources().generic()));
                assertFalse(TimeStops.active(level), "ended early");
                assertEquals(90f, target.getHealth(), EPS, "landed");

                // Logging out is no way out: if what was held kills them, they die.
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
                target.setHealth(5f);
                target.invulnerableTime = 0;
                TimeStops.start(caster, caster.position(), 48, 60);
                target.hurt(caster.damageSources().playerAttack(caster), 12f);
                TimeStops.onLeaving(target);
                assertTrue(target.isDeadOrDying(), "killed by the held hit as they left");
                TestServer.advanceGameTime(server, 60);
                TimeStops.tick(level);
            } finally {
                pig.discard();
                wither.discard();
                reset(server);
            }
        });
    }
}
