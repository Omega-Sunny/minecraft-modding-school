package com.example.stoneworks;

import com.example.stoneworks.data.ModDataGenerators;
import com.example.stoneworks.registry.ModBlocks;
import com.example.stoneworks.registry.ModBlockEntities;
import com.example.stoneworks.registry.ModAttachments;
import com.example.stoneworks.registry.ModCreativeTabs;
import com.example.stoneworks.registry.ModDataComponents;
import com.example.stoneworks.registry.ModItems;
import com.example.stoneworks.registry.ModMenus;
import com.example.stoneworks.network.ModPayloads;
import com.example.stoneworks.registry.ModEntities;
import com.example.stoneworks.performance.ServerWorkerPool;
import com.example.stoneworks.world.block.entity.StoneworksGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@Mod(Stoneworks.MOD_ID)
public final class Stoneworks {
    public static final String MOD_ID = "stoneworks";

    public Stoneworks(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModAttachments.TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.TYPES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        ModDataComponents.COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(ModDataGenerators::gatherData);
        modBus.addListener(ModPayloads::register);
        modBus.addListener(StoneworksGameTests::register);
        NeoForge.EVENT_BUS.addListener(ServerWorkerPool::onServerStarting);
        NeoForge.EVENT_BUS.addListener(ServerWorkerPool::onServerStopping);
    }
}
