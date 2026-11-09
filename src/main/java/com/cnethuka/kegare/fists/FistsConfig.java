package com.cnethuka.kegare.fists;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reinforced Fists' numbers (DESIGN §3.28): {@code [reinforced_fists]} of {@code kegare-server.toml}. Times in seconds. */
public final class FistsConfig {
    public static ModConfigSpec.DoubleValue ON_COST;
    public static ModConfigSpec.DoubleValue DRAIN;
    public static ModConfigSpec.DoubleValue REARM;
    public static ModConfigSpec.DoubleValue FIST_BONUS;
    public static ModConfigSpec.DoubleValue WEAPON_BONUS;
    public static ModConfigSpec.DoubleValue SECOND_IMPACT_DELAY;
    public static ModConfigSpec.DoubleValue SECOND_IMPACT_DAMAGE;

    private FistsConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Reinforced Fists (shared, TOGGLE): every Malice user has it. Malice wrapped round the fists: melee hits deal more,",
                "and a Reinforced Strike landed with it on hits again a moment later. Severed players have none (no Malice).").push("reinforced_fists");
        ON_COST = b.comment("Malice to turn it on.").defineInRange("onCost", 30.0, 0.0, 100_000.0);
        DRAIN = b.comment("Malice drained per second while it's on. It turns off when Malice runs out.").defineInRange("drainPerSecond", 12.0, 0.0, 100_000.0);
        REARM = b.comment("After it turns off, this long before it can go on again (so it can't be flicked on for every swing).")
                .defineInRange("rearmCooldown", 1.5, 0.0, 3600.0);
        FIST_BONUS = b.comment("Melee damage added with an empty hand, as a share (2.0 = three times a punch).").defineInRange("fistBonus", 2.0, 0.0, 100.0);
        WEAPON_BONUS = b.comment("Melee damage added with anything in hand, as a share (smaller than the fist's, so fists are worth using).")
                .defineInRange("weaponBonus", 0.2, 0.0, 100.0);
        SECOND_IMPACT_DELAY = b.comment("A Reinforced Strike landed with it on hits again this much later, at the same spot.")
                .defineInRange("secondImpactDelay", 0.3, 0.05, 5.0);
        SECOND_IMPACT_DAMAGE = b.comment("The second impact's damage, as a share of the Strike's.").defineInRange("secondImpactDamage", 0.5, 0.0, 10.0);
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
