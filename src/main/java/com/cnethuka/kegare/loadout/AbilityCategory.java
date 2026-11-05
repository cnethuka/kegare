package com.cnethuka.kegare.loadout;

import java.util.Locale;

/**
 * Where an ability comes from, as the Loadout page groups them (one category tab each). Innate Technique abilities
 * come with the current innate technique and change on a reroll; Shared abilities are unlocked by the player and
 * kept for good. Later categories (e.g. martial arts, cursed tools) slot in here.
 */
public enum AbilityCategory {
    INNATE_TECHNIQUE,
    SHARED;

    public String translationKey() {
        return "ability_category.kegare." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
