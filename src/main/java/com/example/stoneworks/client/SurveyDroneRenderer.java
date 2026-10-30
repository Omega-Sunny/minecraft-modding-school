package com.example.stoneworks.client;

import com.example.stoneworks.world.entity.SurveyDrone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public final class SurveyDroneRenderer extends MobRenderer<SurveyDrone, SurveyDroneModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/allay/allay.png");

    public SurveyDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new SurveyDroneModel(context.bakeLayer(SurveyDroneModel.LAYER)), 0.35F);
    }

    @Override
    public void render(SurveyDrone entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        try {
            poseStack.translate(0.0D, 0.0625D, 0.0D);
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SurveyDrone entity) {
        return TEXTURE;
    }
}