package com.example.stoneworks.client;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.registry.ModMenus;
import com.example.stoneworks.world.menu.SampleCrateMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SampleCrateScreen extends AbstractContainerScreen<SampleCrateMenu> {
    public SampleCrateScreen(SampleCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.stoneworks.add_sample"), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 40, topPos + 55, 96, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202830);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.samples", menu.samples()),
                8, 30, 0xFFFFFF, false);
    }
}