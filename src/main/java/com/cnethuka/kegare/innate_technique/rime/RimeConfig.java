package com.cnethuka.kegare.innate_technique.rime;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Rime's numbers: section {@code [rime]} of {@code kegare-server.toml}. Times are in seconds. */
public final class RimeConfig {
    // frost_shard (INSTANT)
    public static ModConfigSpec.DoubleValue SHARD_COST;
    public static ModConfigSpec.DoubleValue SHARD_COOLDOWN;
    public static ModConfigSpec.DoubleValue SHARD_DAMAGE;
    public static ModConfigSpec.DoubleValue SHARD_SPEED;
    public static ModConfigSpec.DoubleValue SHARD_RANGE_SECONDS;
    public static ModConfigSpec.DoubleValue SHARD_SLOWNESS_SECONDS;
    public static ModConfigSpec.IntValue SHARD_SLOWNESS_AMPLIFIER;
    public static ModConfigSpec.IntValue SHARD_FREEZE_STACKS;

    // glacial_lance (CHARGED)
    public static ModConfigSpec.DoubleValue LANCE_COST;
    public static ModConfigSpec.DoubleValue LANCE_COOLDOWN;
    public static ModConfigSpec.DoubleValue LANCE_MIN_CHARGE;
    public static ModConfigSpec.DoubleValue LANCE_MAX_CHARGE;
    public static ModConfigSpec.DoubleValue LANCE_MIN_DAMAGE;
    public static ModConfigSpec.DoubleValue LANCE_MAX_DAMAGE;
    public static ModConfigSpec.DoubleValue LANCE_SPEED;
    public static ModConfigSpec.DoubleValue LANCE_RANGE_SECONDS;
    public static ModConfigSpec.DoubleValue LANCE_SLOWNESS_SECONDS;
    public static ModConfigSpec.IntValue LANCE_SLOWNESS_AMPLIFIER;
    public static ModConfigSpec.IntValue LANCE_FREEZE_STACKS;

    // frost_field (CHANNELED)
    public static ModConfigSpec.DoubleValue FIELD_COST;
    public static ModConfigSpec.DoubleValue FIELD_UPKEEP;
    public static ModConfigSpec.DoubleValue FIELD_COOLDOWN;
    public static ModConfigSpec.DoubleValue FIELD_MAX_DURATION;
    public static ModConfigSpec.DoubleValue FIELD_RANGE;
    public static ModConfigSpec.DoubleValue FIELD_HALF_ANGLE;
    public static ModConfigSpec.IntValue FIELD_SLOWNESS_AMPLIFIER;
    public static ModConfigSpec.IntValue FIELD_FREEZE_STACKS_PER_SECOND;

    // rime_shell (TOGGLE)
    public static ModConfigSpec.DoubleValue SHELL_COST;
    public static ModConfigSpec.DoubleValue SHELL_UPKEEP;
    public static ModConfigSpec.DoubleValue SHELL_COOLDOWN;
    public static ModConfigSpec.DoubleValue SHELL_DAMAGE_REDUCTION;
    public static ModConfigSpec.DoubleValue SHELL_ATTACKER_SLOWNESS_SECONDS;
    public static ModConfigSpec.IntValue SHELL_ATTACKER_SLOWNESS_AMPLIFIER;

    // splinter (special of frost_shard, INSTANT)
    public static ModConfigSpec.DoubleValue SPLINTER_COST;
    public static ModConfigSpec.DoubleValue SPLINTER_COOLDOWN;
    public static ModConfigSpec.DoubleValue SPLINTER_SPREAD;
    public static ModConfigSpec.DoubleValue SPLINTER_DAMAGE;
    public static ModConfigSpec.DoubleValue SPLINTER_SPEED;
    public static ModConfigSpec.DoubleValue SPLINTER_RANGE_SECONDS;
    public static ModConfigSpec.IntValue SPLINTER_FREEZE_STACKS;

    // glacial_spire (special of glacial_lance, CHARGED)
    public static ModConfigSpec.DoubleValue SPIRE_COST;
    public static ModConfigSpec.DoubleValue SPIRE_COOLDOWN;
    public static ModConfigSpec.DoubleValue SPIRE_MIN_CHARGE;
    public static ModConfigSpec.DoubleValue SPIRE_MAX_CHARGE;
    public static ModConfigSpec.DoubleValue SPIRE_RANGE;
    public static ModConfigSpec.DoubleValue SPIRE_DELAY;
    public static ModConfigSpec.DoubleValue SPIRE_RADIUS;
    public static ModConfigSpec.DoubleValue SPIRE_HEIGHT;
    public static ModConfigSpec.DoubleValue SPIRE_DAMAGE;
    public static ModConfigSpec.IntValue SPIRE_FREEZE_STACKS;
    public static ModConfigSpec.DoubleValue SPIRE_MIN_LAUNCH;
    public static ModConfigSpec.DoubleValue SPIRE_MAX_LAUNCH;
    public static ModConfigSpec.DoubleValue SPIRE_STANDS_SECONDS;

    // ice_path (special of frost_field, CHANNELED)
    public static ModConfigSpec.DoubleValue PATH_COST;
    public static ModConfigSpec.DoubleValue PATH_UPKEEP;
    public static ModConfigSpec.DoubleValue PATH_COOLDOWN;
    public static ModConfigSpec.DoubleValue PATH_MAX_DURATION;
    public static ModConfigSpec.DoubleValue PATH_SPEED;
    public static ModConfigSpec.DoubleValue PATH_TRAIL_SECONDS;
    public static ModConfigSpec.DoubleValue PATH_TRAIL_RADIUS;
    public static ModConfigSpec.IntValue PATH_SLOWNESS_AMPLIFIER;

    // shatter_shell (special of rime_shell, INSTANT)
    public static ModConfigSpec.DoubleValue SHATTER_COST;
    public static ModConfigSpec.DoubleValue SHATTER_COST_NO_SHELL;
    public static ModConfigSpec.DoubleValue SHATTER_COOLDOWN;
    public static ModConfigSpec.DoubleValue SHATTER_RADIUS;
    public static ModConfigSpec.DoubleValue SHATTER_KNOCKBACK;
    public static ModConfigSpec.IntValue SHATTER_FREEZE_STACKS;

    // freeze stacks
    public static ModConfigSpec.IntValue FREEZE_MAX_STACKS;
    public static ModConfigSpec.DoubleValue FREEZE_DURATION;
    public static ModConfigSpec.DoubleValue FREEZE_DECAY_SECONDS;
    public static ModConfigSpec.DoubleValue FREEZE_IMMUNITY_SECONDS;

    // environment
    public static ModConfigSpec.DoubleValue ENV_PRECIPITATION;
    public static ModConfigSpec.DoubleValue ENV_WATER;
    public static ModConfigSpec.DoubleValue ENV_COLD_BIOME;
    public static ModConfigSpec.DoubleValue ENV_COLD_BIOME_TEMPERATURE;
    public static ModConfigSpec.DoubleValue ENV_HOT_BIOME;
    public static ModConfigSpec.DoubleValue ENV_HOT_BIOME_TEMPERATURE;
    public static ModConfigSpec.DoubleValue ENV_NETHER;
    public static ModConfigSpec.DoubleValue ENV_MIN;
    public static ModConfigSpec.DoubleValue ENV_MAX;

    private RimeConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Rime (RARE innate technique): ice projectiles, a frost cone and an ice shell. Times are in seconds.").push("rime");

        b.push("frost_shard");
        SHARD_COST = b.defineInRange("cost", 40.0, 0.0, 100_000.0);
        SHARD_COOLDOWN = b.defineInRange("cooldown", 1.0, 0.0, 3600.0);
        SHARD_DAMAGE = b.defineInRange("damage", 3.0, 0.0, 1000.0);
        SHARD_SPEED = b.comment("Blocks per tick.").defineInRange("speed", 2.2, 0.1, 10.0);
        SHARD_RANGE_SECONDS = b.comment("Flight time before it melts away.").defineInRange("lifetime", 1.5, 0.1, 30.0);
        SHARD_SLOWNESS_SECONDS = b.defineInRange("slownessSeconds", 1.5, 0.0, 60.0);
        SHARD_SLOWNESS_AMPLIFIER = b.comment("0 = Slowness I.").defineInRange("slownessAmplifier", 0, 0, 10);
        SHARD_FREEZE_STACKS = b.defineInRange("freezeStacks", 1, 0, 10);
        b.pop();

        b.push("glacial_lance");
        LANCE_COST = b.defineInRange("cost", 100.0, 0.0, 100_000.0);
        LANCE_COOLDOWN = b.defineInRange("cooldown", 6.0, 0.0, 3600.0);
        LANCE_MIN_CHARGE = b.comment("Release before this and it fizzles (no cost, no cooldown).").defineInRange("minCharge", 0.4, 0.0, 60.0);
        LANCE_MAX_CHARGE = b.comment("Full charge.").defineInRange("maxCharge", 2.0, 0.05, 60.0);
        LANCE_MIN_DAMAGE = b.comment("Damage at minimum charge; scales linearly to maxDamage at full charge.").defineInRange("minDamage", 4.0, 0.0, 1000.0);
        LANCE_MAX_DAMAGE = b.defineInRange("maxDamage", 12.0, 0.0, 1000.0);
        LANCE_SPEED = b.comment("Blocks per tick (slower than a shard).").defineInRange("speed", 1.4, 0.1, 10.0);
        LANCE_RANGE_SECONDS = b.defineInRange("lifetime", 2.5, 0.1, 30.0);
        LANCE_SLOWNESS_SECONDS = b.defineInRange("slownessSeconds", 3.0, 0.0, 60.0);
        LANCE_SLOWNESS_AMPLIFIER = b.defineInRange("slownessAmplifier", 1, 0, 10);
        LANCE_FREEZE_STACKS = b.defineInRange("freezeStacks", 2, 0, 10);
        b.pop();

        b.push("frost_field");
        FIELD_COST = b.comment("Cost to start the channel.").defineInRange("cost", 32.0, 0.0, 100_000.0);
        FIELD_UPKEEP = b.comment("Drain per second while channeling.").defineInRange("upkeepPerSecond", 48.0, 0.0, 100_000.0);
        FIELD_COOLDOWN = b.comment("Starts when the channel ends.").defineInRange("cooldown", 5.0, 0.0, 3600.0);
        FIELD_MAX_DURATION = b.defineInRange("maxDuration", 5.0, 0.05, 3600.0);
        FIELD_RANGE = b.comment("Cone length in blocks.").defineInRange("range", 6.0, 0.5, 64.0);
        FIELD_HALF_ANGLE = b.comment("Cone half-angle in degrees.").defineInRange("halfAngle", 35.0, 1.0, 89.0);
        FIELD_SLOWNESS_AMPLIFIER = b.defineInRange("slownessAmplifier", 1, 0, 10);
        FIELD_FREEZE_STACKS_PER_SECOND = b.defineInRange("freezeStacksPerSecond", 1, 0, 10);
        b.pop();

        b.push("rime_shell");
        SHELL_COST = b.comment("Cost to switch on.").defineInRange("cost", 40.0, 0.0, 100_000.0);
        SHELL_UPKEEP = b.comment("Drain per second while on.").defineInRange("upkeepPerSecond", 16.0, 0.0, 100_000.0);
        SHELL_COOLDOWN = b.comment("Starts when switched off.").defineInRange("cooldown", 8.0, 0.0, 3600.0);
        SHELL_DAMAGE_REDUCTION = b.comment("Fraction of incoming damage removed (0.3 = 30%).").defineInRange("damageReduction", 0.3, 0.0, 1.0);
        SHELL_ATTACKER_SLOWNESS_SECONDS = b.comment("Slowness for anyone who hits you in melee.").defineInRange("attackerSlownessSeconds", 2.0, 0.0, 60.0);
        SHELL_ATTACKER_SLOWNESS_AMPLIFIER = b.defineInRange("attackerSlownessAmplifier", 1, 0, 10);
        b.pop();

        // ---- Specials (hold R and the base ability's number key). Each shares its base ability's cooldown; "cooldown"
        // here is how long that shared cooldown runs after the special is used.
        b.comment("Special of Frost Shard (INSTANT): three smaller shards in a fan.").push("splinter");
        SPLINTER_COST = b.defineInRange("cost", 56.0, 0.0, 100_000.0);
        SPLINTER_COOLDOWN = b.comment("Shared with Frost Shard.").defineInRange("cooldown", 3.0, 0.0, 3600.0);
        SPLINTER_SPREAD = b.comment("Degrees between neighbouring shards (the fan spans twice this).").defineInRange("spreadAngle", 9.0, 0.0, 60.0);
        SPLINTER_DAMAGE = b.comment("Per shard. All three can hit the same target.").defineInRange("damage", 2.0, 0.0, 1000.0);
        SPLINTER_SPEED = b.comment("Blocks per tick.").defineInRange("speed", 2.0, 0.1, 10.0);
        SPLINTER_RANGE_SECONDS = b.comment("Flight time before a shard melts away.").defineInRange("lifetime", 1.0, 0.1, 30.0);
        SPLINTER_FREEZE_STACKS = b.comment("Per shard that hits.").defineInRange("freezeStacks", 1, 0, 10);
        b.pop();

        b.comment("Special of Glacial Lance (CHARGED): an ice spike erupts from the ground where you aim.").push("glacial_spire");
        SPIRE_COST = b.defineInRange("cost", 120.0, 0.0, 100_000.0);
        SPIRE_COOLDOWN = b.comment("Shared with Glacial Lance.").defineInRange("cooldown", 8.0, 0.0, 3600.0);
        SPIRE_MIN_CHARGE = b.comment("Release before this and it fizzles (no cost, no cooldown).").defineInRange("minCharge", 0.3, 0.0, 60.0);
        SPIRE_MAX_CHARGE = b.comment("Full charge.").defineInRange("maxCharge", 2.0, 0.05, 60.0);
        SPIRE_RANGE = b.comment("Max distance to the aimed-at ground, in blocks. No ground in range: fizzles for free.").defineInRange("range", 20.0, 1.0, 128.0);
        SPIRE_DELAY = b.comment("The frost warning circle shows this long before the spike erupts.").defineInRange("warningSeconds", 0.75, 0.0, 10.0);
        SPIRE_RADIUS = b.comment("Radius of the circle (and of the spike's hit area), in blocks.").defineInRange("radius", 1.5, 0.25, 8.0);
        SPIRE_HEIGHT = b.comment("How far above the ground the eruption hits, in blocks.").defineInRange("height", 2.5, 0.25, 16.0);
        SPIRE_DAMAGE = b.defineInRange("damage", 6.0, 0.0, 1000.0);
        SPIRE_FREEZE_STACKS = b.defineInRange("freezeStacks", 2, 0, 10);
        SPIRE_MIN_LAUNCH = b.comment("Upward launch speed at minimum charge (blocks/tick; 0.42 is a jump).").defineInRange("minLaunch", 0.6, 0.0, 5.0);
        SPIRE_MAX_LAUNCH = b.comment("Upward launch speed at full charge.").defineInRange("maxLaunch", 1.2, 0.0, 5.0);
        SPIRE_STANDS_SECONDS = b.comment("How long the spike stands before it shatters (visual only; no blocks).").defineInRange("spikeSeconds", 1.25, 0.05, 30.0);
        b.pop();

        b.comment("Special of Frost Field (CHANNELED): slide fast over a temporary ice trail.").push("ice_path");
        PATH_COST = b.comment("Cost to start the channel.").defineInRange("cost", 32.0, 0.0, 100_000.0);
        PATH_UPKEEP = b.comment("Drain per second while sliding.").defineInRange("upkeepPerSecond", 40.0, 0.0, 100_000.0);
        PATH_COOLDOWN = b.comment("Shared with Frost Field; starts when the slide ends.").defineInRange("cooldown", 6.0, 0.0, 3600.0);
        PATH_MAX_DURATION = b.defineInRange("maxDuration", 3.0, 0.05, 3600.0);
        PATH_SPEED = b.comment("Slide speed in blocks per tick (sprinting is about 0.28).").defineInRange("slideSpeed", 0.65, 0.05, 3.0);
        PATH_TRAIL_SECONDS = b.comment("How long each bit of the ice trail lasts before it melts.").defineInRange("trailSeconds", 4.0, 0.05, 120.0);
        PATH_TRAIL_RADIUS = b.comment("Anyone else within this many blocks (sideways) of the trail is on it.").defineInRange("trailRadius", 0.9, 0.1, 8.0);
        PATH_SLOWNESS_AMPLIFIER = b.comment("Slowness for anyone else on the trail (0 = Slowness I).").defineInRange("slownessAmplifier", 1, 0, 10);
        b.pop();

        b.comment("Special of Rime Shell (INSTANT): burst outward, knocking back and freezing everyone near.").push("shatter_shell");
        SHATTER_COST = b.comment("Cost while Rime Shell is on (the shell is used up).").defineInRange("cost", 32.0, 0.0, 100_000.0);
        SHATTER_COST_NO_SHELL = b.comment("Cost with no shell up.").defineInRange("costWithoutShell", 88.0, 0.0, 100_000.0);
        SHATTER_COOLDOWN = b.comment("Shared with Rime Shell.").defineInRange("cooldown", 8.0, 0.0, 3600.0);
        SHATTER_RADIUS = b.defineInRange("radius", 4.5, 0.5, 32.0);
        SHATTER_KNOCKBACK = b.defineInRange("knockback", 1.4, 0.0, 10.0);
        SHATTER_FREEZE_STACKS = b.defineInRange("freezeStacks", 2, 0, 10);
        b.pop();

        b.comment("Freeze stacks, applied by Rime to any living target.").push("freeze");
        FREEZE_MAX_STACKS = b.comment("At this many stacks the target is frozen solid (a stun), then stacks reset.").defineInRange("maxStacks", 5, 1, 100);
        FREEZE_DURATION = b.comment("How long frozen solid lasts.").defineInRange("frozenSeconds", 1.0, 0.05, 60.0);
        FREEZE_DECAY_SECONDS = b.comment("One stack melts this long after the last one was added (and every this-long after).").defineInRange("decaySeconds", 3.0, 0.05, 600.0);
        FREEZE_IMMUNITY_SECONDS = b.comment("After breaking free, no new stacks for this long (no freeze-locking).").defineInRange("immunitySeconds", 2.0, 0.0, 600.0);
        b.pop();

        b.comment("Malice cost multipliers by surroundings (applied to cost and upkeep). Cold factors stack.").push("environment");
        ENV_PRECIPITATION = b.comment("Rain or snow falling on you.").defineInRange("precipitation", 0.85, 0.05, 10.0);
        ENV_WATER = b.comment("In or next to water.").defineInRange("water", 0.85, 0.05, 10.0);
        ENV_COLD_BIOME = b.comment("In a cold biome.").defineInRange("coldBiome", 0.85, 0.05, 10.0);
        ENV_COLD_BIOME_TEMPERATURE = b.comment("A biome is cold below this temperature (where snow falls: 0.15).").defineInRange("coldBiomeTemperature", 0.15, -10.0, 10.0);
        ENV_HOT_BIOME = b.comment("In a hot biome (not the Nether).").defineInRange("hotBiome", 1.25, 0.05, 10.0);
        ENV_HOT_BIOME_TEMPERATURE = b.comment("A biome is hot at or above this temperature (desert, badlands, savanna).").defineInRange("hotBiomeTemperature", 1.0, -10.0, 10.0);
        ENV_NETHER = b.comment("In the Nether (or any ultra-warm dimension); replaces the hot-biome factor.").defineInRange("nether", 1.5, 0.05, 10.0);
        ENV_MIN = b.comment("The combined multiplier is clamped to [min, max].").defineInRange("min", 0.5, 0.01, 10.0);
        ENV_MAX = b.defineInRange("max", 2.0, 0.01, 10.0);
        b.pop();

        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
