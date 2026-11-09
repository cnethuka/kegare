package com.cnethuka.kegare.reinforcement;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reinforcement's numbers (DESIGN §3.16, §3.31): {@code [reinforcement]} of {@code kegare-server.toml}. Times in seconds. */
public final class ReinforcementConfig {
    public static ModConfigSpec.DoubleValue ON_COST;
    public static ModConfigSpec.DoubleValue DRAIN;
    public static ModConfigSpec.DoubleValue REARM;
    public static ModConfigSpec.DoubleValue REDUCTION_PHYSICAL;
    public static ModConfigSpec.DoubleValue REDUCTION_TECHNIQUE;
    public static ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;

    private ReinforcementConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Reinforcement (Malice, TOGGLE): Malice routed through the body, for durability and force tolerance. Every Malice user has it.",
                "It adds no damage (that's Reinforced Fists); both can run together.").push("reinforcement");
        ON_COST = b.comment("Malice to turn it on.").defineInRange("onCost", 40.0, 0.0, 100_000.0);
        DRAIN = b.comment("Malice drained per second while it's on. It turns off when Malice runs out.").defineInRange("drainPerSecond", 10.0, 0.0, 100_000.0);
        REARM = b.comment("After it turns off, this long before it can go on again.").defineInRange("rearmCooldown", 1.5, 0.0, 3600.0);
        REDUCTION_PHYSICAL = b.comment("Share of physical damage (hits, arrows, falls, fire) taken off.").defineInRange("reductionPhysical", 0.25, 0.0, 1.0);
        REDUCTION_TECHNIQUE = b.comment("Share of technique damage taken off.").defineInRange("reductionTechnique", 0.2, 0.0, 1.0);
        KNOCKBACK_RESISTANCE = b.comment("Knockback resistance added (0 to 1): force tolerance.").defineInRange("knockbackResistance", 0.4, 0.0, 1.0);
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
