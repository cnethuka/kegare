package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.UnlockRequirements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Steady Output (DESIGN §3.31): while it runs, the upkeep of your active toggles drains {@code upkeepReduction} (30%) less: Reinforced
 * Fists, the Flow ability, Accelerate, Rime Shell and any other toggle's per-second cost ({@link CastManager#toggleUpkeep}).
 * Not other Background Tasks (they don't pay through the cast system), not a channel, and never a start cost. Its own drain is small.
 * Unlocked at Refinement 5, kept once earned, even after a reroll. Nothing visible.
 */
public final class SteadyOutput extends BackgroundTask {
    public static final ResourceLocation ID = Kegare.id("steady_output");
    public static SteadyOutput TASK;

    private SteadyOutput() {
        super(ID);
    }

    static void init() {
        TASK = BackgroundTasks.register(new SteadyOutput());
        CastManager.addToggleUpkeepModifier((player, ability, perSecond) ->
                BackgroundTaskService.isRunning(player, ID) ? perSecond * (1.0 - BackgroundTaskConfig.UPKEEP_REDUCTION.get()) : perSecond);
    }

    @Override
    public UnlockRequirements requirements() {
        return UnlockRequirements.builder().refinement(5).condition(p -> !MaliceService.isSevered(p)).build();
    }

    @Override
    public double drainPerSecond(ServerPlayer player) {
        return BackgroundTaskConfig.OUTPUT_DRAIN.get();
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(BackgroundTaskConfig.UPKEEP_REDUCTION)};
    }
}
