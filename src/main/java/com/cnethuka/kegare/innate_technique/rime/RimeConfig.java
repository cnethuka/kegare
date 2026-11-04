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
        SHARD_COST = b.defineInRange("cost", 10.0, 0.0, 100_000.0);
        SHARD_COOLDOWN = b.defineInRange("cooldown", 1.0, 0.0, 3600.0);
        SHARD_DAMAGE = b.defineInRange("damage", 3.0, 0.0, 1000.0);
        SHARD_SPEED = b.comment("Blocks per tick.").defineInRange("speed", 2.2, 0.1, 10.0);
        SHARD_RANGE_SECONDS = b.comment("Flight time before it melts away.").defineInRange("lifetime", 1.5, 0.1, 30.0);
        SHARD_SLOWNESS_SECONDS = b.defineInRange("slownessSeconds", 1.5, 0.0, 60.0);
        SHARD_SLOWNESS_AMPLIFIER = b.comment("0 = Slowness I.").defineInRange("slownessAmplifier", 0, 0, 10);
        SHARD_FREEZE_STACKS = b.defineInRange("freezeStacks", 1, 0, 10);
        b.pop();

        b.push("glacial_lance");
        LANCE_COST = b.defineInRange("cost", 25.0, 0.0, 100_000.0);
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
        FIELD_COST = b.comment("Cost to start the channel.").defineInRange("cost", 8.0, 0.0, 100_000.0);
        FIELD_UPKEEP = b.comment("Drain per second while channeling.").defineInRange("upkeepPerSecond", 12.0, 0.0, 100_000.0);
        FIELD_COOLDOWN = b.comment("Starts when the channel ends.").defineInRange("cooldown", 5.0, 0.0, 3600.0);
        FIELD_MAX_DURATION = b.defineInRange("maxDuration", 5.0, 0.05, 3600.0);
        FIELD_RANGE = b.comment("Cone length in blocks.").defineInRange("range", 6.0, 0.5, 64.0);
        FIELD_HALF_ANGLE = b.comment("Cone half-angle in degrees.").defineInRange("halfAngle", 35.0, 1.0, 89.0);
        FIELD_SLOWNESS_AMPLIFIER = b.defineInRange("slownessAmplifier", 1, 0, 10);
        FIELD_FREEZE_STACKS_PER_SECOND = b.defineInRange("freezeStacksPerSecond", 1, 0, 10);
        b.pop();

        b.push("rime_shell");
        SHELL_COST = b.comment("Cost to switch on.").defineInRange("cost", 10.0, 0.0, 100_000.0);
        SHELL_UPKEEP = b.comment("Drain per second while on.").defineInRange("upkeepPerSecond", 4.0, 0.0, 100_000.0);
        SHELL_COOLDOWN = b.comment("Starts when switched off.").defineInRange("cooldown", 8.0, 0.0, 3600.0);
        SHELL_DAMAGE_REDUCTION = b.comment("Fraction of incoming damage removed (0.3 = 30%).").defineInRange("damageReduction", 0.3, 0.0, 1.0);
        SHELL_ATTACKER_SLOWNESS_SECONDS = b.comment("Slowness for anyone who hits you in melee.").defineInRange("attackerSlownessSeconds", 2.0, 0.0, 60.0);
        SHELL_ATTACKER_SLOWNESS_AMPLIFIER = b.defineInRange("attackerSlownessAmplifier", 1, 0, 10);
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
