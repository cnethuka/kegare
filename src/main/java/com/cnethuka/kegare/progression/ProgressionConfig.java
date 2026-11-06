package com.cnethuka.kegare.progression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Every progression number: section {@code [progression]} of {@code kegare-server.toml}. Times are in seconds. */
public final class ProgressionConfig {
    public static ModConfigSpec.BooleanValue REQUIRE_REFINEMENT;
    public static ModConfigSpec.ConfigValue<List<? extends String>> UNLOCK_OVERRIDES;
    private static List<? extends String> parsedFrom;
    private static Map<ResourceLocation, Integer> parsedOverrides = Map.of();
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

    // Assessment
    public static ModConfigSpec.DoubleValue QUALIFY_MIN_LENGTH;
    public static ModConfigSpec.DoubleValue QUALIFY_MIN_DAMAGE;
    public static ModConfigSpec.DoubleValue WEIGHT_TECHNIQUE;
    public static ModConfigSpec.DoubleValue WEIGHT_EFFICIENCY;
    public static ModConfigSpec.DoubleValue WEIGHT_PERFORMANCE;
    public static ModConfigSpec.DoubleValue WEIGHT_OPPONENT;
    public static ModConfigSpec.DoubleValue TECHNIQUE_DISTINCT;
    public static ModConfigSpec.DoubleValue TECHNIQUE_SPECIALS;
    public static ModConfigSpec.DoubleValue TECHNIQUE_MIXING;
    public static ModConfigSpec.DoubleValue TECHNIQUE_COMBOS;
    public static ModConfigSpec.DoubleValue DISTINCT_FULL_SHARE;
    public static ModConfigSpec.DoubleValue SPAM_FREE_SHARE;
    public static ModConfigSpec.DoubleValue SPAM_PENALTY;
    public static ModConfigSpec.IntValue COMBO_TARGET;
    public static ModConfigSpec.DoubleValue COMBO_WINDOW;
    public static ModConfigSpec.DoubleValue EFFICIENCY_FULL;
    public static ModConfigSpec.DoubleValue PERFORMANCE_FULL_SHARE;
    public static ModConfigSpec.DoubleValue OPPONENT_EVEN;
    public static ModConfigSpec.DoubleValue OPPONENT_PER_ABOVE;
    public static ModConfigSpec.DoubleValue OPPONENT_PER_BELOW;
    public static ModConfigSpec.DoubleValue OPPONENT_FLOOR;
    public static ModConfigSpec.IntValue SEVERED_FULL_MOVES;
    private static final Gate[] GATES = new Gate[Grade.values().length];
    // The shared hit path
    public static ModConfigSpec.DoubleValue LANDING_REPEAT;
    public static ModConfigSpec.DoubleValue NO_TARGET_WINDOW;

    // Refinement
    public static ModConfigSpec.IntValue REFINEMENT_MAX_LEVEL;
    public static ModConfigSpec.DoubleValue REFINEMENT_XP_BASE;
    public static ModConfigSpec.DoubleValue REFINEMENT_XP_EXPONENT;
    public static ModConfigSpec.DoubleValue REFINEMENT_BASE_XP;
    public static ModConfigSpec.DoubleValue REFINEMENT_SPECIAL_XP;
    public static ModConfigSpec.DoubleValue REFINEMENT_PLAYER_MULTIPLIER;
    public static ModConfigSpec.DoubleValue REFINEMENT_DECAY;
    public static ModConfigSpec.DoubleValue REFINEMENT_DECAY_FLOOR;
    private static final ModConfigSpec.DoubleValue[] GRADE_MULTIPLIER = new ModConfigSpec.DoubleValue[Grade.values().length];

    // Unlocks
    public static ModConfigSpec.DoubleValue INSIGHT_REFINEMENT_RATE;

    // Insight
    static final Map<InsightSource, ModConfigSpec.EnumValue<InsightCategory>> INSIGHT_CATEGORY = new java.util.EnumMap<>(InsightSource.class);
    private static final Map<InsightSource, ModConfigSpec.IntValue> INSIGHT_AMOUNT = new java.util.EnumMap<>(InsightSource.class);

    private record Gate(ModConfigSpec.DoubleValue average, ModConfigSpec.IntValue fights, ModConfigSpec.IntValue opponents, ModConfigSpec.IntValue peak) {}

    private ProgressionConfig() {}

    /** Called from KegareServerConfig while it builds the spec. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Grade, Assessment, Refinement and Insight. None of this changes damage, costs or Malice capacity.").push("progression");
        REQUIRE_REFINEMENT = b.comment("Abilities, specials and ultimates of an innate technique unlock through Refinement. False: everything is",
                        "unlocked, for testing and casual servers. Refinement is still earned, and shared abilities keep their own rules.")
                .define("requireRefinementForAbilities", true);

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

        b.comment("Assessment: how well you use your technique. Each qualifying fight gets a 0-100 score; kills and wins add nothing.",
                "Capacity, rarity and traits are never inputs, so a Low Malice player can reach Special Grade.").push("assessment");
        QUALIFY_MIN_LENGTH = b.comment("A fight qualifies if it lasts this long, first event to last hit...")
                .defineInRange("qualifyMinLength", 10.0, 0.0, 3600.0);
        QUALIFY_MIN_DAMAGE = b.comment("...and both players dealt at least this much damage.")
                .defineInRange("qualifyMinDamage", 4.0, 0.0, 1000.0);
        b.comment("What each part of the score is worth. Severed players have no technique or Malice: melee variety takes the",
                "technique weight, efficiency drops out and the rest is rescaled to 100.").push("weights");
        WEIGHT_TECHNIQUE = b.comment("Technique use: the main factor.").defineInRange("technique", 50.0, 0.0, 1000.0);
        WEIGHT_EFFICIENCY = b.comment("Damage dealt per BASE Malice cost of what you cast.").defineInRange("efficiency", 25.0, 0.0, 1000.0);
        WEIGHT_PERFORMANCE = b.comment("Damage dealt vs damage taken.").defineInRange("performance", 15.0, 0.0, 1000.0);
        WEIGHT_OPPONENT = b.comment("Fighting above your Grade counts for more; far weaker opponents for almost nothing.").defineInRange("opponent", 10.0, 0.0, 1000.0);
        b.pop();
        b.comment("How Technique use splits up, always against what you have UNLOCKED. Locked abilities don't lower it.").push("technique");
        TECHNIQUE_DISTINCT = b.comment("Distinct abilities landed.").defineInRange("distinct", 40.0, 0.0, 1000.0);
        TECHNIQUE_SPECIALS = b.comment("Distinct specials landed.").defineInRange("specials", 25.0, 0.0, 1000.0);
        TECHNIQUE_MIXING = b.comment("Landing both a base ability and a special.").defineInRange("mixing", 10.0, 0.0, 1000.0);
        TECHNIQUE_COMBOS = b.comment("Combining different abilities within comboWindow.").defineInRange("combos", 25.0, 0.0, 1000.0);
        DISTINCT_FULL_SHARE = b.comment("Land this share (rounded up) of the abilities, and of the specials, you have unlocked for full marks.")
                .defineInRange("distinctFullShare", 0.6, 0.01, 1.0);
        SPAM_FREE_SHARE = b.comment("One move can be this share of your landings before it starts to lower the score.")
                .defineInRange("spamFreeShare", 0.5, 0.0, 1.0);
        SPAM_PENALTY = b.comment("What Technique use loses when one move is every landing (0.6 = down to 40%).")
                .defineInRange("spamPenalty", 0.6, 0.0, 1.0);
        COMBO_TARGET = b.comment("Different abilities landed back to back, for full marks on combining.")
                .defineInRange("comboTarget", 3, 1, 1000);
        COMBO_WINDOW = b.comment("Seconds between two landings for them to count as combined.").defineInRange("comboWindow", 4.0, 0.0, 60.0);
        SEVERED_FULL_MOVES = b.comment("Severed players: different melee moves (a weapon, crit or sprint) for full marks on variety.")
                .defineInRange("severedFullMoves", 4, 1, 100);
        b.pop();
        EFFICIENCY_FULL = b.comment("Damage dealt per base Malice for full marks on efficiency.")
                .defineInRange("efficiencyFull", 0.08, 0.0001, 100.0);
        PERFORMANCE_FULL_SHARE = b.comment("Your share of the damage traded for full marks on performance (0.7 = dealing a bit over twice what you take).")
                .defineInRange("performanceFullShare", 0.7, 0.01, 1.0);
        OPPONENT_EVEN = b.comment("What an opponent of your own Grade is worth (0..1). Must leave Special Grade reachable with no stronger opponent.")
                .defineInRange("opponentEven", 0.8, 0.0, 1.0);
        OPPONENT_PER_ABOVE = b.comment("Added per Grade your opponent is above you.").defineInRange("opponentPerAbove", 0.1, 0.0, 1.0);
        OPPONENT_PER_BELOW = b.comment("Taken away per Grade your opponent is below you.").defineInRange("opponentPerBelow", 0.3, 0.0, 1.0);
        OPPONENT_FLOOR = b.comment("The least a far weaker opponent is worth.").defineInRange("opponentFloor", 0.02, 0.0, 1.0);
        b.comment("What each Grade asks for. peakRefinement: the highest level ever reached on any technique. It survives rerolls and",
                "Severed players skip it. Asking for more than the top Refinement level means the top level.").push("requirements");
        GATES[Grade.GRADE_2.ordinal()] = gate(b, "grade_2", 50.0, 5, 2, 2);
        GATES[Grade.GRADE_1.ordinal()] = gate(b, "grade_1", 70.0, 10, 3, 5);
        GATES[Grade.SPECIAL_GRADE.ordinal()] = gate(b, "special_grade", 90.0, 20, 3, 10);
        b.pop();
        b.pop();

        b.comment("Refinement: per technique, reset by a reroll. Earned only by landing the current technique's abilities on real targets.",
                "It only unlocks things: it never changes damage, cost or cooldown.").push("refinement");
        REFINEMENT_MAX_LEVEL = b.comment("The top level.").defineInRange("maxLevel", 10, 1, 1000);
        REFINEMENT_XP_BASE = b.comment("Level L costs xpBase * L^xpExponent XP. By default that puts level 2 (the third ability) about",
                        "12 minutes into fighting mobs, or 4 fighting players, and level 9 (the full kit) several sessions in.")
                .defineInRange("xpBase", 30.0, 0.01, 1_000_000.0);
        REFINEMENT_XP_EXPONENT = b.defineInRange("xpExponent", 1.6, 0.0, 10.0);
        UNLOCK_OVERRIDES = b.comment("Overrides the level an ability, special or ultimate unlocks at, as ability=level.",
                        "Example: [\"kegare:frost_field=1\", \"kegare:total_swap=5\"]")
                .defineListAllowEmpty("unlockLevelOverrides", List.of(), () -> "kegare:ability=0", ProgressionConfig::validOverride);
        REFINEMENT_BASE_XP = b.comment("XP for landing a base ability or the ultimate.").defineInRange("baseXp", 1.0, 0.0, 10_000.0);
        REFINEMENT_SPECIAL_XP = b.comment("XP for landing a special.").defineInRange("specialXp", 2.5, 0.0, 10_000.0);
        REFINEMENT_PLAYER_MULTIPLIER = b.comment("Hits on players count this many times a hit on a mob. Mobs still count, so a player can practice alone.")
                .defineInRange("playerMultiplier", 3.0, 0.0, 1000.0);
        REFINEMENT_DECAY = b.comment("Each earlier landing on the same target within grade.rollingWindow multiplies this one's XP by this.")
                .defineInRange("targetDecay", 0.97, 0.0, 1.0);
        REFINEMENT_DECAY_FLOOR = b.comment("The least diminishing returns can multiply a landing's XP by.")
                .defineInRange("targetDecayFloor", 0.2, 0.0, 1.0);
        b.comment("Multiplies Refinement XP by Grade, so an experienced sorcerer re-learns faster after a reroll.").push("grade_multiplier");
        double[] multipliers = {1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5};
        for (Grade grade : Grade.values()) {
            GRADE_MULTIPLIER[grade.ordinal()] = b.defineInRange(grade.id(), multipliers[grade.ordinal()], 0.0, 1000.0);
        }
        b.pop();
        b.pop();

        b.comment("Insight: each source pays once per player, ever, into one of DOMINION, WARD or OUTPUT. It's kept on reroll and death.",
                "hit_by_technique pays once per technique, first_legendary_fight once ever. The other sources wait for systems that don't exist yet.").push("insight");
        for (InsightSource source : InsightSource.values()) {
            b.push(source.id());
            INSIGHT_CATEGORY.put(source, b.comment("Which kind of Insight this pays into.").defineEnum("category", source.defaultCategory));
            INSIGHT_AMOUNT.put(source, b.comment("How much it pays.").defineInRange("amount", source.defaultAmount, 0, 100_000));
            b.pop();
        }
        b.pop();

        b.comment("The framework for shared and system unlocks, separate from a technique's own abilities.").push("unlocks");
        INSIGHT_REFINEMENT_RATE = b.comment("Refinement levels matching Insight takes off an unlock's requirement, per point of Insight. It rounds down, so by default 10 Insight is 1 level.")
                .defineInRange("refinementLoweredPerInsight", 0.1, 0.0, 100.0);
        b.pop();

        b.comment("The shared hit path: what counts as landing an ability on a real target.").push("landing");
        LANDING_REPEAT = b.comment("Seconds before the same ability can land on the same target again: one volley or channel isn't twenty landings.")
                .defineInRange("repeat", 1.0, 0.0, 60.0);
        NO_TARGET_WINDOW = b.comment("An ability with no target of its own only counts used this long after trading a hit with something real.",
                        "Used with nobody around, it earns nothing.")
                .defineInRange("noTargetWindow", 10.0, 0.0, 600.0);
        b.pop();

        b.pop();
    }

    private static boolean validOverride(Object entry) {
        return entry instanceof String s && parseOverride(s) != null;
    }

    private static Map.Entry<ResourceLocation, Integer> parseOverride(String entry) {
        int split = entry.lastIndexOf('=');
        if (split <= 0) return null;
        ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, split).trim());
        if (id == null) return null;
        try {
            return Map.entry(id, Math.max(0, Integer.parseInt(entry.substring(split + 1).trim())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The configured level for this ability, if the server overrides it. Parsed once per config load. */
    static OptionalInt unlockOverride(ResourceLocation ability) {
        List<? extends String> current = UNLOCK_OVERRIDES.get();
        if (current != parsedFrom) {
            Map<ResourceLocation, Integer> parsed = new HashMap<>();
            for (String entry : current) {
                Map.Entry<ResourceLocation, Integer> override = parseOverride(entry);
                if (override != null) parsed.put(override.getKey(), override.getValue());
            }
            parsedOverrides = parsed;
            parsedFrom = current;
        }
        Integer level = parsedOverrides.get(ability);
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }

    private static Gate gate(ModConfigSpec.Builder b, String name, double average, int fights, int opponents, int peak) {
        b.push(name);
        Gate gate = new Gate(
                b.comment("Average score needed...").defineInRange("average", average, 0.0, 100.0),
                b.comment("...over this many of your most recent qualifying fights...").defineInRange("fights", fights, 1, 64),
                b.comment("...against at least this many different opponents, so one friend can't carry it.").defineInRange("opponents", opponents, 1, 64),
                b.comment("Peak Refinement needed.").defineInRange("peakRefinement", peak, 0, 1000));
        b.pop();
        return gate;
    }

    /** What a Grade's Assessment asks for. Only for Grades that take one. */
    static AssessmentRules.Requirement requirement(Grade grade) {
        Gate gate = GATES[grade.ordinal()];
        return new AssessmentRules.Requirement(gate.average.get(), gate.fights.get(), gate.opponents.get(),
                Math.min(gate.peak.get(), REFINEMENT_MAX_LEVEL.get()));
    }

    static InsightCategory insightCategory(InsightSource source) {
        return INSIGHT_CATEGORY.get(source).get();
    }

    static int insightAmount(InsightSource source) {
        return INSIGHT_AMOUNT.get(source).get();
    }

    static double gradeMultiplier(Grade grade) {
        return GRADE_MULTIPLIER[grade.ordinal()].get();
    }

    public static long ticks(ModConfigSpec.DoubleValue seconds) {
        return Math.round(seconds.get() * 20.0);
    }
}
