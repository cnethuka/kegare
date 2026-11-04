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

/** All Kegare advancements use {@code minecraft:impossible} and are granted from code. */
public final class KegareAdvancementGenerator implements AdvancementProvider.AdvancementGenerator {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/obsidian.png");

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper files) {
        AdvancementHolder awakening = Advancement.Builder.advancement()
                .display(Items.ENDER_EYE, title("awakening"), description("awakening"), BACKGROUND, AdvancementType.TASK, true, true, false)
                .addCriterion("awakened", impossible())
                .save(saver, KegareAdvancements.AWAKENING.toString());

        AdvancementHolder reawakening = child(saver, awakening, KegareAdvancements.REAWAKENING, "reawakening",
                KegareItems.PURIFICATION_TALISMAN.get(), AdvancementType.TASK, false);
        child(saver, awakening, KegareAdvancements.CHOSEN_BY_FATE, "chosen_by_fate", Items.NETHER_STAR, AdvancementType.CHALLENGE, false);
        child(saver, reawakening, KegareAdvancements.BITTER_LUCK, "bitter_luck", Items.POISONOUS_POTATO, AdvancementType.GOAL, true);
        child(saver, awakening, KegareAdvancements.SEVERED, "severed", Items.SHEARS, AdvancementType.GOAL, true);

        // Lensing Eyes: hidden until earned, and NOT announced in chat. Announcing them would name the
        // bearer/heir and defeat the anonymous succession broadcast. The earner still gets the toast.
        child(saver, awakening, KegareAdvancements.CLEAR_SIGHT, "clear_sight", Items.SPYGLASS, AdvancementType.CHALLENGE, true, false);
        child(saver, awakening, KegareAdvancements.THE_CYCLE_CONTINUES, "the_cycle_continues", Items.CLOCK, AdvancementType.CHALLENGE, true, false);
        child(saver, awakening, KegareAdvancements.EYES_CLOSED, "eyes_closed", Items.INK_SAC, AdvancementType.TASK, true, false);
        child(saver, awakening, KegareAdvancements.BLINDED, "blinded", Items.FERMENTED_SPIDER_EYE, AdvancementType.GOAL, true, false);
    }

    private static AdvancementHolder child(Consumer<AdvancementHolder> saver, AdvancementHolder parent, ResourceLocation id, String key,
                                           ItemLike icon, AdvancementType type, boolean hidden) {
        return child(saver, parent, id, key, icon, type, hidden, true);
    }

    private static AdvancementHolder child(Consumer<AdvancementHolder> saver, AdvancementHolder parent, ResourceLocation id, String key,
                                           ItemLike icon, AdvancementType type, boolean hidden, boolean announceChat) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon, title(key), description(key), null, type, true, announceChat, hidden)
                .addCriterion("granted", impossible())
                .save(saver, id.toString());
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
