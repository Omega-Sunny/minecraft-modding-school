package com.example.stoneworks.world.menu;

import com.example.stoneworks.registry.ModAttachments;
import com.example.stoneworks.registry.ModBlocks;
import com.example.stoneworks.registry.ModMenus;
import com.example.stoneworks.data.ResearchProgress;
import com.example.stoneworks.world.block.entity.SurveyRelayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Objects;

public final class SurveyRelayMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final SurveyRelayBlockEntity relay;
    private final Player player;
    private final ContainerData data;
    private int clientCharge;

    public SurveyRelayMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolveRelay(inventory, buffer.readBlockPos()));
    }

    public SurveyRelayMenu(int id, Inventory inventory, SurveyRelayBlockEntity relay) {
        super(ModMenus.SURVEY_RELAY.get(), id);
        this.pos = relay.getBlockPos();
        this.relay = relay;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(Objects.requireNonNull(relay.getLevel()), pos);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index != 0) return 0;
                return player.level().isClientSide() ? clientCharge : relay.charge();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) clientCharge = Mth.clamp(value, 0, 100);
            }

            @Override
            public int getCount() { return 1; }
        };
        addDataSlots(data);
    }

    private static SurveyRelayBlockEntity resolveRelay(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof SurveyRelayBlockEntity relay) return relay;
        throw new IllegalStateException("No survey relay at menu position " + pos);
    }

    public BlockPos blockPos() { return pos; }
    public boolean isFor(SurveyRelayBlockEntity candidate) { return relay == candidate; }
    public int charge() { return data.get(0); }
    public int researchPoints() {
        return Mth.clamp(player.getData(ModAttachments.RESEARCH).points(), 0, ResearchProgress.MAX_POINTS);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SURVEY_RELAY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}