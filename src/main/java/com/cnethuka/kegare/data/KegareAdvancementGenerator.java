package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
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
 *   <li>Lensing Eyes tab (root {@code clear_sight}, secret identity: hidden and silent)</li>
 *   <li>one tab per non-test innate technique (root {@code innate_technique/<id>}, unlocked on the first roll)</li>
 * </ul>
 * Secret-identity rule: any ID in {@link KegareAdvancements#SECRET_IDENTITY} is forced hidden and never announced
 * to chat, and generation fails if one slips through.
 */
public final class KegareAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {
    private static final ResourceLocation BASE_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/obsidian.png");
    private static final ResourceLocation LENSING_EYES_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/amethyst_block.png");
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

        // ---- Lensing Eyes tab: secret identity, so silent; children hidden, root visible (documented exception) ----
        AdvancementHolder clearSight = root(saver, KegareAdvancements.CLEAR_SIGHT, "clear_sight", "granted", Items.SPYGLASS,
                LENSING_EYES_BACKGROUND, AdvancementType.CHALLENGE, false); // visible root: SECRET_VISIBLE_ROOTS
        child(saver, clearSight, KegareAdvancements.THE_CYCLE_CONTINUES, "the_cycle_continues", Items.CLOCK, AdvancementType.CHALLENGE, true);
        child(saver, clearSight, KegareAdvancements.EYES_CLOSED, "eyes_closed", Items.INK_SAC, AdvancementType.TASK, true);
        child(saver, clearSight, KegareAdvancements.BLINDED, "blinded", Items.FERMENTED_SPIDER_EYE, AdvancementType.GOAL, true);

        // Shrine (vessel_of_the_king, unworthy_heir, unsealed): reserved in SECRET_IDENTITY, added with the Shrine.

        // ---- One tab per innate technique: root unlocks on the first roll. Test content gets no tab. ----
        // Silent in chat: announcing it would tell every opponent which innate technique a player rolled.
        for (InnateTechnique innateTechnique : KegareRegistries.INNATE_TECHNIQUE) {
            if (innateTechnique.isTestContent() || !innateTechnique.id().getNamespace().equals(Kegare.MODID)) continue;
            Advancement.Builder.advancement()
                    .display(innateTechnique.tabIcon(), innateTechnique.displayName(),
                            Component.translatable("advancements.kegare.innate_technique_root.description"),
                            INNATE_TECHNIQUE_BACKGROUND, AdvancementType.TASK, true, false, false)
                    .addCriterion("rolled", impossible())
                    .save(saver, KegareAdvancements.innateTechniqueRoot(innateTechnique.id()).toString());
        }
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

    private static boolean announces(ResourceLocation id) {
        return !KegareAdvancements.SECRET_IDENTITY.contains(id) && !KegareAdvancements.SILENT.contains(id);
    }

    /**
     * Fails datagen if a secret-identity or SILENT advancement would be announced, or if a secret one is visible
     * without being an explicitly listed tab root (KegareAdvancements.SECRET_VISIBLE_ROOTS, no parent).
     */
    private static void enforceSecretIdentityRule(AdvancementHolder holder) {
        ResourceLocation id = holder.id();
        boolean secret = KegareAdvancements.SECRET_IDENTITY.contains(id);
        if (!secret && !KegareAdvancements.SILENT.contains(id)) return;
        var display = holder.value().display().orElseThrow(() -> new IllegalStateException(id + " needs a display"));
        if (display.shouldAnnounceChat()) {
            throw new IllegalStateException("Advancement " + id + " must never be announced to chat"
                    + " (KegareAdvancements.SECRET_IDENTITY / SILENT)");
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
