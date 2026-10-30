package com.example.stoneworks.network;

import com.example.stoneworks.Stoneworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActivateRelayPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ActivateRelayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "activate_relay")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateRelayPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ActivateRelayPayload::pos, ActivateRelayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}