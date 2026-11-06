package com.cnethuka.kegare.progression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Whether a fight history meets a requirement. Pure, so one friend carrying it is easy to test. */
public final class AssessmentRules {
    /** One scored fight. */
    public record Entry(double score, UUID opponent, long tick) {}

    public record Requirement(double average, int fights, int opponents, int peakRefinement) {}

    /** Where a history stands against a requirement. {@code average}: of up to the last {@code fights} entries. */
    public record Status(double average, int fights, int opponents, int peakRefinement, boolean severed, Requirement requirement) {
        public boolean met() {
            return fights >= requirement.fights && average + 1.0e-9 >= requirement.average && opponents >= requirement.opponents
                    && (severed || peakRefinement >= requirement.peakRefinement);
        }
    }

    private AssessmentRules() {}

    /** @param history oldest first */
    public static Status evaluate(List<Entry> history, Requirement requirement, int peakRefinement, boolean severed) {
        int from = Math.max(0, history.size() - requirement.fights);
        List<Entry> recent = history.subList(from, history.size());
        double sum = 0;
        Set<UUID> opponents = new HashSet<>();
        for (Entry entry : recent) {
            sum += entry.score;
            opponents.add(entry.opponent);
        }
        double average = recent.isEmpty() ? 0.0 : sum / recent.size();
        return new Status(average, recent.size(), opponents.size(), peakRefinement, severed, requirement);
    }
}
