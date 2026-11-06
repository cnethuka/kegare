package com.cnethuka.kegare.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 0-100 score for one fight: how well a player used their technique. Pure maths, and on purpose it is never handed
 * who won, who died, Malice capacity, rarity or traits: none of those can move it.
 */
public final class AssessmentScoring {
    public record Weights(double technique, double efficiency, double performance, double opponent) {}

    /** How Technique use splits up. */
    public record TechniqueWeights(double distinct, double specials, double mixing, double combos) {}

    /**
     * @param distinctFullShare land this share of what you have unlocked (rounded up) for full marks on variety
     * @param spamFreeShare one move can be this share of your landings before it starts to cost you
     * @param spamPenalty what the Technique score loses when one move is all you landed
     * @param comboTarget different abilities landed back to back within the window, for full marks on combining
     * @param efficiencyFull damage dealt per base Malice for full marks on Efficiency
     * @param performanceFullShare your share of the damage traded for full marks on Performance
     * @param opponentEven what an opponent of your own Grade is worth (0..1)
     * @param severedFullMoves different melee moves for full marks, where a Severed player has no technique to vary
     */
    public record Settings(Weights weights, TechniqueWeights techniqueWeights, double distinctFullShare, double spamFreeShare,
                           double spamPenalty, int comboTarget, long comboWindowTicks, double efficiencyFull, double performanceFullShare,
                           double opponentEven, double opponentPerAbove, double opponentPerBelow, double opponentFloor, int severedFullMoves) {}

    private AssessmentScoring() {}

    /**
     * Technique use (0..1), against what the player has UNLOCKED: a locked ability can't lower it, and with no special
     * unlocked yet the special parts drop out and their weight goes to the rest.
     *
     * @param uses every landing in the fight, in order
     */
    public static double techniqueUse(List<Fight.Use> uses, Set<String> unlockedBase, Set<String> unlockedSpecials, Settings s) {
        Set<String> landedBase = new HashSet<>();
        Set<String> landedSpecials = new HashSet<>();
        for (Fight.Use use : uses) {
            if (unlockedBase.contains(use.id())) landedBase.add(use.id());
            if (unlockedSpecials.contains(use.id())) landedSpecials.add(use.id());
        }
        TechniqueWeights w = s.techniqueWeights;
        double earned = w.distinct * coverage(landedBase.size(), unlockedBase.size(), s.distinctFullShare);
        double possible = w.distinct;
        if (!unlockedSpecials.isEmpty()) {
            earned += w.specials * coverage(landedSpecials.size(), unlockedSpecials.size(), s.distinctFullShare);
            earned += w.mixing * (!landedBase.isEmpty() && !landedSpecials.isEmpty() ? 1.0 : 0.0);
            possible += w.specials + w.mixing;
        }
        if (unlockedBase.size() + unlockedSpecials.size() >= 2) {
            earned += w.combos * Math.min(1.0, combos(uses, s.comboWindowTicks) / (double) Math.max(1, s.comboTarget));
            possible += w.combos;
        }
        if (possible <= 0) return 0.0;
        return earned / possible * spamMultiplier(uses, s);
    }

    /** A Severed player's stand-in for Technique use: how varied their melee was. */
    public static double meleeVariety(List<Fight.Use> moves, Settings s) {
        Set<String> distinct = new HashSet<>();
        for (Fight.Use move : moves) distinct.add(move.id());
        TechniqueWeights w = s.techniqueWeights;
        double earned = w.distinct * Math.min(1.0, distinct.size() / (double) Math.max(1, s.severedFullMoves))
                + w.combos * Math.min(1.0, combos(moves, s.comboWindowTicks) / (double) Math.max(1, s.comboTarget));
        double possible = w.distinct + w.combos;
        return possible <= 0 ? 0.0 : earned / possible * spamMultiplier(moves, s);
    }

    private static double coverage(int landed, int unlocked, double fullShare) {
        if (unlocked <= 0) return 0.0;
        int needed = Math.max(1, (int) Math.ceil(unlocked * fullShare - 1.0e-9));
        return Math.min(1.0, landed / (double) needed);
    }

    /** Different abilities landed back to back within the window. */
    static int combos(List<Fight.Use> uses, long windowTicks) {
        int combos = 0;
        for (int i = 1; i < uses.size(); i++) {
            Fight.Use before = uses.get(i - 1);
            Fight.Use now = uses.get(i);
            if (!now.id().equals(before.id()) && now.tick() - before.tick() <= windowTicks) combos++;
        }
        return combos;
    }

    /** 1 until one move is more than the free share of your landings, down to {@code 1 - spamPenalty} when it's all of them. */
    static double spamMultiplier(List<Fight.Use> uses, Settings s) {
        if (uses.isEmpty()) return 1.0;
        Map<String, Integer> counts = new HashMap<>();
        int top = 0;
        for (Fight.Use use : uses) top = Math.max(top, counts.merge(use.id(), 1, Integer::sum));
        double share = top / (double) uses.size();
        if (share <= s.spamFreeShare || s.spamFreeShare >= 1.0) return 1.0;
        return 1.0 - s.spamPenalty * (share - s.spamFreeShare) / (1.0 - s.spamFreeShare);
    }

    /** Efficiency (0..1): damage per BASE Malice, so capacity and discounts can't inflate it. Nothing cast: nothing to rate. */
    public static double efficiency(double dealt, double baseMalice, Settings s) {
        if (baseMalice <= 0 || s.efficiencyFull <= 0) return 0.0;
        return Math.min(1.0, dealt / baseMalice / s.efficiencyFull);
    }

    /** Performance (0..1): your share of the damage traded. */
    public static double performance(double dealt, double taken, Settings s) {
        double total = dealt + taken;
        if (total <= 0 || s.performanceFullShare <= 0) return 0.0;
        return Math.min(1.0, dealt / total / s.performanceFullShare);
    }

    /** Opponent (0..1): more for fighting above your Grade, almost nothing for far below it. Full marks never need a stronger one. */
    public static double opponent(Grade me, Grade them, Settings s) {
        int above = them.stepsAbove(me);
        double value = above >= 0 ? s.opponentEven + above * s.opponentPerAbove : s.opponentEven + above * s.opponentPerBelow;
        return Math.max(s.opponentFloor, Math.min(1.0, value));
    }

    /** A sorcerer's score (0-100). */
    public static double score(double technique, double efficiency, double performance, double opponent, Settings s) {
        Weights w = s.weights;
        double total = w.technique + w.efficiency + w.performance + w.opponent;
        if (total <= 0) return 0.0;
        return 100.0 * (w.technique * technique + w.efficiency * efficiency + w.performance * performance + w.opponent * opponent) / total;
    }

    /** A Severed player's score (0-100): no technique and no Malice, so melee variety takes Technique's weight and Efficiency drops out. */
    public static double severedScore(double meleeVariety, double performance, double opponent, Settings s) {
        Weights w = s.weights;
        double total = w.technique + w.performance + w.opponent;
        if (total <= 0) return 0.0;
        return 100.0 * (w.technique * meleeVariety + w.performance * performance + w.opponent * opponent) / total;
    }
}
