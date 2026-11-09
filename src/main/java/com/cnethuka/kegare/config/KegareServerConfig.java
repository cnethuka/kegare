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

    // ---- combat ----
    public static final ModConfigSpec.DoubleValue COMBAT_TAG_SECONDS;

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
        SIX_EYES_BIRTH_CHANCE = b.comment("Chance (0-1) to be born with six_eyes (unique, needs Malice; its numbers are in [six_eyes]).")
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
        b.pop();

        b.comment("Shared combat state used by abilities and future systems.").push("combat");
        COMBAT_TAG_SECONDS = b.comment("A player counts as 'in combat' for this many seconds after dealing or taking damage.")
                .defineInRange("combatTagSeconds", 10.0, 0.0, 600.0);
        b.pop();

        com.cnethuka.kegare.innate_technique.rime.RimeConfig.define(b);
        com.cnethuka.kegare.innate_technique.swap.SwapConfig.define(b);
        com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetConfig.define(b);
        com.cnethuka.kegare.innate_technique.time_manipulation.TimeConfig.define(b);
        com.cnethuka.kegare.severed.SeveredConfig.define(b);
        com.cnethuka.kegare.melee.MeleeConfig.define(b);
        com.cnethuka.kegare.impact.ImpactConfig.define(b);
        com.cnethuka.kegare.battledamage.BattleDamageConfig.define(b);
        com.cnethuka.kegare.rmt.RmtConfig.define(b);
        com.cnethuka.kegare.reinforcement.ReinforcementConfig.define(b);
        com.cnethuka.kegare.backgroundtask.BackgroundTaskConfig.define(b);
        com.cnethuka.kegare.sixeyes.SixEyesConfig.define(b);
        com.cnethuka.kegare.fists.FistsConfig.define(b);
        com.cnethuka.kegare.timing.TimingConfig.define(b);
        com.cnethuka.kegare.sight.SightConfig.define(b);
        com.cnethuka.kegare.progression.ProgressionConfig.define(b);


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
