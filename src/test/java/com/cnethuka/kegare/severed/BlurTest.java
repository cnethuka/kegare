package com.cnethuka.kegare.severed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.severed.client.SeveredKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Blur (DESIGN §3.27): G is Blur only for the Severed; its hunger drain for the distance covered at its speed, crashing off at the
 * food floor (winded, and no Blur until food is back above it); the wall crash scaling with the speed lost; and what ends it.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class BlurTest {
    private static final double Y = 120;

    @Test
    void gIsBlurOnlyForTheSevered() {
        assertTrue(SeveredKeys.gTogglesBlur(true, true), "Severed: Blur");
        assertFalse(SeveredKeys.gTogglesBlur(true, false), "everyone else: Technique Mode, unchanged");
        assertFalse(SeveredKeys.gTogglesBlur(false, false), "not born yet: Technique Mode (which does nothing)");
    }

    /** {@code ticks} ticks of running +X at {@code speed} blocks a tick, the server checking each. */
    static void run(MinecraftServer server, ServerPlayer p, double speed, int ticks) {
        for (int i = 0; i < ticks; i++) {
            p.setPos(p.getX() + speed, p.getY(), p.getZ());
            p.horizontalCollision = false;
            Blur.tick(p, Severed.state(p));
            TestServer.advanceGameTime(server, 1);
        }
    }

    @Test
    void blurDrainsHungerByDistanceAndCrashesOffAtTheFloor(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            try {
                ServerPlayer s = SeveredBodyTest.severed(server, level, "blur_drain");
                ServerPlayer m = SeveredBodyTest.maliceUser(server, level, "blur_other");
                s.moveTo(0.5, Y, 0.5);
                Severed.toggleBlur(m);
                assertFalse(Severed.blurring(m), "a Malice user's G never reaches here as Blur");
                Severed.toggleBlur(s);
                assertTrue(Severed.blurring(s), "on");

                // Running at Blur speed burns hunger by the distance; Stamina doesn't cut it. A walk burns none of it.
                s.getFoodData().setFoodLevel(20);
                s.getFoodData().setSaturation(20f);
                s.getFoodData().setExhaustion(0f);
                double speed = SeveredConfig.BLUR_SPEED.get();
                run(server, s, speed, 1); // the first tick only sets where they were
                s.getFoodData().setExhaustion(0f);
                run(server, s, speed, 10);
                assertEquals(10 * speed * SeveredConfig.BLUR_EXHAUSTION.get(), s.getFoodData().getExhaustionLevel(), 1.0e-3, "by the distance");
                s.getFoodData().setExhaustion(0f);
                run(server, s, 0.2, 10);
                assertEquals(0f, s.getFoodData().getExhaustionLevel(), 1.0e-6, "a walk costs Blur nothing");

                // At the floor it crashes off: winded, slowed, and refused until food is above it again.
                s.getFoodData().setFoodLevel(SeveredConfig.BLUR_FOOD_FLOOR.get());
                run(server, s, speed, 1);
                assertFalse(Severed.blurring(s), "crashed off");
                assertTrue(s.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "winded");
                Severed.toggleBlur(s);
                assertFalse(Severed.blurring(s), "refused at the floor");
                s.getFoodData().setFoodLevel(SeveredConfig.BLUR_FOOD_FLOOR.get() + 1);
                Severed.toggleBlur(s);
                assertFalse(Severed.blurring(s), "still winded");
                TestServer.advanceGameTime(server, SeveredConfig.ticks(SeveredConfig.WINDED_SECONDS.get()));
                Severed.toggleBlur(s);
                assertTrue(Severed.blurring(s), "food back above the floor and no longer winded");
                Severed.toggleBlur(s);
                assertFalse(Severed.blurring(s), "G again: off");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aWallCrashScalesWithTheSpeedLost(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            try {
                double scrape = SeveredConfig.CRASH_MIN_LOSS.get() * 0.8;
                assertEquals(0f, WallCrash.damage(scrape), "a glancing scrape: nothing");
                assertEquals(0, WallCrash.stunTicks(scrape));
                assertTrue(WallCrash.damage(0.6) < WallCrash.damage(1.2) && WallCrash.damage(1.2) <= WallCrash.damage(4.0), "more speed, more damage");
                assertTrue(WallCrash.stunTicks(0.6) < WallCrash.stunTicks(1.0), "and a longer stun");
                assertEquals(SeveredConfig.CRASH_MAX_DAMAGE.get().floatValue(), WallCrash.damage(40.0), 1.0e-4, "capped");

                // Running into a wall at Blur speed: hurt (Toughness taking its share), stunned, and Blur over.
                ServerPlayer s = SeveredBodyTest.severed(server, level, "blur_crash");
                s.moveTo(0.5, Y, 0.5);
                Severed.toggleBlur(s);
                double speed = SeveredConfig.BLUR_SPEED.get();
                run(server, s, speed, 4);
                s.setPos(s.getX() + 0.05, s.getY(), s.getZ());
                s.horizontalCollision = true;
                Blur.tick(s, Severed.state(s));
                float expected = WallCrash.damage(speed - 0.05) * SeveredConfig.TAKEN_PHYSICAL.get().floatValue();
                assertEquals(s.getMaxHealth() - expected, s.getHealth(), 1.0e-3, "a head-on crash, Toughness applied");
                assertTrue(CombatHelper.isStunned(s), "stunned");
                assertFalse(Severed.blurring(s), "and out of Blur");

                // A scrape along a wall (most of the speed kept) does nothing.
                CombatHelper.clearStun(s);
                s.setHealth(s.getMaxHealth());
                TestServer.advanceGameTime(server, 20);
                Severed.toggleBlur(s);
                run(server, s, speed, 4);
                s.setPos(s.getX() + speed * 0.8, s.getY(), s.getZ());
                s.horizontalCollision = true;
                Blur.tick(s, Severed.state(s));
                assertEquals(s.getMaxHealth(), s.getHealth(), 1.0e-3, "a scrape");
                assertTrue(Severed.blurring(s), "still blurring");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aStunOrNoLongerBeingSeveredEndsIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                ServerPlayer s = SeveredBodyTest.severed(server, level, "blur_end");
                s.moveTo(0.5, Y, 0.5);
                Severed.toggleBlur(s);
                CombatHelper.stun(s, 20);
                Blur.tick(s, Severed.state(s));
                assertFalse(Severed.blurring(s), "a stun");
                CombatHelper.clearStun(s);
                Severed.toggleBlur(s);
                assertTrue(Severed.blurring(s));
                MaliceService.adminSetCapacity(s, 400);
                Blur.tick(s, Severed.state(s));
                assertFalse(Severed.blurring(s), "no longer Severed");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
