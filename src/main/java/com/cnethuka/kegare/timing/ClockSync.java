package com.cnethuka.kegare.timing;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps each player's clock estimate fresh (DESIGN §3.29): a ping every {@code clockSyncSeconds}, and a few quickly after they join so
 * there's an estimate within a second or two. The answer comes back on the network thread ({@link #answered}).
 */
final class ClockSync {
    /** The first few pings, this far apart (ms). */
    private static final double FIRST_PINGS_MS = 200;
    private static final int FIRST_PINGS = 5;

    private ClockSync() {}

    /** Every server tick, per player. */
    static void tick(ServerPlayer player) {
        PlayerClock clock = SubTick.clock(player);
        double now = SubTick.nowMs();
        if (now < clock.nextPingMs) return;
        synchronized (clock) {
            clock.pingId++;
            clock.pingSentMs = now;
            clock.pingsSent++;
            clock.nextPingMs = now + (clock.pingsSent < FIRST_PINGS ? FIRST_PINGS_MS : TimingConfig.SYNC_SECONDS.get() * 1000.0);
        }
        PacketDistributor.sendToPlayer(player, new TimingNet.Ping(clock.pingId));
    }

    /** A ping's answer arrived (network thread). An answer to an older ping than the last one sent is dropped. */
    static void answered(UUID player, int id, long clientMs, double arrivalMs) {
        PlayerClock clock = SubTick.CLOCKS_VIEW.apply(player);
        if (clock == null) return;
        double sent;
        synchronized (clock) {
            if (id != clock.pingId || Double.isNaN(clock.pingSentMs)) return;
            sent = clock.pingSentMs;
            clock.pingSentMs = Double.NaN;
        }
        clock.sample(sent, clientMs, arrivalMs);
    }
}
