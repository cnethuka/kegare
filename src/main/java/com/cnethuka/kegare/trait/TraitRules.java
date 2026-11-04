package com.cnethuka.kegare.trait;

/** Pure trait eligibility rules (no Minecraft types, unit-tested). */
public final class TraitRules {
    /** What happens to a trait when its bearer becomes Severed. */
    public enum SeveredAction {
        /** Doesn't need Malice: the player keeps it. */
        KEEP,
        /** Needs Malice: removed (a unique one is released, free to be rolled again). */
        REMOVE,
        /** Needs Malice, unique and inheritable: removed and sent through the normal succession (transfer). */
        TRANSFER
    }

    private TraitRules() {}

    public static SeveredAction whenSevered(boolean requiresMalice, boolean unique, boolean inheritable) {
        if (!requiresMalice) return SeveredAction.KEEP;
        return unique && inheritable ? SeveredAction.TRANSFER : SeveredAction.REMOVE;
    }

    /** A trait that needs Malice can never sit on a Severed (zero-Malice) player. */
    public static boolean canHold(boolean requiresMalice, boolean severed) {
        return !(requiresMalice && severed);
    }

    /** Whether a trait is even considered in a player's birth roll. */
    public static boolean canRollAtBirth(double birthChance, boolean requiresMalice, boolean severed) {
        return birthChance > 0 && canHold(requiresMalice, severed);
    }
}
