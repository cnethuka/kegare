package com.cnethuka.kegare.loadout;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Finds any ability by ID, wherever it comes from: the innate technique (with specials) or the shared ones. */
public final class AbilityLookup {
    private AbilityLookup() {}

    public static Optional<Ability> find(@Nullable InnateTechnique technique, ResourceLocation id) {
        if (technique != null) {
            Optional<Ability> own = technique.findAbility(id);
            if (own.isPresent()) return own;
        }
        return SharedAbilities.find(id);
    }

    public static AbilityCategory category(ResourceLocation id) {
        return SharedAbilities.find(id).map(com.cnethuka.kegare.innatetechnique.Ability::category).orElse(AbilityCategory.INNATE);
    }
}
