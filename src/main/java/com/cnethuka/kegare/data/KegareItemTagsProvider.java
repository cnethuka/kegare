package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.registry.KegareTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class KegareItemTagsProvider extends ItemTagsProvider {
    public KegareItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                  CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper files) {
        super(output, lookup, blockTags, Kegare.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(KegareTags.TALISMANS).add(KegareItems.PURIFICATION_TALISMAN.get());
        // Curios' "eyes" slot takes what this tag holds (the slot itself: data/kegare/curios).
        tag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("curios", "eyes")))
                .add(KegareItems.BLINDFOLD.get(), KegareItems.TINTED_GLASSES.get());
    }
}
