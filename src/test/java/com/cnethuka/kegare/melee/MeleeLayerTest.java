package com.cnethuka.kegare.melee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.test.TestAbilities;
import com.cnethuka.kegare.innatetechnique.test.TestInnateTechniques;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeIntentPayload.Action;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.ProgressionConfig;
import com.cnethuka.kegare.progression.TechniqueUnlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Dash, Parry and Reinforced Strike on a throwaway server. Everything stays in chunk (0,0), which stays loaded. */
@ExtendWith(EphemeralTestServerProvider.class)
class MeleeLayerTest {
    private static final float EPS = 1.0e-3f;
    /** What the game itself gives a target of any melee hit, Strike or not. */
    private static final double VANILLA_KNOCKBACK = 0.4;

    private static ServerLevel world(MinecraftServer server) {
        TestServer.ensureOverworld(server);
        return server.overworld();
    }

    private static int ground(ServerLevel level) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
    }

    private static ServerPlayer player(MinecraftServer server, String name, int capacity) {
        ServerLevel level = world(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        if (capacity > 0) {
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            MaliceService.refill(p);
        }
        p.moveTo(2.5, ground(level), 8.5, -90f, 0f);
        p.setYHeadRot(-90f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static void send(ServerPlayer p, Action action) {
        MeleeIntentPayload.apply(p, new MeleeIntentPayload(action, (byte) 0, (byte) 0));
    }

    private static <T extends LivingEntity> T place(ServerLevel level, T mob, double x, double z) {
        mob.moveTo(x, ground(level), z, 0f, 0f);
        mob.setNoGravity(true);
        if (mob instanceof net.minecraft.world.entity.Mob m) m.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    /** Hits {@code victim} with a melee hit from {@code attacker}, past the game's hit immunity. Returns the damage taken. */
    private static float melee(LivingEntity victim, LivingEntity attacker, float amount) {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        victim.hurt(attacker instanceof ServerPlayer p ? p.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker), amount);
        return before - victim.getHealth();
    }

    private static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }

    private static void advance(MinecraftServer server, ServerPlayer p, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TestServer.advanceGameTime(server, 1);
            MeleeLayer.tick(p);
        }
    }

    // ---------------------------------------------------------------- Dash

    @Test
    void directionFollowsTheKeysAndIsForwardWithNone() {
        assertVec(new Vec3(0, 0, 1), Dash.direction(0f, 0, 0), "nothing held at yaw 0: forward, +Z");
        assertVec(new Vec3(0, 0, -1), Dash.direction(0f, -1, 0), "back");
        assertVec(new Vec3(1, 0, 0), Dash.direction(0f, 0, 1), "left at yaw 0 is +X");
        assertVec(new Vec3(1, 0, 0), Dash.direction(-90f, 0, 0), "yaw -90 faces +X");
        Vec3 diagonal = Dash.direction(0f, 1, 1);
        assertEquals(1.0, diagonal.length(), 1e-3, "a diagonal is no faster");
        assertVec(new Vec3(0, 0, 1), Dash.direction(0f, 100, 0), "out-of-range keys from a modified client are clamped, not trusted");
    }

    private static void assertVec(Vec3 expected, Vec3 actual, String message) {
        assertEquals(0, expected.distanceTo(actual), 1e-3, message + ": " + actual);
    }

    @Test
    void aDashCostsMaliceCoversItsDistanceAndWaitsOutItsCooldown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "dash_basic", 1000);
            double before = malice(p);
            send(p, Action.DASH);
            MeleeState state = MeleeLayer.state(p);
            assertEquals(before - MeleeConfig.DASH_COST.get(), malice(p), EPS, "paid");
            assertNotNull(state.dashDirection, "on its way");
            assertEquals(0, new Vec3(1, 0, 0).distanceTo(state.dashDirection), 1e-3, "the way they face: " + state.dashDirection);
            assertEquals(MeleeConfig.DASH_DISTANCE.get(), state.dashDistance, 0.01, "its distance, decided here");

            // A modified client can ask as often as it likes.
            for (int i = 0; i < 5; i++) send(p, Action.DASH);
            assertEquals(before - MeleeConfig.DASH_COST.get(), malice(p), EPS, "no second dash while one is under way");

            int ticks = 0;
            while (state.dashDirection != null && ticks++ < 40) advance(server, p, 1);
            assertNull(state.dashDirection, "it ends");

            send(p, Action.DASH);
            assertEquals(before - MeleeConfig.DASH_COST.get(), malice(p), EPS, "still on cooldown");
            advance(server, p, MeleeConfig.ticks(MeleeConfig.DASH_COOLDOWN.get()));
            send(p, Action.DASH);
            assertEquals(before - 2 * MeleeConfig.DASH_COST.get(), malice(p), EPS, "ready again");
        });
    }

    @Test
    void wallsStopADashAndAWallRightThereRefusesIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            int y = ground(level);
            ServerPlayer p = player(server, "dash_wall", 1000);
            List<BlockPos> wall = new ArrayList<>();
            for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy < 3; dy++) wall.add(new BlockPos(5, y + dy, 8 + dz));
            wall.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
            try {
                Vec3 east = new Vec3(1, 0, 0);
                double clear = Dash.clearDistance(p, east, 4.0);
                assertTrue(clear < 2.3, "stops at the wall, x=5, from x=2.5: " + clear);
                assertTrue(clear >= 1.6, "and gets most of the way there: " + clear);

                send(p, Action.DASH);
                MeleeState state = MeleeLayer.state(p);
                assertTrue(state.dashDistance < 2.3, "it never aims past the wall: " + state.dashDistance);
                MeleeLayer.reset(p);

                // Pressed up against it: refused for free, with no cooldown.
                ServerPlayer q = player(server, "dash_wall_close", 1000);
                q.moveTo(4.65, y, 8.5, -90f, 0f);
                q.setYHeadRot(-90f);
                double before = malice(q);
                send(q, Action.DASH);
                assertEquals(before, malice(q), EPS, "nothing paid");
                assertEquals(0, MeleeLayer.state(q).dashReadyAt, "no cooldown");
            } finally {
                wall.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            }
        });
    }

    @Test
    void aStepCanBeRiddenOverButAWallCant(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            int y = ground(level);
            ServerPlayer p = player(server, "dash_step", 1000);
            BlockPos slab = new BlockPos(4, y, 8);
            level.setBlockAndUpdate(slab, Blocks.STONE_SLAB.defaultBlockState());
            try {
                assertEquals(4.0, Dash.clearDistance(p, new Vec3(1, 0, 0), 4.0), 0.01, "half a block is a step");
            } finally {
                level.setBlockAndUpdate(slab, Blocks.AIR.defaultBlockState());
            }
        });
    }

    @Test
    void lowMaliceAndStunsStopADash(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer poor = player(server, "dash_poor", 1000);
            MaliceService.setCurrent(poor, MeleeConfig.DASH_COST.get() - 1);
            send(poor, Action.DASH);
            assertNull(MeleeLayer.state(poor).dashDirection, "can't afford it");
            assertEquals(MeleeConfig.DASH_COST.get() - 1, malice(poor), EPS, "nothing paid");

            ServerPlayer stunned = player(server, "dash_stunned", 1000);
            CombatHelper.stun(stunned, 40);
            send(stunned, Action.DASH);
            assertNull(MeleeLayer.state(stunned).dashDirection, "stunned");
            CombatHelper.clearStun(stunned);
        });
    }

    // ---------------------------------------------------------------- Parry

    @Test
    void thePerfectWindowCancelsAHitAndStaggersTheAttacker(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_perfect", 1000);
            Zombie attacker = place(level, new Zombie(EntityType.ZOMBIE, level), 4.5, 8.5);
            List<MeleeImpact> impacts = new ArrayList<>();
            MeleeImpact.Listener listener = (moment, l, at, who, other) -> impacts.add(moment);
            MeleeImpact.listen(listener);
            try {
                send(p, Action.PARRY_PRESS);
                advance(server, p, MeleeConfig.ticks(MeleeConfig.PARRY_PERFECT_WINDOW.get()) - 1);
                double before = malice(p);
                assertEquals(0f, melee(p, attacker, 6f), EPS, "the last tick of the window still parries");
                assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "staggered: slowed");
                assertEquals(MeleeConfig.ticks(MeleeConfig.PARRY_STAGGER_SECONDS.get()), attacker.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration(), "for its time");
                assertTrue(ReinforcedStrike.isReady(p), "and a Strike is earned");
                assertEquals(before, malice(p), EPS, "the window itself costs nothing");
                assertTrue(impacts.contains(MeleeImpact.PARRY_PERFECT), "fires its named moment");
                assertTrue(impacts.contains(MeleeImpact.STAGGER), "fires its named moment");
            } finally {
                MeleeImpact.remove(listener);
                attacker.discard();
            }
        });
    }

    @Test
    void aStaggerRestartsAPlayersAttackButNotTheirAbilities(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_on_player", 1000);
            ServerPlayer attacker = player(server, "parry_attacker", 1000);
            try {
                java.lang.reflect.Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                ticker.setAccessible(true);
                ticker.setInt(attacker, 200);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            assertEquals(1f, attacker.getAttackStrengthScale(0f), EPS, "fully charged");
            send(p, Action.PARRY_PRESS);
            assertEquals(0f, melee(p, attacker, 6f), EPS, "parried");
            assertTrue(attacker.getAttackStrengthScale(0f) < 0.1f, "attack cooldown started over");
            assertFalse(CombatHelper.isStunned(attacker), "not stunned: casting still works");
        });
    }

    @Test
    void afterTheWindowAGuardTakesFortyPercentAndDrains(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_guard", 1000);
            Zombie attacker = place(level, new Zombie(EntityType.ZOMBIE, level), 4.5, 8.5);
            try {
                send(p, Action.PARRY_PRESS);
                advance(server, p, MeleeConfig.ticks(MeleeConfig.PARRY_PERFECT_WINDOW.get()));
                double before = malice(p);
                assertEquals(6f * (1f - MeleeConfig.GUARD_MELEE_REDUCTION.get().floatValue()), melee(p, attacker, 6f), EPS, "40% less");
                assertFalse(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "no stagger past the window");
                assertFalse(ReinforcedStrike.isReady(p), "no Strike either");
                advance(server, p, 20);
                assertEquals(before - MeleeConfig.GUARD_DRAIN.get(), malice(p), 0.01, "drains its rate over a second");

                // Out of Malice: the guard drops.
                MaliceService.setCurrent(p, 0.1);
                advance(server, p, 2);
                assertFalse(MeleeLayer.state(p).isGuarding(), "the guard drops when the Malice runs out");
            } finally {
                attacker.discard();
            }
        });
    }

    @Test
    void techniquesAndProjectilesAreNeverParried(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_technique", 1000);
            ServerPlayer caster = player(server, "parry_caster", 1000);
            send(p, Action.PARRY_PRESS);

            p.invulnerableTime = 0;
            float before = p.getHealth();
            p.hurt(RimeDamage.source(level, caster, caster), 10f);
            assertEquals(10f * (1f - MeleeConfig.GUARD_TECHNIQUE_REDUCTION.get().floatValue()), before - p.getHealth(), EPS, "in the perfect window: only the slight reduction");
            assertFalse(ReinforcedStrike.isReady(p), "and no Strike");
            assertFalse(caster.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and no stagger");

            p.setHealth(p.getMaxHealth());
            p.invulnerableTime = 0;
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(caster);
            before = p.getHealth();
            p.hurt(p.damageSources().arrow(arrow, caster), 5f);
            assertEquals(5f, before - p.getHealth(), EPS, "an arrow isn't parried or reduced");
            assertFalse(ReinforcedStrike.isReady(p), "and no Strike");
        });
    }

    @Test
    void recoveryStopsMashingAndAPressIsTimedOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_recovery", 1000);
            Zombie attacker = place(level, new Zombie(EntityType.ZOMBIE, level), 4.5, 8.5);
            MeleeState state = MeleeLayer.state(p);
            try {
                send(p, Action.PARRY_PRESS);
                long start = state.guardStart;
                advance(server, p, 3);
                send(p, Action.PARRY_PRESS);
                assertEquals(start, state.guardStart, "pressing again doesn't restart the window");

                send(p, Action.PARRY_RELEASE);
                assertFalse(state.isGuarding());
                send(p, Action.PARRY_PRESS);
                assertFalse(state.isGuarding(), "no parry while recovering");
                assertEquals(6f, melee(p, attacker, 6f), EPS, "and the hit lands in full: mashing leaves you open");
                advance(server, p, MeleeConfig.ticks(MeleeConfig.PARRY_RECOVERY.get()) - 1);
                send(p, Action.PARRY_PRESS);
                assertFalse(state.isGuarding(), "one tick short");
                advance(server, p, 1);
                send(p, Action.PARRY_PRESS);
                assertTrue(state.isGuarding(), "recovered");
            } finally {
                attacker.discard();
            }
        });
    }

    @Test
    void aGuardCantStartMidCharge(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_charging", 1000);
            InnateTechniqueService.select(p, 1); // Glacial Lance, charged
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNotNull(CastManager.state(p).casting(), "charging");
            send(p, Action.PARRY_PRESS);
            assertFalse(MeleeLayer.state(p).isGuarding(), "hands are busy");
            CastManager.interrupt(p);
        });
    }

    @Test
    void aDashBringsTheGuardDownWithItsRecovery(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_dash", 1000);
            MeleeState state = MeleeLayer.state(p);
            send(p, Action.PARRY_PRESS);
            send(p, Action.DASH);
            assertFalse(state.isGuarding(), "a guard can't go with you");
            assertNotNull(state.dashDirection, "the dash goes");
            assertEquals(p.level().getGameTime() + MeleeConfig.ticks(MeleeConfig.PARRY_RECOVERY.get()), state.recoveryUntil, "and it costs the recovery");
        });
    }

    @Test
    void stunsStopAndForbidAParry(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_stun", 1000);
            Zombie attacker = place(level, new Zombie(EntityType.ZOMBIE, level), 4.5, 8.5);
            try {
                CombatHelper.stun(p, 40);
                send(p, Action.PARRY_PRESS);
                assertFalse(MeleeLayer.state(p).isGuarding(), "can't start stunned");
                CombatHelper.clearStun(p);

                send(p, Action.PARRY_PRESS);
                assertTrue(MeleeLayer.state(p).isGuarding());
                CombatHelper.stun(p, 40);
                assertEquals(6f, melee(p, attacker, 6f), EPS, "stunned in the window: the hit lands the same tick");
                advance(server, p, 1);
                assertFalse(MeleeLayer.state(p).isGuarding(), "and the guard is down");
                CombatHelper.clearStun(p);
            } finally {
                attacker.discard();
            }
        });
    }

    @Test
    void bossesAreReducedButNeverCancelledOrStaggered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "parry_boss", 1000);
            WitherBoss boss = place(level, new WitherBoss(EntityType.WITHER, level), 6.5, 8.5);
            try {
                send(p, Action.PARRY_PRESS);
                float taken = melee(p, boss, 6f);
                assertEquals(6f * (1f - MeleeConfig.GUARD_MELEE_REDUCTION.get().floatValue()), taken, EPS, "reduced, not cancelled, even in the window");
                assertFalse(boss.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "bosses can't be staggered");
                assertFalse(ReinforcedStrike.isReady(p), "so nothing was parried");
            } finally {
                boss.discard();
            }
        });
    }

    @Test
    void aPressThatCastsNothingLeavesTheGuardUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_no_cast", 1000);
            send(p, Action.PARRY_PRESS);

            InnateTechniqueService.select(p, 8);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertTrue(MeleeLayer.state(p).isGuarding(), "an empty slot");

            InnateTechniqueService.select(p, 0);
            ProgressionConfig.REQUIRE_REFINEMENT.set(true);
            try {
                assertFalse(TechniqueUnlocks.isUnlocked(p, Rime.SPLINTER), "setup: Splinter is locked at Refinement 0");
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(MeleeLayer.state(p).isGuarding(), "a locked ability");
            } finally {
                ProgressionConfig.REQUIRE_REFINEMENT.set(false);
            }

            CastManager.handleIntent(p, CastIntent.ULTIMATE_PRESS);
            assertTrue(MeleeLayer.state(p).isGuarding(), "a locked ultimate");

            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertFalse(MeleeLayer.state(p).isGuarding(), "a real cast does drop it");
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD) > 0, "setup: Frost Shard is on cooldown");

            send(p, Action.PARRY_RELEASE);
            MeleeState state = MeleeLayer.state(p);
            state.recoveryUntil = 0;
            send(p, Action.PARRY_PRESS);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(state.isGuarding(), "a cooldown");

            MaliceService.trySpend(p, MaliceService.get(p).current() - 1);
            InnateTechniqueService.clearCooldowns(p);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(state.isGuarding(), "not enough Malice");
        });
    }

    @Test
    void noTargetAndSwitchingAToggleOffLeaveTheGuardUpToo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer swap = player(server, "parry_no_target", 1000);
            InnateTechniqueService.setInnateTechnique(swap, Swap.SWAP.get());
            MaliceService.refill(swap);
            swap.moveTo(swap.getX(), swap.getY(), swap.getZ(), 0f, -90f); // straight up: nobody to swap with
            send(swap, Action.PARRY_PRESS);
            InnateTechniqueService.select(swap, 0); // Positions
            CastManager.handleIntent(swap, CastIntent.CAST_PRESS);
            assertTrue(MeleeLayer.state(swap).isGuarding(), "no target");

            ServerPlayer p = player(server, "parry_toggle", 1000);
            InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.APEX.get());
            MaliceService.refill(p);
            InnateTechniqueService.select(p, 3); // Aura
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(CastManager.isToggleActive(p, TestAbilities.AURA), "setup: on");
            send(p, Action.PARRY_PRESS);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertFalse(CastManager.isToggleActive(p, TestAbilities.AURA), "setup: off");
            assertTrue(MeleeLayer.state(p).isGuarding(), "switching a toggle off isn't a cast starting");
        });
    }

    @Test
    void everyTypeOfCastThatStartsDropsTheGuard(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_each_type", 1000);
            InnateTechniqueService.setInnateTechnique(p, TestInnateTechniques.APEX.get()); // Dash, Burst, Hover, Aura
            MaliceService.refill(p);
            for (int slot = 0; slot < 4; slot++) {
                MeleeLayer.state(p).recoveryUntil = 0;
                send(p, Action.PARRY_PRESS);
                assertTrue(MeleeLayer.state(p).isGuarding(), "setup: guarding, slot " + slot);
                InnateTechniqueService.select(p, slot);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertFalse(MeleeLayer.state(p).isGuarding(), "slot " + slot);
                CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            }
        });
    }

    @Test
    void castingAndAttackingDropTheGuard(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "parry_cast", 1000);
            send(p, Action.PARRY_PRESS);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertFalse(MeleeLayer.state(p).isGuarding(), "a cast drops it");

            ServerLevel level = world(server);
            ServerPlayer q = player(server, "parry_attack", 1000);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 8.5);
            try {
                send(q, Action.PARRY_PRESS);
                q.attack(cow);
                assertFalse(MeleeLayer.state(q).isGuarding(), "so does attacking");
            } finally {
                cow.discard();
            }
        });
    }

    // ---------------------------------------------------------------- Reinforced Strike

    private static void ready(ServerPlayer p) {
        ReinforcedStrike.earn(p, MeleeLayer.state(p));
    }

    @Test
    void aStrikeAddsDamageAndKnockbackAndPaysOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "strike_hit", 1000);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 8.5);
            try {
                double before = malice(p);
                ready(p);
                ready(p); // earning it twice doesn't stack
                float taken = melee(cow, p, 4f);
                assertEquals(4f * (1f + MeleeConfig.STRIKE_BONUS.get().floatValue()), taken, EPS, "50% more, once");
                assertEquals(before - MeleeConfig.STRIKE_COST.get(), malice(p), EPS, "paid once");
                assertFalse(ReinforcedStrike.isReady(p), "spent");
                assertTrue(cow.getDeltaMovement().x > 0.1, "pushed away: " + cow.getDeltaMovement());

                cow.setHealth(cow.getMaxHealth());
                assertEquals(4f, melee(cow, p, 4f), EPS, "the next hit is normal");
                assertEquals(before - MeleeConfig.STRIKE_COST.get(), malice(p), EPS, "and free");
            } finally {
                cow.discard();
            }
        });
    }

    @Test
    void withoutTheMaliceTheHitIsNormalAndTheStrikeWaits(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "strike_poor", 1000);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 8.5);
            try {
                ready(p);
                MaliceService.setCurrent(p, MeleeConfig.STRIKE_COST.get() - 1);
                assertEquals(4f, melee(cow, p, 4f), EPS, "a normal hit");
                assertEquals(MeleeConfig.STRIKE_COST.get() - 1, malice(p), EPS, "nothing paid");
                assertTrue(ReinforcedStrike.isReady(p), "the Strike isn't spent");
            } finally {
                cow.discard();
            }
        });
    }

    @Test
    void aStrikeExpires(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "strike_expiry", 1000);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 8.5);
            try {
                ready(p);
                advance(server, p, MeleeConfig.ticks(MeleeConfig.STRIKE_WINDOW.get()) - 1);
                assertTrue(ReinforcedStrike.isReady(p), "one tick left");
                advance(server, p, 1);
                assertFalse(ReinforcedStrike.isReady(p), "gone");
                assertEquals(4f, melee(cow, p, 4f), EPS, "a normal hit");
            } finally {
                cow.discard();
            }
        });
    }

    @Test
    void techniquesDontUseAStrikeAndBossesArentPushed(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "strike_kinds", 1000);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 8.5);
            WitherBoss boss = place(level, new WitherBoss(EntityType.WITHER, level), 6.5, 12.5);
            try {
                ready(p);
                cow.hurt(RimeDamage.source(level, p, p), 4f);
                assertTrue(ReinforcedStrike.isReady(p), "a technique hit isn't a melee hit");

                boss.setDeltaMovement(Vec3.ZERO);
                boss.setInvulnerableTicks(0);
                float taken = melee(boss, p, 4f);
                assertTrue(taken > 4f, "bosses take the bonus, on top of what gets past their armor: " + taken);
                assertEquals(VANILLA_KNOCKBACK, boss.getDeltaMovement().horizontalDistance(), 1e-3, "and get none of the Strike's knockback, only what any hit does");
            } finally {
                cow.discard();
                boss.discard();
            }
        });
    }

    // ---------------------------------------------------------------- Clean dodge

    /** Dashes, then stands them where the dash would have (a fake player doesn't move by itself). */
    private static void dashAside(ServerPlayer p, ServerLevel level) {
        send(p, Action.DASH);
        MeleeLayer.state(p).dashDirection = null;
        p.moveTo(p.getX(), ground(level), p.getZ() + 4, p.getYRot(), 0f);
    }

    @Test
    void aSwingAtWhereTheyWereIsACleanDodge(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "dodge_swing", 1000);
            p.moveTo(8.5, ground(level), 2.5, 0f, 0f);
            ServerPlayer attacker = player(server, "dodge_swing_attacker", 1000);
            attacker.moveTo(11.0, ground(level), 2.5, 90f, 0f);
            attacker.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
            level.addNewPlayer(attacker);
            try {
                dashAside(p, level);
                MeleeLayer.tick(p);
                assertFalse(ReinforcedStrike.isReady(p), "nothing thrown yet");
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(p);
                assertTrue(ReinforcedStrike.isReady(p), "they swung at the old spot and missed");
            } finally {
                attacker.swinging = false;
                level.removePlayerImmediately(attacker, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void aSwingElsewhereOrAtThemNowOrTooLateIsnt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer attacker = player(server, "dodge_miss_attacker", 1000);
            attacker.moveTo(11.0, ground(level), 2.5, 90f, 0f);
            level.addNewPlayer(attacker);
            try {
                // Aimed the other way.
                ServerPlayer p = player(server, "dodge_miss_wrong_way", 1000);
                p.moveTo(8.5, ground(level), 2.5, 0f, 0f);
                attacker.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(20, attacker.getEyeY(), 2.5));
                dashAside(p, level);
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(p);
                assertFalse(ReinforcedStrike.isReady(p), "not aimed at them");
                attacker.swinging = false;

                // They didn't really get out of the way: the swing reaches where they are now too.
                ServerPlayer near = player(server, "dodge_miss_near", 1000);
                near.moveTo(8.5, ground(level), 2.5, 0f, 0f);
                attacker.lookAt(EntityAnchorArgument.Anchor.EYES, near.getEyePosition());
                send(near, Action.DASH);
                MeleeLayer.state(near).dashDirection = null;
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(near);
                assertFalse(ReinforcedStrike.isReady(near), "still in the way");
                attacker.swinging = false;

                // Too late.
                ServerPlayer late = player(server, "dodge_miss_late", 1000);
                late.moveTo(8.5, ground(level), 2.5, 0f, 0f);
                attacker.lookAt(EntityAnchorArgument.Anchor.EYES, late.getEyePosition());
                dashAside(late, level);
                advance(server, late, MeleeConfig.ticks(MeleeConfig.DODGE_WINDOW.get()) + 1);
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(late);
                assertFalse(ReinforcedStrike.isReady(late), "past the window");
                attacker.swinging = false;

                // Hit in the window: not clean, whatever follows.
                ServerPlayer hit = player(server, "dodge_miss_hit", 1000);
                hit.moveTo(8.5, ground(level), 2.5, 0f, 0f);
                attacker.lookAt(EntityAnchorArgument.Anchor.EYES, hit.getEyePosition());
                dashAside(hit, level);
                melee(hit, attacker, 1f);
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(hit);
                assertFalse(ReinforcedStrike.isReady(hit), "a hit landed: not clean");
            } finally {
                attacker.swinging = false;
                level.removePlayerImmediately(attacker, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void aProjectileThroughWhereTheyWereIsACleanDodge(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "dodge_arrow", 1000);
            p.moveTo(8.5, ground(level), 2.5, 0f, 0f);
            Vec3 chest = p.position().add(0, 1, 0);
            dashAside(p, level);
            Arrow away = new Arrow(EntityType.ARROW, level);
            away.setPos(chest.add(-1.5, 0, 0));
            away.setDeltaMovement(-1.5, 0, 0); // going away from it
            level.addFreshEntity(away);
            MeleeLayer.tick(p);
            assertFalse(ReinforcedStrike.isReady(p), "flying away from the spot");
            away.discard();

            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setPos(chest.add(-1.5, 0, 0));
            arrow.setDeltaMovement(1.5, 0, 0);
            level.addFreshEntity(arrow);
            MeleeLayer.tick(p);
            assertTrue(ReinforcedStrike.isReady(p), "flying through the spot they left");
            arrow.discard();
        });
    }

    @Test
    void anAreaOverWhereTheyWereIsACleanDodgeUnlessItsTheirOwn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "dodge_area", 1000);
            ServerPlayer enemy = player(server, "dodge_area_enemy", 1000);
            p.moveTo(8.5, ground(level), 2.5, 0f, 0f);
            Vec3 old = p.position().add(0, 1, 0);
            level.addNewPlayer(p);
            try {
                dashAside(p, level);
                CleanDodge.areaWentOff(level, p, CleanDodge.sphere(old, 2.0));
                assertFalse(ReinforcedStrike.isReady(p), "your own area doesn't count");
                CleanDodge.areaWentOff(level, enemy, CleanDodge.sphere(old.add(0, 0, -9), 2.0));
                assertFalse(ReinforcedStrike.isReady(p), "not over the old spot");
                CleanDodge.areaWentOff(level, enemy, CleanDodge.sphere(old, 2.0));
                assertTrue(ReinforcedStrike.isReady(p), "the old spot, not the new one");
            } finally {
                level.removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        });
    }

    // ---------------------------------------------------------------- Severed

    private static double defaultOf(String key) {
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value = com.cnethuka.kegare.config.KegareServerConfig.SPEC.getValues().get("melee." + key);
        return ((Number) value.getDefault()).doubleValue();
    }

    /** Severed trade all their Malice for superhuman abilities, so their numbers beat standard. Read from the defaults, so a local toml can't change this. */
    @Test
    void severedDefaultsAreStrongerThanStandard() {
        assertEquals(6.0, defaultOf("severed.dashDistance"), 1e-9);
        assertEquals(2.5, defaultOf("severed.dashCooldown"), 1e-9);
        assertEquals(0.3, defaultOf("severed.perfectWindow"), 1e-9);
        assertEquals(0.5, defaultOf("severed.guardMeleeReduction"), 1e-9);
        assertEquals(0.0, defaultOf("severed.guardTechniqueReduction"), 1e-9);
        assertEquals(4.0, defaultOf("severed.guardMaxSeconds"), 1e-9);
        assertEquals(0.75, defaultOf("severed.recovery"), 1e-9);
        assertEquals(0.75, defaultOf("severed.strikeDamageBonus"), 1e-9);
        assertTrue(defaultOf("severed.dashDistance") > defaultOf("dash.distance"));
        assertTrue(defaultOf("severed.dashCooldown") < defaultOf("dash.cooldown"));
        assertEquals(defaultOf("parry.perfectWindow"), defaultOf("severed.perfectWindow"), 1e-9);
        assertTrue(defaultOf("severed.guardMeleeReduction") > defaultOf("parry.guardMeleeReduction"));
        assertTrue(defaultOf("severed.recovery") < defaultOf("parry.recovery"));
        assertTrue(defaultOf("severed.strikeDamageBonus") > defaultOf("reinforced_strike.damageBonus"));
        assertTrue(defaultOf("severed.strikeKnockback") > defaultOf("reinforced_strike.knockback"), "more knockback than standard");
    }

    @Test
    void severedPlayersGetAPhysicalVersionWithNoMalice(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "severed", 0);
            assertTrue(MaliceService.isSevered(p));
            MeleeState state = MeleeLayer.state(p);

            send(p, Action.DASH);
            assertNotNull(state.dashDirection, "dashes with no Malice");
            assertEquals(MeleeConfig.SEVERED_DASH_DISTANCE.get(), state.dashDistance, 0.01, "its own distance");
            long start = level.getGameTime();
            assertEquals(start + MeleeConfig.ticks(MeleeConfig.SEVERED_DASH_COOLDOWN.get()), state.dashReadyAt, "its own cooldown");
            MeleeLayer.reset(p);

            Zombie attacker = place(level, new Zombie(EntityType.ZOMBIE, level), 4.5, 8.5);
            Cow cow = place(level, new Cow(EntityType.COW, level), 4.5, 12.5);
            try {
                send(p, Action.PARRY_PRESS);
                assertTrue(state.isGuarding(), "guards with no Malice");
                advance(server, p, MeleeConfig.ticks(MeleeConfig.SEVERED_PERFECT_WINDOW.get()) - 1);
                assertEquals(0f, melee(p, attacker, 6f), EPS, "parries in its own window");
                assertTrue(ReinforcedStrike.isReady(p), "earns a Strike");
                assertEquals(4f * com.cnethuka.kegare.severed.SeveredConfig.MELEE_DAMAGE.get().floatValue()
                        * (1f + MeleeConfig.SEVERED_STRIKE_BONUS.get().floatValue()), melee(cow, p, 4f), EPS, "its own bonus, for free, on their Strength");

                advance(server, p, 1);
                assertEquals(6f * (1f - MeleeConfig.SEVERED_GUARD_MELEE_REDUCTION.get().floatValue())
                        * com.cnethuka.kegare.severed.SeveredConfig.TAKEN_PHYSICAL.get().floatValue(), melee(p, attacker, 6f), EPS, "its own guard, on their Toughness");
                p.setHealth(p.getMaxHealth());
                advance(server, p, MeleeConfig.ticks(MeleeConfig.SEVERED_GUARD_MAX.get()));
                assertFalse(state.isGuarding(), "no Malice to run out, so a guard has a limit of its own");
            } finally {
                attacker.discard();
                cow.discard();
            }
        });
    }

    @Test
    void theUnbornCantUseAny(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = TestServer.damageable(server, "melee_unborn_" + System.nanoTime());
            send(p, Action.PARRY_PRESS);
            send(p, Action.DASH);
            assertFalse(MeleeLayer.state(p).isGuarding());
            assertNull(MeleeLayer.state(p).dashDirection);
        });
    }
}
