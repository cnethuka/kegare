package com.cnethuka.kegare.sixeyes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A bearer's own state (attachment {@code kegare:six_eyes}, saved, kept on death): Attunement, Strain (0 to 100) and the collapse risk.
 * All of it is wiped when the eyes pass on or are lost: a new bearer starts out clumsy.
 */
public final class SixEyesData {
    public static final Codec<SixEyesData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("attunement", 0.0).forGetter(d -> d.attunement),
            Codec.DOUBLE.optionalFieldOf("strain", 0.0).forGetter(d -> d.strain),
            Codec.DOUBLE.optionalFieldOf("risk", 0.0).forGetter(d -> d.risk),
            Codec.BOOL.optionalFieldOf("edge_lost", false).forGetter(d -> d.edgeLost)
    ).apply(i, SixEyesData::new));

    double attunement;
    double strain;
    /** Chance per second of collapsing, while uncovered at the top. */
    double risk;
    /** At the top, the uncovered edge goes until Strain cools below {@code edgeBackBelow}. */
    boolean edgeLost;

    public SixEyesData() {}

    SixEyesData(double attunement, double strain, double risk, boolean edgeLost) {
        this.attunement = attunement;
        this.strain = strain;
        this.risk = risk;
        this.edgeLost = edgeLost;
    }

    public double attunement() {
        return attunement;
    }

    public double strain() {
        return strain;
    }

    public double risk() {
        return risk;
    }

    public boolean edgeLost() {
        return edgeLost;
    }

    void wipe() {
        attunement = strain = risk = 0;
        edgeLost = false;
    }
}
