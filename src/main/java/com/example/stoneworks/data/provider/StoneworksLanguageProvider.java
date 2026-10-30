package com.example.stoneworks.data.provider;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.registry.ModBlocks;
import com.example.stoneworks.registry.ModItems;
import com.example.stoneworks.registry.ModEntities;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public final class StoneworksLanguageProvider extends LanguageProvider {
    public StoneworksLanguageProvider(PackOutput output) {
        super(output, Stoneworks.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addItem(ModItems.RAW_COPPER, "Raw Copper");
        addItem(ModItems.SURVEY_TOKEN, "Survey Token");
        addItem(ModItems.COPPER_PICKAXE, "Copper Pickaxe");
        addItem(ModItems.COPPER_HELMET, "Copper Helmet");

        addBlock(ModBlocks.LUMINOUS_BLOCK, "Luminous Block");
        addBlock(ModBlocks.SAMPLE_CRATE, "Sample Crate");
        addBlock(ModBlocks.SURVEY_RELAY, "Survey Relay");
        addBlock(ModBlocks.COPPER_ORE, "Copper Ore");
        addBlock(ModBlocks.DEEPSLATE_COPPER_ORE, "Deepslate Copper Ore");
        addEntityType(ModEntities.SURVEY_DRONE, "Survey Drone");

        add("itemGroup.stoneworks", "Stoneworks");
        add("menu.stoneworks.sample_crate", "Sample Crate");
        add("menu.stoneworks.survey_relay", "Survey Relay");
        add("screen.stoneworks.add_sample", "Add sample");
        add("screen.stoneworks.samples", "Samples: %s");
        add("screen.stoneworks.activate", "Activate");
        add("screen.stoneworks.relay_charge", "Charge: %s");
        add("screen.stoneworks.research", "Research: %s");
    }
}