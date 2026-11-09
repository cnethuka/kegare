package com.cnethuka.kegare.rmt;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reverse Malice Technique's numbers: section {@code [reverse_malice_technique]} of {@code kegare-server.toml}. */
public final class RmtConfig {
    public static ModConfigSpec.DoubleValue COST;
    public static ModConfigSpec.DoubleValue UPKEEP;
    public static ModConfigSpec.DoubleValue HEAL_PER_SECOND;
    public static ModConfigSpec.DoubleValue COOLDOWN;
    public static ModConfigSpec.DoubleValue MAX_DURATION;
    public static ModConfigSpec.DoubleValue OUTPUT_GRADE_BONUS;
    public static ModConfigSpec.DoubleValue OUTPUT_PEAK_BONUS;
    public static ModConfigSpec.DoubleValue FATIGUE_WEIGHT;
    public static ModConfigSpec.DoubleValue MAX_FATIGUE;

    // The green orb (unlock)
    public static ModConfigSpec.DoubleValue ORB_HEALTH_THRESHOLD;
    public static ModConfigSpec.DoubleValue ORB_CHANCE;
    public static ModConfigSpec.DoubleValue ORB_ROLL_COOLDOWN;
    public static ModConfigSpec.DoubleValue ORB_LIFETIME;
    public static ModConfigSpec.DoubleValue ORB_FOLLOW_SECONDS;
    public static ModConfigSpec.IntValue ORB_FOLLOW_REWIND;
    public static ModConfigSpec.DoubleValue ORB_LOOK_ANGLE;
    public static ModConfigSpec.IntValue ORB_PATH_LEGS;
    public static ModConfigSpec.DoubleValue ORB_PATH_STEP;
    public static ModConfigSpec.DoubleValue ORB_PATH_YAW_RANGE;
    public static ModConfigSpec.DoubleValue ORB_PATH_PITCH_RANGE;
    public static ModConfigSpec.DoubleValue ORB_PATH_MAX_TURN;
    public static ModConfigSpec.DoubleValue ORB_DISTANCE;
    public static ModConfigSpec.DoubleValue ORB_SIDE_ANGLE;
    public static ModConfigSpec.DoubleValue ORB_UNLOCK_HEAL;
    public static ModConfigSpec.DoubleValue ORB_INSIGHT_BONUS;
    public static ModConfigSpec.DoubleValue ORB_CHANCE_CAP;
    public static ModConfigSpec.DoubleValue LOW_HEALTH;
    public static ModConfigSpec.DoubleValue SURVIVE_SECONDS;

    private RmtConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Reverse Malice Technique (shared ability, CHANNELED): heal while held. Locked until found.",
                "Severed players can never unlock or use it. Times are in seconds.").push("reverse_malice_technique");
        COST = b.comment("Malice to start the channel.").defineInRange("cost", 40.0, 0.0, 100_000.0);
        UPKEEP = b.comment("Malice drained per second while healing (about double a typical attack's cost).")
                .defineInRange("upkeepPerSecond", 96.0, 0.0, 100_000.0);
        HEAL_PER_SECOND = b.comment("Health restored per second held at an output of 1 (2 = one heart), a little every tick, so damage is",
                        "healed as it lands. The real rate is this times your output.")
                .defineInRange("healPerSecond", 2.0, 0.0, 1000.0);
        COOLDOWN = b.comment("Starts when the channel ends (release, out of Malice, interrupted).")
                .defineInRange("cooldown", 15.0, 0.0, 3600.0);
        MAX_DURATION = b.comment("Longest channel; 0 = until released or out of Malice. It keeps running at full health.")
                .defineInRange("maxDuration", 0.0, 0.0, 3600.0);

        b.comment("Output: how strongly it heals, 1 + a Grade bonus + a Peak Refinement bonus. An exception to progression only",
                "unlocking things: in canon, reverse healing rides on the user's output.").push("output");
        OUTPUT_GRADE_BONUS = b.comment("Added at Special Grade; 0 at Grade 4, evenly up the ladder between.").defineInRange("gradeBonus", 0.3, 0.0, 10.0);
        OUTPUT_PEAK_BONUS = b.comment("Added at the top Peak Refinement level; 0 at 0, evenly between.").defineInRange("peakBonus", 0.3, 0.0, 10.0);
        b.pop();

        b.comment("Output Insight it pays (amounts are in [progression.insight]).").push("insight");
        LOW_HEALTH = b.comment("rmt_survived_low: at this much health or less during your own channel (6 = 3 hearts), healed above it...")
                .defineInRange("lowHealth", 6.0, 0.0, 1024.0);
        SURVIVE_SECONDS = b.comment("...and alive this many seconds later.").defineInRange("surviveSeconds", 10.0, 0.0, 600.0);
        b.pop();

        b.comment("Injury fatigue: damage taken in a fight drags output down. fatigue = fatigueWeight x (damage taken / max health),",
                "at most maxFatigue; output is multiplied by (1 - fatigue). Healing doesn't take it away. It resets when the fight ends,",
                "on death and on logout.").push("fatigue");
        FATIGUE_WEIGHT = b.defineInRange("fatigueWeight", 0.15, 0.0, 10.0);
        MAX_FATIGUE = b.comment("Never more than this, so it never stops healing outright.").defineInRange("maxFatigue", 0.6, 0.0, 0.95);
        b.pop();

        b.comment("The green orb: how Reverse Malice Technique is found. Only its finder sees it.").push("orb");
        ORB_HEALTH_THRESHOLD = b.comment("A hit from a player that leaves you at this much health or less can bring the orb (10 = 5 hearts).")
                .defineInRange("healthThreshold", 10.0, 0.0, 1024.0);
        ORB_CHANCE = b.comment("Chance (0-1) per roll, before Output Insight.").defineInRange("chance", 0.15, 0.0, 1.0);
        ORB_INSIGHT_BONUS = b.comment("Added to the chance per point of Output Insight.").defineInRange("insightBonus", 0.005, 0.0, 1.0);
        ORB_CHANCE_CAP = b.comment("The chance never goes past this with Insight.").defineInRange("chanceCap", 0.3, 0.0, 1.0);
        ORB_ROLL_COOLDOWN = b.comment("At most one roll per this many seconds per player.").defineInRange("rollCooldown", 60.0, 0.0, 86_400.0);
        ORB_LIFETIME = b.comment("The orb fades after this many seconds. Time spent following it doesn't count.").defineInRange("lifetime", 5.0, 0.5, 600.0);
        ORB_FOLLOW_SECONDS = b.comment("Looking at it sends it off along a random route, and it only moves on while you keep looking.",
                        "This is how long the whole route takes. Reach the end to take it in.")
                .defineInRange("followSeconds", 3.0, 0.05, 60.0);
        ORB_FOLLOW_REWIND = b.comment("Lose sight of it and it runs back along the route, this many times as fast.")
                .defineInRange("followRewind", 3, 0, 100);
        ORB_LOOK_ANGLE = b.comment("How far off-centre (degrees) still counts as looking at it.").defineInRange("lookAngle", 10.0, 1.0, 45.0);
        ORB_PATH_LEGS = b.comment("How many steps the route takes. It turns between each.").defineInRange("pathLegs", 5, 1, 16);
        ORB_PATH_STEP = b.comment("How far each step goes, in degrees of your view. With followSeconds, this sets how fast you have to track it:",
                        "pathLegs x pathStep / followSeconds degrees a second.")
                .defineInRange("pathStep", 18.0, 1.0, 60.0);
        ORB_PATH_YAW_RANGE = b.comment("How far left and right (degrees) of where you were looking the route can wander.").defineInRange("pathYawRange", 45.0, 5.0, 90.0);
        ORB_PATH_PITCH_RANGE = b.comment("How far up and down (degrees) it can wander.").defineInRange("pathPitchRange", 25.0, 5.0, 45.0);
        ORB_PATH_MAX_TURN = b.comment("The sharpest turn between two steps (degrees). The gentlest is a third of this.").defineInRange("pathMaxTurn", 120.0, 3.0, 170.0);
        ORB_DISTANCE = b.comment("How far in front of the player it appears, in blocks (closer if a wall is in the way).")
                .defineInRange("distance", 3.0, 1.0, 16.0);
        ORB_SIDE_ANGLE = b.comment("Degrees to the left or right (random) of where the player looks: in view, but off the crosshair,",
                        "so taking it in is a choice and not an accident mid-fight. Keep it above lookAngle.")
                .defineInRange("sideAngle", 25.0, 0.0, 60.0);
        ORB_UNLOCK_HEAL = b.comment("Health restored when it is taken in (never above max).").defineInRange("unlockHeal", 8.0, 0.0, 1024.0);
        b.pop();
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
