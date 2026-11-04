package com.cnethuka.kegare.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** {@code ./gradlew runData} writes everything into {@code src/generated/resources}. */
public final class KegareDataGen {
    private KegareDataGen() {}

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        // Assets
        generator.addProvider(event.includeClient(), new KegareLanguageProvider(output));
        generator.addProvider(event.includeClient(), new KegareItemModelProvider(output, files));

        // Data
        BlockTagsProvider blockTags = generator.addProvider(event.includeServer(), new KegareBlockTagsProvider(output, lookup, files));
        generator.addProvider(event.includeServer(), new KegareItemTagsProvider(output, lookup, blockTags.contentsGetter(), files));
        generator.addProvider(event.includeServer(), new KegareRecipeProvider(output, lookup));
        generator.addProvider(event.includeServer(), new AdvancementProvider(output, lookup, files, List.of(new KegareAdvancementGenerator())));
    }
}
