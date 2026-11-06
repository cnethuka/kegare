package com.cnethuka.kegare.progression;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Every progression number: section {@code [progression]} of {@code kegare-server.toml}. Times are in seconds. */
public final class ProgressionConfig {
    // Fights
    public static ModConfigSpec.DoubleValue FIGHT_TIMEOUT;
    public static ModConfigSpec.DoubleValue WIN_SHARE;
    public static ModConfigSpec.DoubleValue OPENING_CAST_SECONDS;

    // Grade
    /** XP the step INTO each Grade costs, by Grade ordinal. Null where that step takes no XP. */
    public static final ModConfigSpec.DoubleValue[] XP_TO_GRADE = new ModConfigSpec.DoubleValue[Grade.values().length];
    public static ModConfigSpec.DoubleValue XP_PER_DAMAGE;
    public static ModConfigSpec.DoubleValue XP_WIN;
    public static ModConfigSpec.DoubleValue XP_LOSS;
    public static ModConfigSpec.DoubleValue XP_SURVIVE;
    public static ModConfigSpec.DoubleValue SURVIVE_MIN_DAMAGE;
    public static ModConfigSpec.DoubleValue XP_PER_MOB_DAMAGE;
    public static ModConfigSpec.DoubleValue MOB_XP_PER_WINDOW;
    public static ModConfigSpec.DoubleValue ROLLING_WINDOW;
    public static ModConfigSpec.DoubleValue FIGHT_DECAY;
    public static ModConfigSpec.DoubleValue FIGHT_DECAY_FLOOR;
    public static ModConfigSpec.DoubleValue WEAKER_PER_GRADE;
    public static ModConfigSpec.DoubleValue WEAKER_FLOOR;
    public static ModConfigSpec.DoubleValue WIN_BONUS_PER_GRADE;
    public static ModConfigSpec.DoubleValue WIN_BONUS_MAX;

    private ProgressionConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Grade, Assessment, Refinement and Insight. None of this changes damage, costs or Malice capacity.").push("progression");

        b.comment("Shared fight tracking. A fight is between two players, and everything below reads from it.").push("fights");
        FIGHT_TIMEOUT = b.comment("A fight ends after this long with no damage between the two players (or on a death or a logout).")
                .defineInRange("timeout", 30.0, 1.0, 3600.0);
        WIN_SHARE = b.comment("You win when your opponent dies during the fight and you dealt at least this share (0..1) of the",
                        "damage they took from players in it. Wins only affect Grade XP, never Assessment.")
                .defineInRange("winShare", 0.25, 0.0, 1.0);
        OPENING_CAST_SECONDS = b.comment("Malice spent this long before a fight starts still counts as spent in it (the cast that opened it).")
                .defineInRange("openingCastSeconds", 5.0, 0.0, 60.0);
        b.pop();

        b.comment("Grade: kept on reroll and death, only ever seen by its owner. It never goes down and never changes Malice capacity.",
                "There are no trials: no promotion requires killing or beating anyone.").push("grade");
        b.comment("XP each step costs. Grade 1 to Special Grade takes no XP at all: that one is Assessment only.").push("xp_to");
        XP_TO_GRADE[Grade.GRADE_3.ordinal()] = b.defineInRange("grade_3", 200.0, 0.0, 1_000_000.0);
        XP_TO_GRADE[Grade.SEMI_GRADE_2.ordinal()] = b.defineInRange("semi_grade_2", 500.0, 0.0, 1_000_000.0);
        XP_TO_GRADE[Grade.GRADE_2.ordinal()] = b.comment("Also takes an Assessment.").defineInRange("grade_2", 1000.0, 0.0, 1_000_000.0);
        XP_TO_GRADE[Grade.SEMI_GRADE_1.ordinal()] = b.defineInRange("semi_grade_1", 2000.0, 0.0, 1_000_000.0);
        XP_TO_GRADE[Grade.GRADE_1.ordinal()] = b.comment("Also takes an Assessment.").defineInRange("grade_1", 4000.0, 0.0, 1_000_000.0);
        b.pop();
        XP_PER_DAMAGE = b.comment("XP per point of damage dealt to a player in a fight (20 health = 40 XP).").defineInRange("xpPerDamage", 2.0, 0.0, 1000.0);
        XP_WIN = b.comment("XP for winning a fight.").defineInRange("xpWin", 40.0, 0.0, 100_000.0);
        XP_LOSS = b.comment("XP for losing one: you still learn something.").defineInRange("xpLoss", 15.0, 0.0, 100_000.0);
        XP_SURVIVE = b.comment("XP for a fight that ended with nobody dead.").defineInRange("xpSurvive", 20.0, 0.0, 100_000.0);
        SURVIVE_MIN_DAMAGE = b.comment("To earn xpSurvive you must both deal and take this much damage: a tap isn't a fight.")
                .defineInRange("surviveMinDamage", 1.0, 0.0, 1000.0);
        XP_PER_MOB_DAMAGE = b.comment("XP per point of damage dealt to a mob: small, so solo players progress slowly.")
                .defineInRange("xpPerMobDamage", 0.1, 0.0, 1000.0);
        MOB_XP_PER_WINDOW = b.comment("The most XP mobs can give per rollingWindow, so a farm can't replace fighting.")
                .defineInRange("mobXpPerWindow", 40.0, 0.0, 100_000.0);
        ROLLING_WINDOW = b.comment("Seconds of history diminishing returns look back over (3600 = a rolling hour).")
                .defineInRange("rollingWindow", 3600.0, 1.0, 604_800.0);
        FIGHT_DECAY = b.comment("Each earlier fight against the same player in the window multiplies this one's XP by this.")
                .defineInRange("fightDecay", 0.6, 0.0, 1.0);
        FIGHT_DECAY_FLOOR = b.comment("The least diminishing returns can multiply a fight's XP by.")
                .defineInRange("fightDecayFloor", 0.05, 0.0, 1.0);
        WEAKER_PER_GRADE = b.comment("Lost from a fight's XP per Grade your opponent is below you.")
                .defineInRange("weakerPerGrade", 0.35, 0.0, 1.0);
        WEAKER_FLOOR = b.comment("The least a far weaker opponent's fight can be multiplied by: almost nothing.")
                .defineInRange("weakerFloor", 0.02, 0.0, 1.0);
        WIN_BONUS_PER_GRADE = b.comment("Added to the win XP multiplier per Grade your opponent is above you.")
                .defineInRange("winBonusPerGrade", 0.5, 0.0, 100.0);
        WIN_BONUS_MAX = b.comment("The most win XP can be multiplied by.").defineInRange("winBonusMax", 3.0, 1.0, 100.0);
        b.pop();

        b.pop();
    }

    public static long ticks(ModConfigSpec.DoubleValue seconds) {
        return Math.round(seconds.get() * 20.0);
    }
}
