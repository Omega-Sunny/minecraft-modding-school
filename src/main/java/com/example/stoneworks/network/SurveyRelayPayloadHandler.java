package com.example.stoneworks.network;

import com.example.stoneworks.world.block.entity.SurveyRelayBlockEntity;
import com.example.stoneworks.world.menu.SurveyRelayMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SurveyRelayPayloadHandler {
    private SurveyRelayPayloadHandler() {}

    public static void activate(ActivateRelayPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = payload.pos();
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) return;
        if (!(level.getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay)) return;
        if (!(player.containerMenu instanceof SurveyRelayMenu menu)
                || !menu.blockPos().equals(pos)
                || !menu.isFor(relay)
                || !menu.stillValid(player)) return;

        relay.tryActivate(player);
    }
}