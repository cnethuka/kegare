package com.cnethuka.kegare.innatetechnique;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * An innate technique (registry {@code kegare:innate_technique}): a rarity, an ordered list of abilities
 * (picked with the scroll wheel, number keys or radial menu; each may have a special on R) and an optional ultimate (the ultimate key; future dominions plug in here).
 * Register new ones with a {@code DeferredRegister} on {@link KegareRegistries#INNATE_TECHNIQUE_KEY}.
 */
public class InnateTechnique {
    private final InnateTechniqueRarity rarity;
    private final List<Ability> abilities;
    @Nullable
    private final Ability ultimate;
    private final boolean testContent;
    private final ItemLike tabIcon;
    private final boolean ultimateLocked;

    protected InnateTechnique(Builder builder) {
        if (builder.abilities.isEmpty()) throw new IllegalArgumentException("An innate technique needs at least one ability");
        this.rarity = builder.rarity;
        this.abilities = List.copyOf(builder.abilities);
        this.ultimate = builder.ultimate;
        this.testContent = builder.testContent;
        this.tabIcon = builder.tabIcon;
        this.ultimateLocked = builder.ultimate == null && builder.ultimateLocked;
    }

    public static Builder builder(InnateTechniqueRarity rarity) {
        return new Builder(rarity);
    }

    public final InnateTechniqueRarity rarity() {
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

    /** No ultimate yet, but one is planned (its Dominion comes later): the ultimate slot shows as locked. */
    public final boolean ultimateLocked() {
        return ultimateLocked;
    }

    /**
     * Hook: how the player's surroundings change this innate technique's Malice costs. Abilities apply the multiplier
     * in their own {@code cost}/{@code upkeepPerSecond}; this also feeds the HUD icon and tooltips. Default: none.
     */
    public CostEnvironment environment(ServerPlayer player) {
        return CostEnvironment.NEUTRAL;
    }

    /** Finds an ability (slots, ultimate or any of their specials) by ID. */
    public final Optional<Ability> findAbility(ResourceLocation abilityId) {
        for (Ability a : everyAbility()) if (a.id().equals(abilityId)) return Optional.of(a);
        return Optional.empty();
    }

    /** Every castable thing: each slot ability then its special, then the ultimate and its special. */
    public final List<Ability> everyAbility() {
        List<Ability> out = new ArrayList<>();
        for (Ability a : abilities) {
            out.add(a);
            a.special().ifPresent(out::add);
        }
        if (ultimate != null) {
            out.add(ultimate);
            ultimate.special().ifPresent(out::add);
        }
        return out;
    }

    /** Test/placeholder content; excluded from rolls when {@code roll.includeTestInnateTechniques=false}. */
    public final boolean isTestContent() {
        return testContent;
    }

    /** Icon of this innate technique's own advancement tab. */
    public final ItemLike tabIcon() {
        return tabIcon;
    }

    public final ResourceLocation id() {
        return KegareRegistries.INNATE_TECHNIQUE.getKey(this);
    }

    public final String descriptionId() {
        return Util.makeDescriptionId("innate_technique", id());
    }

    /** Name colored by rarity. */
    public MutableComponent displayName() {
        return Component.translatable(descriptionId()).withColor(rarity.color());
    }

    /** Hook: this innate technique was just assigned to the player. */
    public void onAssigned(ServerPlayer player) {}

    /** Hook: this innate technique was just taken away (reroll, admin, rebirth). */
    public void onRemoved(ServerPlayer player) {}

    public static final class Builder {
        private final InnateTechniqueRarity rarity;
        private final List<Ability> abilities = new ArrayList<>();
        @Nullable
        private Ability ultimate;
        private boolean testContent;
        private ItemLike tabIcon = Items.ENCHANTED_BOOK;
        private boolean ultimateLocked;

        private Builder(InnateTechniqueRarity rarity) {
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

        /** Icon of this innate technique's own advancement tab (see KegareAdvancements#innateTechniqueRoot). */
        public Builder tabIcon(ItemLike icon) {
            this.tabIcon = icon;
            return this;
        }

        /** No ultimate yet; the ultimate slot shows as locked until one is added. */
        public Builder lockedUltimate() {
            this.ultimateLocked = true;
            return this;
        }

        public Builder testContent() {
            this.testContent = true;
            return this;
        }

        public InnateTechnique build() {
            return new InnateTechnique(this);
        }
    }
}
