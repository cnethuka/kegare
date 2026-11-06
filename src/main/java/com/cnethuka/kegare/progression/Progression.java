package com.cnethuka.kegare.progression;

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
    }

    public static Grade getGrade(ServerPlayer player) {
        return GradeService.grade(player);
    }
}
