package com.example.stoneworks.client;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.network.ActivateRelayPayload;
import com.example.stoneworks.registry.ModMenus;
import com.example.stoneworks.world.menu.SurveyRelayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SurveyRelayScreen extends AbstractContainerScreen<SurveyRelayMenu> {
    public SurveyRelayScreen(SurveyRelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.stoneworks.activate"), button ->
                PacketDistributor.sendToServer(new ActivateRelayPayload(menu.blockPos())))
                .bounds(leftPos + 40, topPos + 68, 96, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202830);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.relay_charge", menu.charge()),
                8, 28, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.research", menu.researchPoints()),
                8, 45, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}