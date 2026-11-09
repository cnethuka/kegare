package com.cnethuka.kegare.loadout;

import java.util.Locale;

/**
 * A broad group of abilities of one kind, one tab each on the Sorcerer screen's left edge (DESIGN §3.16), never one ability on its own:
 * each will hold more as the mod grows. Every ability declares its own ({@link com.cnethuka.kegare.innatetechnique.Ability#category}).
 * <ul>
 *   <li>{@link #INNATE}: the current innate technique's abilities (the ultimate stays in its own V tile). A reroll replaces them.</li>
 *   <li>{@link #MALICE}: energy techniques any Malice user can learn and keeps through rerolls: Reinforcement, Reverse Malice Technique.
 *       Later: Maximum Output and the like.</li>
 *   <li>{@link #MELEE}: close-combat abilities: Reinforced Fists. Later: martial arts and cursed-tool moves.</li>
 * </ul>
 * Malice and Melee abilities live in the shared-abilities registry ({@link SharedAbilities}): unlocked once, kept through rerolls.
 */
public enum AbilityCategory {
    INNATE,
    MALICE,
    MELEE;

    public String translationKey() {
        return "ability_category.kegare." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** The tabs a player sees: all of them, in order, or none for a Severed player (no tabs and no bar). */
    public static java.util.List<AbilityCategory> tabsFor(boolean severed) {
        return severed ? java.util.List.of() : java.util.List.of(values());
    }

    /** What's under this tab: the technique's own abilities for {@link #INNATE}, else the shared abilities of this category. */
    public java.util.List<com.cnethuka.kegare.innatetechnique.Ability> abilities(@javax.annotation.Nullable com.cnethuka.kegare.innatetechnique.InnateTechnique technique) {
        if (this == INNATE) return technique == null ? java.util.List.of() : technique.abilities();
        return SharedAbilities.all().stream().filter(a -> a.category() == this).toList();
    }
}
