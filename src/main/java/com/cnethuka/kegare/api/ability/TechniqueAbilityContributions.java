package com.cnethuka.kegare.api.ability;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Mod-construction-time additions to an innate technique's ordinary loadout abilities.
 *
 * <p>Register contributions from a mod constructor, before innate technique registry entries are constructed. Every
 * contribution supplies its own Refinement level and, when it has a special, that special's level too. Contributions
 * append in registration order unless an existing ordinary ability is named to insert after. Several contributions
 * naming the same anchor retain registration order.</p>
 *
 * <p>A technique supplier applies its contributions with {@link #applyTo(ResourceLocation, InnateTechnique.Builder)}
 * immediately before building. Applying the first definition closes registration; the resulting technique then owns
 * immutable ability and unlock-level lists.</p>
 */
public final class TechniqueAbilityContributions {
    private static final Definition DEFINITIONS = new Definition();

    private TechniqueAbilityContributions() {}

    /** Appends an ability without a special at {@code unlockLevel}. */
    public static void register(ResourceLocation techniqueId, Ability ability, int unlockLevel) {
        DEFINITIONS.register(techniqueId, null, ability, unlockLevel, null);
    }

    /** Appends an ability and its special at their respective Refinement levels. */
    public static void register(ResourceLocation techniqueId, Ability ability, int unlockLevel, int specialUnlockLevel) {
        DEFINITIONS.register(techniqueId, null, ability, unlockLevel, specialUnlockLevel);
    }

    /** Inserts an ability without a special directly after {@code after}, in deterministic registration order. */
    public static void registerAfter(ResourceLocation techniqueId, ResourceLocation after, Ability ability, int unlockLevel) {
        DEFINITIONS.register(techniqueId, after, ability, unlockLevel, null);
    }

    /** Inserts an ability and its special directly after {@code after}, at their respective Refinement levels. */
    public static void registerAfter(ResourceLocation techniqueId, ResourceLocation after, Ability ability,
            int unlockLevel, int specialUnlockLevel) {
        DEFINITIONS.register(techniqueId, after, ability, unlockLevel, specialUnlockLevel);
    }

    /**
     * Adds all contributions for {@code techniqueId} to a definition under construction. Addon techniques can use this
     * too, so other addons can contribute to them. Do not call it on an already built technique.
     */
    public static InnateTechnique.Builder applyTo(ResourceLocation techniqueId, InnateTechnique.Builder builder) {
        return DEFINITIONS.applyTo(techniqueId, builder);
    }

    /** Isolated registration state, also used by the API tests. */
    static final class Definition {
        private record Contribution(@Nullable ResourceLocation after, Ability ability, int unlockLevel,
                @Nullable Integer specialUnlockLevel) {}
        private record Placement(Contribution contribution, @Nullable ResourceLocation previous) {}

        private final Map<ResourceLocation, List<Contribution>> byTechnique = new LinkedHashMap<>();
        private final Map<ResourceLocation, ResourceLocation> owners = new HashMap<>();
        private final Set<ResourceLocation> consumed = new HashSet<>();
        private boolean sealed;

        void register(ResourceLocation techniqueId, Ability ability, int unlockLevel) {
            register(techniqueId, null, ability, unlockLevel, null);
        }

        void register(ResourceLocation techniqueId, Ability ability, int unlockLevel, int specialUnlockLevel) {
            register(techniqueId, null, ability, unlockLevel, specialUnlockLevel);
        }

        void registerAfter(ResourceLocation techniqueId, ResourceLocation after, Ability ability, int unlockLevel) {
            register(techniqueId, after, ability, unlockLevel, null);
        }

        void registerAfter(ResourceLocation techniqueId, ResourceLocation after, Ability ability, int unlockLevel,
                int specialUnlockLevel) {
            register(techniqueId, after, ability, unlockLevel, specialUnlockLevel);
        }

        synchronized void register(ResourceLocation techniqueId, @Nullable ResourceLocation after, Ability ability,
                int unlockLevel, @Nullable Integer specialUnlockLevel) {
            if (sealed) throw new IllegalStateException("Technique ability contributions are closed: registry definitions are being built");
            if (techniqueId == null) throw new NullPointerException("techniqueId");
            if (ability == null) throw new NullPointerException("ability");
            if (unlockLevel < 0 || specialUnlockLevel != null && specialUnlockLevel < 0) {
                throw new IllegalArgumentException("Unlock levels cannot be negative");
            }
            Contribution contribution = new Contribution(after, ability, unlockLevel, specialUnlockLevel);
            Ability special = validate(contribution);
            ResourceLocation abilityId = idOf(ability);
            if (abilityId.equals(after) || special != null && idOf(special).equals(after)) {
                throw new IllegalArgumentException("An ability cannot be inserted after itself: " + abilityId);
            }
            if (owners.containsKey(abilityId)) throw duplicate(abilityId);
            if (special != null && owners.containsKey(idOf(special))) throw duplicate(idOf(special));
            owners.put(abilityId, techniqueId);
            if (special != null) owners.put(idOf(special), techniqueId);
            byTechnique.computeIfAbsent(techniqueId, ignored -> new ArrayList<>()).add(contribution);
        }

        synchronized InnateTechnique.Builder applyTo(ResourceLocation techniqueId, InnateTechnique.Builder builder) {
            if (techniqueId == null) throw new NullPointerException("techniqueId");
            if (builder == null) throw new NullPointerException("builder");
            if (consumed.contains(techniqueId)) {
                throw new IllegalStateException("Technique definition has already consumed its contributions: " + techniqueId);
            }
            sealed = true;
            List<Ability> ordinary = builder.abilities();
            Set<ResourceLocation> ids = new HashSet<>();
            for (Ability ability : ordinary) checkNew(ability, ids);
            for (Ability ability : ordinary) {
                Optional<Ability> special = ability.special();
                if (special.isPresent()) checkNew(special.get(), ids);
            }
            if (builder.ultimate().isPresent()) {
                Ability ultimate = builder.ultimate().get();
                checkNew(ultimate, ids);
                ultimate.special().ifPresent(special -> checkNew(special, ids));
            }

            List<Contribution> contributions = byTechnique.getOrDefault(techniqueId, List.of());
            for (Contribution contribution : contributions) {
                Ability special = validate(contribution);
                checkNew(contribution.ability(), ids);
                if (special != null) checkNew(special, ids);
            }

            Set<ResourceLocation> ordinaryIds = new HashSet<>();
            for (Ability ability : ordinary) ordinaryIds.add(ability.id());
            Map<ResourceLocation, ResourceLocation> lastAfter = new HashMap<>();
            List<Placement> placements = new ArrayList<>();
            for (Contribution contribution : contributions) {
                ResourceLocation previous = null;
                if (contribution.after() != null) {
                    previous = lastAfter.getOrDefault(contribution.after(), contribution.after());
                    if (!ordinaryIds.contains(previous)) {
                        throw new IllegalArgumentException("Unknown ordinary ability " + contribution.after()
                                + " to insert " + contribution.ability().id() + " after in " + techniqueId);
                    }
                }
                ordinaryIds.add(contribution.ability().id());
                if (contribution.after() != null) lastAfter.put(contribution.after(), contribution.ability().id());
                placements.add(new Placement(contribution, previous));
            }

            // Nothing above changes the builder: an invalid contribution cannot leave a half-applied definition.
            if (!contributions.isEmpty()) builder.preserveDefaultUnlockLevels();
            for (Placement placement : placements) {
                Contribution contribution = placement.contribution();
                Ability ability = contribution.ability();
                if (placement.previous() == null) builder.ability(ability);
                else builder.abilityAfter(placement.previous(), ability);
                builder.unlockLevel(ability, contribution.unlockLevel());
                if (contribution.specialUnlockLevel() != null) {
                    builder.unlockLevel(ability.special().orElseThrow(), contribution.specialUnlockLevel());
                }
            }
            consumed.add(techniqueId);
            return builder;
        }

        @Nullable
        private static Ability validate(Contribution contribution) {
            Ability ability = contribution.ability();
            ResourceLocation abilityId = idOf(ability);
            if (ability.isSpecial()) throw new IllegalArgumentException("A loadout contribution cannot itself be a special: " + abilityId);
            Ability special = ability.special().orElse(null);
            if (special == null) {
                if (contribution.specialUnlockLevel() != null) {
                    throw new IllegalArgumentException("Special unlock level supplied for an ability without a special: " + abilityId);
                }
                return null;
            }
            if (contribution.specialUnlockLevel() == null) {
                throw new IllegalArgumentException("Missing special unlock level for " + idOf(special));
            }
            ResourceLocation specialId = idOf(special);
            if (specialId.equals(abilityId)) throw duplicate(specialId);
            if (!special.isSpecial() || special.base().isEmpty() || !special.base().get().id().equals(abilityId)) {
                throw new IllegalArgumentException(specialId + " is not a special of " + abilityId);
            }
            if (special.special().isPresent()) throw new IllegalArgumentException("A special cannot have another special: " + specialId);
            return special;
        }

        private static ResourceLocation idOf(Ability ability) {
            if (ability.id() == null) throw new NullPointerException("ability.id()");
            return ability.id();
        }

        private static void checkNew(Ability ability, Set<ResourceLocation> ids) {
            ResourceLocation id = idOf(ability);
            if (!ids.add(id)) throw duplicate(id);
        }

        private static IllegalArgumentException duplicate(ResourceLocation id) {
            return new IllegalArgumentException("Duplicate technique ability " + id);
        }
    }
}
