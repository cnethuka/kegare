package com.cnethuka.kegare.dominion;

import com.mojang.serialization.Codec;

/** A player's Dominion Meter, 0 to 100 (attachment {@code kegare:dominion}): saved, kept on death, private to its owner. */
public final class DominionData {
    public static final Codec<DominionData> CODEC = Codec.DOUBLE.xmap(DominionData::new, d -> d.meter);
    public static final double MAX = 100.0;

    double meter;
    /** Runtime only: the whole number last sent to the owner (-1: nothing sent yet), and whether it was shown. */
    int sent = -1;
    boolean sentShown;

    public DominionData() {
        this(0);
    }

    private DominionData(double meter) {
        this.meter = Math.max(0, Math.min(MAX, meter));
    }

    public double meter() {
        return meter;
    }
}
