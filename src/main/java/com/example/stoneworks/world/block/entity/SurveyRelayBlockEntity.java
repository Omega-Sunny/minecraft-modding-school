package com.example.stoneworks.world.block.entity;

import com.example.stoneworks.data.ResearchProgress;
import com.example.stoneworks.network.ResearchProgressPayload;
import com.example.stoneworks.registry.ModAttachments;
import com.example.stoneworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SurveyRelayBlockEntity extends BlockEntity {
    private int charge = 100;
    private int lastSurveyScore;

    public SurveyRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SURVEY_RELAY.get(), pos, state);
    }

    public int charge() {
        return charge;
    }

    public int lastSurveyScore() {
        return lastSurveyScore;
    }

    public void applyCalculatedResult(int score) {
        lastSurveyScore = Mth.clamp(score, 0, 100);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean tryActivate(ServerPlayer player) {
        ResearchProgress current = player.getData(ModAttachments.RESEARCH);
        if (charge < 10 || current.points() >= ResearchProgress.MAX_POINTS) {
            return false;
        }

        charge -= 10;
        ResearchProgress updated = current.addPoint();
        player.setData(ModAttachments.RESEARCH, updated);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        PacketDistributor.sendToPlayer(player, new ResearchProgressPayload(updated.points()));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
        tag.putInt("last_survey_score", lastSurveyScore);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Mth.clamp(tag.getInt("charge"), 0, 100);
        lastSurveyScore = Mth.clamp(tag.getInt("last_survey_score"), 0, 100);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}