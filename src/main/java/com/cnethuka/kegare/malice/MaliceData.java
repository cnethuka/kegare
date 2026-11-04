package com.cnethuka.kegare.malice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player Malice state (attachment {@code kegare:malice}, kept on death).
 * <p>
 * {@code capacity}, {@code tier} and {@code severed} are fixed at birth. Only {@link MaliceService}
 * changes them (birth roll, admin debug, rebirth). {@code current} is the spendable reserve.
 */
public final class MaliceData {
    private static final Codec<MaliceTier> TIER_CODEC = Codec.STRING.xmap(MaliceTier::byName, MaliceTier::serializedName);

    public static final Codec<MaliceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("born", false).forGetter(d -> d.born),
            Codec.BOOL.optionalFieldOf("severed", false).forGetter(d -> d.severed),
            TIER_CODEC.optionalFieldOf("tier", MaliceTier.ZERO).forGetter(d -> d.tier),
            Codec.INT.optionalFieldOf("capacity", 0).forGetter(d -> d.capacity),
            Codec.DOUBLE.optionalFieldOf("current", 0.0).forGetter(d -> d.current)
    ).apply(i, MaliceData::new));

    private boolean born;
    private boolean severed;
    private MaliceTier tier;
    private int capacity;
    private double current;

    public MaliceData() {
        this(false, false, MaliceTier.ZERO, 0, 0.0);
    }

    private MaliceData(boolean born, boolean severed, MaliceTier tier, int capacity, double current) {
        this.born = born;
        this.severed = severed;
        this.tier = tier;
        this.capacity = capacity;
        this.current = current;
    }

    public boolean isBorn() { return born; }
    public boolean isSevered() { return severed; }
    public MaliceTier tier() { return tier; }
    public int capacity() { return capacity; }
    public double current() { return current; }

    // Mutators are package-private: go through MaliceService.
    void setBirth(MaliceTier tier, int capacity) {
        this.born = true;
        this.tier = tier;
        this.capacity = capacity;
        this.severed = capacity <= 0;
        this.current = capacity;
    }

    void setCurrent(double current) {
        this.current = current;
    }
}
