package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A player's Grade: kept on reroll and death, seen by nobody else. */
public final class GradeData {
    public static final Codec<GradeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("grade", Grade.GRADE_4.id()).forGetter(d -> d.grade.id()),
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(d -> d.xp),
            OpponentLedger.CODEC.optionalFieldOf("fights", new OpponentLedger()).forGetter(d -> d.fights),
            Codec.LONG.optionalFieldOf("mob_window_start", 0L).forGetter(d -> d.mobWindowStart),
            Codec.DOUBLE.optionalFieldOf("mob_window_xp", 0.0).forGetter(d -> d.mobWindowXp)
    ).apply(i, (grade, xp, fights, start, mobXp) -> new GradeData(Grade.byId(grade).orElse(Grade.GRADE_4), xp, fights, start, mobXp)));

    Grade grade;
    /** Toward the next step only (a promotion carries the rest over). */
    double xp;
    final OpponentLedger fights;
    long mobWindowStart;
    double mobWindowXp;

    public GradeData() {
        this(Grade.GRADE_4, 0.0, new OpponentLedger(), 0L, 0.0);
    }

    private GradeData(Grade grade, double xp, OpponentLedger fights, long mobWindowStart, double mobWindowXp) {
        this.grade = grade;
        this.xp = xp;
        this.fights = fights;
        this.mobWindowStart = mobWindowStart;
        this.mobWindowXp = mobWindowXp;
    }

    public Grade grade() {
        return grade;
    }

    public double xp() {
        return xp;
    }
}
