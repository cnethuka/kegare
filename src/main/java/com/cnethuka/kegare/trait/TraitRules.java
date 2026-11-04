package com.cnethuka.kegare.trait;

/** Pure trait eligibility rules (no Minecraft types, unit-tested). */
public final class TraitRules {
    private TraitRules() {}

    /** A trait that needs Malice can never sit on a Severed (zero-Malice) player. */
    public static boolean canHold(boolean requiresMalice, boolean severed) {
        return !(requiresMalice && severed);
    }

    /** Whether a trait is even considered in a player's birth roll. */
    public static boolean canRollAtBirth(double birthChance, boolean requiresMalice, boolean severed) {
        return birthChance > 0 && canHold(requiresMalice, severed);
    }
}
