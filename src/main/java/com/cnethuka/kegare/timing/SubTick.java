package com.cnethuka.kegare.timing;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Sub-tick timing (DESIGN §3.29): when an input really happened, finer than the 50 ms tick the server handles it in.
 * <p>
 * The client stamps the inputs that need it ({@link TimedInput}) with its own monotonic clock in ms, in a {@link TimingNet.Stamp} sent just
 * before the packet that does the thing. The server keeps an estimate of each player's clock offset and ping ({@link PlayerClock}, from a
 * ping every {@code clockSyncSeconds} answered on the network thread), maps a stamp to server time and clamps it between
 * (arrival - ping - toleranceMs) and arrival, so a modified client can't claim to be earlier than it could have been. With no stamp, or one
 * further than {@code plausibleMs} from its arrival, or no clock estimate yet, the arrival time stands: never worse than the tick.
 * <p>
 * Server time here is {@link #nowMs}: the server's monotonic clock in ms. For later systems: {@link #time} and {@link #gap}.
 */
public final class SubTick {
    private static final Map<UUID, PlayerClock> CLOCKS = new ConcurrentHashMap<>();
    /** The clock of a player by id, if there is one (for the network thread, which has no player to make one for). */
    static final java.util.function.Function<UUID, PlayerClock> CLOCKS_VIEW = CLOCKS::get;

    private SubTick() {}

    /** The server's monotonic clock, in ms. */
    public static double nowMs() {
        return System.nanoTime() / 1.0e6;
    }

    static PlayerClock clock(Player player) {
        return CLOCKS.computeIfAbsent(player.getUUID(), id -> new PlayerClock());
    }

    /** When this player's latest {@code input} happened, on {@link #nowMs}'s clock; empty if it never has. */
    public static OptionalDouble time(Player player, TimedInput input) {
        PlayerClock clock = CLOCKS.get(player.getUUID());
        Double at = clock == null ? null : clock.latest.get(input);
        return at == null ? OptionalDouble.empty() : OptionalDouble.of(at);
    }

    /** From this player's latest {@code a} to their latest {@code b}, in ms (negative if {@code b} came first); empty if either is missing. */
    public static OptionalDouble gap(Player player, TimedInput a, TimedInput b) {
        OptionalDouble from = time(player, a), to = time(player, b);
        return from.isPresent() && to.isPresent() ? OptionalDouble.of(to.getAsDouble() - from.getAsDouble()) : OptionalDouble.empty();
    }

    /**
     * The server is handling {@code input} now: takes its stamp, if one came, and records when it happened. Returns that time.
     * Call it on the server thread, where the input is handled.
     */
    public static double mark(ServerPlayer player, TimedInput input) {
        double now = nowMs();
        PlayerClock clock = clock(player);
        PlayerClock.Stamp stamp = clock.take(input, now);
        double at = stamp == null ? now : map(clock, stamp.clientMs(), stamp.arrivalMs());
        clock.latest.put(input, at);
        return at;
    }

    /**
     * A stamp on the server's clock: their clock plus the offset, clamped between (arrival - ping - toleranceMs) and arrival. Its arrival
     * time if it isn't to be believed (no estimate yet, or further than {@code plausibleMs} from its arrival either way).
     */
    static double map(PlayerClock clock, long clientMs, double arrivalMs) {
        if (!clock.synced()) return arrivalMs;
        double at = clientMs + clock.offset();
        if (Math.abs(at - arrivalMs) > TimingConfig.PLAUSIBLE_MS.get()) return arrivalMs;
        double earliest = arrivalMs - clock.ping() - TimingConfig.TOLERANCE_MS.get();
        return Math.max(earliest, Math.min(arrivalMs, at));
    }

    /** A stamp arrived (network thread). */
    static void arrived(UUID player, TimedInput input, long clientMs, double arrivalMs) {
        CLOCKS.computeIfAbsent(player, id -> new PlayerClock()).arrived(input, clientMs, arrivalMs);
    }

    /** The clock estimate for this player: offset (their clock to the server's) and ping, ms. For the debug log. */
    public static String describe(Player player) {
        PlayerClock clock = CLOCKS.get(player.getUUID());
        if (clock == null || !clock.synced()) return "no clock yet";
        return String.format(java.util.Locale.ROOT, "ping %.1f ms", clock.ping());
        // (the offset is between two monotonic clocks, so its value means nothing on its own)
    }

    /** What the network thread does with a ping's answer, by hand (tests, and a check of the clamp without a client). */
    public static void sample(Player player, double sentMs, long clientMs, double arrivalMs) {
        clock(player).sample(sentMs, clientMs, arrivalMs);
    }

    /** What the network thread does with a stamp, by hand (tests). */
    public static void stamp(Player player, TimedInput input, long clientMs, double arrivalMs) {
        arrived(player.getUUID(), input, clientMs, arrivalMs);
    }

    static void forget(UUID player) {
        CLOCKS.remove(player);
    }

    public static void clear() {
        CLOCKS.clear();
    }
}
