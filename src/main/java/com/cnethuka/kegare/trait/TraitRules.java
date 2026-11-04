package com.cnethuka.kegare.trait;

/** Pure trait eligibility rules (no Minecraft types, unit-tested). */
public final class TraitRules {
    private TraitRules() {}

    /** A trait that needs ce can never sit on a Severed (zero-ce) player. */
    public static boolean canHold(boolean requiresCe, boolean severed) {
        return !(requiresCe && severed);
    }

    /** Whether a trait is even considered in a player's birth roll. */
    public static boolean canRollAtBirth(double birthChance, boolean requiresCe, boolean severed) {
        return birthChance > 0 && canHold(requiresCe, severed);
    }
}
