package com.cnethuka.kegare.melee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeIntentPayload.Action;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.timing.SubTick;
import com.cnethuka.kegare.timing.TimedInput;
import com.cnethuka.kegare.timing.TimingConfig;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Parry's perfect window by ticks and by sub-tick timing (DESIGN §3.29), on hits with real latencies stamped in. Ticks stay the rule:
 * sub-tick timing is better at the window's far edge and worse when the attacker's lag gave the defender time, so it stays off.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ParryTimingTest {
    private static ServerPlayer player(MinecraftServer server, String name, double x, float yaw) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), 8.5, yaw, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    /** A clock with no offset and this round trip, as the pings would leave it. */
    private static void clock(ServerPlayer p, double rtt) {
        double at = SubTick.nowMs() - 2000;
        SubTick.sample(p, at - rtt, Math.round(at - rtt / 2), at);
    }

    private static void press(ServerPlayer p, double pressedAt, double arrivedAt) {
        SubTick.stamp(p, TimedInput.PARRY, Math.round(pressedAt), arrivedAt);
        MeleeIntentPayload.apply(p, new MeleeIntentPayload(Action.PARRY_PRESS, (byte) 0, (byte) 0));
        assertTrue(MeleeLayer.state(p).guarding, "setup: guarding");
    }

    private static void swing(ServerPlayer attacker, ServerPlayer defender, double swungAt, double arrivedAt) {
        SubTick.stamp(attacker, TimedInput.ATTACK, Math.round(swungAt), arrivedAt);
        defender.invulnerableTime = 0;
        attacker.attack(defender);
    }

    @Test
    void ticksStayTheRuleAndSubTickIsLoggedBesideIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            server.setPvpAllowed(true);
            try {
                ServerPlayer defender = player(server, "parry_t_def", 2.5, -90f);
                ServerPlayer attacker = player(server, "parry_t_att", 4.0, 90f);
                clock(defender, 20);  // 10 ms each way
                clock(attacker, 300); // 150 ms each way

                // The attacker's lag gave the defender time: they pressed 50 ms after the swing, and it still reached the server first.
                double now = SubTick.nowMs();
                double swungAt = now - 150, pressedAt = swungAt + 50;
                press(defender, pressedAt, pressedAt + 10);
                assertEquals(pressedAt, MeleeLayer.state(defender).guardStartMs, 1.0, "the press, on the server's clock");
                float health = defender.getHealth();
                swing(attacker, defender, swungAt, now);
                assertEquals(health, defender.getHealth(), 1.0e-4, "by ticks: perfect (both reached the server in the same tick)");
                assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "parried: staggered");
                double gap = SubTick.time(attacker, TimedInput.ATTACK).orElseThrow() - MeleeLayer.state(defender).guardStartMs;
                assertEquals(-50, gap, 1.0, "the swing came 50 ms before the press");
                assertFalse(Parry.subTickPerfect(gap, MeleeNumbers.of(defender).perfectTicks() * 50.0), "sub-tick: too late, so it's worse here");
            } finally {
                SubTick.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void atTheWindowsFarEdgeSubTickIsKinderAndTheSwitchUsesIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            server.setPvpAllowed(true);
            boolean was = TimingConfig.PARRY_SUB_TICK.get();
            try {
                ServerPlayer defender = player(server, "parry_e_def", 2.5, -90f);
                ServerPlayer attacker = player(server, "parry_e_att", 4.0, 90f);
                clock(defender, 20);
                clock(attacker, 20);
                int window = MeleeNumbers.of(defender).perfectTicks();

                // Pressed 290 ms before the swing (inside the 300), but the hit is handled six ticks after the press: out by ticks.
                double now = SubTick.nowMs();
                press(defender, now - 290, now - 280);
                for (int i = 0; i < window; i++) {
                    TestServer.advanceGameTime(server, 1);
                    MeleeLayer.tick(defender);
                }
                float health = defender.getHealth();
                swing(attacker, defender, now, SubTick.nowMs());
                assertTrue(defender.getHealth() < health, "by ticks: not perfect, only guarded");
                double gap = SubTick.time(attacker, TimedInput.ATTACK).orElseThrow() - MeleeLayer.state(defender).guardStartMs;
                assertEquals(290, gap, 1.0);
                assertTrue(Parry.subTickPerfect(gap, window * 50.0), "sub-tick: inside the window");

                // With the switch on, the same timing parries.
                TimingConfig.PARRY_SUB_TICK.set(true);
                MeleeIntentPayload.apply(defender, new MeleeIntentPayload(Action.PARRY_RELEASE, (byte) 0, (byte) 0));
                MeleeLayer.state(defender).recoveryUntil = 0;
                now = SubTick.nowMs();
                press(defender, now - 290, now - 280);
                for (int i = 0; i < window; i++) {
                    TestServer.advanceGameTime(server, 1);
                    MeleeLayer.tick(defender);
                }
                defender.setHealth(defender.getMaxHealth());
                swing(attacker, defender, now, SubTick.nowMs());
                assertEquals(defender.getMaxHealth(), defender.getHealth(), 1.0e-4, "parrySubTick on: perfect");
            } finally {
                TimingConfig.PARRY_SUB_TICK.set(was);
                SubTick.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
