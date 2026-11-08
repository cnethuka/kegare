package com.cnethuka.kegare.severed;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A Severed player's body (DESIGN §3.27): section {@code [severed]} of {@code kegare-server.toml}. None of it grows with Grade or
 * Refinement: their body is complete from birth. Every number is a starting point to tune live.
 */
public final class SeveredConfig {
    // body
    public static ModConfigSpec.DoubleValue MELEE_DAMAGE;
    public static ModConfigSpec.DoubleValue TAKEN_PHYSICAL;
    public static ModConfigSpec.DoubleValue TAKEN_TECHNIQUE;
    public static ModConfigSpec.DoubleValue TAKEN_FALL;
    public static ModConfigSpec.DoubleValue EXHAUSTION;
    // senses
    public static ModConfigSpec.DoubleValue SENSES_RANGE;

    private SeveredConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Severed players: no Malice, and a superhuman body in exchange. None of it grows with Grade or Refinement.").push("severed");

        b.comment("Always on.").push("body");
        MELEE_DAMAGE = b.comment("Strength: their melee hits (fists and weapons) deal this times as much, before a Reinforced Strike's own bonus.")
                .defineInRange("meleeDamage", 1.25, 1.0, 10.0);
        TAKEN_PHYSICAL = b.comment("Toughness: share of damage they take from everything that isn't a technique or a fall (hits, arrows, fire,",
                "explosions). The void, /kill and starving are never reduced.").defineInRange("takenPhysical", 0.75, 0.0, 1.0);
        TAKEN_TECHNIQUE = b.comment("Share of damage they take from techniques (any Kegare damage type).").defineInRange("takenTechnique", 0.9, 0.0, 1.0);
        TAKEN_FALL = b.comment("Share of fall damage they take.").defineInRange("takenFall", 0.4, 0.0, 1.0);
        EXHAUSTION = b.comment("Stamina: share of normal hunger drain (sprinting, jumping, attacking, mining, healing) they feel. Blur's own",
                "drain isn't reduced.").defineInRange("exhaustion", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Senses: every player and mob this close shows to them as a faint outline through walls, still or moving, invisible",
                "or not.").push("senses");
        SENSES_RANGE = b.defineInRange("range", 16.0, 0.0, 64.0);
        b.pop();

        b.pop();
    }

    public static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
