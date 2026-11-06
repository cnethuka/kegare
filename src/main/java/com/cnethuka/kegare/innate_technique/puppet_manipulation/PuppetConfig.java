package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Puppet Manipulation's numbers: section {@code [puppet_manipulation]} of {@code kegare-server.toml}. Times are in seconds. */
public final class PuppetConfig {
    // the puppet entity
    public static ModConfigSpec.DoubleValue SCALE;
    public static ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static ModConfigSpec.DoubleValue MELEE_DAMAGE;
    public static ModConfigSpec.IntValue MAX_PUPPETS;
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

    // curio_burst (INSTANT) and chain_burst (special of curio_burst, INSTANT)
    public static ModConfigSpec.DoubleValue CURIO_COST;
    public static ModConfigSpec.DoubleValue CURIO_COOLDOWN;
    public static ModConfigSpec.DoubleValue CURIO_RANGE;
    public static final ModConfigSpec.DoubleValue[] CURIO_DAMAGE = new ModConfigSpec.DoubleValue[PuppetTier.values().length];
    public static final ModConfigSpec.DoubleValue[] CURIO_RADIUS = new ModConfigSpec.DoubleValue[PuppetTier.values().length];
    public static ModConfigSpec.DoubleValue CURIO_KNOCKBACK;
    public static ModConfigSpec.DoubleValue CHAIN_COST;
    public static ModConfigSpec.DoubleValue CHAIN_RADIUS;
    public static ModConfigSpec.DoubleValue CHAIN_DELAY;

    // understudy (INSTANT) and stand_in (special of understudy, INSTANT)
    public static ModConfigSpec.DoubleValue UNDERSTUDY_COST;
    public static ModConfigSpec.DoubleValue UNDERSTUDY_COOLDOWN;
    public static ModConfigSpec.DoubleValue UNDERSTUDY_RANGE;
    public static ModConfigSpec.DoubleValue STAND_IN_COST;
    public static ModConfigSpec.DoubleValue STAND_IN_DURATION;
    public static ModConfigSpec.DoubleValue STAND_IN_GROW;

    // strings (INSTANT, two stages) and zip_leash (special of strings, INSTANT)
    public static ModConfigSpec.DoubleValue STRINGS_FIRE_COST;
    public static ModConfigSpec.DoubleValue STRINGS_REEL_COST;
    public static ModConfigSpec.DoubleValue STRINGS_TIE_COST;
    public static ModConfigSpec.DoubleValue STRINGS_ZIP_COST;
    public static ModConfigSpec.DoubleValue STRINGS_LEASH_COST;
    public static ModConfigSpec.DoubleValue STRINGS_COOLDOWN;
    public static ModConfigSpec.DoubleValue STRINGS_FIRE_COOLDOWN;
    public static ModConfigSpec.DoubleValue STRINGS_RANGE;
    public static ModConfigSpec.DoubleValue STRINGS_LIFE;
    public static ModConfigSpec.DoubleValue STRINGS_SNAP_RANGE;
    public static ModConfigSpec.DoubleValue STRINGS_PULL_SPEED;
    public static ModConfigSpec.DoubleValue STRINGS_PULL_TIMEOUT;
    public static ModConfigSpec.DoubleValue STRINGS_ARRIVE;
    public static ModConfigSpec.DoubleValue STRINGS_COLLIDE;
    public static ModConfigSpec.DoubleValue STRINGS_TIE_DAMAGE;
    public static ModConfigSpec.DoubleValue STRINGS_TETHER_DISTANCE;
    public static ModConfigSpec.DoubleValue STRINGS_TETHER_DURATION;
    public static ModConfigSpec.DoubleValue STRINGS_TETHER_SPEED;
    public static ModConfigSpec.DoubleValue STRINGS_LEASH_DISTANCE;
    public static ModConfigSpec.DoubleValue STRINGS_LEASH_DURATION;
    public static ModConfigSpec.DoubleValue STRINGS_LEASH_SLOW;

    private PuppetConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Puppet Manipulation (EPIC innate technique): half-size puppets of yourself, charged with Malice. Times are in seconds.")
                .push("puppet_manipulation");

        b.comment("The puppets themselves.").push("puppet");
        SCALE = b.comment("Size next to their owner (the 1.21 scale attribute). Never changes, except during Stand-In.")
                .defineInRange("scale", 0.5, 0.1, 1.0);
        MOVEMENT_SPEED = b.comment("Movement speed attribute (mob scale, not a player's): 0.42 walks a little faster than a walking player.",
                "Following puppets left behind run at 1.35x that to catch up, which keeps pace with a sprint.")
                .defineInRange("movementSpeed", 0.42, 0.01, 2.0);
        MELEE_DAMAGE = b.comment("Damage of a puppet's punch (and of a possessed puppet's).").defineInRange("meleeDamage", 1.0, 0.0, 100.0);
        MAX_PUPPETS = b.comment("The most puppets one player can have at once, counting ones in unloaded chunks. At the cap, Puppet Creation",
                "fizzles for free; a puppet that dies, dissolves or detonates frees its slot. Lowering this never removes puppets:",
                "a player over the new cap keeps theirs but can't make more until below it.").defineInRange("maxPuppets", 15, 1, 10_000);
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
        double[] costs = {40.0, 100.0, 180.0};
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
        POSSESS_COST = b.defineInRange("cost", 60.0, 0.0, 100_000.0);
        POSSESS_RANGE = b.comment("How far away (blocks) the puppet can be.").defineInRange("range", 32.0, 1.0, 256.0);
        POSSESS_MAX_DISTANCE = b.comment("Wander further than this (blocks) from your shell and you snap back.")
                .defineInRange("maxDistance", 64.0, 4.0, 1024.0);
        b.comment("If the possessed puppet dies, you snap back with backlash.").push("backlash");
        BACKLASH_NAUSEA = b.comment("Nausea, seconds.").defineInRange("nausea", 4.0, 0.0, 60.0);
        BACKLASH_STUN = b.comment("Stun (the shared stun: no casting, charges and channels interrupted), seconds.")
                .defineInRange("stun", 1.0, 0.0, 30.0);
        BACKLASH_MALICE = b.comment("Malice lost.").defineInRange("malice", 80.0, 0.0, 100_000.0);
        b.pop();
        b.pop();

        b.comment("Give every puppet in range the next order: Follow, Stay, Guard (attack whoever damages you), Go (walk to the",
                "block you're looking at).").push("directive");
        DIRECTIVE_COST = b.defineInRange("cost", 20.0, 0.0, 100_000.0);
        DIRECTIVE_COOLDOWN = b.defineInRange("cooldown", 1.0, 0.0, 3600.0);
        DIRECTIVE_RANGE = b.comment("Puppets this close (blocks) hear Directive and Focus; also how far Go can point.")
                .defineInRange("range", 32.0, 1.0, 256.0);
        b.pop();

        b.comment("Special of Directive: every puppet in range swarms the player or mob you're looking at.").push("focus");
        FOCUS_COST = b.defineInRange("cost", 40.0, 0.0, 100_000.0);
        FOCUS_TARGET_RANGE = b.comment("How far away (blocks) the target can be.").defineInRange("targetRange", 32.0, 1.0, 256.0);
        b.pop();

        b.comment("Detonate the puppet you're looking at. Damage and radius grow with its charge tier; full damage at the",
                "centre, half at the edge. Never hurts you or your puppets; breaks no blocks.").push("curio_burst");
        CURIO_COST = b.defineInRange("cost", 32.0, 0.0, 100_000.0);
        CURIO_COOLDOWN = b.defineInRange("cooldown", 4.0, 0.0, 3600.0);
        CURIO_RANGE = b.comment("How far away (blocks) the puppet can be.").defineInRange("range", 32.0, 1.0, 256.0);
        double[] damage = {4.0, 7.0, 12.0};
        double[] radius = {2.5, 3.0, 4.0};
        for (PuppetTier tier : PuppetTier.values()) {
            b.push(tier.serializedName());
            CURIO_DAMAGE[tier.ordinal()] = b.defineInRange("damage", damage[tier.ordinal()], 0.0, 1000.0);
            CURIO_RADIUS[tier.ordinal()] = b.defineInRange("radius", radius[tier.ordinal()], 0.5, 32.0);
            b.pop();
        }
        CURIO_KNOCKBACK = b.comment("Push away from the blast (bosses are never pushed).").defineInRange("knockback", 0.6, 0.0, 10.0);
        b.pop();

        b.comment("Special of Curio Burst: every puppet of yours near what you're looking at detonates, one after another.")
                .push("chain_burst");
        CHAIN_COST = b.defineInRange("cost", 64.0, 0.0, 100_000.0);
        CHAIN_RADIUS = b.comment("Puppets this close (blocks) to the target go off.").defineInRange("radius", 8.0, 1.0, 64.0);
        CHAIN_DELAY = b.comment("Between one detonation and the next, seconds.").defineInRange("delay", 0.25, 0.0, 5.0);
        b.pop();

        b.comment("Swap places with the puppet you're looking at (fires SwapTargetEvent: a technique that blocks swaps blocks it).")
                .push("understudy");
        UNDERSTUDY_COST = b.defineInRange("cost", 40.0, 0.0, 100_000.0);
        UNDERSTUDY_COOLDOWN = b.defineInRange("cooldown", 6.0, 0.0, 3600.0);
        UNDERSTUDY_RANGE = b.comment("How far away (blocks) the puppet can be. Stand-In uses it too.").defineInRange("range", 32.0, 1.0, 256.0);
        b.pop();

        b.comment("Special of Understudy: one puppet (the one you look at, else your nearest) grows to your size and copies your",
                "movements, held items, armor and name: it looks exactly like you. Then it shrinks back.").push("stand_in");
        STAND_IN_COST = b.defineInRange("cost", 80.0, 0.0, 100_000.0);
        STAND_IN_DURATION = b.defineInRange("duration", 8.0, 0.5, 600.0);
        STAND_IN_GROW = b.comment("How long growing (and shrinking back) takes.").defineInRange("grow", 0.4, 0.0, 5.0);
        b.pop();

        b.comment("Fire a string at the player, mob or puppet you're looking at, then use it: Reel In (cast key) or Zip (R), or, looking",
                "at a different target, Tie (cast key) or Leash (R). Bosses can be attached to but are never moved.").push("strings");
        STRINGS_FIRE_COST = b.comment("Stage 1: firing the string.").defineInRange("fireCost", 20.0, 0.0, 100_000.0);
        STRINGS_REEL_COST = b.defineInRange("reelCost", 48.0, 0.0, 100_000.0);
        STRINGS_TIE_COST = b.defineInRange("tieCost", 80.0, 0.0, 100_000.0);
        STRINGS_ZIP_COST = b.defineInRange("zipCost", 40.0, 0.0, 100_000.0);
        STRINGS_LEASH_COST = b.defineInRange("leashCost", 100.0, 0.0, 100_000.0);
        STRINGS_COOLDOWN = b.comment("Starts at stage 2, or when a string snaps or expires without one.").defineInRange("cooldown", 10.0, 0.0, 3600.0);
        STRINGS_FIRE_COOLDOWN = b.comment("After firing: just long enough that a double press doesn't fire and pull at once.")
                .defineInRange("fireCooldown", 0.25, 0.0, 60.0);
        STRINGS_RANGE = b.comment("How far away (blocks) a target can be: for firing, and for a Tie or Leash's second target.")
                .defineInRange("range", 24.0, 1.0, 256.0);
        STRINGS_LIFE = b.comment("How long an attached string waits for its second stage.").defineInRange("life", 8.0, 0.5, 600.0);
        STRINGS_SNAP_RANGE = b.comment("Stretched beyond this (blocks), a string snaps.").defineInRange("snapRange", 32.0, 1.0, 512.0);
        STRINGS_PULL_SPEED = b.comment("Reel In, Zip and a Tie's first pull, blocks per tick.").defineInRange("pullSpeed", 1.2, 0.1, 5.0);
        STRINGS_PULL_TIMEOUT = b.comment("A pull gives up after this long (a wall in the way).").defineInRange("pullTimeout", 2.0, 0.1, 30.0);
        STRINGS_ARRIVE = b.comment("Reel In and Zip stop this close (blocks).").defineInRange("arriveDistance", 2.0, 0.5, 16.0);
        STRINGS_COLLIDE = b.comment("A Tie's two targets collide this close (blocks).").defineInRange("collideDistance", 1.2, 0.1, 16.0);
        STRINGS_TIE_DAMAGE = b.comment("The collision's damage, to each of them. Default: a weak puppet (2 health) survives it.")
                .defineInRange("tieDamage", 1.5, 0.0, 1000.0);
        STRINGS_TETHER_DISTANCE = b.comment("After colliding, a Tie's targets stay within this (blocks) of each other.")
                .defineInRange("tetherDistance", 3.0, 0.5, 32.0);
        STRINGS_TETHER_DURATION = b.defineInRange("tetherDuration", 4.0, 0.1, 600.0);
        STRINGS_TETHER_SPEED = b.comment("How hard a Tie or Leash drags back, in blocks per tick, a block past its limit (twice that two past, up to pullSpeed).",
                "Above a sprint (0.28), or it could be outrun.").defineInRange("dragSpeed", 0.4, 0.05, 5.0);
        STRINGS_LEASH_DISTANCE = b.comment("A Leash's targets stay within this (blocks) of you.").defineInRange("leashDistance", 6.0, 0.5, 32.0);
        STRINGS_LEASH_DURATION = b.defineInRange("leashDuration", 5.0, 0.1, 600.0);
        STRINGS_LEASH_SLOW = b.comment("Your speed lost while leashing, as a fraction.").defineInRange("leashSlow", 0.35, 0.0, 1.0);
        b.pop();

        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
