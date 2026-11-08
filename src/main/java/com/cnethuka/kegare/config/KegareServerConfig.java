package com.cnethuka.kegare.config;

import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.trait.TransferMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code <world>/serverconfig/kegare-server.toml}). Every balance number lives here;
 * nothing is tuned in code. Server configs are synced to clients on join.
 */
public final class KegareServerConfig {
    public static final ModConfigSpec SPEC;

    /** What this file has been migrated through ({@link KegareConfigMigration}). */
    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    // ---- Malice ----
    public static final ModConfigSpec.DoubleValue REGEN_PAUSE_SECONDS;
    public static final ModConfigSpec.DoubleValue CAPACITY_VARIATION_PERCENT;
    private static final ModConfigSpec.IntValue[] TIER_WEIGHTS = new ModConfigSpec.IntValue[MaliceTier.values().length];
    /** Index by tier ordinal. {@code null} for {@link MaliceTier#ZERO} (always 0). */
    private static final ModConfigSpec.IntValue[] TIER_BASES = new ModConfigSpec.IntValue[MaliceTier.values().length];
    /** Index by tier ordinal. {@code null} for {@link MaliceTier#ZERO} (nothing to regenerate). */
    private static final ModConfigSpec.DoubleValue[] TIER_REGEN = new ModConfigSpec.DoubleValue[MaliceTier.values().length];

    // ---- traits ----
    public static final ModConfigSpec.DoubleValue SIX_EYES_BIRTH_CHANCE;
    public static final ModConfigSpec.EnumValue<TransferMode> SIX_EYES_TRANSFER_MODE;
    public static final ModConfigSpec.DoubleValue SIX_EYES_TRANSFER_DELAY;
    public static final ModConfigSpec.DoubleValue UNIQUE_TRAIT_INACTIVE_DAYS;

    // ---- roll ----
    private static final ModConfigSpec.IntValue[] RARITY_WEIGHTS = new ModConfigSpec.IntValue[InnateTechniqueRarity.values().length];
    public static final ModConfigSpec.IntValue SOFT_PITY_START;
    public static final ModConfigSpec.IntValue SOFT_PITY_INCREMENT;
    public static final ModConfigSpec.IntValue HARD_PITY;
    public static final ModConfigSpec.BooleanValue INCLUDE_TEST_INNATE_TECHNIQUES;

    // ---- combat ----
    public static final ModConfigSpec.DoubleValue COMBAT_TAG_SECONDS;

    // ---- test abilities (TEST CONTENT) ----
    public static final ModConfigSpec.DoubleValue DASH_COST;
    public static final ModConfigSpec.DoubleValue DASH_COOLDOWN;
    public static final ModConfigSpec.DoubleValue DASH_STRENGTH;
    public static final ModConfigSpec.DoubleValue DASH_LIFT;

    public static final ModConfigSpec.DoubleValue BURST_COST;
    public static final ModConfigSpec.DoubleValue BURST_COOLDOWN;
    public static final ModConfigSpec.DoubleValue BURST_MIN_CHARGE;
    public static final ModConfigSpec.DoubleValue BURST_MAX_CHARGE;
    public static final ModConfigSpec.DoubleValue BURST_MIN_RADIUS;
    public static final ModConfigSpec.DoubleValue BURST_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue BURST_MIN_KNOCKBACK;
    public static final ModConfigSpec.DoubleValue BURST_MAX_KNOCKBACK;

    public static final ModConfigSpec.DoubleValue HOVER_COST;
    public static final ModConfigSpec.DoubleValue HOVER_UPKEEP;
    public static final ModConfigSpec.DoubleValue HOVER_MAX_DURATION;
    public static final ModConfigSpec.DoubleValue HOVER_COOLDOWN;
    public static final ModConfigSpec.IntValue HOVER_LEVITATION_LEVEL;
    public static final ModConfigSpec.DoubleValue HOVER_LANDING_SLOW_FALL;

    public static final ModConfigSpec.DoubleValue AURA_COST;
    public static final ModConfigSpec.DoubleValue AURA_UPKEEP;
    public static final ModConfigSpec.DoubleValue AURA_COOLDOWN;
    public static final ModConfigSpec.IntValue AURA_SPEED_LEVEL;
    public static final ModConfigSpec.DoubleValue BLINK_COST;
    public static final ModConfigSpec.DoubleValue BLINK_DISTANCE;
    public static final ModConfigSpec.DoubleValue UPDRAFT_COST;
    public static final ModConfigSpec.DoubleValue UPDRAFT_MIN_CHARGE;
    public static final ModConfigSpec.DoubleValue UPDRAFT_MAX_CHARGE;
    public static final ModConfigSpec.DoubleValue UPDRAFT_MIN_LAUNCH;
    public static final ModConfigSpec.DoubleValue UPDRAFT_MAX_LAUNCH;

    public static final ModConfigSpec.DoubleValue SHOCKWAVE_COST;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_COOLDOWN;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_RADIUS;
    public static final ModConfigSpec.DoubleValue SHOCKWAVE_KNOCKBACK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CONFIG_VERSION = b.comment("Which one-time fixes this file has been through. Leave it alone.").defineInRange("configVersion", 1, 1, Integer.MAX_VALUE);

        b.comment("Malice (cursed energy). Capacity is rolled once at birth and never changes.").push("malice");
        REGEN_PAUSE_SECONDS = b.comment("Regen pauses for this many seconds after the player deals or takes damage.")
                .defineInRange("regenPauseSeconds", 5.0, 0.0, 600.0);
        CAPACITY_VARIATION_PERCENT = b.comment("A non-zero tier's capacity at birth: its base, plus or minus up to this percent of the base. Never reaches 0.")
                .defineInRange("capacityVariationPercent", 10.0, 0.0, 100.0);
        b.comment("Birth tiers. weight = relative roll chance, base = capacity before variation,",
                "regenPercentPerSecond = reserve regenerated per second, as a percent of capacity.").push("tiers");
        int[] defaultWeights = {2, 30, 45, 20, 3};
        int[] defaultBases = {0, 250, 600, 1500, 10_000};
        double[] defaultRegen = {0.0, 3.0, 2.0, 1.5, 0.75};
        for (MaliceTier tier : MaliceTier.values()) {
            b.push(tier.serializedName());
            TIER_WEIGHTS[tier.ordinal()] = b.defineInRange("weight", defaultWeights[tier.ordinal()], 0, 1_000_000);
            if (tier != MaliceTier.ZERO) {
                TIER_BASES[tier.ordinal()] = b.defineInRange("base", defaultBases[tier.ordinal()], 1, 1_000_000);
                TIER_REGEN[tier.ordinal()] = b.defineInRange("regenPercentPerSecond", defaultRegen[tier.ordinal()], 0.0, 100.0);
            }
            b.pop();
        }
        b.pop(2);

        b.comment("Traits rolled at birth. Unique traits have at most one bearer per world.").push("traits");
        SIX_EYES_BIRTH_CHANCE = b.comment("Chance (0-1) to be born with six_eyes (unique, needs Malice; no effects yet).")
                .defineInRange("sixEyesBirthChance", 0.01, 0.0, 1.0);
        SIX_EYES_TRANSFER_MODE = b.comment(
                        "Who inherits six_eyes when its bearer loses it (killed by a player, released, inactive).",
                        "RANDOM = a random eligible online player. KILLER = the killer if eligible and online, else RANDOM.",
                        "Eligible = online, not Severed, not the previous bearer. If nobody is eligible it waits for the next eligible login.")
                .defineEnum("sixEyesTransferMode", TransferMode.RANDOM);
        SIX_EYES_TRANSFER_DELAY = b.comment("Seconds between losing six_eyes and it opening in someone else.")
                .defineInRange("sixEyesTransferDelaySeconds", 30.0, 0.0, 86_400.0);
        UNIQUE_TRAIT_INACTIVE_DAYS = b.comment(
                        "Release a unique trait if its holder hasn't logged in for this many days (fractions allowed).",
                        "0 = disabled. Inheritable traits (six_eyes) then pass on as if their bearer had lost them.")
                .defineInRange("uniqueTraitInactiveDays", 0.0, 0.0, 3650.0);
        b.pop();

        b.comment("Innate technique rolls (birth and rerolls).").push("roll");
        b.comment("Relative rarity weights. Rarities with no eligible innate technique are skipped.").push("weights");
        int[] defaultRarity = {50, 30, 15, 5};
        for (InnateTechniqueRarity rarity : InnateTechniqueRarity.values()) {
            RARITY_WEIGHTS[rarity.ordinal()] = b.defineInRange(rarity.serializedName(), defaultRarity[rarity.ordinal()], 0, 1_000_000);
        }
        b.pop();
        SOFT_PITY_START = b.comment("After this many rolls without a LEGENDARY, each further roll adds softPityIncrement to the LEGENDARY weight.")
                .defineInRange("softPityStart", 30, 0, 100_000);
        SOFT_PITY_INCREMENT = b.comment("LEGENDARY weight added per roll once soft pity is active.")
                .defineInRange("softPityIncrement", 5, 0, 1_000_000);
        HARD_PITY = b.comment("This roll number (counting since the last LEGENDARY) is a guaranteed LEGENDARY. 0 disables hard pity.")
                .defineInRange("hardPity", 50, 0, 100_000);
        INCLUDE_TEST_INNATE_TECHNIQUES = b.comment("Whether TEST innate techniques can be rolled. Turn off once real ones exist.")
                .define("includeTestInnateTechniques", true);
        b.pop();

        b.comment("Shared combat state used by abilities and future systems.").push("combat");
        COMBAT_TAG_SECONDS = b.comment("A player counts as 'in combat' for this many seconds after dealing or taking damage.")
                .defineInRange("combatTagSeconds", 10.0, 0.0, 600.0);
        b.pop();

        com.cnethuka.kegare.innate_technique.rime.RimeConfig.define(b);
        com.cnethuka.kegare.innate_technique.swap.SwapConfig.define(b);
        com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetConfig.define(b);
        com.cnethuka.kegare.melee.MeleeConfig.define(b);
        com.cnethuka.kegare.impact.ImpactConfig.define(b);
        com.cnethuka.kegare.battledamage.BattleDamageConfig.define(b);
        com.cnethuka.kegare.rmt.RmtConfig.define(b);
        com.cnethuka.kegare.progression.ProgressionConfig.define(b);

        b.comment("TEST CONTENT: numbers for the placeholder test abilities. Delete with the test innate techniques.").push("test_abilities");
        b.push("dash");
        DASH_COST = b.defineInRange("cost", 40.0, 0.0, 100_000.0);
        DASH_COOLDOWN = b.comment("Seconds.").defineInRange("cooldown", 3.0, 0.0, 3600.0);
        DASH_STRENGTH = b.comment("Horizontal launch speed (blocks/tick).").defineInRange("strength", 1.4, 0.0, 10.0);
        DASH_LIFT = b.comment("Vertical launch speed (blocks/tick).").defineInRange("lift", 0.25, 0.0, 10.0);
        b.pop();

        b.push("burst");
        BURST_COST = b.defineInRange("cost", 60.0, 0.0, 100_000.0);
        BURST_COOLDOWN = b.comment("Seconds.").defineInRange("cooldown", 6.0, 0.0, 3600.0);
        BURST_MIN_CHARGE = b.comment("Seconds of charge needed before release fires.").defineInRange("minCharge", 0.25, 0.0, 60.0);
        BURST_MAX_CHARGE = b.comment("Seconds to reach full charge.").defineInRange("maxCharge", 1.5, 0.05, 60.0);
        BURST_MIN_RADIUS = b.defineInRange("minRadius", 3.0, 0.0, 64.0);
        BURST_MAX_RADIUS = b.defineInRange("maxRadius", 6.0, 0.0, 64.0);
        BURST_MIN_KNOCKBACK = b.defineInRange("minKnockback", 0.5, 0.0, 10.0);
        BURST_MAX_KNOCKBACK = b.defineInRange("maxKnockback", 1.5, 0.0, 10.0);
        b.pop();

        b.push("hover");
        HOVER_COST = b.comment("Cost to start the channel.").defineInRange("cost", 20.0, 0.0, 100_000.0);
        HOVER_UPKEEP = b.comment("Drain per second while channeling.").defineInRange("upkeepPerSecond", 32.0, 0.0, 100_000.0);
        HOVER_MAX_DURATION = b.comment("Seconds.").defineInRange("maxDuration", 5.0, 0.05, 3600.0);
        HOVER_COOLDOWN = b.comment("Seconds, starts when the channel ends.").defineInRange("cooldown", 4.0, 0.0, 3600.0);
        HOVER_LEVITATION_LEVEL = b.comment("Levitation amplifier (0 = Levitation I).").defineInRange("levitationAmplifier", 0, 0, 10);
        HOVER_LANDING_SLOW_FALL = b.comment("Seconds of Slow Falling after the channel ends (0 = none).").defineInRange("landingSlowFall", 3.0, 0.0, 60.0);
        b.pop();

        b.push("aura");
        AURA_COST = b.comment("Cost to switch on.").defineInRange("cost", 20.0, 0.0, 100_000.0);
        AURA_UPKEEP = b.comment("Drain per second while active.").defineInRange("upkeepPerSecond", 12.0, 0.0, 100_000.0);
        AURA_COOLDOWN = b.comment("Seconds, starts when switched off.").defineInRange("cooldown", 2.0, 0.0, 3600.0);
        AURA_SPEED_LEVEL = b.comment("Speed amplifier while active (0 = Speed I).").defineInRange("speedAmplifier", 0, 0, 10);
        b.pop();

        b.comment("Special of Dash (R). Needs Aura switched on. Shares Dash's cooldown.").push("blink");
        BLINK_COST = b.defineInRange("cost", 32.0, 0.0, 100_000.0);
        BLINK_DISTANCE = b.comment("Max teleport distance in blocks (stops short of walls).").defineInRange("distance", 8.0, 0.5, 64.0);
        b.pop();

        b.comment("Special of Hover (R, hold to charge). Needs the trait in DESIGN.md. Shares Hover's cooldown.").push("updraft");
        UPDRAFT_COST = b.defineInRange("cost", 48.0, 0.0, 100_000.0);
        UPDRAFT_MIN_CHARGE = b.comment("Seconds of charge needed before release fires.").defineInRange("minCharge", 0.25, 0.0, 60.0);
        UPDRAFT_MAX_CHARGE = b.comment("Seconds to reach full charge.").defineInRange("maxCharge", 1.0, 0.05, 60.0);
        UPDRAFT_MIN_LAUNCH = b.comment("Upward speed at no charge (blocks/tick).").defineInRange("minLaunch", 0.6, 0.0, 10.0);
        UPDRAFT_MAX_LAUNCH = b.comment("Upward speed at full charge (blocks/tick).").defineInRange("maxLaunch", 1.4, 0.0, 10.0);
        b.pop();

        b.push("shockwave");
        SHOCKWAVE_COST = b.defineInRange("cost", 240.0, 0.0, 100_000.0);
        SHOCKWAVE_COOLDOWN = b.comment("Seconds.").defineInRange("cooldown", 30.0, 0.0, 3600.0);
        SHOCKWAVE_RADIUS = b.defineInRange("radius", 8.0, 0.0, 64.0);
        SHOCKWAVE_KNOCKBACK = b.defineInRange("knockback", 2.0, 0.0, 10.0);
        b.pop();
        b.pop();

        SPEC = b.build();
    }

    private KegareServerConfig() {}

    public static int[] tierWeights() {
        int[] out = new int[TIER_WEIGHTS.length];
        for (int i = 0; i < out.length; i++) out[i] = TIER_WEIGHTS[i].get();
        return out;
    }

    /** Capacity base per tier ordinal; {@link MaliceTier#ZERO} is always 0. */
    public static int[] tierBases() {
        int[] out = new int[TIER_BASES.length];
        for (int i = 0; i < out.length; i++) out[i] = TIER_BASES[i] == null ? 0 : TIER_BASES[i].get();
        return out;
    }

    /** Reserve regenerated per second, as a percent of capacity. {@link MaliceTier#ZERO} has none. */
    public static double regenPercent(MaliceTier tier) {
        ModConfigSpec.DoubleValue value = TIER_REGEN[tier.ordinal()];
        return value == null ? 0.0 : value.get();
    }

    public static int[] rarityWeights() {
        int[] out = new int[RARITY_WEIGHTS.length];
        for (int i = 0; i < out.length; i++) out[i] = RARITY_WEIGHTS[i].get();
        return out;
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
