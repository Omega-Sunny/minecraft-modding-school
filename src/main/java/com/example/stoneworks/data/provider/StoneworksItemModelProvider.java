package com.example.stoneworks.data.provider;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class StoneworksItemModelProvider extends ItemModelProvider {
    public StoneworksItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Stoneworks.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        flatItem("raw_copper", "item/raw_copper");
        flatItem("survey_token", "item/copper_ingot");
        handheldItem("copper_pickaxe", "item/iron_pickaxe");
        flatItem("copper_helmet", "item/iron_helmet");
    }

    private void flatItem(String itemName, String texturePath) {
        withExistingParent(itemName, mcLoc("item/generated"))
                .texture("layer0", ResourceLocation.withDefaultNamespace(texturePath));
    }

    private void handheldItem(String itemName, String texturePath) {
        withExistingParent(itemName, mcLoc("item/handheld"))
                .texture("layer0", ResourceLocation.withDefaultNamespace(texturePath));
    }
}