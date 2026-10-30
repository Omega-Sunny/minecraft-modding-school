package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.world.block.LuminousBlock;
import com.example.stoneworks.world.block.SampleCrateBlock;
import com.example.stoneworks.world.block.SurveyRelayBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Stoneworks.MOD_ID);

    public static final DeferredBlock<LuminousBlock> LUMINOUS_BLOCK = BLOCKS.registerBlock(
            "luminous_block",
            LuminousBlock::new,
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .destroyTime(1.5F)
                    .explosionResistance(6.0F)
                    .sound(SoundType.GLASS)
    );

                public static final DeferredBlock<SampleCrateBlock> SAMPLE_CRATE = BLOCKS.registerBlock(
                    "sample_crate",
                    SampleCrateBlock::new,
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .mapColor(MapColor.WOOD)
                        .strength(2.0F)
                );

                    public static final DeferredBlock<SurveyRelayBlock> SURVEY_RELAY = BLOCKS.registerBlock(
                        "survey_relay",
                        SurveyRelayBlock::new,
                        net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_CYAN)
                            .strength(3.0F)
                            .requiresCorrectToolForDrops()
                    );

                        public static final DeferredBlock<Block> COPPER_ORE = BLOCKS.registerSimpleBlock(
                            "copper_ore",
                            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                                .strength(3.0F, 3.0F)
                                .requiresCorrectToolForDrops()
                        );

                        public static final DeferredBlock<Block> DEEPSLATE_COPPER_ORE = BLOCKS.registerSimpleBlock(
                            "deepslate_copper_ore",
                            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                                .strength(4.5F, 3.0F)
                                .requiresCorrectToolForDrops()
                        );

    private ModBlocks() {}
}
