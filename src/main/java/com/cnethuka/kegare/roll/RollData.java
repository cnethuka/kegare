package com.cnethuka.kegare.roll;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Pity state (attachment {@code kegare:roll}, kept on death). Transitions live in {@link PityState}. */
public final class RollData {
    public static final Codec<RollData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("rolls_since_legendary", 0).forGetter(d -> d.state.rollsSinceLegendary()),
            Codec.INT.optionalFieldOf("total_rolls", 0).forGetter(d -> d.state.totalRolls())
    ).apply(i, (since, total) -> new RollData(new PityState(since, total))));

    private PityState state;

    public RollData() {
        this(new PityState(0, 0));
    }

    private RollData(PityState state) {
        this.state = state;
    }

    public PityState state() {
        return state;
    }

    public int rollsSinceLegendary() {
        return state.rollsSinceLegendary();
    }

    public int totalRolls() {
        return state.totalRolls();
    }

    void set(PityState state) {
        this.state = state;
    }

    void setRollsSinceLegendary(int value) {
        state = new PityState(Math.max(0, value), state.totalRolls());
    }
}
