package com.cnethuka.kegare.data;

import com.cnethuka.kegare.Kegare;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** No block tags yet; the item tags provider needs one to copy from. */
public final class KegareBlockTagsProvider extends BlockTagsProvider {
    public KegareBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        super(output, lookup, Kegare.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {}
}
