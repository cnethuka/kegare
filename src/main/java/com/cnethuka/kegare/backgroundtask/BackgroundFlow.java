package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.flow.Flow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Background Flow (DESIGN §3.31): Flow (§3.16) kept up without thinking. Unlocked at Grade 3 (kept once earned). While it runs you have
 * {@code backgroundStrength} (0.4) of every one of the ability's boosts (Offense, Defense, Movement, Innate Technique), for a smaller drain.
 * With the Flow ability on too they don't stack: the stronger applies ({@link Flow#strength}). Its aura is the ability's, much calmer and
 * fainter, still shown to everyone.
 */
public final class BackgroundFlow extends BackgroundTask {
    public static final ResourceLocation ID = Kegare.id("background_flow");
    public static BackgroundFlow TASK;

    private BackgroundFlow() {
        super(ID);
    }

    static void init() {
        TASK = BackgroundTasks.register(new BackgroundFlow());
        Flow.addSource(player -> BackgroundTaskService.isRunning(player, ID) ? BackgroundTaskConfig.FLOW_STRENGTH.get() : 0.0);
    }

    @Override
    public UnlockRequirements requirements() {
        return UnlockRequirements.builder().grade(Grade.GRADE_3).condition(p -> !MaliceService.isSevered(p)).build();
    }

    @Override
    public double drainPerSecond(ServerPlayer player) {
        return BackgroundTaskConfig.FLOW_DRAIN.get();
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(BackgroundTaskConfig.FLOW_STRENGTH)};
    }

    @Override
    public void tick(ServerPlayer player) {
        Flow.update(player); // rewrites the modifier only when the strength changes
    }

    @Override
    public void stopped(ServerPlayer player) {
        Flow.update(player);
    }
}
