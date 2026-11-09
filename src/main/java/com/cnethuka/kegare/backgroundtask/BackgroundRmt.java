package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.config.ConfigText;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeStops;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.reinforcement.AuraTell;
import com.cnethuka.kegare.rmt.ReverseMaliceTechnique;
import com.cnethuka.kegare.rmt.RmtConfig;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.rmt.RmtOutput;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Background RMT (DESIGN §3.31): Reverse Malice Technique left running, slowly and steadily.
 * <ul>
 * <li>Unlock: {@code healToUnlock} (500) health healed in total with your own channelled RMT, healing others included ({@link RmtHealed});
 * it needs RMT unlocked; Severed never. "Always On" (silent, hidden) is awarded when it unlocks.</li>
 * <li>Effect: heals {@code backgroundOutput} (1/6) of your channelled RMT's rate at your current output, so Grade, Peak Refinement,
 * injury fatigue and every output modifier (Black Spark's zone) apply: about 1 health every 3 s at an output of 1. Like the channel it
 * keeps running at full health and heals damage as it lands. Never absorption, and no pulse or chime: nobody can tell, except a Six
 * Eyes bearer, who sees a faint green shimmer on anyone running it.</li>
 * <li>Cost: {@code drainPerSecond}, and natural Malice regeneration ×{@code regenFactor} while it runs.</li>
 * <li>It pauses on its own below {@code pauseBelow} (20%) of capacity and resumes above {@code resumeAbove} (30%). Only low Malice stops
 * it: stuns and Burnout don't. Frozen in a Standstill, it's paused while time is stopped for you, and resumes with it.</li>
 * <li>While it runs, every Burnout applied to you lasts ×{@code burnoutFactor}.</li>
 * </ul>
 * Hooks for later ({@link #addHook}, not built): the Six Eyes step (holding Strain back, easing the overload) and Oxidation's continuous
 * Dividation, each told every tick it runs.
 */
public final class BackgroundRmt extends BackgroundTask {
    public static final ResourceLocation ID = Kegare.id("background_rmt");
    public static final ResourceLocation ALWAYS_ON = KegareAdvancements.ALWAYS_ON;

    /** Told every tick Background RMT runs on a player. For the Six Eyes step and Oxidation's continuous Dividation, later. */
    @FunctionalInterface
    public interface Hook {
        void whileRunning(ServerPlayer player);
    }

    private static final List<Hook> HOOKS = new CopyOnWriteArrayList<>();
    public static BackgroundRmt TASK;

    private BackgroundRmt() {
        super(ID);
    }

    static void init() {
        TASK = BackgroundTasks.register(new BackgroundRmt());
        ReverseMaliceTechnique.addHealListener((caster, healed) -> RmtHealed.add(caster, healed));
        Unlocks.addListener((player, id) -> {
            if (ID.equals(id)) KegareAdvancements.award(player, ALWAYS_ON);
        });
        MaliceService.addRegenModifier(player -> running(player) ? BackgroundTaskConfig.RMT_REGEN_FACTOR.get() : 1.0);
        Burnout.addModifier((player, ticks) -> running(player) ? (int) Math.round(ticks * BackgroundTaskConfig.RMT_BURNOUT_FACTOR.get()) : ticks);
        // Only the Six Eyes see it: a faint green shimmer on anyone running it.
        AuraTell.addShimmer((viewer, player) -> running(player) && TraitService.has(viewer, KegareTraits.SIX_EYES.get()),
                player -> BackgroundTaskService.isActive(player, ID));
    }

    public static void addHook(Hook hook) {
        HOOKS.add(hook);
    }

    /** Running right now: slotted, on, unlocked, not paused. */
    public static boolean running(ServerPlayer player) {
        return BackgroundTaskService.isRunning(player, ID);
    }

    @Override
    public UnlockRequirements requirements() {
        return UnlockRequirements.builder().condition(p -> !MaliceService.isSevered(p) && SharedAbilityService.isUnlocked(p, RmtIds.ABILITY)
                && RmtHealed.get(p) >= BackgroundTaskConfig.RMT_HEAL_TO_UNLOCK.get()).build();
    }

    @Override
    public double drainPerSecond(ServerPlayer player) {
        return BackgroundTaskConfig.RMT_DRAIN.get();
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {ConfigText.percent(BackgroundTaskConfig.RMT_OUTPUT), ConfigText.percent(BackgroundTaskConfig.RMT_PAUSE_BELOW),
                ConfigText.percent(BackgroundTaskConfig.RMT_RESUME_ABOVE)};
    }

    /** Paused for low Malice, by player: below 20% it pauses, and then waits for 30%. */
    private static final java.util.Set<java.util.UUID> LOW = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Low Malice (with the 20%/30% gap), or frozen in a Standstill: paused while time is stopped for them, resumed with it. */
    @Override
    public boolean paused(ServerPlayer player, boolean wasPaused) {
        double share = BackgroundTaskConfig.share(player);
        boolean low = LOW.contains(player.getUUID()) ? share <= BackgroundTaskConfig.RMT_RESUME_ABOVE.get() : share < BackgroundTaskConfig.RMT_PAUSE_BELOW.get();
        if (low) LOW.add(player.getUUID());
        else LOW.remove(player.getUUID());
        return low || TimeStops.frozen(player);
    }

    static void forget(java.util.UUID player) {
        LOW.remove(player);
    }

    /** A tick's healing at the background rate: never above max health, never absorption, nothing to see or hear. */
    static double rate(ServerPlayer player) {
        return RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(player) * BackgroundTaskConfig.RMT_OUTPUT.get() / 20.0;
    }

    @Override
    public void tick(ServerPlayer player) {
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing > 0 && player.isAlive()) player.heal((float) Math.min(missing, rate(player)));
        for (Hook hook : HOOKS) hook.whileRunning(player);
    }
}
