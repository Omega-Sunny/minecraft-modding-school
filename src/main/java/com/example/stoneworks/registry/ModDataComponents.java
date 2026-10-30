package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Stoneworks.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SAMPLE_QUALITY =
            COMPONENTS.registerComponentType(
                    "sample_quality",
                    builder -> builder
                            .persistent(Codec.intRange(0, 100))
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    private ModDataComponents() {}
}
