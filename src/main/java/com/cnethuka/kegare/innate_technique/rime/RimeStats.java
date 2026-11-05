package com.cnethuka.kegare.innate_technique.rime;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Saved per player, kept on death (attachment {@code kegare:rime_stats}): progress toward Thin Ice (damage the shell
 * blocked) and Slip 'n Slide (blocks slid with Ice Path).
 */
public final class RimeStats {
    public static final Codec<RimeStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("shell_blocked", 0.0).forGetter(s -> s.shellBlocked),
            Codec.DOUBLE.optionalFieldOf("ice_path_distance", 0.0).forGetter(s -> s.icePathDistance)
    ).apply(i, RimeStats::new));

    double shellBlocked;
    double icePathDistance;

    public RimeStats() {
        this(0.0, 0.0);
    }

    private RimeStats(double shellBlocked, double icePathDistance) {
        this.shellBlocked = shellBlocked;
        this.icePathDistance = icePathDistance;
    }

    public double shellBlocked() {
        return shellBlocked;
    }

    public double icePathDistance() {
        return icePathDistance;
    }
}
