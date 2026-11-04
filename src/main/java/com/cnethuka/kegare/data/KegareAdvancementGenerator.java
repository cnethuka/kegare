package com.cnethuka.kegare.data;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.registry.KegareItems;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * All Kegare advancements use {@code minecraft:impossible} and are granted from code.
 * Secret-identity rule: any ID in {@link KegareAdvancements#SECRET_IDENTITY} is forced hidden and never announced
 * to chat, and generation fails if one slips through.
 */
public final class KegareAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/obsidian.png");

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output, ExistingFileHelper files) {
        Consumer<AdvancementHolder> saver = holder -> {
            enforceSecretIdentityRule(holder);
            output.accept(holder);
        };
        AdvancementHolder awakening = Advancement.Builder.advancement()
                .display(Items.ENDER_EYE, title("awakening"), description("awakening"), BACKGROUND, AdvancementType.TASK, true, true, false)
                .addCriterion("awakened", impossible())
                .save(saver, KegareAdvancements.AWAKENING.toString());

        AdvancementHolder reawakening = child(saver, awakening, KegareAdvancements.REAWAKENING, "reawakening",
                KegareItems.PURIFICATION_TALISMAN.get(), AdvancementType.TASK, false);
        child(saver, awakening, KegareAdvancements.CHOSEN_BY_FATE, "chosen_by_fate", Items.NETHER_STAR, AdvancementType.CHALLENGE, false);
        child(saver, reawakening, KegareAdvancements.BITTER_LUCK, "bitter_luck", Items.POISONOUS_POTATO, AdvancementType.GOAL, true);
        child(saver, awakening, KegareAdvancements.SEVERED, "severed", Items.SHEARS, AdvancementType.GOAL, true);

        // Lensing Eyes: secret identity (see KegareAdvancements.SECRET_IDENTITY), so hidden and silent.
        child(saver, awakening, KegareAdvancements.CLEAR_SIGHT, "clear_sight", Items.SPYGLASS, AdvancementType.CHALLENGE, true);
        child(saver, awakening, KegareAdvancements.THE_CYCLE_CONTINUES, "the_cycle_continues", Items.CLOCK, AdvancementType.CHALLENGE, true);
        child(saver, awakening, KegareAdvancements.EYES_CLOSED, "eyes_closed", Items.INK_SAC, AdvancementType.TASK, true);
        child(saver, awakening, KegareAdvancements.BLINDED, "blinded", Items.FERMENTED_SPIDER_EYE, AdvancementType.GOAL, true);
        // Shrine (vessel_of_the_king, unworthy_heir, unsealed): reserved in SECRET_IDENTITY, added with the Shrine.
    }

    /** Secret-identity IDs are always hidden and never announced to chat, whatever the caller passes. */
    private static AdvancementHolder child(Consumer<AdvancementHolder> saver, AdvancementHolder parent, ResourceLocation id, String key,
                                           ItemLike icon, AdvancementType type, boolean hidden) {
        boolean secret = KegareAdvancements.SECRET_IDENTITY.contains(id);
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, title(key), description(key), null, type, true, !secret, hidden || secret)
                .addCriterion("granted", impossible())
                .save(saver, id.toString());
    }

    private static void enforceSecretIdentityRule(AdvancementHolder holder) {
        if (!KegareAdvancements.SECRET_IDENTITY.contains(holder.id())) return;
        boolean ok = holder.value().display().map(d -> d.isHidden() && !d.shouldAnnounceChat()).orElse(false);
        if (!ok) {
            throw new IllegalStateException("Advancement " + holder.id() + " reveals a secret identity: it must be hidden"
                    + " and never announced to chat (KegareAdvancements.SECRET_IDENTITY)");
        }
    }

    private static net.minecraft.advancements.Criterion<ImpossibleTrigger.TriggerInstance> impossible() {
        return CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance());
    }

    private static Component title(String key) {
        return Component.translatable("advancements.kegare." + key + ".title");
    }

    private static Component description(String key) {
        return Component.translatable("advancements.kegare." + key + ".description");
    }
}
