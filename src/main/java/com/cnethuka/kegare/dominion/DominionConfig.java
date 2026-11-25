package com.cnethuka.kegare.dominion;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Dominions' numbers (DESIGN §3.35): {@code [dominion]} of {@code kegare-server.toml}. Times in seconds, distances in blocks. */
public final class DominionConfig {
    // The Dominion Meter
    public static ModConfigSpec.DoubleValue METER_PER_DAMAGE_DEALT;
    public static ModConfigSpec.DoubleValue METER_PER_DAMAGE_TAKEN;
    public static ModConfigSpec.DoubleValue METER_DRAIN;
    // Opening and the barrier
    public static ModConfigSpec.DoubleValue OPEN_SECONDS;
    public static ModConfigSpec.DoubleValue FULL_RADIUS;
    public static ModConfigSpec.DoubleValue FULL_DURATION;
    public static ModConfigSpec.DoubleValue FULL_BARRIER;
    public static ModConfigSpec.DoubleValue DOMINION_BURNOUT;
    public static ModConfigSpec.DoubleValue MELEE_REACH;
    public static ModConfigSpec.DoubleValue BLAST_REACH;
    public static ModConfigSpec.DoubleValue PROJECTILE_STRENGTH;

    private DominionConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Dominions: a sorcerer's inner space expanded round them inside a barrier. A full Dominion carries its technique and is",
                "that technique's ultimate; an Incomplete Dominion is the inner space with Malice alone, which any Malice user can learn.")
                .push("dominion");

        b.comment("The Dominion Meter, 0 to 100, private to its owner. It fills from damage dealt to and taken from players in fights, and",
                "drains slowly out of combat. A full Dominion takes a full meter; an Incomplete Dominion takes part of it.").push("meter");
        METER_PER_DAMAGE_DEALT = b.comment("Meter gained per point of damage dealt to a player in a fight (after their defences).")
                .defineInRange("perDamageDealt", 2.0, 0.0, 100.0);
        METER_PER_DAMAGE_TAKEN = b.comment("Meter gained per point of damage taken from a player in a fight.")
                .defineInRange("perDamageTaken", 1.5, 0.0, 100.0);
        METER_DRAIN = b.comment("Meter lost per second while out of combat.").defineInRange("drainPerSecond", 0.25, 0.0, 100.0);
        b.pop();

        OPEN_SECONDS = b.comment("How long the barrier takes to rise and close. Whoever is inside when it closes is trapped; anyone can still",
                "get out before that.").defineInRange("openSeconds", 1.0, 0.05, 10.0);
        MELEE_REACH = b.comment("A swing hits the barrier when the shell is this close along the look (no block or body nearer).")
                .defineInRange("meleeReach", 3.0, 0.5, 8.0);
        BLAST_REACH = b.comment("An area blast this close to the shell, inside or outside, hits the barrier (full strength at the shell, less",
                "further off).").defineInRange("blastReach", 2.5, 0.0, 16.0);
        PROJECTILE_STRENGTH = b.comment("A plain projectile stopped by the barrier hits it this hard (the Impact System's strength). A cast's",
                "projectile adds the Malice behind its cast, by the Impact System's rule.").defineInRange("projectileStrength", 0.3, 0.0, 100.0);

        b.comment("A full Dominion: a technique's ultimate (none ships yet).").push("full");
        FULL_RADIUS = b.comment("The barrier's radius.").defineInRange("radius", 16.0, 2.0, 64.0);
        FULL_DURATION = b.comment("How long it stays open once closed.").defineInRange("durationSeconds", 30.0, 1.0, 600.0);
        FULL_BARRIER = b.comment("Barrier strength: the Impact System's strength of the hits it takes before it breaks.")
                .defineInRange("barrierStrength", 40.0, 0.1, 100_000.0);
        DOMINION_BURNOUT = b.comment("Burnout on the caster afterwards. Keep it equal to",
                "reverse_malice_technique.repair.dominionBurnoutAssumed, so the brain's repair math stays right.")
                .defineInRange("dominionBurnout", 60.0, 0.0, 3600.0);
        b.pop();
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
