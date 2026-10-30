package com.example.stoneworks.network;

import com.example.stoneworks.data.ResearchProgress;
import com.example.stoneworks.registry.ModAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ResearchProgressPayloadHandler {
    private ResearchProgressPayloadHandler() {}

    public static void handle(ResearchProgressPayload payload, IPayloadContext context) {
        Player player = context.player();
        int points = Mth.clamp(payload.points(), 0, ResearchProgress.MAX_POINTS);
        player.setData(ModAttachments.RESEARCH, new ResearchProgress(points));
    }
}