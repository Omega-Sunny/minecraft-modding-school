package com.example.stoneworks;

import com.example.stoneworks.client.SampleCrateScreen;
import com.example.stoneworks.client.SurveyDroneModel;
import com.example.stoneworks.client.SurveyDroneRenderer;
import com.example.stoneworks.client.SurveyRelayScreen;
import com.example.stoneworks.registry.ModEntities;
import com.example.stoneworks.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = Stoneworks.MOD_ID, dist = Dist.CLIENT)
public final class StoneworksClient {
    public StoneworksClient(IEventBus modBus) {
        modBus.addListener(this::registerScreens);
        modBus.addListener(this::registerEntityLayers);
        modBus.addListener(this::registerEntityRenderers);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SAMPLE_CRATE.get(), SampleCrateScreen::new);
        event.register(ModMenus.SURVEY_RELAY.get(), SurveyRelayScreen::new);
    }

    private void registerEntityLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SurveyDroneModel.LAYER, SurveyDroneModel::createLayer);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SURVEY_DRONE.get(), SurveyDroneRenderer::new);
    }
}