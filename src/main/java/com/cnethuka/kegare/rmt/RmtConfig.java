package com.cnethuka.kegare.rmt;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reverse Malice Technique's numbers: section {@code [reverse_malice_technique]} of {@code kegare-server.toml}. */
public final class RmtConfig {
    public static ModConfigSpec.DoubleValue COST;
    public static ModConfigSpec.DoubleValue UPKEEP;
    public static ModConfigSpec.DoubleValue HEAL_PER_SECOND;
    public static ModConfigSpec.DoubleValue COOLDOWN;
    public static ModConfigSpec.DoubleValue MAX_DURATION;

    // The green orb (unlock)
    public static ModConfigSpec.DoubleValue ORB_HEALTH_THRESHOLD;
    public static ModConfigSpec.DoubleValue ORB_CHANCE;
    public static ModConfigSpec.DoubleValue ORB_ROLL_COOLDOWN;
    public static ModConfigSpec.DoubleValue ORB_LIFETIME;
    public static ModConfigSpec.DoubleValue ORB_LOOK_SECONDS;
    public static ModConfigSpec.DoubleValue ORB_LOOK_ANGLE;
    public static ModConfigSpec.DoubleValue ORB_DISTANCE;
    public static ModConfigSpec.DoubleValue ORB_SIDE_ANGLE;
    public static ModConfigSpec.DoubleValue ORB_UNLOCK_HEAL;

    private RmtConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Reverse Malice Technique (shared ability, CHANNELED): heal while held. Locked until found.",
                "Severed players can never unlock or use it. Times are in seconds.").push("reverse_malice_technique");
        COST = b.comment("Malice to start the channel.").defineInRange("cost", 10.0, 0.0, 100_000.0);
        UPKEEP = b.comment("Malice drained per second while healing (about double a typical attack's cost).")
                .defineInRange("upkeepPerSecond", 24.0, 0.0, 100_000.0);
        HEAL_PER_SECOND = b.comment("Health restored at the end of every full second held (2 = one heart).")
                .defineInRange("healPerSecond", 2.0, 0.0, 1000.0);
        COOLDOWN = b.comment("Starts when the channel ends (release, out of Malice, interrupted, fully healed).")
                .defineInRange("cooldown", 15.0, 0.0, 3600.0);
        MAX_DURATION = b.comment("Longest channel; 0 = until released, fully healed or out of Malice.")
                .defineInRange("maxDuration", 0.0, 0.0, 3600.0);

        b.comment("The green orb: how Reverse Malice Technique is found. Only its finder sees it.").push("orb");
        ORB_HEALTH_THRESHOLD = b.comment("A hit from a player that leaves you at this much health or less can bring the orb (10 = 5 hearts).")
                .defineInRange("healthThreshold", 10.0, 0.0, 1024.0);
        ORB_CHANCE = b.comment("Chance (0-1) per roll.").defineInRange("chance", 0.15, 0.0, 1.0);
        ORB_ROLL_COOLDOWN = b.comment("At most one roll per this many seconds per player.").defineInRange("rollCooldown", 60.0, 0.0, 86_400.0);
        ORB_LIFETIME = b.comment("The orb fades after this many seconds.").defineInRange("lifetime", 5.0, 0.5, 600.0);
        ORB_LOOK_SECONDS = b.comment("Look at it this long (without looking away) to take it in.").defineInRange("lookSeconds", 1.0, 0.05, 60.0);
        ORB_LOOK_ANGLE = b.comment("How far off-centre (degrees) still counts as looking at it.").defineInRange("lookAngle", 10.0, 1.0, 45.0);
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
