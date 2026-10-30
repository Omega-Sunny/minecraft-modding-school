package com.example.stoneworks.network;

import com.example.stoneworks.Stoneworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ResearchProgressPayload(int points) implements CustomPacketPayload {
    public static final Type<ResearchProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "research_progress")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchProgressPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ResearchProgressPayload::points,
                    ResearchProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}