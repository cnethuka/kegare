package com.cnethuka.kegare.sixeyes;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Six Eyes' numbers (DESIGN §3.32): {@code [six_eyes]} of {@code kegare-server.toml}. Perception has none: it never grows and has no cap. */
public final class SixEyesConfig {
    // Strain
    public static ModConfigSpec.DoubleValue STRAIN_BUILD;
    public static ModConfigSpec.DoubleValue GLASSES_BUILD;
    public static ModConfigSpec.DoubleValue BLINDFOLD_BUILD;
    public static ModConfigSpec.DoubleValue BACKGROUND_RMT_BUILD;
    public static ModConfigSpec.DoubleValue STRAIN_FALL;
    public static ModConfigSpec.DoubleValue STRAIN_FALL_COVERED;
    public static ModConfigSpec.DoubleValue RMT_STRAIN_RELIEF;
    public static ModConfigSpec.DoubleValue EDGE_BACK_BELOW;
    public static ModConfigSpec.DoubleValue RISK_GROWTH;
    public static ModConfigSpec.DoubleValue RMT_RISK_RELIEF;
    public static ModConfigSpec.DoubleValue COLLAPSE_STUN;
    public static ModConfigSpec.DoubleValue COLLAPSE_DAMAGE;
    public static ModConfigSpec.DoubleValue EDGE_RANGE;
    // Efficiency, Attunement and RMT
    public static ModConfigSpec.DoubleValue EFFICIENCY_BASE;
    public static ModConfigSpec.DoubleValue EFFICIENCY_GRADE;
    public static ModConfigSpec.DoubleValue EFFICIENCY_ATTUNEMENT;
    public static ModConfigSpec.DoubleValue ATTUNEMENT_SCALE;
    public static ModConfigSpec.DoubleValue RMT_OUTPUT_WEIGHT;
    public static ModConfigSpec.DoubleValue ATTUNEMENT_FIGHT;
    public static ModConfigSpec.DoubleValue ATTUNEMENT_READ;
    public static ModConfigSpec.DoubleValue ATTUNEMENT_PIN;

    private SixEyesConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Six Eyes: passive, always on, at full from the moment someone has it. Perception never grows and has no number here;",
                "only efficiency and RMT output grow, through Grade and Attunement. Strain is 0 to 100.").push("six_eyes");

        b.comment("Strain: the eyes' fatigue. It builds in a fight and falls out of one.").push("strain");
        STRAIN_BUILD = b.comment("Strain a second in a fight with the eyes uncovered (100 / this = seconds to the top).").defineInRange("buildPerSecond", 1.25, 0.0, 100.0);
        GLASSES_BUILD = b.comment("That, times this, with tinted glasses on.").defineInRange("glassesFactor", 0.5, 0.0, 1.0);
        BLINDFOLD_BUILD = b.comment("That, times this, with a blindfold on.").defineInRange("blindfoldFactor", 0.2, 0.0, 1.0);
        BACKGROUND_RMT_BUILD = b.comment("And times this while Background RMT runs: it holds Strain back, so a bearer running it can stay uncovered",
                "a whole fight.").defineInRange("backgroundRmtFactor", 0.2, 0.0, 1.0);
        STRAIN_FALL = b.comment("Strain a second out of a fight, uncovered.").defineInRange("fallPerSecond", 1.0, 0.0, 100.0);
        STRAIN_FALL_COVERED = b.comment("Out of a fight, covered (glasses or a blindfold).").defineInRange("fallCoveredPerSecond", 3.0, 0.0, 100.0);
        RMT_STRAIN_RELIEF = b.comment("Strain a second taken off while channelling Reverse Malice Technique (it refreshes the brain).").defineInRange("rmtReliefPerSecond", 8.0, 0.0, 100.0);
        EDGE_BACK_BELOW = b.comment("At the top, the uncovered edge is lost until Strain cools below this.").defineInRange("edgeBackBelow", 60.0, 0.0, 100.0);
        RISK_GROWTH = b.comment("Collapse risk: uncovered at the top, the chance of collapsing each second grows this much a second (0.01: 10% a",
                "second after 10 s). Covering the eyes stops it growing.").defineInRange("riskGrowthPerSecond", 0.01, 0.0, 1.0);
        RMT_RISK_RELIEF = b.comment("Risk a second taken off while channelling Reverse Malice Technique.").defineInRange("rmtRiskReliefPerSecond", 0.05, 0.0, 1.0);
        COLLAPSE_STUN = b.comment("A collapse: stunned this long (no moving, no casting)...").defineInRange("collapseStun", 2.0, 0.0, 60.0);
        COLLAPSE_DAMAGE = b.comment("...and this much damage armour doesn't stop.").defineInRange("collapseDamage", 6.0, 0.0, 1000.0);
        EDGE_RANGE = b.comment("The uncovered edge: how far off a top-tier opponent's cast can be read before it starts.").defineInRange("edgeRange", 64.0, 1.0, 100_000.0);
        b.pop();

        b.comment("Efficiency: every Malice cost the bearer pays is x 1 / (1 + x), x = base + grade x Grade step (0 at Grade 4, 6 at Special",
                "Grade) + attunement x sqrt(Attunement / attunementScale). It keeps climbing with no flat point: costs near free and never get",
                "there. Every cast still costs at least 1. Eyewear and Strain never touch it.").push("efficiency");
        EFFICIENCY_BASE = b.defineInRange("base", 0.25, 0.0, 100.0);
        EFFICIENCY_GRADE = b.defineInRange("grade", 0.35, 0.0, 100.0);
        EFFICIENCY_ATTUNEMENT = b.defineInRange("attunement", 1.5, 0.0, 100.0);
        ATTUNEMENT_SCALE = b.defineInRange("attunementScale", 200.0, 1.0, 1.0e9);
        RMT_OUTPUT_WEIGHT = b.comment("Reverse Malice Technique's output x (1 + this x x), the same x: beyond the normal 1.6x, no fixed cap",
                "(about x1.9 at Special Grade and Attunement 20,000). Injury fatigue still applies.").defineInRange("rmtOutputWeight", 0.052, 0.0, 100.0);
        b.pop();

        b.comment("Attunement grows by using the eyes, and is lost entirely when they pass on: a new bearer starts out clumsy.").push("attunement");
        ATTUNEMENT_FIGHT = b.comment("A second in a fight while holding the eyes.").defineInRange("perFightSecond", 1.0, 0.0, 1000.0);
        ATTUNEMENT_READ = b.comment("A full-detail Read of someone new (each player at most once a minute).").defineInRange("perRead", 3.0, 0.0, 1000.0);
        ATTUNEMENT_PIN = b.comment("A pin (each one at most once a minute).").defineInRange("perPin", 1.0, 0.0, 1000.0);
        b.pop();

        b.pop();
    }
}
