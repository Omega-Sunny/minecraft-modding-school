package com.example.stoneworks.world.block.entity;

import com.example.stoneworks.registry.ModBlockEntities;
import com.example.stoneworks.world.menu.SampleCrateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SampleCrateBlockEntity extends BlockEntity implements MenuProvider {
    private int samples;

    public SampleCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAMPLE_CRATE.get(), pos, state);
    }

    public int samples() {
        return samples;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.stoneworks.sample_crate");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SampleCrateMenu(id, inventory, this);
    }

    public boolean addSample() {
        if (samples >= 64) {
            return false;
        }

        samples++;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("samples", samples);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        samples = Mth.clamp(tag.getInt("samples"), 0, 64);
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