package com.example.stoneworks.world.menu;

import com.example.stoneworks.registry.ModBlocks;
import com.example.stoneworks.registry.ModMenus;
import com.example.stoneworks.world.block.entity.SampleCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Objects;

public final class SampleCrateMenu extends AbstractContainerMenu {
    private final SampleCrateBlockEntity crate;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private int clientSamples;

    public SampleCrateMenu(int id, Inventory inventory, FriendlyByteBuf openingData) {
        this(id, inventory, resolveCrate(inventory, openingData.readBlockPos()));
    }

    public SampleCrateMenu(int id, Inventory inventory, SampleCrateBlockEntity crate) {
        super(ModMenus.SAMPLE_CRATE.get(), id);
        this.crate = crate;
        this.access = ContainerLevelAccess.create(Objects.requireNonNull(crate.getLevel()), crate.getBlockPos());
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index != 0) return 0;
                return inventory.player.level().isClientSide() ? clientSamples : crate.samples();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) clientSamples = Math.max(0, Math.min(64, value));
            }

            @Override
            public int getCount() { return 1; }
        };
        addDataSlots(data);
    }

    private static SampleCrateBlockEntity resolveCrate(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof SampleCrateBlockEntity crate) return crate;
        throw new IllegalStateException("No sample crate at menu position " + pos);
    }

    public int samples() { return data.get(0); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SAMPLE_CRATE.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != 0 || player.level().isClientSide() || player.containerMenu != this || !stillValid(player)
                || player.level().getBlockEntity(crate.getBlockPos()) != crate) {
            return false;
        }
        return crate.addSample();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}