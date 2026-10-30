package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.world.entity.SurveyDrone;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Stoneworks.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SurveyDrone>> SURVEY_DRONE = TYPES.register(
            "survey_drone",
            () -> EntityType.Builder.of(SurveyDrone::new, MobCategory.CREATURE)
                    .sized(0.7F, 0.9F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("survey_drone")
    );

    private ModEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SURVEY_DRONE.get(), SurveyDrone.createAttributes().build());
    }
}