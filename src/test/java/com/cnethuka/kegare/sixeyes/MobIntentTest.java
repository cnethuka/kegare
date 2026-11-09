package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cnethuka.kegare.TestServer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Six Eyes' reads of a mob's AI (DESIGN §3.32), each on the mob's own real AI, ticked by hand: the pulse must come before the attack it
 * reads, never with it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class MobIntentTest {
    private static final int FLOOR_Y = 119;
    private static final int FLOOR = 14;

    /** Everything a case needs: a bearer off to the side, a target that survives anything, a floor, a difficulty where mobs attack. */
    private interface Case {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer target);
    }

    private static void scene(MinecraftServer server, Case c) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            Difficulty before = level.getDifficulty();
            server.setDifficulty(Difficulty.NORMAL, true);
            for (int x = -FLOOR; x <= FLOOR; x++) {
                for (int z = -FLOOR; z <= FLOOR; z++) level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
            }
            ServerPlayer bearer = SightTest.player(server, "intent_eyes", 1000, -12.5, -12.5);
            ServerPlayer target = SightTest.player(server, "intent_target", 1000, 0.5, 0.5);
            try {
                SightTest.eyes(server, bearer, true);
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100_000, 4, false, false));
                target.setDeltaMovement(0, 0, 0);
                c.run(server, level, target);
            } finally {
                Sight.sentHook = null;
                MobIntent.clear();
                for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-40, 100, -40, 40, 160, 40), e -> !(e instanceof ServerPlayer))) e.discard();
                for (int x = -FLOOR; x <= FLOOR; x++) {
                    for (int z = -FLOOR; z <= FLOOR; z++) level.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.AIR.defaultBlockState());
                }
                server.setDifficulty(before, true);
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double z) {
        T mob = type.create(level);
        mob.moveTo(x, FLOOR_Y + 1, z, 0, 0);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    /** Runs the mob's AI until it acts; the pulse of {@code kind} must have come at least a tick before. Returns how many ticks before the latest. */
    private static int pulsedBefore(MinecraftServer server, ServerLevel level, Mob mob, int kind, BooleanSupplier acted, int maxTicks) {
        List<SightTest.Sent> sent = SightTest.capture();
        int pulsed = -1, seen = 0;
        for (int t = 0; t < maxTicks; t++) {
            TestServer.advanceGameTime(server, 1);
            mob.tickCount++;
            mob.tick();
            boolean did = acted.getAsBoolean();
            MobIntent.check(level, mob);
            int count = (int) sent.stream().filter(s -> s.payload() instanceof SightPayloads.Pulse p && p.entityId() == mob.getId() && p.kind() == kind).count();
            if (count > seen) pulsed = t; // the latest: vanilla can pause a timer (a shulker's look goal), and then it pulses again
            seen = count;
            if (did) {
                assertTrue(pulsed >= 0 && pulsed < t, mob.getType().toShortString() + " acted at tick " + t + " with its pulse at " + pulsed);
                return t - pulsed;
            }
        }
        return fail(mob.getType().toShortString() + " never acted in " + maxTicks + " ticks");
    }

    /** A melee mob's first swing as it closes in, then the swing after it. */
    private static void swings(MinecraftServer server, ServerLevel level, Mob mob, int maxTicks) {
        int first = pulsedBefore(server, level, mob, SightPayloads.Pulse.SWING, () -> mob.swinging, maxTicks);
        assertTrue(first >= 1, "the first swing is read as it closes in");
        // Hoglins never clear this themselves: reset it so the next swing shows.
        mob.swinging = false;
        mob.swingTime = 0;
        int second = pulsedBefore(server, level, mob, SightPayloads.Pulse.SWING, () -> mob.swinging, maxTicks);
        assertTrue(second >= 1, "and the swing after it");
    }

    private static boolean any(ServerLevel level, Class<? extends Entity> type) {
        return !level.getEntitiesOfClass(type, new AABB(-40, 100, -40, 40, 160, 40)).isEmpty();
    }

    @Test
    void theFirstSwingOfTheVanillaMeleeGoalIsReadAsItClosesIn(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Zombie zombie = spawn(level, EntityType.ZOMBIE, 5.5, 0.5);
            zombie.setTarget(target);
            swings(s, level, zombie, 200);
        });
    }

    @Test
    void brainDrivenMeleeIsRead(MinecraftServer server) throws Exception {
        for (EntityType<? extends Mob> type : List.of(EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.PIGLIN_BRUTE, EntityType.WARDEN)) {
            scene(server, (s, level, target) -> {
                Mob mob = spawn(level, type, 3.5, 0.5);
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                if (mob instanceof Warden warden) {
                    warden.increaseAngerAt(target, AngerLevel.ANGRY.getMinimumAnger() + 40, false); // (it gives up if not angry)
                    warden.getBrain().setMemoryWithExpiry(MemoryModuleType.SONIC_BOOM_COOLDOWN, Unit.INSTANCE, 100_000); // (only its melee here: a boom goes through anything)
                    warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 100_000); // (what spawning it properly sets: else it digs itself away)
                }
                assertEquals(target, mob.getTarget(), "setup: its brain has its target");
                swings(s, level, mob, 300);
            });
        }
    }

    @Test
    void aWitchsPotionIsReadBeforeItsThrown(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Mob witch = spawn(level, EntityType.WITCH, 6.5, 0.5);
            witch.setTarget(target);
            int lead = pulsedBefore(s, level, witch, SightPayloads.Pulse.THROW, () -> any(level, ThrownPotion.class), 200);
            assertEquals(MobIntent.SHOT_LEAD, lead, "the timer's read exactly");
        });
    }

    @Test
    void aDrownedsTridentIsReadBeforeItsThrown(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Mob drowned = spawn(level, EntityType.DROWNED, 6.5, 0.5);
            drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            drowned.setTarget(target);
            int lead = pulsedBefore(s, level, drowned, SightPayloads.Pulse.THROW, () -> any(level, ThrownTrident.class), 200);
            assertEquals(MobIntent.SHOT_LEAD, lead, "the timer's read exactly");
        });
    }

    @Test
    void aShulkersBulletIsReadBeforeItsFired(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Mob shulker = spawn(level, EntityType.SHULKER, 6.5, 0.5);
            shulker.setTarget(target);
            int lead = pulsedBefore(s, level, shulker, SightPayloads.Pulse.BULLET, () -> any(level, ShulkerBullet.class), 400);
            assertEquals(MobIntent.SHOT_LEAD, lead, "the timer's read exactly");
        });
    }

    @Test
    void aBreezesWindChargeIsReadFromItsShootingPose(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Mob breeze = spawn(level, EntityType.BREEZE, 6.5, 0.5);
            breeze.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            List<SightTest.Sent> sent = SightTest.capture();
            MobIntent.check(level, breeze);
            assertTrue(pulses(sent, breeze).isEmpty(), "a real read now, not the fallback's lock");
            int lead = pulsedBefore(s, level, breeze, SightPayloads.Pulse.CHARGE, () -> any(level, BreezeWindCharge.class), 600);
            assertTrue(lead >= MobIntent.SWING_LEAD, "it breathes in first, and that's the warning: " + lead);
        });
    }

    @Test
    void theWithersSkullsAreReadFromEveryHeadAndItIsNotMoved(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            Mob wither = spawn(level, EntityType.WITHER, 8.5, 0.5);
            wither.setTarget(target);
            int lead = pulsedBefore(s, level, wither, SightPayloads.Pulse.SKULL, () -> any(level, WitherSkull.class), 200);
            assertTrue(lead >= 1, "before the first skull");
            // The reading itself never moves or disables it (a boss): a check changes nothing about it.
            var pos = wither.position();
            var motion = wither.getDeltaMovement();
            MobIntent.check(level, wither);
            assertEquals(pos, wither.position());
            assertEquals(motion, wither.getDeltaMovement());
            assertFalse(wither.isNoAi());
        });
    }

    @Test
    void aMobWithAiThereIsNothingToReadInFallsBackOnWhatItShows(MinecraftServer server) throws Exception {
        scene(server, (s, level, target) -> {
            // A zombie stripped of its goals looks to the eyes like a modded mob's own AI.
            Zombie custom = spawn(level, EntityType.ZOMBIE, 8.5, 0.5);
            custom.goalSelector.removeAllGoals(g -> true);
            custom.targetSelector.removeAllGoals(g -> true);
            List<SightTest.Sent> sent = SightTest.capture();
            MobIntent.check(level, custom);
            assertTrue(pulses(sent, custom).isEmpty(), "nothing with no target");

            custom.setTarget(target);
            MobIntent.check(level, custom);
            assertEquals(List.of(SightPayloads.Pulse.LOCK), pulses(sent, custom), "locked on");
            sent.clear();
            MobIntent.check(level, custom);
            assertTrue(pulses(sent, custom).isEmpty(), "once");

            custom.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            custom.startUsingItem(InteractionHand.MAIN_HAND);
            MobIntent.check(level, custom);
            assertEquals(List.of(SightPayloads.Pulse.LOOSE), pulses(sent, custom), "using an item");
            custom.stopUsingItem();
            sent.clear();

            custom.moveTo(1.5, FLOOR_Y + 1, 0.5, 0, 0);
            MobIntent.check(level, custom);
            assertEquals(List.of(SightPayloads.Pulse.SWING), pulses(sent, custom), "its target in reach");
            sent.clear();

            // A hit: not read again until it's had time to come round.
            custom.setLastHurtMob(target);
            MobIntent.check(level, custom);
            assertTrue(pulses(sent, custom).isEmpty(), "just hit");
            custom.tickCount += MobIntent.GUESS_REST;
            MobIntent.check(level, custom);
            assertEquals(List.of(SightPayloads.Pulse.SWING), pulses(sent, custom), "and again, with no timer to go by");
            sent.clear();

            // Only something hostile gets the guess.
            Cow cow = spawn(level, EntityType.COW, -1.5, 0.5);
            cow.setTarget(target);
            MobIntent.check(level, cow);
            assertTrue(pulses(sent, cow).isEmpty(), "a cow's flow is faint");
        });
    }

    private static List<Integer> pulses(List<SightTest.Sent> sent, Mob mob) {
        List<Integer> kinds = new ArrayList<>();
        for (SightTest.Sent s : sent) if (s.payload() instanceof SightPayloads.Pulse p && p.entityId() == mob.getId()) kinds.add(p.kind());
        return kinds;
    }
}
