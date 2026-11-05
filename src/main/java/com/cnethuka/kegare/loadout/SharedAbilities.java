package com.cnethuka.kegare.loadout;

import com.cnethuka.kegare.innatetechnique.Ability;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared abilities ({@link AbilityCategory#SHARED}): not tied to any innate technique. A player unlocks them once
 * ({@link SharedAbilityService}) and keeps them through rerolls; any loadout slot can hold one. Registered in code at
 * mod construction, in display order. Reverse Malice Technique is the first.
 */
public final class SharedAbilities {
    private static final Map<ResourceLocation, Ability> ALL = new LinkedHashMap<>();

    private SharedAbilities() {}

    public static <T extends Ability> T register(T ability) {
        if (ALL.putIfAbsent(ability.id(), ability) != null) throw new IllegalStateException("Duplicate shared ability " + ability.id());
        return ability;
    }

    public static List<Ability> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    /** A shared ability by ID (not its special). */
    public static Optional<Ability> get(ResourceLocation id) {
        return Optional.ofNullable(ALL.get(id));
    }

    public static boolean isShared(ResourceLocation id) {
        return ALL.containsKey(id);
    }

    /** A shared ability or the special of one, by ID. */
    public static Optional<Ability> find(ResourceLocation id) {
        Ability direct = ALL.get(id);
        if (direct != null) return Optional.of(direct);
        for (Ability a : ALL.values()) {
            Optional<Ability> special = a.special();
            if (special.isPresent() && special.get().id().equals(id)) return special;
        }
        return Optional.empty();
    }
}
