package com.cnethuka.kegare.impact;

import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The Impact System's server numbers ({@code [impact]} in kegare-server.toml, DESIGN §3.23): how a hit's strength is worked out and
 * where each tier starts. How strong a tier looks on screen is each player's own (client config, {@code impact}).
 */
public final class ImpactConfig {
    public static ModConfigSpec.DoubleValue DAMAGE_WEIGHT;
    public static ModConfigSpec.DoubleValue KNOCKBACK_WEIGHT;
    public static ModConfigSpec.DoubleValue BASE_KNOCKBACK;
    public static ModConfigSpec.DoubleValue LAUNCH_WEIGHT;
    public static ModConfigSpec.DoubleValue CHARGE_WEIGHT;
    public static ModConfigSpec.DoubleValue MALICE_WEIGHT;
    public static ModConfigSpec.DoubleValue MEDIUM;
    public static ModConfigSpec.DoubleValue HEAVY;
    public static ModConfigSpec.DoubleValue IMPACT_FRAME;
    public static ModConfigSpec.DoubleValue WITNESS_RANGE;
    /** The melee layer's moments that aren't damage (DESIGN §3.22): the tier each plays at. */
    public static final Map<Moment, ModConfigSpec.EnumValue<ImpactTier>> MOMENT_TIER = new EnumMap<>(Moment.class);

    /** The melee moments the Impact System plays by themselves (a guard hit only softens the hit it's part of). */
    public enum Moment {
        DASH_BURST("dashBurst", ImpactTier.LIGHT, "A dash starts: a light shake for the one dashing."),
        CLEAN_DODGE("cleanDodge", ImpactTier.MEDIUM, "A clean dodge: a short freeze for the one who dodged."),
        PARRY_PERFECT("parryPerfect", ImpactTier.MEDIUM, "A perfect parry: freeze, shake and a white flash for both fighters."),
        STAGGER("stagger", ImpactTier.MEDIUM, "Staggered by a parry: a shake for the one staggered.");

        final String key;
        final ImpactTier fallback;
        final String comment;

        Moment(String key, ImpactTier fallback, String comment) {
            this.key = key;
            this.fallback = fallback;
            this.comment = comment;
        }

        public ImpactTier tier() {
            ModConfigSpec.EnumValue<ImpactTier> value = MOMENT_TIER.get(this);
            return value == null ? fallback : value.get();
        }
    }

    private ImpactConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("The Impact System (hit-stop, screen shake, impact flashes and frames). A hit's strength is",
                        "  damageWeight * (damage dealt / the victim's max health)",
                        "  + knockbackWeight * (knockback beyond baseKnockback, which every hit gets)",
                        "  + launchWeight * (upward launch speed, blocks/tick) + chargeWeight * (the charge it was cast at, 0 to 1)",
                        "  + maliceWeight * (the base Malice behind it, per 100: the cast's base cost, plus what a puppet that went off cost to make;",
                        "    a melee hit's is 0, and so is every hit of a Severed player's),",
                        "and its tier is the highest whose threshold it reaches (below medium: light). The top tier is reserved for",
                        "Black Spark: no strength reaches it. Battle damage reads the same strength.")
                .push("impact");
        b.push("strength");
        DAMAGE_WEIGHT = b.defineInRange("damageWeight", 1.2, 0.0, 100.0);
        KNOCKBACK_WEIGHT = b.defineInRange("knockbackWeight", 1.2, 0.0, 100.0);
        BASE_KNOCKBACK = b.comment("Knockback every hit gets anyway (vanilla's 0.4): only what's beyond it counts.")
                .defineInRange("baseKnockback", 0.4, 0.0, 10.0);
        LAUNCH_WEIGHT = b.defineInRange("launchWeight", 1.0, 0.0, 100.0);
        CHARGE_WEIGHT = b.comment("A Reinforced Strike counts as fully charged, and so does an ultimate.")
                .defineInRange("chargeWeight", 1.2, 0.0, 100.0);
        MALICE_WEIGHT = b.comment("Per 100 Malice of base cost behind the hit.").defineInRange("maliceWeight", 0.75, 0.0, 100.0);
        b.pop();
        b.comment("Where each tier starts (strength).").push("tiers");
        MEDIUM = b.defineInRange("medium", 0.51, 0.0, 1000.0);
        HEAVY = b.defineInRange("heavy", 1.98, 0.0, 1000.0);
        IMPACT_FRAME = b.defineInRange("impactFrame", 2.84, 0.0, 1000.0);
        b.pop();
        WITNESS_RANGE = b.comment("Players this close (blocks) to a hit see its flash.").defineInRange("witnessRange", 48.0, 0.0, 256.0);
        b.comment("The melee moments that aren't damage, and the tier each plays at (light, medium, heavy, impact_frame).").push("moments");
        for (Moment moment : Moment.values()) {
            MOMENT_TIER.put(moment, b.comment(moment.comment).defineEnum(moment.key, moment.fallback));
        }
        b.pop();
        b.pop();
    }
}
