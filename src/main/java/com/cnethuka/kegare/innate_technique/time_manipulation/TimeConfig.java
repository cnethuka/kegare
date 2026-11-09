package com.cnethuka.kegare.innate_technique.time_manipulation;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Time Manipulation's numbers: section {@code [time_manipulation]} of {@code kegare-server.toml} (DESIGN §3.26). Times are in seconds. */
public final class TimeConfig {
    // targeting (Rewind)
    public static ModConfigSpec.DoubleValue RANGE;
    public static ModConfigSpec.DoubleValue AIM_TOLERANCE;

    // point_maker (INSTANT, two stages)
    public static ModConfigSpec.DoubleValue MARK_COST;
    public static ModConfigSpec.DoubleValue RETURN_COST;
    public static ModConfigSpec.DoubleValue MARK_RANGE;
    public static ModConfigSpec.DoubleValue MARK_SECONDS;
    public static ModConfigSpec.DoubleValue RETURN_HEAL;
    public static ModConfigSpec.DoubleValue POINT_MAKER_COOLDOWN;

    // rewind (special of point_maker, INSTANT)
    public static ModConfigSpec.DoubleValue REWIND_COST;
    public static ModConfigSpec.DoubleValue REWIND_SECONDS;
    public static ModConfigSpec.DoubleValue REWIND_COOLDOWN;
    public static ModConfigSpec.DoubleValue HISTORY_MOB_RANGE;

    // accelerate (TOGGLE)
    public static ModConfigSpec.DoubleValue ACCELERATE_COST;
    public static ModConfigSpec.DoubleValue ACCELERATE_UPKEEP;
    public static ModConfigSpec.DoubleValue ACCELERATE_COOLDOWN;
    public static ModConfigSpec.DoubleValue ACCELERATE_RATE;

    // fast_forward (special of accelerate, INSTANT)
    public static ModConfigSpec.DoubleValue FAST_FORWARD_COST;
    public static ModConfigSpec.DoubleValue FAST_FORWARD_SECONDS;
    public static ModConfigSpec.DoubleValue FAST_FORWARD_COOLDOWN;

    // decelerate (INSTANT, projectile)
    public static ModConfigSpec.DoubleValue DECELERATE_COST;
    public static ModConfigSpec.DoubleValue DECELERATE_COOLDOWN;
    public static ModConfigSpec.DoubleValue DECELERATE_DAMAGE;
    public static ModConfigSpec.DoubleValue DECELERATE_SPEED;
    public static ModConfigSpec.DoubleValue DECELERATE_RANGE_SECONDS;
    public static ModConfigSpec.DoubleValue DECELERATE_SECONDS;
    public static ModConfigSpec.IntValue DECELERATE_SLOWNESS;
    public static ModConfigSpec.IntValue DECELERATE_FATIGUE;
    public static ModConfigSpec.DoubleValue DECELERATE_COOLDOWN_RATE;

    // stasis_field (special of decelerate, INSTANT)
    public static ModConfigSpec.DoubleValue STASIS_COST;
    public static ModConfigSpec.DoubleValue STASIS_COOLDOWN;
    public static ModConfigSpec.DoubleValue STASIS_RANGE;
    public static ModConfigSpec.DoubleValue STASIS_RADIUS;
    public static ModConfigSpec.DoubleValue STASIS_SECONDS;
    public static ModConfigSpec.IntValue STASIS_SLOWNESS;
    public static ModConfigSpec.DoubleValue STASIS_PROJECTILE_SPEED;

    // standstill (ultimate, INSTANT)
    public static ModConfigSpec.DoubleValue STANDSTILL_COST;
    public static ModConfigSpec.DoubleValue STANDSTILL_COOLDOWN;
    public static ModConfigSpec.DoubleValue STANDSTILL_RADIUS;
    public static ModConfigSpec.DoubleValue STANDSTILL_SECONDS;
    public static ModConfigSpec.DoubleValue STANDSTILL_BURNOUT;
    public static ModConfigSpec.DoubleValue STANDSTILL_DAMAGE_CAP;
    public static ModConfigSpec.IntValue STANDSTILL_FLIGHT_TICKS;

    private TimeConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Time Manipulation (LEGENDARY innate technique): marks to return to, the clock run fast and slow, and time stopped.",
                "Times are in seconds.").push("time_manipulation");

        b.comment("Rewind's target: the player or mob under the crosshair, in line of sight.").push("targeting");
        RANGE = b.defineInRange("range", 24.0, 1.0, 256.0);
        AIM_TOLERANCE = b.comment("How far off the crosshair (blocks) still counts as looking at someone.").defineInRange("aimTolerance", 0.3, 0.0, 2.0);
        b.pop();

        b.comment("First cast: mark the top of the block you're looking at (only you see it). Second cast: return to it, shedding",
                "effects gained since and healing healFraction of max health, never more than you've lost since marking. Never touches",
                "Malice. The cooldown starts on the return, or when the mark runs out.").push("point_maker");
        MARK_COST = b.defineInRange("markCost", 40.0, 0.0, 100_000.0);
        RETURN_COST = b.defineInRange("returnCost", 60.0, 0.0, 100_000.0);
        MARK_RANGE = b.comment("How far away (blocks) the marked block can be.").defineInRange("markRange", 32.0, 1.0, 256.0);
        MARK_SECONDS = b.comment("How long the mark lasts.").defineInRange("markSeconds", 15.0, 0.5, 600.0);
        RETURN_HEAL = b.comment("Share of max health healed on the return.").defineInRange("healFraction", 0.3, 0.0, 1.0);
        POINT_MAKER_COOLDOWN = b.defineInRange("cooldown", 12.0, 0.0, 3600.0);
        b.pop();

        b.comment("Special of Point Maker: snap the player or mob you're looking at back to where it was 3 seconds ago (position only).",
                "Positions are kept for players, and for mobs within historyMobRange of anyone holding Time Manipulation.").push("rewind");
        REWIND_COST = b.defineInRange("cost", 120.0, 0.0, 100_000.0);
        REWIND_SECONDS = b.comment("How far back.").defineInRange("seconds", 3.0, 0.5, 10.0);
        REWIND_COOLDOWN = b.comment("Point Maker's cooldown, started by a Rewind (they share it).").defineInRange("cooldown", 12.0, 0.0, 3600.0);
        HISTORY_MOB_RANGE = b.defineInRange("historyMobRange", 32.0, 0.0, 256.0);
        b.pop();

        b.comment("Toggle: your own clock runs faster, for Malice per second.").push("accelerate");
        ACCELERATE_COST = b.defineInRange("cost", 60.0, 0.0, 100_000.0);
        ACCELERATE_UPKEEP = b.defineInRange("upkeepPerSecond", 40.0, 0.0, 100_000.0);
        ACCELERATE_COOLDOWN = b.comment("Starts when it's switched off.").defineInRange("cooldown", 8.0, 0.0, 3600.0);
        ACCELERATE_RATE = b.comment("How many times as fast your own clock runs while it's on, for everything about you at once: walking, sprinting and",
                "swimming, attack speed, mining speed, using an item (eating, drinking, a bow, a crossbow, a shield) and your technique's other",
                "cooldowns. Jumping and gravity don't change.").defineInRange("rate", 2.0, 1.0, 10.0);
        b.pop();

        b.comment("Special of Accelerate: take time off every OTHER cooldown of your innate technique at once (never the ultimate's,",
                "Burnout or a shared ability's). Switches Accelerate off and puts it on their shared cooldown.").push("fast_forward");
        FAST_FORWARD_COST = b.defineInRange("cost", 320.0, 0.0, 100_000.0);
        FAST_FORWARD_SECONDS = b.comment("Seconds taken off each.").defineInRange("seconds", 6.0, 0.0, 600.0);
        FAST_FORWARD_COOLDOWN = b.comment("Accelerate's cooldown, started by Fast Forward (they share it).").defineInRange("cooldown", 20.0, 0.0, 3600.0);
        b.pop();

        b.comment("A spinning clock hand thrown like a blade. On a hit: damage, Slowness, Mining Fatigue, and the target's ability",
                "cooldowns run at cooldownRate for its time (bosses take the damage only).").push("decelerate");
        DECELERATE_COST = b.defineInRange("cost", 50.0, 0.0, 100_000.0);
        DECELERATE_COOLDOWN = b.defineInRange("cooldown", 6.0, 0.0, 3600.0);
        DECELERATE_DAMAGE = b.defineInRange("damage", 3.0, 0.0, 1000.0);
        DECELERATE_SPEED = b.comment("Blocks per tick.").defineInRange("speed", 2.0, 0.1, 10.0);
        DECELERATE_RANGE_SECONDS = b.comment("Flight time before it's gone.").defineInRange("flightSeconds", 1.5, 0.1, 10.0);
        DECELERATE_SECONDS = b.comment("How long the slow lasts.").defineInRange("seconds", 5.0, 0.0, 60.0);
        DECELERATE_SLOWNESS = b.comment("Slowness amplifier (0 = Slowness I).").defineInRange("slownessAmplifier", 1, 0, 9);
        DECELERATE_FATIGUE = b.comment("Mining Fatigue amplifier.").defineInRange("miningFatigueAmplifier", 1, 0, 9);
        DECELERATE_COOLDOWN_RATE = b.comment("How fast the target's cooldowns run meanwhile (0.5 = half speed).").defineInRange("cooldownRate", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Special of Decelerate: a field where the block you're looking at is. Heavy Slowness on everything inside, and",
                "projectiles inside crawl at projectileSpeed of their speed (same direction), full speed again once out or when it ends.")
                .push("stasis_field");
        STASIS_COST = b.defineInRange("cost", 140.0, 0.0, 100_000.0);
        STASIS_COOLDOWN = b.comment("Decelerate's cooldown, started by Stasis Field (they share it).").defineInRange("cooldown", 14.0, 0.0, 3600.0);
        STASIS_RANGE = b.comment("How far away (blocks) its centre can be.").defineInRange("range", 32.0, 1.0, 256.0);
        STASIS_RADIUS = b.defineInRange("radius", 4.0, 0.5, 32.0);
        STASIS_SECONDS = b.defineInRange("seconds", 5.0, 0.1, 60.0);
        STASIS_SLOWNESS = b.comment("Slowness amplifier inside.").defineInRange("slownessAmplifier", 4, 0, 9);
        STASIS_PROJECTILE_SPEED = b.defineInRange("projectileSpeed", 0.05, 0.0, 1.0);
        b.pop();

        b.comment("Ultimate: time stops round you. Everything within radius freezes for its time except you and anyone holding Time",
                "Manipulation; damage to the frozen lands at once when time resumes, as one hit (at most damageCap). Then Burnout.")
                .push("standstill");
        STANDSTILL_COST = b.defineInRange("cost", 400.0, 0.0, 100_000.0);
        STANDSTILL_COOLDOWN = b.defineInRange("cooldown", 180.0, 0.0, 3600.0);
        STANDSTILL_RADIUS = b.defineInRange("radius", 48.0, 1.0, 256.0);
        STANDSTILL_SECONDS = b.defineInRange("seconds", 3.0, 0.5, 60.0);
        STANDSTILL_BURNOUT = b.comment("Burnout on the caster afterwards (DESIGN §3.25).").defineInRange("burnoutSeconds", 20.0, 0.0, 3600.0);
        STANDSTILL_DAMAGE_CAP = b.comment("The most the one hit on resume can be.").defineInRange("damageCap", 40.0, 0.0, 10_000.0);
        STANDSTILL_FLIGHT_TICKS = b.comment("Ticks a projectile thrown into it flies before it hangs in the air too.")
                .defineInRange("projectileFlightTicks", 3, 0, 100);
        b.pop();
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
