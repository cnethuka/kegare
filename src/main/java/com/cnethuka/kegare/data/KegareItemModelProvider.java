package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class KegareItemModelProvider extends ItemModelProvider {
    public KegareItemModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, Kegare.MODID, files);
    }

    @Override
    protected void registerModels() {
        basicItem(KegareItems.PURIFICATION_TALISMAN.get());
        basicItem(KegareItems.BLINDFOLD.get());
        basicItem(KegareItems.SUNGLASSES.get());
        // Rime's projectiles in flight
        basicItem(com.cnethuka.kegare.innate_technique.rime.RimeRegistry.FROST_SHARD_ITEM.get());
        basicItem(com.cnethuka.kegare.innate_technique.rime.RimeRegistry.GLACIAL_LANCE_ITEM.get());
        basicItem(com.cnethuka.kegare.innate_technique.swap.SwapRegistry.ANCHOR_ITEM.get());
    }
}
