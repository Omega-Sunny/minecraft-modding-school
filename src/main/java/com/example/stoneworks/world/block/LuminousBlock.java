package com.example.stoneworks.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;

public final class LuminousBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public LuminousBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 15 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
