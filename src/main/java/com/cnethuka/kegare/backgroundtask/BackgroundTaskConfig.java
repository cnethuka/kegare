package com.cnethuka.kegare.backgroundtask;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Background Tasks' numbers (DESIGN §3.31): {@code [background_tasks]} of {@code kegare-server.toml}, one table per task. */
public final class BackgroundTaskConfig {
    // Background RMT
    public static ModConfigSpec.DoubleValue RMT_HEAL_TO_UNLOCK;
    public static ModConfigSpec.DoubleValue RMT_OUTPUT;
    public static ModConfigSpec.DoubleValue RMT_DRAIN;
    public static ModConfigSpec.DoubleValue RMT_REGEN_FACTOR;
    public static ModConfigSpec.DoubleValue RMT_PAUSE_BELOW;
    public static ModConfigSpec.DoubleValue RMT_RESUME_ABOVE;
    public static ModConfigSpec.DoubleValue RMT_BURNOUT_FACTOR;
    // Background Reinforcement
    public static ModConfigSpec.DoubleValue REINFORCEMENT_STRENGTH;
    public static ModConfigSpec.DoubleValue REINFORCEMENT_DRAIN;
    // Sustained Flow
    public static ModConfigSpec.DoubleValue FLOW_REDUCTION;
    public static ModConfigSpec.DoubleValue FLOW_DRAIN;

    private BackgroundTaskConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Background Tasks: things a sorcerer keeps running without thinking, separate from the ability bar. Weaker than the",
                "ability, but no bar slot and the hands free.").push("background_tasks");

        b.comment("Background RMT: Reverse Malice Technique left running, slowly.").push("background_rmt");
        RMT_HEAL_TO_UNLOCK = b.comment("Health healed in total with your own channelled Reverse Malice Technique (healing others counts too) to",
                "unlock it.").defineInRange("healToUnlock", 500.0, 0.0, 1_000_000.0);
        RMT_OUTPUT = b.comment("Its heal rate as a share of your channelled RMT's at your current output (Grade, Peak Refinement, injury",
                "fatigue and every output modifier apply). 1/6: about 1 health every 3 s at an output of 1.").defineInRange("backgroundOutput", 1.0 / 6.0, 0.0, 10.0);
        RMT_DRAIN = b.comment("Malice drained per second while it runs (even at full health).").defineInRange("drainPerSecond", 12.0, 0.0, 100_000.0);
        RMT_REGEN_FACTOR = b.comment("Your natural Malice regeneration is multiplied by this while it runs.").defineInRange("regenFactor", 0.5, 0.0, 1.0);
        RMT_PAUSE_BELOW = b.comment("It pauses on its own below this share of your Malice capacity...").defineInRange("pauseBelow", 0.2, 0.0, 1.0);
        RMT_RESUME_ABOVE = b.comment("...and resumes on its own above this share.").defineInRange("resumeAbove", 0.3, 0.0, 1.0);
        RMT_BURNOUT_FACTOR = b.comment("While it runs, every Burnout applied to you lasts this share of its length.").defineInRange("burnoutFactor", 0.75, 0.0, 1.0);
        b.pop();

        b.comment("Background Reinforcement: Reinforcement kept up without thinking, weaker. Unlocked at Grade 3.").push("background_reinforcement");
        REINFORCEMENT_STRENGTH = b.comment("Its share of the Reinforcement ability's damage reductions and knockback resistance. With the ability on",
                "too, they don't stack: the stronger applies.").defineInRange("backgroundStrength", 0.4, 0.0, 1.0);
        REINFORCEMENT_DRAIN = b.comment("Malice drained per second while it runs.").defineInRange("drainPerSecond", 4.0, 0.0, 100_000.0);
        b.pop();

        b.comment("Sustained Flow: a steady flow that makes your active toggles cheaper to keep up. Unlocked at Refinement 5.").push("sustained_flow");
        FLOW_REDUCTION = b.comment("Share taken off every active toggle's per-second upkeep (Reinforced Fists, Reinforcement, Accelerate, Rime",
                "Shell, any toggle). Never a start cost, never another Background Task.").defineInRange("flowReduction", 0.3, 0.0, 1.0);
        FLOW_DRAIN = b.comment("Malice drained per second while it runs.").defineInRange("drainPerSecond", 1.5, 0.0, 100_000.0);
        b.pop();

        b.pop();
    }

    /** Their Malice as a share of capacity (0 with none). */
    static double share(Player player) {
        var malice = com.cnethuka.kegare.malice.MaliceService.get(player);
        return malice.capacity() <= 0 ? 0.0 : malice.current() / malice.capacity();
    }
}
