package dev.simulated_team.simulated.content.blocks.m24;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public class M24PhysicalComponentBlock extends DirectionalBlock implements EntityBlock, BlockSubLevelCollisionShape {
    private final M24Family family;
    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;

    public M24PhysicalComponentBlock(final BlockBehaviour.Properties properties,
                                     final M24Family family) {
        super(properties);
        this.family = family;
        this.factory = (pos, state) -> new M24PhysicalBlockEntity(family, pos, state);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public M24Family family() {
        return this.family;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return this.factory.apply(pos, state);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter blockGetter, final BlockState state) {
        if (this.family == M24Family.ROPE_CONNECTOR || this.family == M24Family.ROPE_WINCH) {
            return ropeConnectorPhysicsCollider(state.getValue(FACING));
        }
        return state.getCollisionShape(blockGetter, BlockPos.ZERO);
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return this.family.isSensorOrControl();
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof final M24PhysicalBlockEntity component) {
            return component.getRedstoneSignal();
        }
        return 0;
    }

    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos, final Player player,
                                 final InteractionHand hand, final BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof final M24PhysicalBlockEntity component) {
            component.cycleManualState(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                final net.minecraft.world.level.block.Block block, final BlockPos fromPos,
                                final boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof final M24PhysicalBlockEntity component) {
            component.onNeighborSignalChanged();
        }
    }

    @Override
    public boolean triggerEvent(final BlockState state, final Level level, final BlockPos pos,
                                final int id, final int param) {
        super.triggerEvent(state, level, pos, id, param);
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    private static VoxelShape ropeConnectorPhysicsCollider(final Direction facing) {
        return switch (facing) {
            case DOWN -> box(1.0D, 15.75D, 1.0D, 15.0D, 16.0D, 15.0D);
            case UP -> box(1.0D, 0.0D, 1.0D, 15.0D, 0.25D, 15.0D);
            case NORTH -> box(1.0D, 1.0D, 15.75D, 15.0D, 15.0D, 16.0D);
            case SOUTH -> box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 0.25D);
            case WEST -> box(15.75D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);
            case EAST -> box(0.0D, 1.0D, 1.0D, 0.25D, 15.0D, 15.0D);
        };
    }
}
