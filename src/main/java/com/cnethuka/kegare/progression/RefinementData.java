package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Refinement on the current technique, which a reroll resets, and Peak Refinement, which nothing does: the highest level
 * ever reached on any technique.
 */
public final class RefinementData {
    public static final Codec<RefinementData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("level", 0).forGetter(d -> d.level),
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(d -> d.xp),
            Codec.INT.optionalFieldOf("peak", 0).forGetter(d -> d.peak),
            OpponentLedger.CODEC.optionalFieldOf("hits", new OpponentLedger()).forGetter(d -> d.hits)
    ).apply(i, RefinementData::new));

    int level;
    /** Toward the next level. */
    double xp;
    int peak;
    final OpponentLedger hits;

    public RefinementData() {
        this(0, 0.0, 0, new OpponentLedger());
    }

    private RefinementData(int level, double xp, int peak, OpponentLedger hits) {
        this.level = level;
        this.xp = xp;
        this.peak = peak;
        this.hits = hits;
    }

    public int level() {
        return level;
    }

    public double xp() {
        return xp;
    }

    public int peak() {
        return peak;
    }
}
