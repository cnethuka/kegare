package com.cnethuka.kegare.technique;

import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A technique (registry {@code kegare:technique}): a rarity, an ordered list of abilities
 * (cycled with next/previous) and an optional ultimate (the ultimate key; future domains plug in here).
 * Register new ones with a {@code DeferredRegister} on {@link KegareRegistries#TECHNIQUE_KEY}.
 */
public class Technique {
    private final TechniqueRarity rarity;
    private final List<Ability> abilities;
    @Nullable
    private final Ability ultimate;
    private final boolean testContent;

    protected Technique(Builder builder) {
        if (builder.abilities.isEmpty()) throw new IllegalArgumentException("A technique needs at least one ability");
        this.rarity = builder.rarity;
        this.abilities = List.copyOf(builder.abilities);
        this.ultimate = builder.ultimate;
        this.testContent = builder.testContent;
    }

    public static Builder builder(TechniqueRarity rarity) {
        return new Builder(rarity);
    }

    public final TechniqueRarity rarity() {
        return rarity;
    }

    public final List<Ability> abilities() {
        return abilities;
    }

    /** Ability in a cycle slot, or null if out of range. */
    @Nullable
    public final Ability ability(int slot) {
        return slot >= 0 && slot < abilities.size() ? abilities.get(slot) : null;
    }

    public final Optional<Ability> ultimate() {
        return Optional.ofNullable(ultimate);
    }

    /** Finds an ability (cycle slots or ultimate) by ID. */
    public final Optional<Ability> findAbility(ResourceLocation abilityId) {
        for (Ability a : abilities) if (a.id().equals(abilityId)) return Optional.of(a);
        if (ultimate != null && ultimate.id().equals(abilityId)) return Optional.of(ultimate);
        return Optional.empty();
    }

    /** Test/placeholder content; excluded from rolls when {@code roll.includeTestTechniques=false}. */
    public final boolean isTestContent() {
        return testContent;
    }

    public final ResourceLocation id() {
        return KegareRegistries.TECHNIQUE.getKey(this);
    }

    public final String descriptionId() {
        return Util.makeDescriptionId("technique", id());
    }

    /** Name colored by rarity. */
    public MutableComponent displayName() {
        return Component.translatable(descriptionId()).withColor(rarity.color());
    }

    /** Hook: this technique was just assigned to the player. */
    public void onAssigned(ServerPlayer player) {}

    /** Hook: this technique was just taken away (reroll, admin, rebirth). */
    public void onRemoved(ServerPlayer player) {}

    public static final class Builder {
        private final TechniqueRarity rarity;
        private final List<Ability> abilities = new ArrayList<>();
        @Nullable
        private Ability ultimate;
        private boolean testContent;

        private Builder(TechniqueRarity rarity) {
            this.rarity = rarity;
        }

        public Builder ability(Ability ability) {
            abilities.add(ability);
            return this;
        }

        public Builder ultimate(Ability ability) {
            this.ultimate = ability;
            return this;
        }

        public Builder testContent() {
            this.testContent = true;
            return this;
        }

        public Technique build() {
            return new Technique(this);
        }
    }
}
