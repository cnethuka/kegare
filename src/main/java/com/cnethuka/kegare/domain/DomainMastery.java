package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.RefinementService;
import net.minecraft.server.level.ServerPlayer;

/**
 * How long a Domain holds before it breaks (DESIGN §3.35): its base duration times a share that rides on how far its caster has come.
 * Mastery, from 0 to 1, is their Grade (Grade 4 to Special Grade, evenly up the ladder) and their Refinement (0 to the top level), weighed
 * by {@code mastery.gradeWeight}: for a Domain Expansion its technique's Refinement (it's that technique's ultimate), for an Incomplete one
 * their Peak Refinement (it belongs to the player, and Peak is what it's learned from). The share runs from {@code mastery.leastShare} at
 * none to {@code mastery.mostShare} at all of it.
 */
public final class DomainMastery {
    private DomainMastery() {}

    /** Their mastery for a full ({@code complete}) or an Incomplete Domain, 0 to 1. */
    public static double of(ServerPlayer caster, boolean complete) {
        return mastery(GradeService.grade(caster), complete ? RefinementService.level(caster) : RefinementService.peak(caster));
    }

    /** Mastery from a Grade and a Refinement level, 0 to 1. */
    static double mastery(Grade grade, int refinement) {
        int topGrade = Grade.values().length - 1, topLevel = Math.max(1, RefinementService.maxLevel());
        double weight = DomainConfig.MASTERY_GRADE_WEIGHT.get();
        return weight * grade.ordinal() / topGrade + (1 - weight) * Math.clamp(refinement, 0, topLevel) / (double) topLevel;
    }

    /** The share of its base duration a Domain of this mastery holds for. */
    static double share(double mastery) {
        double least = DomainConfig.MASTERY_LEAST_SHARE.get(), most = DomainConfig.MASTERY_MOST_SHARE.get();
        return least + (most - least) * Math.clamp(mastery, 0.0, 1.0);
    }

    /** How long its Domain holds once sealed, in ticks: {@code baseTicks} scaled by its caster's mastery. */
    public static int durationTicks(ServerPlayer caster, boolean complete, int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks * share(of(caster, complete))));
    }
}
