package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.world.block.entity.SampleCrateBlockEntity;
import com.example.stoneworks.world.block.entity.SurveyRelayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Stoneworks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SampleCrateBlockEntity>> SAMPLE_CRATE =
            TYPES.register("sample_crate", () -> BlockEntityType.Builder
                    .of(SampleCrateBlockEntity::new, ModBlocks.SAMPLE_CRATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyRelayBlockEntity>> SURVEY_RELAY =
            TYPES.register("survey_relay", () -> BlockEntityType.Builder
                    .of(SurveyRelayBlockEntity::new, ModBlocks.SURVEY_RELAY.get())
                    .build(null));

    private ModBlockEntities() {}
}