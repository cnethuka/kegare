package com.cnethuka.kegare.melee;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The shared melee layer's numbers: section {@code [melee]} of {@code kegare-server.toml}. Times are in seconds, Malice costs in
 * Malice. {@code [melee.severed]} is the physical version a Severed player gets: no costs, its own numbers (DESIGN §3.22).
 */
public final class MeleeConfig {
    public static ModConfigSpec.DoubleValue DASH_COST;
    public static ModConfigSpec.DoubleValue DASH_COOLDOWN;
    public static ModConfigSpec.DoubleValue DASH_DISTANCE;
    public static ModConfigSpec.DoubleValue DASH_SPEED;
    public static ModConfigSpec.DoubleValue DODGE_WINDOW;

    public static ModConfigSpec.DoubleValue PARRY_PERFECT_WINDOW;
    public static ModConfigSpec.DoubleValue PARRY_ASSESSMENT_CHARGE;
    public static ModConfigSpec.DoubleValue PARRY_STAGGER_SECONDS;
    public static ModConfigSpec.IntValue PARRY_STAGGER_SLOWNESS;
    public static ModConfigSpec.DoubleValue GUARD_MELEE_REDUCTION;
    public static ModConfigSpec.DoubleValue GUARD_TECHNIQUE_REDUCTION;
    public static ModConfigSpec.DoubleValue GUARD_DRAIN;
    public static ModConfigSpec.DoubleValue GUARD_MIN_MALICE;
    public static ModConfigSpec.DoubleValue PARRY_RECOVERY;

    public static ModConfigSpec.DoubleValue STRIKE_WINDOW;
    public static ModConfigSpec.DoubleValue STRIKE_BONUS;
    public static ModConfigSpec.DoubleValue STRIKE_KNOCKBACK;
    public static ModConfigSpec.DoubleValue STRIKE_COST;

    public static ModConfigSpec.DoubleValue SEVERED_DASH_COOLDOWN;
    public static ModConfigSpec.DoubleValue SEVERED_DASH_DISTANCE;
    public static ModConfigSpec.DoubleValue SEVERED_PERFECT_WINDOW;
    public static ModConfigSpec.DoubleValue SEVERED_GUARD_MELEE_REDUCTION;
    public static ModConfigSpec.DoubleValue SEVERED_GUARD_TECHNIQUE_REDUCTION;
    public static ModConfigSpec.DoubleValue SEVERED_GUARD_MAX;
    public static ModConfigSpec.DoubleValue SEVERED_RECOVERY;
    public static ModConfigSpec.DoubleValue SEVERED_STRIKE_WINDOW;
    public static ModConfigSpec.DoubleValue SEVERED_STRIKE_BONUS;
    public static ModConfigSpec.DoubleValue SEVERED_STRIKE_KNOCKBACK;

    private MeleeConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Dash, Parry and Reinforced Strike: shared by every player, in and out of Technique Mode.").push("melee");

        b.push("dash");
        DASH_COST = b.comment("Malice per dash.").defineInRange("cost", 25.0, 0.0, 100000.0);
        DASH_COOLDOWN = b.comment("Seconds between dashes.").defineInRange("cooldown", 3.0, 0.0, 600.0);
        DASH_DISTANCE = b.comment("Blocks covered, stopping short of walls.").defineInRange("distance", 4.0, 0.5, 32.0);
        DASH_SPEED = b.comment("Blocks per tick while dashing.").defineInRange("speed", 1.0, 0.1, 4.0);
        DODGE_WINDOW = b.comment("Seconds after dashing in which something aimed at where you were counts as a clean dodge.")
                .defineInRange("dodgeWindow", 0.5, 0.0, 10.0);
        b.pop();

        b.push("parry");
        PARRY_PERFECT_WINDOW = b.comment("The first seconds of holding: a melee hit is cancelled and its attacker staggered.")
                .defineInRange("perfectWindow", 0.3, 0.0, 10.0);
        PARRY_ASSESSMENT_CHARGE = b.comment("How charged a player's swing has to be for a perfect parry of it to count as a move in Assessment. 1.0 is a full attack bar. The game itself calls anything past 0.9 full strength, if that's preferred.")
                .defineInRange("assessmentCharge", 1.0, 0.0, 1.0);
        PARRY_STAGGER_SECONDS = b.comment("How long a perfect parry slows the attacker. Their abilities aren't stopped.")
                .defineInRange("staggerSeconds", 0.5, 0.0, 10.0);
        PARRY_STAGGER_SLOWNESS = b.comment("Slowness amplifier of the stagger (2 = Slowness III).").defineInRange("staggerSlowness", 2, 0, 9);
        GUARD_MELEE_REDUCTION = b.comment("Melee damage taken off while holding past the window (0.4 = 40%).")
                .defineInRange("guardMeleeReduction", 0.4, 0.0, 1.0);
        GUARD_TECHNIQUE_REDUCTION = b.comment("Technique damage taken off the whole time you hold.")
                .defineInRange("guardTechniqueReduction", 0.1, 0.0, 1.0);
        GUARD_DRAIN = b.comment("Malice per second while holding past the window. The guard drops when it runs out.")
                .defineInRange("guardDrain", 16.0, 0.0, 100000.0);
        GUARD_MIN_MALICE = b.comment("Malice you need to raise a guard at all (it isn't spent).").defineInRange("guardMinMalice", 10.0, 0.0, 100000.0);
        PARRY_RECOVERY = b.comment("Seconds after a guard ends before you can parry again.").defineInRange("recovery", 1.0, 0.0, 60.0);
        b.pop();

        b.push("reinforced_strike");
        STRIKE_WINDOW = b.comment("Seconds to land the hit after a perfect parry or clean dodge.").defineInRange("window", 1.5, 0.05, 60.0);
        STRIKE_BONUS = b.comment("Extra damage (0.5 = 50% more).").defineInRange("damageBonus", 0.5, 0.0, 10.0);
        STRIKE_KNOCKBACK = b.comment("Extra knockback strength. Never on bosses.").defineInRange("knockback", 0.6, 0.0, 10.0);
        STRIKE_COST = b.comment("Malice spent when it lands. Short of this, the hit stays normal and the Strike isn't used.")
                .defineInRange("cost", 20.0, 0.0, 100000.0);
        b.pop();

        b.comment("The physical version for Severed players, who trade all their Malice for superhuman abilities: stronger than standard, with no Malice cost, so everything Malice would limit has its own number.").push("severed");
        SEVERED_DASH_COOLDOWN = b.comment("Seconds between dashes.").defineInRange("dashCooldown", 2.5, 0.0, 600.0);
        SEVERED_DASH_DISTANCE = b.comment("Blocks covered.").defineInRange("dashDistance", 6.0, 0.5, 32.0);
        SEVERED_PERFECT_WINDOW = b.comment("Seconds of perfect parry window.").defineInRange("perfectWindow", 0.3, 0.0, 10.0);
        SEVERED_GUARD_MELEE_REDUCTION = b.comment("Melee damage taken off while holding past the window.")
                .defineInRange("guardMeleeReduction", 0.5, 0.0, 1.0);
        SEVERED_GUARD_TECHNIQUE_REDUCTION = b.comment("Technique damage taken off while holding: nothing to reinforce with.")
                .defineInRange("guardTechniqueReduction", 0.0, 0.0, 1.0);
        SEVERED_GUARD_MAX = b.comment("Seconds a guard can be held, since there's no Malice to run out of.").defineInRange("guardMaxSeconds", 4.0, 0.05, 600.0);
        SEVERED_RECOVERY = b.comment("Seconds after a guard ends before you can parry again.").defineInRange("recovery", 0.75, 0.0, 60.0);
        SEVERED_STRIKE_WINDOW = b.comment("Seconds to land the hit.").defineInRange("strikeWindow", 1.5, 0.05, 60.0);
        SEVERED_STRIKE_BONUS = b.comment("Extra damage.").defineInRange("strikeDamageBonus", 0.75, 0.0, 10.0);
        SEVERED_STRIKE_KNOCKBACK = b.comment("Extra knockback strength, more than the standard Strike's.").defineInRange("strikeKnockback", 0.9, 0.0, 10.0);
        b.pop();

        b.pop();
    }

    static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
