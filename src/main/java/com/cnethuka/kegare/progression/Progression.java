package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server-side face of progression, for other systems. The future Six Eyes Read and the Death Binding strength
 * check read a player's Grade through here. Never send what this returns to anyone but its owner: Grade is private.
 */
public final class Progression {
    private Progression() {}

    /** Wires the systems that listen to fights and promotions. Called once, at mod construction. */
    public static void init() {
        // Assessment first: a fight is rated against the Grades the two players held while it was on.
        FightTracker.addListener(AssessmentService::onFightEnded);
        FightTracker.addListener(GradeService::onFightEnded);
        FightTracker.addStartListener(InsightHooks::onFightStarted);
        RefinementService.addLevelListener(TechniqueUnlocks::onLevelChanged);
        RefinementService.addLevelListener((player, from, to) -> Unlocks.evaluate(player));
        GradeService.addPromotionListener((player, grade) -> Unlocks.evaluate(player));
        GradeService.addPromotionListener(ProgressionToasts::promoted);
        GradeService.addPromotionListener(Progression::awardGradeAdvancements);
        RefinementService.addLevelListener((player, from, to) -> TechniqueUnlocks.checkFullKit(player));
        Unlocks.addListener(ProgressionToasts::unlocked);
    }

    /** Every Grade reached, so an admin jumping someone up the ladder lands the same as walking it. Silent: Grade is private. */
    private static void awardGradeAdvancements(ServerPlayer player, Grade grade) {
        if (grade.atLeast(Grade.GRADE_3)) KegareAdvancements.award(player, KegareAdvancements.MOVING_UP);
        if (grade.atLeast(Grade.GRADE_2)) KegareAdvancements.award(player, KegareAdvancements.PROVEN);
        if (grade.atLeast(Grade.GRADE_1)) KegareAdvancements.award(player, KegareAdvancements.RECOGNIZED);
        if (grade.atLeast(Grade.SPECIAL_GRADE)) KegareAdvancements.award(player, KegareAdvancements.OUTSIDE_THE_LADDER);
    }

    /** Admin: Grade, XP, Assessment, Refinement, Peak Refinement, Insight, firsts and unlocks all back to nothing. */
    public static void reset(ServerPlayer player) {
        GradeService.reset(player);
        AssessmentService.reset(player);
        RefinementService.reset(player); // takes what's now locked off the loadout
        Insight.reset(player);
        Unlocks.reset(player);
    }

    public static Grade getGrade(ServerPlayer player) {
        return GradeService.grade(player);
    }
}
