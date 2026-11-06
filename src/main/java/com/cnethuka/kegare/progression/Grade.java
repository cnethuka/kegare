package com.cnethuka.kegare.progression;

import java.util.Locale;
import java.util.Optional;

/**
 * The ladder, lowest first. Everyone starts at {@link #GRADE_4}; Grade never goes down and never changes Malice
 * capacity. There are no trials anywhere on it: no promotion requires killing or beating anyone, at any rank.
 */
public enum Grade {
    GRADE_4(false, false),
    GRADE_3(true, false),
    SEMI_GRADE_2(true, false),
    GRADE_2(true, true),
    SEMI_GRADE_1(true, false),
    GRADE_1(true, true),
    SPECIAL_GRADE(false, true);

    private final boolean takesXp;
    private final boolean takesAssessment;

    /** @param takesXp reaching this Grade takes XP; @param takesAssessment reaching it takes an Assessment */
    Grade(boolean takesXp, boolean takesAssessment) {
        this.takesXp = takesXp;
        this.takesAssessment = takesAssessment;
    }

    public boolean takesXp() {
        return takesXp;
    }

    public boolean takesAssessment() {
        return takesAssessment;
    }

    /** The next step up, or empty at Special Grade. */
    public Optional<Grade> next() {
        Grade[] all = values();
        return ordinal() + 1 < all.length ? Optional.of(all[ordinal() + 1]) : Optional.empty();
    }

    public boolean atLeast(Grade other) {
        return ordinal() >= other.ordinal();
    }

    /** How far above {@code other} this Grade is (negative if below). */
    public int stepsAbove(Grade other) {
        return ordinal() - other.ordinal();
    }

    public static Grade byOrdinal(int ordinal) {
        Grade[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "grade.kegare." + id();
    }

    public static Optional<Grade> byId(String id) {
        for (Grade grade : values()) if (grade.id().equals(id)) return Optional.of(grade);
        return Optional.empty();
    }
}
