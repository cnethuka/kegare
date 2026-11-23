package com.cnethuka.kegare.flow;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Flow's numbers (DESIGN §3.16, §3.31): {@code [flow]} of {@code kegare-server.toml}. Times in seconds. */
public final class FlowConfig {
    public static ModConfigSpec.DoubleValue ON_COST;
    public static ModConfigSpec.DoubleValue DRAIN;
    public static ModConfigSpec.DoubleValue REARM;
    public static ModConfigSpec.DoubleValue REDUCTION_PHYSICAL;
    public static ModConfigSpec.DoubleValue REDUCTION_TECHNIQUE;
    public static ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;
    public static ModConfigSpec.DoubleValue OFFENSE;
    public static ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static ModConfigSpec.DoubleValue DASH_RECHARGE;
    public static ModConfigSpec.DoubleValue TECHNIQUE_COST;

    private FlowConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Flow (Malice, TOGGLE): Malice let flow through the whole body. It boosts Offense, Defense, Movement and your Innate",
                "Technique. Every Malice user has it; it runs alongside Reinforced Fists.").push("flow");
        ON_COST = b.comment("Malice to turn it on.").defineInRange("onCost", 40.0, 0.0, 100_000.0);
        DRAIN = b.comment("Malice drained per second while it's on. It turns off when Malice runs out.").defineInRange("drainPerSecond", 12.0, 0.0, 100_000.0);
        REARM = b.comment("After it turns off, this long before it can go on again.").defineInRange("rearmCooldown", 1.5, 0.0, 3600.0);
        OFFENSE = b.comment("Offense: share added to all the damage you deal (melee, weapons, projectiles, techniques), on top of Reinforced",
                "Fists' bonus.").defineInRange("offense", 0.15, 0.0, 10.0);
        REDUCTION_PHYSICAL = b.comment("Defense: share of physical damage (hits, arrows, falls, fire) taken off.").defineInRange("reductionPhysical", 0.25, 0.0, 1.0);
        REDUCTION_TECHNIQUE = b.comment("Defense: share of technique damage taken off.").defineInRange("reductionTechnique", 0.2, 0.0, 1.0);
        KNOCKBACK_RESISTANCE = b.comment("Defense: knockback resistance added (0 to 1), force tolerance.").defineInRange("knockbackResistance", 0.4, 0.0, 1.0);
        MOVEMENT_SPEED = b.comment("Movement: share added to your movement speed.").defineInRange("movementSpeed", 0.15, 0.0, 10.0);
        DASH_RECHARGE = b.comment("Movement: share taken off Dash's recharge.").defineInRange("dashRecharge", 0.25, 0.0, 1.0);
        TECHNIQUE_COST = b.comment("Innate Technique: share taken off what its abilities cost (casts, releases and upkeep; specials and the",
                "ultimate too).").defineInRange("techniqueCost", 0.15, 0.0, 1.0);
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
