package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.world.menu.SampleCrateMenu;
import com.example.stoneworks.world.menu.SurveyRelayMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Stoneworks.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<SampleCrateMenu>> SAMPLE_CRATE =
            MENUS.register("sample_crate", () -> IMenuTypeExtension.create(SampleCrateMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SurveyRelayMenu>> SURVEY_RELAY =
            MENUS.register("survey_relay", () -> IMenuTypeExtension.create(SurveyRelayMenu::new));

    private ModMenus() {}
}