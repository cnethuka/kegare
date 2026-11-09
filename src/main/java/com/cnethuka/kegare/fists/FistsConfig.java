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

    // Black Spark (DESIGN §3.28)
    public static ModConfigSpec.DoubleValue JITTER_MS;
    public static ModConfigSpec.DoubleValue WINDOW_MS;
    public static ModConfigSpec.DoubleValue MAX_WINDOW_SHARE;
    public static ModConfigSpec.DoubleValue TRY_MS;
    public static ModConfigSpec.DoubleValue SCALE_DAMAGE;
    public static ModConfigSpec.DoubleValue EXPONENT;
    public static ModConfigSpec.DoubleValue KNOCKBACK;
    public static ModConfigSpec.DoubleValue MALICE_EMPTY;
    public static ModConfigSpec.DoubleValue MALICE_FULL;
    public static ModConfigSpec.DoubleValue OPPONENT_WEIGHT;
    public static ModConfigSpec.DoubleValue OPPONENT_MIN;
    public static ModConfigSpec.DoubleValue OPPONENT_MAX;
    public static ModConfigSpec.DoubleValue HEALTH_LOW;
    public static ModConfigSpec.DoubleValue HUNGER_LOW;
    public static ModConfigSpec.DoubleValue WEAKNESS;
    public static ModConfigSpec.DoubleValue SLOWNESS;
    public static ModConfigSpec.DoubleValue FREEZE_PER_STACK;
    public static ModConfigSpec.DoubleValue MILD_TEMPERATURE;
    public static ModConfigSpec.DoubleValue TEMPERATURE_WEIGHT;
    public static ModConfigSpec.DoubleValue HUMIDITY_WEIGHT;
    public static ModConfigSpec.DoubleValue RAIN;
    public static ModConfigSpec.DoubleValue THUNDER;
    public static ModConfigSpec.DoubleValue GRADE_STEP;
    // The zone (DESIGN §3.28)
    public static ModConfigSpec.DoubleValue ZONE_WINDOW;

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

        b.comment("Black Spark: Fists turned on just before a melee hit lands. Each try the server secretly rolls where the spark sits, an",
                "offset in [0, jitterMs]; the hit is a Black Spark if the gap from Fists on to the attack falls in [offset, offset + window].",
                "The window is windowMs times every factor below, worked out fresh each try. All times in ms.").push("black_spark");
        JITTER_MS = b.comment("How far the spark's secret offset can sit.").defineInRange("jitterMs", 150.0, 1.0, 5000.0);
        WINDOW_MS = b.comment("The window before its factors.").defineInRange("windowMs", 25.0, 0.0, 5000.0);
        MAX_WINDOW_SHARE = b.comment("The window never reaches more than this share of jitterMs, whatever the factors: it's never certain.")
                .defineInRange("maxWindowShare", 0.5, 0.0, 0.99);
        TRY_MS = b.comment("Only the first melee hit after Fists go on is a try, and only this soon after.").defineInRange("tryMs", 1000.0, 1.0, 60_000.0);
        SCALE_DAMAGE = b.comment("Its power: the hit's damage on a scale that starts at 2 (s = 2 + damage / scaleDamage), raised to the",
                "exponent and turned back into damage (times s^(exponent - 1)). A unit, not a limit: there is no cap.")
                .defineInRange("scaleDamage", 4.0, 0.01, 1000.0);
        EXPONENT = b.comment("Canon's exponent.").defineInRange("exponent", 2.5, 1.0, 10.0);
        KNOCKBACK = b.comment("Knockback it adds (bosses are never moved).").defineInRange("knockback", 1.2, 0.0, 10.0);
        b.comment("The window's factors (multipliers).").push("window");
        MALICE_EMPTY = b.comment("Your Malice, current against capacity: the factor at empty...").defineInRange("maliceEmpty", 0.85, 0.0, 10.0);
        MALICE_FULL = b.comment("...and at full (fuller is wider).").defineInRange("maliceFull", 1.15, 0.0, 10.0);
        OPPONENT_WEIGHT = b.comment("Your opponent's Malice capacity against yours: 1 / (1 + opponentWeight x (theirs / yours - 1)), stronger",
                "is narrower. Mobs and Severed players have none.").defineInRange("opponentWeight", 0.15, 0.0, 10.0);
        OPPONENT_MIN = b.defineInRange("opponentMin", 0.75, 0.0, 10.0);
        OPPONENT_MAX = b.defineInRange("opponentMax", 1.15, 0.0, 10.0);
        HEALTH_LOW = b.comment("Your health: the factor at none (1 at full).").defineInRange("healthLow", 0.8, 0.0, 1.0);
        HUNGER_LOW = b.comment("Your hunger: the factor at an empty food bar (1 at full).").defineInRange("hungerLow", 0.9, 0.0, 1.0);
        WEAKNESS = b.comment("Each level of Weakness on you.").defineInRange("weakness", 0.9, 0.0, 1.0);
        SLOWNESS = b.comment("Each level of Slowness on you.").defineInRange("slowness", 0.92, 0.0, 1.0);
        FREEZE_PER_STACK = b.comment("Taken off for each Freeze stack on you (never below half).").defineInRange("freezePerStack", 0.06, 0.0, 1.0);
        MILD_TEMPERATURE = b.comment("Temperature and humidity (canon names them, not which way they push: an assumption). The biome temperature",
                "that's best (plains are 0.8)...").defineInRange("mildTemperature", 0.8, -2.0, 3.0);
        TEMPERATURE_WEIGHT = b.comment("...and how much narrower it is per unit of temperature away from it (at most one unit counts).")
                .defineInRange("temperatureWeight", 0.05, 0.0, 1.0);
        HUMIDITY_WEIGHT = b.comment("Narrower by this times the biome's rainfall (0 to 1).").defineInRange("humidityWeight", 0.03, 0.0, 1.0);
        RAIN = b.comment("Raining where you are.").defineInRange("rain", 0.97, 0.0, 1.0);
        THUNDER = b.comment("A thunderstorm, on top of the rain.").defineInRange("thunder", 0.97, 0.0, 1.0);
        GRADE_STEP = b.comment("Wider by this for each Grade above Grade 4.").defineInRange("gradeStep", 0.03, 0.0, 1.0);
        ZONE_WINDOW = b.comment("In the zone (after a Black Spark): the window is this many times wider, so chains are possible.")
                .defineInRange("zoneWindow", 1.6, 1.0, 100.0);
        b.pop();
        b.pop();
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
