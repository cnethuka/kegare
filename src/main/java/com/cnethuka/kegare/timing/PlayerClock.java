package com.cnethuka.kegare.timing;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * One player's clock against the server's (DESIGN §3.29): the last few ping samples, the stamps that have arrived and not been used, and
 * the time of each input once the server handled it. Samples and stamps arrive on the network thread; everything else is the server's.
 */
final class PlayerClock {
    static final int SAMPLES = 8;
    /** How long a stamp waits for the input it belongs to, in ms. */
    static final double STAMP_LIFE_MS = 500;

    record Stamp(long clientMs, double arrivalMs) {}

    private final double[] rtt = new double[SAMPLES];
    private final double[] offset = new double[SAMPLES];
    private int count, next;
    private final Map<TimedInput, Stamp> pending = new EnumMap<>(TimedInput.class);
    /** Server ms of each input's latest occurrence (server thread only). */
    final Map<TimedInput, Double> latest = new EnumMap<>(TimedInput.class);
    /** The ping out now: its id and when it was sent. */
    int pingId;
    double pingSentMs = Double.NaN;
    double nextPingMs;
    int pingsSent;

    /** A ping's answer: {@code sentMs} on the server's clock, {@code clientMs} on theirs as they answered, {@code arrivalMs} back on the server's. */
    synchronized void sample(double sentMs, long clientMs, double arrivalMs) {
        double r = Math.max(0, arrivalMs - sentMs);
        rtt[next] = r;
        offset[next] = sentMs + r / 2.0 - clientMs; // the answer came halfway round
        next = (next + 1) % SAMPLES;
        count = Math.min(SAMPLES, count + 1);
    }

    synchronized boolean synced() {
        return count > 0;
    }

    /** Their clock to the server's: from the sample with the shortest round trip, the least held up on the way. */
    synchronized double offset() {
        int best = 0;
        for (int i = 1; i < count; i++) if (rtt[i] < rtt[best]) best = i;
        return offset[best];
    }

    /** The round trip, ms: the median of the samples, so one slow answer doesn't widen what a stamp may claim. */
    synchronized double ping() {
        if (count == 0) return 0;
        double[] sorted = Arrays.copyOf(rtt, count);
        Arrays.sort(sorted);
        return count % 2 == 1 ? sorted[count / 2] : (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0;
    }

    synchronized void arrived(TimedInput input, long clientMs, double arrivalMs) {
        pending.put(input, new Stamp(clientMs, arrivalMs));
    }

    /** The stamp for this input, if one arrived lately and before now; used up either way. */
    synchronized Stamp take(TimedInput input, double nowMs) {
        Stamp stamp = pending.remove(input);
        if (stamp == null || stamp.arrivalMs() > nowMs + 1.0e-6 || nowMs - stamp.arrivalMs() > STAMP_LIFE_MS) return null;
        return stamp;
    }
}
