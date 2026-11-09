package com.cnethuka.kegare.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Sub-tick timing (DESIGN §3.29): the clock estimate, mapping and clamping a stamp, and falling back to its arrival. */
@ExtendWith(EphemeralTestServerProvider.class)
class SubTickTest {
    private static final double EPS = 1.0e-6;

    /** Their clock runs 400 ms behind the server's; a 10 ms round trip. */
    private static PlayerClock synced() {
        PlayerClock clock = new PlayerClock();
        clock.sample(1000, 605, 1010);
        return clock;
    }

    @Test
    void theEstimateComesFromThePings() {
        PlayerClock clock = synced();
        assertTrue(clock.synced());
        assertEquals(400, clock.offset(), EPS, "halfway round: sent at 1000, answered at their 605, back at 1010");
        assertEquals(10, clock.ping(), EPS);
        // A slow answer: the ping is the median, and the offset comes from the quickest.
        clock.sample(2000, 1640, 2080); // 80 ms round trip, and on its face an offset of 400
        clock.sample(3000, 2606, 3012);
        assertEquals(12, clock.ping(), EPS, "median of 10, 12 and 80");
        assertEquals(400, clock.offset(), EPS, "from the 10 ms one");
    }

    @Test
    void stampsAreMappedAndClamped() {
        PlayerClock clock = synced(); // offset 400, ping 10
        double tolerance = TimingConfig.TOLERANCE_MS.get();
        assertEquals(1100, SubTick.map(clock, 700, 1105), EPS, "an honest stamp, 5 ms before it arrived");
        assertEquals(1105 - 10 - tolerance, SubTick.map(clock, 600, 1105), EPS, "claiming 105 ms early: clamped to arrival - ping - tolerance");
        assertEquals(1105, SubTick.map(clock, 800, 1105), EPS, "from the future: its arrival");
        assertEquals(1105, SubTick.map(clock, -50_000, 1105), EPS, "implausible: its arrival");
        assertEquals(1105, SubTick.map(new PlayerClock(), 700, 1105), EPS, "no estimate yet: its arrival");
    }

    @Test
    void markTakesTheStampOrFallsBack(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = TestServer.player(server, "subtick_mark");
                double now = SubTick.nowMs();
                long theirs = 50_000; // their clock reads 50 000 at the server's `now`
                SubTick.sample(p, now - 20, theirs - 10, now); // a 20 ms round trip: offset now - 50 000
                double offset = now - theirs;

                // Missing: the time it's handled.
                double before = SubTick.nowMs();
                double missing = SubTick.mark(p, TimedInput.ATTACK);
                assertTrue(missing >= before && missing <= SubTick.nowMs(), "no stamp: now");

                // An honest stamp, 6 ms before it arrived: taken as stamped.
                double arrival = SubTick.nowMs();
                long honest = Math.round(arrival - 6 - offset);
                SubTick.stamp(p, TimedInput.FISTS_ON, honest, arrival);
                double fistsOn = SubTick.mark(p, TimedInput.FISTS_ON);
                assertEquals(honest + offset, fistsOn, 1.0e-3, "mapped to the server's clock");

                // An early-claiming stamp, 200 ms before it arrived: clamped.
                arrival = SubTick.nowMs();
                SubTick.stamp(p, TimedInput.ATTACK, Math.round(arrival - 200 - offset), arrival);
                double clamped = SubTick.mark(p, TimedInput.ATTACK);
                assertEquals(arrival - 20 - TimingConfig.TOLERANCE_MS.get(), clamped, 1.0e-3, "never before arrival - ping - tolerance");

                // The gap between them, both on the server's clock.
                assertEquals(clamped - fistsOn, SubTick.gap(p, TimedInput.FISTS_ON, TimedInput.ATTACK).orElseThrow(), 1.0e-6);
                assertFalse(SubTick.gap(p, TimedInput.FISTS_ON, TimedInput.PARRY).isPresent(), "never parried: no gap");

                // A stale stamp (it arrived over half a second ago) is never matched to a later input.
                SubTick.stamp(p, TimedInput.PARRY, theirs, SubTick.nowMs() - PlayerClock.STAMP_LIFE_MS - 50);
                double stale = SubTick.mark(p, TimedInput.PARRY);
                assertTrue(SubTick.nowMs() - stale < 50, "stale: falls back to now");
                // And a stamp is used once.
                double again = SubTick.mark(p, TimedInput.FISTS_ON);
                assertTrue(SubTick.nowMs() - again < 50, "used up: now");
            } finally {
                SubTick.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
