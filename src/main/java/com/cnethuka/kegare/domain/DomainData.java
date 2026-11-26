package com.cnethuka.kegare.domain;

import com.mojang.serialization.Codec;

/** A player's Domain Meter, 0 to 100 (attachment {@code kegare:domain}): saved, kept on death, private to its owner. */
public final class DomainData {
    public static final Codec<DomainData> CODEC = Codec.DOUBLE.xmap(DomainData::new, d -> d.meter);
    public static final double MAX = 100.0;

    double meter;
    /** Runtime only: the whole number last sent to the owner (-1: nothing sent yet), and whether it was shown. */
    int sent = -1;
    boolean sentShown;

    public DomainData() {
        this(0);
    }

    private DomainData(double meter) {
        this.meter = Math.max(0, Math.min(MAX, meter));
    }

    public double meter() {
        return meter;
    }
}
