package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.progression.Grade;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Dominions' numbers (DESIGN §3.35): {@code [dominion]} of {@code kegare-server.toml}. Times in seconds, distances in blocks. */
public final class DominionConfig {
    // The Dominion Meter
    public static ModConfigSpec.DoubleValue METER_PER_DAMAGE_DEALT;
    public static ModConfigSpec.DoubleValue METER_PER_DAMAGE_TAKEN;
    public static ModConfigSpec.DoubleValue METER_DRAIN;
    // Opening and the barrier
    public static ModConfigSpec.DoubleValue OPEN_SECONDS;
    public static ModConfigSpec.DoubleValue FULL_RADIUS;
    public static ModConfigSpec.DoubleValue FULL_DURATION;
    public static ModConfigSpec.DoubleValue FULL_BARRIER;
    public static ModConfigSpec.DoubleValue DOMINION_BURNOUT;
    public static ModConfigSpec.IntValue FULL_INNER_RADIUS;
    public static ModConfigSpec.DoubleValue RETURN_DELAY;
    public static ModConfigSpec.DoubleValue MELEE_REACH;
    public static ModConfigSpec.DoubleValue BLAST_REACH;
    public static ModConfigSpec.DoubleValue PROJECTILE_STRENGTH;
    // The Incomplete Dominion
    public static ModConfigSpec.DoubleValue INCOMPLETE_RADIUS;
    public static ModConfigSpec.IntValue INCOMPLETE_INNER_RADIUS;
    public static ModConfigSpec.DoubleValue INCOMPLETE_DURATION;
    public static ModConfigSpec.DoubleValue INCOMPLETE_BARRIER;
    public static ModConfigSpec.DoubleValue INCOMPLETE_BURNOUT;
    public static ModConfigSpec.DoubleValue INCOMPLETE_METER_SHARE;
    public static ModConfigSpec.DoubleValue INCOMPLETE_COST;
    public static ModConfigSpec.DoubleValue INCOMPLETE_COOLDOWN;
    public static ModConfigSpec.DoubleValue INCOMPLETE_TECHNIQUE_COST;
    public static ModConfigSpec.DoubleValue INCOMPLETE_COOLDOWN_RATE;
    public static ModConfigSpec.DoubleValue INCOMPLETE_OUTPUT;
    public static ModConfigSpec.EnumValue<Grade> INCOMPLETE_UNLOCK_GRADE;
    public static ModConfigSpec.IntValue INCOMPLETE_UNLOCK_REFINEMENT;
    // Clashes
    public static ModConfigSpec.DoubleValue CLASH_GRIP;
    public static ModConfigSpec.DoubleValue CLASH_MAINTAIN;
    public static ModConfigSpec.DoubleValue CLASH_FIGHT;
    public static ModConfigSpec.DoubleValue CLASH_DRAIN;
    public static ModConfigSpec.DoubleValue CLASH_LEASH;
    public static ModConfigSpec.DoubleValue CLASH_PROMPT_MIN;
    public static ModConfigSpec.DoubleValue CLASH_PROMPT_MAX;
    public static ModConfigSpec.IntValue CLASH_PROMPT_LEAD_MS;
    public static ModConfigSpec.IntValue CLASH_PROMPT_WINDOW_MS;
    public static ModConfigSpec.DoubleValue CLASH_PUSH_PER_HIT;
    public static ModConfigSpec.DoubleValue CLASH_PUSH_PER_MISS;
    public static ModConfigSpec.DoubleValue CLASH_CRACK_PER_MISS;
    public static ModConfigSpec.DoubleValue CLASH_INCOMPLETE_HANDICAP;
    public static ModConfigSpec.DoubleValue CLASH_TIE_MARGIN;
    public static ModConfigSpec.DoubleValue CLASH_EXHAUSTED_BURNOUT;

    private DominionConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Dominions: a sorcerer's inner space expanded round them inside a barrier. A full Dominion carries its technique and is",
                "that technique's ultimate; an Incomplete Dominion is the inner space with Malice alone, which any Malice user can learn.")
                .push("dominion");

        b.comment("The Dominion Meter, 0 to 100, private to its owner. It fills from damage dealt to and taken from players in fights, and",
                "drains slowly out of combat. A full Dominion takes a full meter; an Incomplete Dominion takes part of it.").push("meter");
        METER_PER_DAMAGE_DEALT = b.comment("Meter gained per point of damage dealt to a player in a fight (after their defences).")
                .defineInRange("perDamageDealt", 2.0, 0.0, 100.0);
        METER_PER_DAMAGE_TAKEN = b.comment("Meter gained per point of damage taken from a player in a fight.")
                .defineInRange("perDamageTaken", 1.5, 0.0, 100.0);
        METER_DRAIN = b.comment("Meter lost per second while out of combat.").defineInRange("drainPerSecond", 0.25, 0.0, 100.0);
        b.pop();

        OPEN_SECONDS = b.comment("How long the barrier takes to rise and close. Whoever is inside when it closes is trapped; anyone can still",
                "get out before that.").defineInRange("openSeconds", 1.0, 0.05, 10.0);
        MELEE_REACH = b.comment("A swing hits the barrier when the shell is this close along the look (no block or body nearer).")
                .defineInRange("meleeReach", 3.0, 0.5, 8.0);
        BLAST_REACH = b.comment("An area blast this close to the shell, inside or outside, hits the barrier (full strength at the shell, less",
                "further off).").defineInRange("blastReach", 2.5, 0.0, 16.0);
        RETURN_DELAY = b.comment("As a Dominion ends, how long those in its inner space see it end before they're back in the world.")
                .defineInRange("returnDelaySeconds", 0.5, 0.0, 5.0);
        PROJECTILE_STRENGTH = b.comment("A plain projectile stopped by the barrier hits it this hard (the Impact System's strength). A cast's",
                "projectile adds the Malice behind its cast, by the Impact System's rule.").defineInRange("projectileStrength", 0.3, 0.0, 100.0);

        b.comment("A full Dominion: a technique's ultimate (none ships yet).").push("full");
        FULL_RADIUS = b.comment("The barrier's radius.").defineInRange("radius", 16.0, 2.0, 64.0);
        FULL_INNER_RADIUS = b.comment("How far its inner space reaches (bigger than its shell: the inside is bigger than it looks).")
                .defineInRange("innerRadius", 32, 4, 96);
        FULL_DURATION = b.comment("How long it stays open once closed.").defineInRange("durationSeconds", 30.0, 1.0, 600.0);
        FULL_BARRIER = b.comment("Barrier strength: the Impact System's strength of the hits it takes before it breaks.")
                .defineInRange("barrierStrength", 40.0, 0.1, 100_000.0);
        DOMINION_BURNOUT = b.comment("Burnout on the caster afterwards. Keep it equal to",
                "reverse_malice_technique.repair.dominionBurnoutAssumed, so the brain's repair math stays right.")
                .defineInRange("dominionBurnout", 60.0, 0.0, 3600.0);
        b.pop();

        b.comment("The Incomplete Dominion: the inner space expanded with Malice alone, without a technique in it. Any Malice user can learn",
                "it; it's weaker than a full Dominion and finds nobody (no sure-hit), but inside it its caster's technique runs better.")
                .push("incomplete");
        INCOMPLETE_RADIUS = b.comment("The barrier's radius.").defineInRange("radius", 10.0, 2.0, 64.0);
        INCOMPLETE_INNER_RADIUS = b.comment("How far its inner space reaches (bigger than its shell: the inside is bigger than it looks).")
                .defineInRange("innerRadius", 24, 4, 96);
        INCOMPLETE_DURATION = b.comment("How long it stays open once closed.").defineInRange("durationSeconds", 20.0, 1.0, 600.0);
        INCOMPLETE_BARRIER = b.comment("Barrier strength: the Impact System's strength of the hits it takes before it breaks.")
                .defineInRange("barrierStrength", 10.0, 0.1, 100_000.0);
        INCOMPLETE_BURNOUT = b.comment("Burnout on the caster afterwards.").defineInRange("incompleteBurnout", 15.0, 0.0, 3600.0);
        INCOMPLETE_METER_SHARE = b.comment("The share of a full Dominion Meter it takes to open (0.5: half).")
                .defineInRange("meterShare", 0.5, 0.0, 1.0);
        INCOMPLETE_COST = b.comment("Malice it costs to open, on top of the meter.").defineInRange("cost", 150.0, 0.0, 100_000.0);
        INCOMPLETE_COOLDOWN = b.comment("Its cooldown, from the moment it's cast.").defineInRange("cooldownSeconds", 30.0, 0.0, 3600.0);
        b.comment("What it does for its caster while it stands (not while it rises, nor in a clash), and for nobody else.").push("boosts");
        INCOMPLETE_TECHNIQUE_COST = b.comment("Their innate technique's casts, releases and upkeep cost this much less (0.25: a quarter).")
                .defineInRange("techniqueCost", 0.25, 0.0, 0.9);
        INCOMPLETE_COOLDOWN_RATE = b.comment("Their innate technique's cooldowns run this fast (1.5: half again as fast).")
                .defineInRange("cooldownRate", 1.5, 1.0, 4.0);
        INCOMPLETE_OUTPUT = b.comment("Their innate technique's hits do this much more (0.15: 15%), before the target's defences.")
                .defineInRange("output", 0.15, 0.0, 2.0);
        b.pop();
        b.comment("What it takes to learn it. Kept once learned, through rerolls. Severed players never can.").push("unlock");
        INCOMPLETE_UNLOCK_GRADE = b.comment("The Grade needed.").defineEnum("grade", Grade.SEMI_GRADE_2);
        INCOMPLETE_UNLOCK_REFINEMENT = b.comment("The Peak Refinement needed (the highest Refinement reached on any technique). Dominion",
                "Insight lowers it, by progression.unlocks' refinementLoweredPerInsight, at most refinementLoweredCap of it.")
                .defineInRange("peakRefinement", 6, 0, 10);
        b.pop();
        b.pop();

        b.comment("Clashes: a Dominion opening over one that's opening or open. Two sides at most. First both casters hold on (Maintain):",
                "a tug of war won by timing, pushed all the way wins. If neither is pushed out, they fight (Fight): the one who deals the",
                "other more damage wins. The loser's barrier breaks and they burn out; the winner's closes. Too close to call, both break.")
                .push("clash");
        CLASH_GRIP = b.comment("At the start, how long both casters have to be holding the key that opens their Dominion (the one whose",
                "Dominion was already open has to press it again). Not holding it then loses.").defineInRange("gripSeconds", 2.0, 0.0, 10.0);
        CLASH_MAINTAIN = b.comment("How long the Maintain phase lasts before it comes to a fight.").defineInRange("maintainSeconds", 10.0, 1.0, 120.0);
        CLASH_FIGHT = b.comment("How long the Fight phase lasts.").defineInRange("fightSeconds", 15.0, 1.0, 300.0);
        CLASH_DRAIN = b.comment("Malice each caster pays a second while they hold on in Maintain. Running out loses at once.")
                .defineInRange("drainPerSecond", 15.0, 0.0, 10_000.0);
        CLASH_LEASH = b.comment("In Maintain, how far a caster can move from where they stood.").defineInRange("leash", 1.5, 0.0, 16.0);
        CLASH_PROMPT_MIN = b.comment("The shortest time between two of a caster's timing prompts.").defineInRange("promptEveryMin", 1.0, 0.3, 10.0);
        CLASH_PROMPT_MAX = b.comment("The longest time between two of a caster's timing prompts.").defineInRange("promptEveryMax", 2.0, 0.3, 10.0);
        CLASH_PROMPT_LEAD_MS = b.comment("How long a prompt shows before its beat, in ms.").defineInRange("promptLeadMs", 900, 100, 5000);
        CLASH_PROMPT_WINDOW_MS = b.comment("The window round a prompt's beat that counts as a hit, in ms (half before it, half after), judged",
                "by sub-tick timing.").defineInRange("promptWindowMs", 500, 50, 2000);
        CLASH_PUSH_PER_HIT = b.comment("A hit pushes the bar this far toward the other side (all the way is 1).").defineInRange("pushPerHit", 0.2, 0.0, 1.0);
        CLASH_PUSH_PER_MISS = b.comment("A miss (the wrong key, too early, too late or not at all) pushes the bar back this far.")
                .defineInRange("pushPerMiss", 0.1, 0.0, 1.0);
        CLASH_CRACK_PER_MISS = b.comment("A miss also cracks your own barrier: this share of its strength. A barrier that runs out breaks and",
                "loses the clash.").defineInRange("crackPerMiss", 0.1, 0.0, 1.0);
        CLASH_INCOMPLETE_HANDICAP = b.comment("An Incomplete Dominion against a full one pushes this much less (0.3: 30% less).")
                .defineInRange("incompleteHandicap", 0.3, 0.0, 0.95);
        CLASH_TIE_MARGIN = b.comment("In the Fight phase, scores this close (a share of the higher) are a tie: both barriers break.")
                .defineInRange("tieMargin", 0.1, 0.0, 1.0);
        CLASH_EXHAUSTED_BURNOUT = b.comment("Burnout on both casters after a tie, instead of their Dominions' own.")
                .defineInRange("exhaustedBurnout", 45.0, 0.0, 3600.0);
        b.pop();
        b.pop();
    }

    public static int ticks(ModConfigSpec.DoubleValue seconds) {
        return (int) Math.round(seconds.get() * 20.0);
    }
}
