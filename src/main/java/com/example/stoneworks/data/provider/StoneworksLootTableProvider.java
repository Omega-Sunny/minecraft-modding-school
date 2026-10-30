package com.example.stoneworks.data.provider;

import com.example.stoneworks.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.BlockLootSubProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class StoneworksLootTableProvider extends LootTableProvider {
    public StoneworksLootTableProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(output, Set.of(), List.of(new SubProviderEntry(StoneworksBlockLootSubProvider::new,
                LootContextParamSets.BLOCK)), lookupProvider);
    }

    private static final class StoneworksBlockLootSubProvider extends BlockLootSubProvider {
        private StoneworksBlockLootSubProvider(HolderLookup.Provider lookupProvider) {
            super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookupProvider);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.LUMINOUS_BLOCK.get());
            dropSelf(ModBlocks.SAMPLE_CRATE.get());
            dropSelf(ModBlocks.SURVEY_RELAY.get());
            dropSelf(ModBlocks.COPPER_ORE.get());
            dropSelf(ModBlocks.DEEPSLATE_COPPER_ORE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.of(
                    ModBlocks.LUMINOUS_BLOCK.get(),
                    ModBlocks.SAMPLE_CRATE.get(),
                    ModBlocks.SURVEY_RELAY.get(),
                    ModBlocks.COPPER_ORE.get(),
                    ModBlocks.DEEPSLATE_COPPER_ORE.get()
            ).map(block -> (Block) block).toList();
        }
    }
}