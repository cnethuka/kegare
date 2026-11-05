package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Puppet Manipulation's numbers: section {@code [puppet_manipulation]} of {@code kegare-server.toml}. Times are in seconds. */
public final class PuppetConfig {
    // the puppet entity
    public static ModConfigSpec.DoubleValue SCALE;
    public static ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static ModConfigSpec.DoubleValue MELEE_DAMAGE;
    public static ModConfigSpec.IntValue SAFETY_CAP;
    public static ModConfigSpec.DoubleValue FOLLOW_DISTANCE;
    public static ModConfigSpec.DoubleValue FOLLOW_TELEPORT_DISTANCE;
    public static ModConfigSpec.DoubleValue SPIN_DELAY;

    // puppet_creation (CHARGED): one cost and health per charge tier
    public static ModConfigSpec.DoubleValue CREATION_COOLDOWN;
    public static ModConfigSpec.DoubleValue CREATION_HALF_CHARGE;
    public static ModConfigSpec.DoubleValue CREATION_FULL_CHARGE;
    public static final ModConfigSpec.DoubleValue[] TIER_COST = new ModConfigSpec.DoubleValue[PuppetTier.values().length];
    public static final ModConfigSpec.DoubleValue[] TIER_HEALTH = new ModConfigSpec.DoubleValue[PuppetTier.values().length];

    // possess (special of puppet_creation, INSTANT)
    public static ModConfigSpec.DoubleValue POSSESS_COST;
    public static ModConfigSpec.DoubleValue POSSESS_RANGE;
    public static ModConfigSpec.DoubleValue POSSESS_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue BACKLASH_NAUSEA;
    public static ModConfigSpec.DoubleValue BACKLASH_STUN;
    public static ModConfigSpec.DoubleValue BACKLASH_MALICE;

    // directive (INSTANT) and focus (special of directive, INSTANT)
    public static ModConfigSpec.DoubleValue DIRECTIVE_COST;
    public static ModConfigSpec.DoubleValue DIRECTIVE_COOLDOWN;
    public static ModConfigSpec.DoubleValue DIRECTIVE_RANGE;
    public static ModConfigSpec.DoubleValue FOCUS_COST;
    public static ModConfigSpec.DoubleValue FOCUS_TARGET_RANGE;

    private PuppetConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Puppet Manipulation (EPIC innate technique): half-size puppets of yourself, charged with Malice. Times are in seconds.")
                .push("puppet_manipulation");

        b.comment("The puppets themselves.").push("puppet");
        SCALE = b.comment("Size next to their owner (the 1.21 scale attribute). Never changes, except during Stand-In.")
                .defineInRange("scale", 0.5, 0.1, 1.0);
        MOVEMENT_SPEED = b.comment("Movement speed attribute. Players walk at 0.1 and sprint at 0.13; mobs move at about 2/3 of their attribute in"
                + " player terms, so 0.32 keeps a little ahead of a running player.").defineInRange("movementSpeed", 0.32, 0.01, 2.0);
        MELEE_DAMAGE = b.comment("Damage of a puppet's punch (and of a possessed puppet's).").defineInRange("meleeDamage", 1.0, 0.0, 100.0);
        SAFETY_CAP = b.comment("Lag protection only, not a balance limit: the most puppets one player can have at once (loaded or not).",
                "At the cap, Puppet Creation fizzles for free.").defineInRange("safetyCap", 50, 1, 10_000);
        FOLLOW_DISTANCE = b.comment("Following puppets walk back to their owner once they are this far away (blocks).")
                .defineInRange("followDistance", 3.0, 1.0, 32.0);
        FOLLOW_TELEPORT_DISTANCE = b.comment("Following puppets left this far behind (blocks) catch up at once, like tamed pets.")
                .defineInRange("followTeleportDistance", 24.0, 4.0, 128.0);
        SPIN_DELAY = b.comment("A puppet standing still spins (\"kuru kuru\") after this long. Possessed puppets never spin.")
                .defineInRange("spinDelay", 0.5, 0.0, 60.0);
        b.pop();

        b.comment("Hold to charge, release to make a puppet next to you. The charge picks its tier:",
                "a tap (released before halfCharge), half (before fullCharge) or full.").push("puppet_creation");
        CREATION_COOLDOWN = b.defineInRange("cooldown", 2.0, 0.0, 3600.0);
        CREATION_HALF_CHARGE = b.comment("Charge (seconds) for a half-strength puppet.").defineInRange("halfCharge", 0.6, 0.05, 60.0);
        CREATION_FULL_CHARGE = b.comment("Charge (seconds) for a full-strength puppet (also the full charge bar).")
                .defineInRange("fullCharge", 1.5, 0.1, 60.0);
        double[] costs = {10.0, 25.0, 45.0};
        double[] health = {2.0, 4.0, 8.0};
        for (PuppetTier tier : PuppetTier.values()) {
            b.push(tier.serializedName());
            TIER_COST[tier.ordinal()] = b.defineInRange("cost", costs[tier.ordinal()], 0.0, 100_000.0);
            TIER_HEALTH[tier.ordinal()] = b.defineInRange("health", health[tier.ordinal()], 1.0, 1024.0);
            b.pop();
        }
        b.pop();

        b.comment("Special of Puppet Creation: move your consciousness into a puppet (the one you look at, else your nearest).",
                "You become it; your body stays behind as a defenceless shell. R again returns.").push("possess");
        POSSESS_COST = b.defineInRange("cost", 15.0, 0.0, 100_000.0);
        POSSESS_RANGE = b.comment("How far away (blocks) the puppet can be.").defineInRange("range", 32.0, 1.0, 256.0);
        POSSESS_MAX_DISTANCE = b.comment("Wander further than this (blocks) from your shell and you snap back.")
                .defineInRange("maxDistance", 64.0, 4.0, 1024.0);
        b.comment("If the possessed puppet dies, you snap back with backlash.").push("backlash");
        BACKLASH_NAUSEA = b.comment("Nausea, seconds.").defineInRange("nausea", 4.0, 0.0, 60.0);
        BACKLASH_STUN = b.comment("Stun (the shared stun: no casting, charges and channels interrupted), seconds.")
                .defineInRange("stun", 1.0, 0.0, 30.0);
        BACKLASH_MALICE = b.comment("Malice lost.").defineInRange("malice", 20.0, 0.0, 100_000.0);
        b.pop();
        b.pop();

        b.comment("Give every puppet in range the next order: Follow, Stay, Guard (attack whoever damages you), Go (walk to the",
                "block you're looking at).").push("directive");
        DIRECTIVE_COST = b.defineInRange("cost", 5.0, 0.0, 100_000.0);
        DIRECTIVE_COOLDOWN = b.defineInRange("cooldown", 1.0, 0.0, 3600.0);
        DIRECTIVE_RANGE = b.comment("Puppets this close (blocks) hear Directive and Focus; also how far Go can point.")
                .defineInRange("range", 32.0, 1.0, 256.0);
        b.pop();

        b.comment("Special of Directive: every puppet in range swarms the player or mob you're looking at.").push("focus");
        FOCUS_COST = b.defineInRange("cost", 10.0, 0.0, 100_000.0);
        FOCUS_TARGET_RANGE = b.comment("How far away (blocks) the target can be.").defineInRange("targetRange", 32.0, 1.0, 256.0);
        b.pop();

        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
