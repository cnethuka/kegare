package com.cnethuka.kegare.progression;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Every progression number: section {@code [progression]} of {@code kegare-server.toml}. Times are in seconds. */
public final class ProgressionConfig {
    // Fights
    public static ModConfigSpec.DoubleValue FIGHT_TIMEOUT;
    public static ModConfigSpec.DoubleValue WIN_SHARE;
    public static ModConfigSpec.DoubleValue OPENING_CAST_SECONDS;

    private ProgressionConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Grade, Assessment, Refinement and Insight. None of this changes damage, costs or Malice capacity.").push("progression");

        b.comment("Shared fight tracking. A fight is between two players, and everything below reads from it.").push("fights");
        FIGHT_TIMEOUT = b.comment("A fight ends after this long with no damage between the two players (or on a death or a logout).")
                .defineInRange("timeout", 30.0, 1.0, 3600.0);
        WIN_SHARE = b.comment("You win when your opponent dies during the fight and you dealt at least this share (0..1) of the",
                        "damage they took from players in it. Wins only affect Grade XP, never Assessment.")
                .defineInRange("winShare", 0.25, 0.0, 1.0);
        OPENING_CAST_SECONDS = b.comment("Malice spent this long before a fight starts still counts as spent in it (the cast that opened it).")
                .defineInRange("openingCastSeconds", 5.0, 0.0, 60.0);
        b.pop();

        b.pop();
    }

    public static long ticks(ModConfigSpec.DoubleValue seconds) {
        return Math.round(seconds.get() * 20.0);
    }
}
