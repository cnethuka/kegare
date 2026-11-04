package com.cnethuka.kegare.innate_technique.rime;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Saved per player, kept on death (attachment {@code kegare:rime_stats}): progress toward Thin Ice. */
public final class RimeStats {
    public static final Codec<RimeStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("shell_blocked", 0.0).forGetter(s -> s.shellBlocked)
    ).apply(i, RimeStats::new));

    double shellBlocked;

    public RimeStats() {
        this(0.0);
    }

    private RimeStats(double shellBlocked) {
        this.shellBlocked = shellBlocked;
    }

    public double shellBlocked() {
        return shellBlocked;
    }
}
