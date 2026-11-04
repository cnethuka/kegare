package com.cnethuka.kegare.ce;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player ce state (attachment {@code kegare:ce}, kept on death).
 * <p>
 * {@code capacity}, {@code tier} and {@code severed} are fixed at birth. Only {@link CeService}
 * changes them (birth roll, admin debug, rebirth). {@code current} is the spendable reserve.
 */
public final class CeData {
    private static final Codec<CeTier> TIER_CODEC = Codec.STRING.xmap(CeTier::byName, CeTier::serializedName);

    public static final Codec<CeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("born", false).forGetter(d -> d.born),
            Codec.BOOL.optionalFieldOf("severed", false).forGetter(d -> d.severed),
            TIER_CODEC.optionalFieldOf("tier", CeTier.ZERO).forGetter(d -> d.tier),
            Codec.INT.optionalFieldOf("capacity", 0).forGetter(d -> d.capacity),
            Codec.DOUBLE.optionalFieldOf("current", 0.0).forGetter(d -> d.current)
    ).apply(i, CeData::new));

    private boolean born;
    private boolean severed;
    private CeTier tier;
    private int capacity;
    private double current;

    public CeData() {
        this(false, false, CeTier.ZERO, 0, 0.0);
    }

    private CeData(boolean born, boolean severed, CeTier tier, int capacity, double current) {
        this.born = born;
        this.severed = severed;
        this.tier = tier;
        this.capacity = capacity;
        this.current = current;
    }

    public boolean isBorn() { return born; }
    public boolean isSevered() { return severed; }
    public CeTier tier() { return tier; }
    public int capacity() { return capacity; }
    public double current() { return current; }

    // Mutators are package-private: go through CeService.
    void setBirth(CeTier tier, int capacity) {
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
