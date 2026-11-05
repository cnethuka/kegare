package com.cnethuka.kegare.rmt;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Reverse Malice Technique's numbers: section {@code [reverse_malice_technique]} of {@code kegare-server.toml}. */
public final class RmtConfig {
    public static ModConfigSpec.DoubleValue COST;
    public static ModConfigSpec.DoubleValue UPKEEP;
    public static ModConfigSpec.DoubleValue HEAL_PER_SECOND;
    public static ModConfigSpec.DoubleValue COOLDOWN;
    public static ModConfigSpec.DoubleValue MAX_DURATION;

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
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
