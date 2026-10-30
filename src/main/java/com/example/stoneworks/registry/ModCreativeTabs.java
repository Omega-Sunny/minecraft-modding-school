package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Stoneworks.MOD_ID);

    public static final Supplier<CreativeModeTab> STONEWORKS = TABS.register(
            "stoneworks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stoneworks"))
                    .icon(() -> new ItemStack(ModItems.SURVEY_TOKEN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RAW_COPPER.get());
                        output.accept(ModItems.SURVEY_TOKEN.get());
                        output.accept(ModItems.COPPER_PICKAXE.get());
                        output.accept(ModItems.COPPER_HELMET.get());
                        output.accept(ModItems.LUMINOUS_BLOCK_ITEM.get());
                        output.accept(ModItems.SAMPLE_CRATE_ITEM.get());
                        output.accept(ModItems.SURVEY_RELAY_ITEM.get());
                        output.accept(ModItems.COPPER_ORE_ITEM.get());
                        output.accept(ModItems.DEEPSLATE_COPPER_ORE_ITEM.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {}
}
