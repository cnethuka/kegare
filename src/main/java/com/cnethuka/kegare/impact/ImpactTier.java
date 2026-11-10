package com.cnethuka.kegare.impact;

/**
 * How hard a hit lands (DESIGN §3.23). The server works it out from what the hit did ({@link ImpactStrength}); every part of the
 * Impact System scales with it.
 */
public enum ImpactTier {
    /** A normal hit. It barely registers. */
    LIGHT,
    /** A solid hit. */
    MEDIUM,
    /** Reinforced Strikes and big technique hits. */
    HEAVY,
    /** The biggest hits: a black-and-white impact frame for the two involved. */
    IMPACT_FRAME,
    /** Reserved: no strength reaches it, only a hit that asks for it by name. Black Spark doesn't; it goes through the rule like any hit. */
    TOP;

    public String serializedName() {
        return switch (this) {
            case LIGHT -> "light";
            case MEDIUM -> "medium";
            case HEAVY -> "heavy";
            case IMPACT_FRAME -> "impact_frame";
            case TOP -> "top";
        };
    }

    public ImpactTier lower() {
        return this == LIGHT ? LIGHT : values()[ordinal() - 1];
    }

    public static ImpactTier byId(int id) {
        ImpactTier[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, id))];
    }
}
