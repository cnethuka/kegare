package com.cnethuka.kegare.api.technique;

import com.cnethuka.kegare.progression.UnlockTable;
import com.cnethuka.kegare.api.KegareApi;
import com.cnethuka.kegare.innatetechnique.change.TechniqueViews;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import com.cnethuka.kegare.api.ability.Ability;

/**
 * An innate technique (registry {@code kegare:innate_technique}): a rarity, an ordered list of abilities (picked with the number keys
 * or scroll wheel in Technique Mode; each may have a special on R) and an optional ultimate (V; Domains will plug in here).
 * Register new ones with a {@code DeferredRegister} on {@link KegareApi#INNATE_TECHNIQUES}.
 */
public class InnateTechnique {
    /** Malice purple: the bar color for innate techniques that don't set their own. */
    public static final int DEFAULT_MALICE_COLOR = 0x9D4EDD;

    private final InnateTechniqueRarity rarity;
    private final List<Ability> abilities;
    @Nullable
    private final Ability ultimate;
    private final ItemLike tabIcon;
    private final boolean ultimateLocked;
    private final int maliceColor;
    @Nullable
    private final ResourceLocation signatureAnimation;
    private final boolean defaultUnlockLevels;
    private final Map<ResourceLocation, Integer> unlockLevelOverrides;
    private final boolean rollable;
    @Nullable
    private Map<ResourceLocation, Integer> unlockLevels;

    protected InnateTechnique(Builder builder) {
        if (builder.abilities.isEmpty()) throw new IllegalArgumentException("An innate technique needs at least one ability");
        if (!builder.defaultUnlockLevels && builder.unlockLevelOverrides.isEmpty()) {
            throw new IllegalStateException("An innate technique must set its Refinement unlock levels: call defaultUnlockLevels() or unlockLevel(...)");
        }
        this.rarity = builder.rarity;
        this.abilities = List.copyOf(builder.abilities);
        this.ultimate = builder.ultimate;
        this.tabIcon = builder.tabIcon;
        this.ultimateLocked = builder.ultimate == null && builder.ultimateLocked;
        this.maliceColor = builder.maliceColor & 0xFFFFFF;
        this.signatureAnimation = builder.signatureAnimation;
        this.defaultUnlockLevels = builder.defaultUnlockLevels;
        this.unlockLevelOverrides = Map.copyOf(builder.unlockLevelOverrides);
        this.rollable = builder.rollable;
    }

    public static Builder builder(InnateTechniqueRarity rarity) {
        return new Builder(rarity);
    }

    public final InnateTechniqueRarity rarity() {
        return rarity;
    }

    /**
     * Its abilities in loadout order, as players get them: with every addon's changes (additions, replacements and tweaks,
     * {@link com.cnethuka.kegare.api.change.TechniqueChanges}) once the registry is complete.
     */
    public final List<Ability> abilities() {
        return TechniqueViews.view(this).abilities();
    }

    /** Ability in a cycle slot, or null if out of range. */
    @Nullable
    public final Ability ability(int slot) {
        List<Ability> list = abilities();
        return slot >= 0 && slot < list.size() ? list.get(slot) : null;
    }

    /** Its ultimate, as players get it (with addons' changes). */
    public final Optional<Ability> ultimate() {
        return Optional.ofNullable(TechniqueViews.view(this).ultimate());
    }

    /** Its abilities as its own definition lists them, before any addon's changes. */
    public final List<Ability> definedAbilities() {
        return abilities;
    }

    /** Its ultimate as its own definition has it, before any addon's changes. */
    public final Optional<Ability> definedUltimate() {
        return Optional.ofNullable(ultimate);
    }

    /**
     * RGB of the Malice bar (fill, ghost and glow) while a player holds this innate technique; the Technique Mode
     * glow uses it too. Client display only: the definition is registry-synced, so nothing extra is sent.
     */
    public final int maliceColor() {
        return maliceColor;
    }

    /** The player animation the roll reveal shows on the player model, if this technique has one. Client display only. */
    public final Optional<ResourceLocation> signatureAnimation() {
        return Optional.ofNullable(signatureAnimation);
    }

    /**
     * Whether rolls can land on it. True unless the definition said {@link Builder#neverRolled()}. An addon's technique can
     * still be kept out of rolls by the server ({@code roll.addonTechniques}); an admin can always give one with a command.
     */
    public final boolean rollable() {
        return rollable;
    }

    /** No ultimate yet, but one is planned (its Domain comes later): the ultimate slot shows as locked. */
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

    /** Every castable thing, as players get it: each slot ability then its special, then the ultimate and its special. */
    public final List<Ability> everyAbility() {
        return TechniqueViews.view(this).every();
    }

    /** Whether this technique says what its abilities unlock at. Always true: a definition that doesn't can't be built. */
    public final boolean hasUnlockLevels() {
        return defaultUnlockLevels || !unlockLevelOverrides.isEmpty();
    }

    /**
     * The Refinement level that unlocks this ability, special or ultimate, as the definition has it (config can override
     * it). One an addon added unlocks where the addon said; one it replaced keeps the level of the one it replaced.
     */
    public final int unlockLevel(Ability ability) {
        return TechniqueViews.view(this).unlockLevel(ability.id(), id -> definedLevels().getOrDefault(id, 0));
    }

    /** The levels its own definition gives, by ID: Kegare's default table by position, then its explicit ones. */
    private Map<ResourceLocation, Integer> definedLevels() {
        if (unlockLevels == null) {
            Map<ResourceLocation, Integer> levels = new HashMap<>();
            for (int i = 0; i < abilities.size(); i++) {
                levels.put(abilities.get(i).id(), UnlockTable.ability(i));
                int index = i;
                abilities.get(i).special().ifPresent(s -> levels.put(s.id(), UnlockTable.special(index)));
            }
            if (ultimate != null) {
                levels.put(ultimate.id(), UnlockTable.ULTIMATE);
                ultimate.special().ifPresent(s -> levels.put(s.id(), UnlockTable.ULTIMATE));
            }
            levels.putAll(unlockLevelOverrides);
            unlockLevels = levels;
        }
        return unlockLevels;
    }

    /** Icon of this innate technique's own advancement tab. */
    public final ItemLike tabIcon() {
        return tabIcon;
    }

    public final ResourceLocation id() {
        return KegareApi.innateTechniques().getKey(this);
    }

    public final String descriptionId() {
        return Util.makeDescriptionId("innate_technique", id());
    }

    /** Name colored by rarity. */
    public MutableComponent displayName() {
        return Component.translatable(descriptionId()).withColor(rarity.color());
    }

    /** Lang key of the epithet, the short line the reveal types out and the Sorcerer screen shows under the name. Datagen requires one. */
    public final String epithetId() {
        return descriptionId() + ".epithet";
    }

    public final Component epithet() {
        return Component.translatable(epithetId());
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
        private ItemLike tabIcon = Items.ENCHANTED_BOOK;
        private boolean ultimateLocked;
        private int maliceColor = DEFAULT_MALICE_COLOR;
        @Nullable
        private ResourceLocation signatureAnimation;
        private boolean defaultUnlockLevels;
        private final Map<ResourceLocation, Integer> unlockLevelOverrides = new HashMap<>();
        private boolean rollable = true;

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

        /** RGB (0xRRGGBB) of the Malice bar for holders of this innate technique. Default: Malice purple. */
        public Builder maliceColor(int rgb) {
            this.maliceColor = rgb & 0xFFFFFF;
            return this;
        }

        /** The player animation (an ID in {@code player_animations/}) the roll reveal plays on the player model. Default: none, a still model. */
        public Builder signatureAnimation(ResourceLocation animation) {
            this.signatureAnimation = animation;
            return this;
        }

        /**
         * Refinement unlocks follow Kegare's default table, by the order the abilities were added: the abilities at levels 0, 0, 2,
         * 4 and 6, their specials at 1, 3, 5, 7 and 9, the ultimate at 8 (a sixth ability onward waits at 10 until it sets its own).
         * Every technique sets this or {@link #unlockLevel}.
         */
        public Builder defaultUnlockLevels() {
            this.defaultUnlockLevels = true;
            return this;
        }

        /** One ability, special or ultimate unlocks at this Refinement level instead of where the table puts it. */
        public Builder unlockLevel(Ability ability, int level) {
            if (level < 0) throw new IllegalArgumentException("Unlock levels can't be negative: " + ability.id() + " at " + level);
            this.unlockLevelOverrides.put(ability.id(), level);
            return this;
        }

        /** Rolls never land on it: it's given another way (a quest, an item, an admin command). Default: rollable. */
        public Builder neverRolled() {
            this.rollable = false;
            return this;
        }

        public InnateTechnique build() {
            return new InnateTechnique(this);
        }
    }
}
