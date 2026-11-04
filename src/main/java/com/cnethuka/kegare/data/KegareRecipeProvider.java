package com.cnethuka.kegare.data;

import com.cnethuka.kegare.registry.KegareItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

/** Recipes are data: server owners can override them with a datapack. */
public final class KegareRecipeProvider extends RecipeProvider {
    public KegareRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Placeholder cost: rerolls should be meaningful but reachable in a friends' PvP world.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, KegareItems.PURIFICATION_TALISMAN.get())
                .pattern("GAG")
                .pattern("APA")
                .pattern("GAG")
                .define('G', Items.GOLD_INGOT)
                .define('A', Items.AMETHYST_SHARD)
                .define('P', Items.PAPER)
                .unlockedBy("has_amethyst_shard", has(Items.AMETHYST_SHARD))
                .save(output);
    }
}
