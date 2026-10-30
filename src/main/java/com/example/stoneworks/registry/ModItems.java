package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Stoneworks.MOD_ID);

    public static final DeferredItem<Item> RAW_COPPER = ITEMS.registerSimpleItem(
            "raw_copper",
            new Item.Properties().stacksTo(32)
    );

    public static final DeferredItem<Item> SURVEY_TOKEN = ITEMS.registerSimpleItem(
            "survey_token",
            new Item.Properties().stacksTo(16)
    );

    public static final DeferredItem<PickaxeItem> COPPER_PICKAXE = ITEMS.registerItem(
            "copper_pickaxe",
            properties -> new PickaxeItem(
                    Tiers.IRON,
                    properties.attributes(PickaxeItem.createAttributes(Tiers.IRON, 1.0F, -2.8F))
            ),
            new Item.Properties()
    );

    public static final DeferredItem<ArmorItem> COPPER_HELMET = ITEMS.registerItem(
            "copper_helmet",
            properties -> new ArmorItem(
                    ArmorMaterials.IRON,
                    ArmorItem.Type.HELMET,
                    properties.durability(ArmorItem.Type.HELMET.getDurability(15))
            ),
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> LUMINOUS_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "luminous_block",
            ModBlocks.LUMINOUS_BLOCK
    );

    public static final DeferredItem<BlockItem> SAMPLE_CRATE_ITEM = ITEMS.registerSimpleBlockItem(
            "sample_crate",
            ModBlocks.SAMPLE_CRATE
    );

    public static final DeferredItem<BlockItem> SURVEY_RELAY_ITEM = ITEMS.registerSimpleBlockItem(
            "survey_relay",
            ModBlocks.SURVEY_RELAY
    );

    public static final DeferredItem<BlockItem> COPPER_ORE_ITEM = ITEMS.registerSimpleBlockItem(
            "copper_ore",
            ModBlocks.COPPER_ORE
    );

    public static final DeferredItem<BlockItem> DEEPSLATE_COPPER_ORE_ITEM = ITEMS.registerSimpleBlockItem(
            "deepslate_copper_ore",
            ModBlocks.DEEPSLATE_COPPER_ORE
    );

    private ModItems() {}
}
