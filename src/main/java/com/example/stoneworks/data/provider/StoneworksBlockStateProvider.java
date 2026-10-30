package com.example.stoneworks.data.provider;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public final class StoneworksBlockStateProvider extends BlockStateProvider {
    public StoneworksBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Stoneworks.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleCube(ModBlocks.LUMINOUS_BLOCK.get(), "luminous_block", "block/sea_lantern");
        simpleCube(ModBlocks.SAMPLE_CRATE.get(), "sample_crate", "block/barrel_side");
        simpleCube(ModBlocks.SURVEY_RELAY.get(), "survey_relay", "block/sea_lantern");
        simpleCube(ModBlocks.COPPER_ORE.get(), "copper_ore", "block/copper_ore");
        simpleCube(ModBlocks.DEEPSLATE_COPPER_ORE.get(), "deepslate_copper_ore", "block/deepslate_copper_ore");
    }

    private void simpleCube(net.minecraft.world.level.block.Block block, String blockName, String texturePath) {
        ResourceLocation texture = mcLoc(texturePath);
        simpleBlockWithItem(block, models().cubeAll(blockName, texture));
    }
}