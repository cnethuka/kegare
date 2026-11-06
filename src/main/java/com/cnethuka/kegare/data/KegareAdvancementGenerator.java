package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.innate_technique.rime.RimeAdvancements;
import com.cnethuka.kegare.innate_technique.swap.SwapAdvancements;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetAdvancements;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * All Kegare advancements use {@code minecraft:impossible} and are granted from code. Every tab has its own root:
 * <ul>
 *   <li>Base tab (root {@code awakening})</li>
 *   <li>Six Eyes tab (root {@code clear_sight}, secret identity: hidden and silent)</li>
 *   <li>one tab per non-test innate technique (root {@code innate_technique/<id>}, unlocked on the first roll)</li>
 * </ul>
 * Secret-identity rule: any ID in {@link KegareAdvancements#SECRET_IDENTITY} is forced hidden and never announced
 * to chat, and generation fails if one slips through.
 */
public final class KegareAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {
    private static final ResourceLocation BASE_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/obsidian.png");
    private static final ResourceLocation SIX_EYES_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/amethyst_block.png");
    private static final ResourceLocation INNATE_TECHNIQUE_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output, ExistingFileHelper files) {
        Consumer<AdvancementHolder> saver = holder -> {
            enforceSecretIdentityRule(holder);
            output.accept(holder);
        };

        // ---- Base tab ----
        // Criterion names are part of saved progress: never rename them (awakening keeps its original "awakened").
        AdvancementHolder awakening = root(saver, KegareAdvancements.AWAKENING, "awakening", "awakened", Items.ENDER_EYE,
                BASE_BACKGROUND, AdvancementType.TASK, false);
        AdvancementHolder reawakening = child(saver, awakening, KegareAdvancements.REAWAKENING, "reawakening",
                KegareItems.PURIFICATION_TALISMAN.get(), AdvancementType.TASK, false);
        child(saver, awakening, KegareAdvancements.CHOSEN_BY_FATE, "chosen_by_fate", Items.NETHER_STAR, AdvancementType.CHALLENGE, false);
        child(saver, reawakening, KegareAdvancements.BITTER_LUCK, "bitter_luck", Items.POISONOUS_POTATO, AdvancementType.GOAL, true);
        child(saver, awakening, KegareAdvancements.SEVERED, "severed", Items.SHEARS, AdvancementType.GOAL, true);
        child(saver, awakening, KegareAdvancements.OVERFLOWING, "overflowing", Items.EXPERIENCE_BOTTLE, AdvancementType.GOAL, true);
        child(saver, awakening, KegareAdvancements.RUNNING_ON_EMPTY, "running_on_empty", Items.GLASS_BOTTLE, AdvancementType.TASK, false);
        // Reverse Malice Technique: hidden and silent (they would tell everyone this player can heal).
        AdvancementHolder greenLight = child(saver, awakening, KegareAdvancements.A_GREEN_LIGHT, "a_green_light",
                Items.GLISTERING_MELON_SLICE, AdvancementType.GOAL, true);
        child(saver, greenLight, KegareAdvancements.ITS_BEEN_A_WHILE, "its_been_a_while", Items.TOTEM_OF_UNDYING, AdvancementType.CHALLENGE, true);

        // ---- Six Eyes tab: secret identity, so silent; children hidden, root visible (documented exception) ----
        AdvancementHolder clearSight = root(saver, KegareAdvancements.CLEAR_SIGHT, "clear_sight", "granted", Items.SPYGLASS,
                SIX_EYES_BACKGROUND, AdvancementType.CHALLENGE, false); // visible root: SECRET_VISIBLE_ROOTS
        child(saver, clearSight, KegareAdvancements.THE_CYCLE_CONTINUES, "the_cycle_continues", Items.CLOCK, AdvancementType.CHALLENGE, true);
        child(saver, clearSight, KegareAdvancements.EYES_CLOSED, "eyes_closed", Items.INK_SAC, AdvancementType.TASK, true);
        child(saver, clearSight, KegareAdvancements.BLINDED, "blinded", Items.FERMENTED_SPIDER_EYE, AdvancementType.GOAL, true);

        // Shrine (vessel_of_the_king, unworthy_heir, unsealed): reserved in SECRET_IDENTITY, added with the Shrine.

        // ---- One tab per innate technique: root unlocks on the first roll. Test content gets no tab. ----
        // Silent in chat: announcing it would tell every opponent which innate technique a player rolled.
        java.util.Map<ResourceLocation, AdvancementHolder> techniqueRoots = new java.util.HashMap<>();
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            if (innateTechnique.isTestContent() || !innateTechnique.id().getNamespace().equals(Kegare.MODID)) continue;
            AdvancementHolder techniqueRoot = Advancement.Builder.advancement()
                    .display(innateTechnique.tabIcon(), innateTechnique.displayName(),
                            Component.translatable("advancements.kegare.innate_technique_root.description"),
                            INNATE_TECHNIQUE_BACKGROUND, AdvancementType.TASK, true, false, false)
                    .addCriterion("rolled", impossible())
                    .save(saver, KegareAdvancements.innateTechniqueRoot(innateTechnique.id()).toString());
            techniqueRoots.put(innateTechnique.id(), techniqueRoot);
        }

        // ---- Rime tab: all hidden and silent (they would reveal who has Rime); PvP ones count player targets only ----
        AdvancementHolder rime = techniqueRoots.get(Kegare.id("rime"));
        if (rime == null) throw new IllegalStateException("Rime's tab root was not generated");
        techniqueChild(saver, rime, RimeAdvancements.COLD_OPEN, "rime.cold_open", Items.SNOWBALL, AdvancementType.TASK);
        techniqueChild(saver, rime, RimeAdvancements.FULL_CHARGE, "rime.full_charge", Items.TRIDENT, AdvancementType.TASK);
        techniqueChild(saver, rime, RimeAdvancements.HOME_FIELD, "rime.home_field", Items.SNOW_BLOCK, AdvancementType.TASK);
        techniqueChild(saver, rime, RimeAdvancements.SHATTER, "rime.shatter", Items.ICE, AdvancementType.GOAL);
        techniqueChild(saver, rime, RimeAdvancements.BRAIN_FREEZE, "rime.brain_freeze", Items.POWDER_SNOW_BUCKET, AdvancementType.GOAL);
        techniqueChild(saver, rime, RimeAdvancements.THIN_ICE, "rime.thin_ice", Items.PACKED_ICE, AdvancementType.GOAL);
        techniqueChild(saver, rime, RimeAdvancements.COLD_BLOODED, "rime.cold_blooded", Items.WITHER_SKELETON_SKULL, AdvancementType.GOAL);
        techniqueChild(saver, rime, RimeAdvancements.DEEP_FREEZE, "rime.deep_freeze", Items.BLUE_ICE, AdvancementType.CHALLENGE);
        techniqueChild(saver, rime, RimeAdvancements.LONG_WAY_FROM_HOME, "rime.long_way_from_home", Items.MAGMA_BLOCK, AdvancementType.CHALLENGE);
        // Specials
        techniqueChild(saver, rime, RimeAdvancements.SPLINTER, "rime.splinter", Items.PRISMARINE_SHARD, AdvancementType.TASK);
        techniqueChild(saver, rime, RimeAdvancements.SLIP_N_SLIDE, "rime.slip_n_slide", Items.SNOW, AdvancementType.TASK);
        techniqueChild(saver, rime, RimeAdvancements.SKY_HIGH, "rime.sky_high", Items.FEATHER, AdvancementType.GOAL);
        techniqueChild(saver, rime, RimeAdvancements.SHATTERED_PRESSURE, "rime.shattered_pressure", Items.LIGHT_BLUE_STAINED_GLASS, AdvancementType.CHALLENGE);

        // ---- Swap tab: all hidden and silent (they would reveal who has Swap); PvP ones count player targets only ----
        AdvancementHolder swap = techniqueRoots.get(Kegare.id("swap"));
        if (swap == null) throw new IllegalStateException("Swap's tab root was not generated");
        techniqueChild(saver, swap, SwapAdvancements.SWITCHEROO, "swap.switcheroo", Items.ENDER_PEARL, AdvancementType.TASK);
        techniqueChild(saver, swap, SwapAdvancements.NOT_ALLOWED, "swap.not_allowed", Items.BARRIER, AdvancementType.TASK);
        techniqueChild(saver, swap, SwapAdvancements.RETURN_TO_SENDER, "swap.special_delivery_swap", Items.ENDER_EYE, AdvancementType.TASK);
        techniqueChild(saver, swap, SwapAdvancements.WRONG_TARGET, "swap.wrong_target", Items.CARVED_PUMPKIN, AdvancementType.GOAL);
        techniqueChild(saver, swap, SwapAdvancements.TWO_OF_YOU, "swap.two_of_you", Items.ARMOR_STAND, AdvancementType.GOAL);
        techniqueChild(saver, swap, SwapAdvancements.FALL_GUY, "swap.fall_guy", Items.POINTED_DRIPSTONE, AdvancementType.GOAL);
        techniqueChild(saver, swap, SwapAdvancements.ROUND_TRIP, "swap.round_trip", Items.COMPASS, AdvancementType.GOAL);
        techniqueChild(saver, swap, SwapAdvancements.IDENTITY_CRISIS, "swap.identity_crisis", Items.PLAYER_HEAD, AdvancementType.CHALLENGE);
        techniqueChild(saver, swap, SwapAdvancements.NEWTONS_THIRD, "swap.newtons_third", Items.WIND_CHARGE, AdvancementType.CHALLENGE);

        // ---- Puppet Manipulation tab: all hidden and silent; mastery ones need a player on the other side ----
        AdvancementHolder puppets = techniqueRoots.get(Kegare.id("puppet_manipulation"));
        if (puppets == null) throw new IllegalStateException("Puppet Manipulation's tab root was not generated");
        techniqueChild(saver, puppets, PuppetAdvancements.PULLING_STRINGS, "puppet_manipulation.pulling_strings", Items.STRING, AdvancementType.TASK);
        techniqueChild(saver, puppets, PuppetAdvancements.OUT_OF_BODY, "puppet_manipulation.out_of_body", Items.SOUL_LANTERN, AdvancementType.TASK);
        techniqueChild(saver, puppets, PuppetAdvancements.KURU_KURU, "puppet_manipulation.kuru_kuru", Items.BREEZE_ROD, AdvancementType.TASK);
        techniqueChild(saver, puppets, PuppetAdvancements.NOBODY_HOME, "puppet_manipulation.nobody_home", Items.ARMOR_STAND, AdvancementType.TASK);
        techniqueChild(saver, puppets, PuppetAdvancements.DECOY, "puppet_manipulation.decoy", Items.HAY_BLOCK, AdvancementType.GOAL);
        techniqueChild(saver, puppets, PuppetAdvancements.WHOS_REAL, "puppet_manipulation.whos_real", Items.TINTED_GLASS, AdvancementType.GOAL);
        techniqueChild(saver, puppets, PuppetAdvancements.CURIOSITY_KILLED, "puppet_manipulation.curiosity_killed", Items.TNT, AdvancementType.GOAL);
        techniqueChild(saver, puppets, PuppetAdvancements.LITTLE_ME, "puppet_manipulation.little_me", Items.TOTEM_OF_UNDYING, AdvancementType.CHALLENGE);
        techniqueChild(saver, puppets, PuppetAdvancements.GET_OVER_HERE, "puppet_manipulation.get_over_here", Items.FISHING_ROD, AdvancementType.TASK);
        techniqueChild(saver, puppets, PuppetAdvancements.TIED_UP, "puppet_manipulation.tied_up", Items.COBWEB, AdvancementType.GOAL);
        techniqueChild(saver, puppets, PuppetAdvancements.WALKIES, "puppet_manipulation.walkies", Items.LEAD, AdvancementType.GOAL);
        techniqueChild(saver, puppets, PuppetAdvancements.STRINGS_ATTACHED, "puppet_manipulation.strings_attached", Items.TRIPWIRE_HOOK, AdvancementType.CHALLENGE);
    }

    /**
     * A tab root. Secret-identity IDs are never announced and are hidden, except roots listed in
     * {@link KegareAdvancements#SECRET_VISIBLE_ROOTS}.
     */
    private static AdvancementHolder root(Consumer<AdvancementHolder> saver, ResourceLocation id, String key, String criterion, ItemLike icon,
                                          ResourceLocation background, AdvancementType type, boolean hidden) {
        boolean secret = KegareAdvancements.SECRET_IDENTITY.contains(id);
        boolean forceHidden = secret && !KegareAdvancements.SECRET_VISIBLE_ROOTS.contains(id);
        return Advancement.Builder.advancement()
                .display(icon, title(key), description(key), background, type, true, announces(id), hidden || forceHidden)
                .addCriterion(criterion, impossible())
                .save(saver, id.toString());
    }

    /** Secret-identity IDs are always hidden and never announced to chat, whatever the caller passes. */
    private static AdvancementHolder child(Consumer<AdvancementHolder> saver, AdvancementHolder parent, ResourceLocation id, String key,
                                           ItemLike icon, AdvancementType type, boolean hidden) {
        boolean secret = KegareAdvancements.SECRET_IDENTITY.contains(id);
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, title(key), description(key), null, type, true, announces(id), hidden || secret)
                .addCriterion("granted", impossible())
                .save(saver, id.toString());
    }

    /** An innate technique tab's advancement: hidden until earned and never announced (it would reveal the technique). */
    private static AdvancementHolder techniqueChild(Consumer<AdvancementHolder> saver, AdvancementHolder parent, ResourceLocation id,
                                                    String key, ItemLike icon, AdvancementType type) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, title(key), description(key), null, type, true, false, true)
                .addCriterion("granted", impossible())
                .save(saver, id.toString());
    }

    private static boolean announces(ResourceLocation id) {
        return !KegareAdvancements.SECRET_IDENTITY.contains(id) && !KegareAdvancements.SILENT.contains(id);
    }

    /**
     * Fails datagen if a secret-identity or SILENT advancement would be announced, or if a secret one is visible
     * without being an explicitly listed tab root (KegareAdvancements.SECRET_VISIBLE_ROOTS, no parent).
     */
    private static void enforceSecretIdentityRule(AdvancementHolder holder) {
        ResourceLocation id = holder.id();
        // Innate technique tabs: nothing in them is ever announced (it would reveal who rolled what).
        if (id.getPath().startsWith("innate_technique/") && holder.value().display().map(d -> d.shouldAnnounceChat()).orElse(false)) {
            throw new IllegalStateException("Advancement " + id + " is in an innate technique tab and must never be announced to chat");
        }
        boolean secret = KegareAdvancements.SECRET_IDENTITY.contains(id);
        if (!secret && !KegareAdvancements.SILENT.contains(id)) return;
        var display = holder.value().display().orElseThrow(() -> new IllegalStateException(id + " needs a display"));
        if (display.shouldAnnounceChat()) {
            throw new IllegalStateException("Advancement " + id + " must never be announced to chat"
                    + " (KegareAdvancements.SECRET_IDENTITY / SILENT)");
        }
        if (KegareAdvancements.SILENT_HIDDEN.contains(id) && !display.isHidden()) {
            throw new IllegalStateException("Advancement " + id + " must be hidden (KegareAdvancements.SILENT_HIDDEN)");
        }
        boolean visibleRootException = KegareAdvancements.SECRET_VISIBLE_ROOTS.contains(id) && holder.value().parent().isEmpty();
        if (secret && !display.isHidden() && !visibleRootException) {
            throw new IllegalStateException("Advancement " + id + " reveals a secret identity: it must be hidden"
                    + " (only tab roots listed in KegareAdvancements.SECRET_VISIBLE_ROOTS may be visible)");
        }
    }

    private static Criterion<ImpossibleTrigger.TriggerInstance> impossible() {
        return CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance());
    }

    private static Component title(String key) {
        return Component.translatable("advancements.kegare." + key + ".title");
    }

    private static Component description(String key) {
        return Component.translatable("advancements.kegare." + key + ".description");
    }
}
