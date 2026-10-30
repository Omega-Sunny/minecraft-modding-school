package com.example.stoneworks.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import com.example.stoneworks.registry.ModItems;
import com.example.stoneworks.data.provider.StoneworksBlockStateProvider;
import com.example.stoneworks.data.provider.StoneworksItemModelProvider;
import com.example.stoneworks.data.provider.StoneworksLanguageProvider;
import com.example.stoneworks.data.provider.StoneworksLootTableProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModDataGenerators {
    private ModDataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        var existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new StoneworksBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new StoneworksItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new StoneworksLanguageProvider(output));
        generator.addProvider(event.includeServer(), new StoneworksLootTableProvider(output, event.getLookupProvider()));
        generator.addProvider(
                event.includeServer(),
                new RecipeProvider(output, event.getLookupProvider()) {
                    @Override
                    protected void buildRecipes(RecipeOutput recipeOutput) {
                        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SURVEY_TOKEN.get())
                                .pattern(" C ")
                                .pattern("CIC")
                                .pattern(" C ")
                                .define('C', Items.COPPER_INGOT)
                                .define('I', Items.IRON_INGOT)
                                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                                .save(recipeOutput);
                    }
                }
        );
    }
}
