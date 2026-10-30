package com.example.stoneworks.world.block;

import com.example.stoneworks.world.block.entity.SurveyRelayBlockEntity;
import com.example.stoneworks.world.menu.SurveyRelayMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.example.stoneworks.data.ResearchProgress;
import com.example.stoneworks.network.ResearchProgressPayload;
import com.example.stoneworks.registry.ModAttachments;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SurveyRelayBlock extends Block implements EntityBlock {
    public SurveyRelayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurveyRelayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return null;
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay) {
            MenuProvider provider = new SimpleMenuProvider(
                (id, inventory, opener) -> new SurveyRelayMenu(id, inventory, relay),
                Component.translatable("menu.stoneworks.survey_relay")
            );
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
            PacketDistributor.sendToPlayer(serverPlayer, new ResearchProgressPayload(
                serverPlayer.getData(ModAttachments.RESEARCH).points()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}