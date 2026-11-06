package com.cnethuka.kegare.progression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Whether a fight history meets a requirement. Pure, so one friend carrying it is easy to test. */
public final class AssessmentRules {
    /**
     * One scored fight.
     *
     * @param technique its Technique use, 0-100 like the score (a Severed player's melee variety)
     */
    public record Entry(double score, double technique, UUID opponent, long tick) {}

    /** @param technique the average Technique use needed, 0-100, so one move repeated can't qualify however well it trades */
    public record Requirement(double average, int fights, int opponents, int peakRefinement, double technique) {}

    /** Where a history stands against a requirement. {@code average} and {@code technique}: of up to the last {@code fights} entries. */
    public record Status(double average, double technique, int fights, int opponents, int peakRefinement, boolean severed, Requirement requirement) {
        public boolean met() {
            return fights >= requirement.fights && average + 1.0e-9 >= requirement.average
                    && technique + 1.0e-9 >= requirement.technique && opponents >= requirement.opponents
                    && (severed || peakRefinement >= requirement.peakRefinement);
        }
    }

    private AssessmentRules() {}

    /** @param history oldest first */
    public static Status evaluate(List<Entry> history, Requirement requirement, int peakRefinement, boolean severed) {
        int from = Math.max(0, history.size() - requirement.fights);
        List<Entry> recent = history.subList(from, history.size());
        double scores = 0;
        double technique = 0;
        Set<UUID> opponents = new HashSet<>();
        for (Entry entry : recent) {
            scores += entry.score;
            technique += entry.technique;
            opponents.add(entry.opponent);
        }
        int count = recent.size();
        return new Status(count == 0 ? 0.0 : scores / count, count == 0 ? 0.0 : technique / count, count, opponents.size(), peakRefinement, severed, requirement);
    }
}
