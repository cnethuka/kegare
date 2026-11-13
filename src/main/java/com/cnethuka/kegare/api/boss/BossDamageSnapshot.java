package com.cnethuka.kegare.api.boss;

/** Raw input, final post-mitigation damage, health-clamped damage and landed hit count. */
public record BossDamageSnapshot(double raw, double postMitigation, double effective, long hits) {
    public static final BossDamageSnapshot EMPTY = new BossDamageSnapshot(0, 0, 0, 0);
}
