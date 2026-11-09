package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.reinforcement.Reinforcement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Background Reinforcement (DESIGN §3.31): Reinforcement (§3.16) kept up without thinking. Unlocked at Grade 3 (kept once earned).
 * While it runs you have {@code backgroundStrength} (0.4) of the ability's damage reductions and knockback resistance, for a smaller
 * drain. With the Reinforcement ability on too they don't stack: the stronger applies ({@link Reinforcement#strength}). Its aura is the
 * ability's, much calmer and fainter: a thin flow with a light outline, still shown to everyone.
 */
public final class BackgroundReinforcement extends BackgroundTask {
    public static final ResourceLocation ID = Kegare.id("background_reinforcement");
    public static BackgroundReinforcement TASK;

    private BackgroundReinforcement() {
        super(ID);
    }

    static void init() {
        TASK = BackgroundTasks.register(new BackgroundReinforcement());
        Reinforcement.addSource(player -> BackgroundTaskService.isRunning(player, ID) ? BackgroundTaskConfig.REINFORCEMENT_STRENGTH.get() : 0.0);
    }

    @Override
    public UnlockRequirements requirements() {
        return UnlockRequirements.builder().grade(Grade.GRADE_3).condition(p -> !MaliceService.isSevered(p)).build();
    }

    @Override
    public double drainPerSecond(ServerPlayer player) {
        return BackgroundTaskConfig.REINFORCEMENT_DRAIN.get();
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(BackgroundTaskConfig.REINFORCEMENT_STRENGTH)};
    }

    @Override
    public void tick(ServerPlayer player) {
        Reinforcement.update(player); // changes only when it changes
    }

    @Override
    public void stopped(ServerPlayer player) {
        Reinforcement.update(player);
    }
}
