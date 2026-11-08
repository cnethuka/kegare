package com.cnethuka.kegare.innate_technique.time_manipulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Time Manipulation's tab (DESIGN §3.26): every advancement loads hidden and silent, only a holder earns them, and each is earned
 * the way it says; the mastery ones need a player.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class TimeAdvancementsTest {
    private static final double Y = 120;
    private static final List<ResourceLocation> ALL = List.of(TimeAdvancements.THE_WORLD_STOOD_STILL, TimeAdvancements.OVERCLOCK,
            TimeAdvancements.SECOND_CHANCE, TimeAdvancements.SLOW_MOTION, TimeAdvancements.NOT_SO_FAST, TimeAdvancements.FROZEN_IN_FLIGHT,
            TimeAdvancements.SAME_CLOCK, TimeAdvancements.PRE_PLANNED);

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, boolean time) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(2000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, time ? TimeManipulation.TIME_MANIPULATION.get() : Swap.SWAP.get());
        p.setHealth(p.getMaxHealth());
        MaliceService.refill(p);
        return p;
    }

    private static boolean has(MinecraftServer server, ServerPlayer p, ResourceLocation id) {
        return TestServer.hasAdvancement(server, p, id);
    }

    @Test
    void everyOneIsHiddenAndSilent(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (ResourceLocation id : ALL) {
                AdvancementHolder holder = server.getAdvancements().get(id);
                assertNotNull(holder, id + " loads");
                DisplayInfo display = holder.value().display().orElseThrow();
                assertTrue(display.isHidden(), id + " is hidden");
                assertFalse(display.shouldAnnounceChat(), id + " is silent");
            }
        });
    }

    @Test
    void eachIsEarnedTheWayItSays(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            server.setPvpAllowed(true);
            Pig pig = EntityType.PIG.create(level);
            try {
                ServerPlayer caster = player(server, level, "tm_adv_caster", true);
                ServerPlayer other = player(server, level, "tm_adv_other", false);
                ServerPlayer clock = player(server, level, "tm_adv_clock", true);
                caster.moveTo(0.5, Y, 0.5, -90f, 0f);
                caster.setYHeadRot(-90f);
                other.moveTo(6.5, Y, 0.5, 90f, 0f);
                clock.moveTo(3.5, Y, 6.5, 0f, 0f);

                // Only a holder earns them.
                TimeAdvancements.award(other, TimeAdvancements.OVERCLOCK);
                assertFalse(has(server, other, TimeAdvancements.OVERCLOCK), "not a holder");

                // Five of the caster's projectiles in the air when time stops (for Pre-Planned below).
                Snowball[] thrown = new Snowball[5];
                for (int i = 0; i < 5; i++) {
                    thrown[i] = new Snowball(level, caster);
                    thrown[i].setPos(4.5, Y + 1, 0.5 + i * 0.2);
                    level.addFreshEntity(thrown[i]);
                }

                // The World Stood Still, and Same Clock for the other Time user inside it.
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                assertTrue(has(server, caster, TimeAdvancements.THE_WORLD_STOOD_STILL), "The World Stood Still");
                assertTrue(has(server, clock, TimeAdvancements.SAME_CLOCK), "Same Clock");
                assertFalse(has(server, caster, TimeAdvancements.SAME_CLOCK), "not in your own");

                // Pre-Planned: those five hung in it, then hit players once time runs again.
                TestServer.advanceGameTime(server, 1);
                TimeStops.tick(level); // they hang: noted
                TestServer.advanceGameTime(server, TimeConfig.ticks(TimeConfig.STANDSTILL_SECONDS));
                TimeStops.tick(level); // time resumes
                for (int i = 0; i < 4; i++) NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(thrown[i], new EntityHitResult(other)));
                assertFalse(has(server, caster, TimeAdvancements.PRE_PLANNED), "four isn't enough");
                NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(thrown[4], new EntityHitResult(other)));
                assertTrue(has(server, caster, TimeAdvancements.PRE_PLANNED), "Pre-Planned");
                for (Snowball s : thrown) s.discard();
                com.cnethuka.kegare.innatetechnique.Burnout.clear(caster);

                // Overclock: Accelerate on for 30 seconds straight.
                InnateTechniqueService.clearCooldowns(caster);
                InnateTechniqueService.select(caster, 1);
                CastManager.handleIntent(caster, CastIntent.CAST_PRESS);
                assertTrue(TimeManipulation.ACCELERATE.isActive(caster), "setup: on");
                TestServer.advanceGameTime(server, (int) TimeAdvancements.OVERCLOCK_TICKS);
                MaliceService.refill(caster);
                CastManager.tick(caster);
                assertTrue(has(server, caster, TimeAdvancements.OVERCLOCK), "Overclock");
                CastManager.handleIntent(caster, CastIntent.CAST_PRESS); // off again

                // Slow Motion: Decelerate on a player with an ability on cooldown (a mob doesn't count).
                DecelerateEntity hand = new DecelerateEntity(level, caster);
                pig.moveTo(3.5, Y, -3.5, 0f, 0f);
                pig.setNoGravity(true);
                level.addFreshEntity(pig);
                DecelerateEntity.strike(hand, caster, pig, 1f);
                assertFalse(has(server, caster, TimeAdvancements.SLOW_MOTION), "a mob doesn't count");
                DecelerateEntity.strike(hand, caster, other, 1f);
                assertFalse(has(server, caster, TimeAdvancements.SLOW_MOTION), "nothing on cooldown");
                InnateTechniqueService.startCooldown(other, Swap.POSITIONS);
                other.invulnerableTime = 0;
                DecelerateEntity.strike(hand, caster, other, 1f);
                assertTrue(has(server, caster, TimeAdvancements.SLOW_MOTION), "Slow Motion");
                CooldownRates.clear();
                other.removeAllEffects();

                // Not So Fast: Rewind a player who'd got 8 blocks further away in those 3 seconds.
                int ticks = TimeConfig.ticks(TimeConfig.REWIND_SECONDS);
                for (int i = 0; i < ticks + 3; i++) {
                    other.moveTo(3.5 + i * (9.0 / ticks), Y, 0.5, 90f, 0f);
                    PositionHistory.tick(server);
                    TestServer.advanceGameTime(server, 1);
                }
                InnateTechniqueService.clearCooldowns(caster);
                InnateTechniqueService.select(caster, 0);
                Vec3 eye = caster.getEyePosition(), at = other.position().add(0, 1, 0), d = at.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                caster.moveTo(caster.getX(), caster.getY(), caster.getZ(), yaw, (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
                caster.setYHeadRot(yaw);
                MaliceService.refill(caster);
                CastManager.handleIntent(caster, CastIntent.SPECIAL_PRESS);
                assertTrue(has(server, caster, TimeAdvancements.NOT_SO_FAST), "Not So Fast");

                // Second Chance: the return under 3 hearts, and alive 10 seconds later.
                PointMarks.mark(caster, caster.blockPosition().below(), caster.position());
                caster.setHealth(4f);
                PointMakerAbility.returnTo(caster, PointMarks.get(caster).orElseThrow());
                TestServer.advanceGameTime(server, (int) TimeAdvancements.SECOND_CHANCE_SURVIVE_TICKS - 1);
                TimeAdvancements.tick(caster);
                assertFalse(has(server, caster, TimeAdvancements.SECOND_CHANCE), "not yet");
                TestServer.advanceGameTime(server, 1);
                TimeAdvancements.tick(caster);
                assertTrue(has(server, caster, TimeAdvancements.SECOND_CHANCE), "Second Chance");

                // Frozen in Flight: five projectiles of another player caught in one field (the caster's own don't count).
                StasisFields.open(caster, new Vec3(3.5, Y, -6.5), 4.0, 100);
                Snowball[] caught = new Snowball[6];
                for (int i = 0; i < 6; i++) {
                    caught[i] = new Snowball(level, i == 0 ? caster : other);
                    caught[i].setPos(3.5, Y + 0.5, -6.5 + i * 0.3);
                    caught[i].setNoGravity(true);
                    caught[i].setDeltaMovement(0.5, 0, 0);
                    level.addFreshEntity(caught[i]);
                    if (i == 4) {
                        StasisFields.tick(level);
                        assertFalse(has(server, caster, TimeAdvancements.FROZEN_IN_FLIGHT), "four of theirs and one of yours");
                    }
                }
                StasisFields.tick(level);
                assertTrue(has(server, caster, TimeAdvancements.FROZEN_IN_FLIGHT), "Frozen in Flight");
                for (Entity e : caught) e.discard();
            } finally {
                pig.discard();
                TimeStops.clear();
                StasisFields.clear();
                PointMarks.clearAll();
                PositionHistory.clear();
                CooldownRates.clear();
                TimeAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
