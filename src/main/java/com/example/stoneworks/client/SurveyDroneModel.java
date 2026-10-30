package com.example.stoneworks.client;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.world.entity.SurveyDrone;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

public final class SurveyDroneModel extends HierarchicalModel<SurveyDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "survey_drone"), "main");
    private final ModelPart root;

    public SurveyDroneModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(SurveyDrone entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {}

    @Override
    public ModelPart root() {
        return root;
    }
}