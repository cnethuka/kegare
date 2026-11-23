package com.cnethuka.kegare.api.ability;

import java.util.Locale;

/**
 * A broad group of abilities of one kind, one tab each on the Sorcerer screen's left edge (DESIGN §3.16), never one ability on its own:
 * each will hold more as the mod grows. Every ability declares its own ({@link com.cnethuka.kegare.api.ability.Ability#category}).
 * <ul>
 *   <li>{@link #INNATE}: the current innate technique's abilities (the ultimate stays in its own V tile). A reroll replaces them.</li>
 *   <li>{@link #MALICE}: energy techniques any Malice user can learn and keeps through rerolls: Flow, Reverse Malice Technique.</li>
 *   <li>{@link #MELEE}: close-combat abilities: Reinforced Fists.</li>
 * </ul>
 * Malice and Melee abilities are shared abilities ({@link com.cnethuka.kegare.api.KegareApi#registerSharedAbility}): unlocked once, kept
 * through rerolls.
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
}
