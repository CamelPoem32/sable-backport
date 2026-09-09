package dev.simulated_team.simulated.content.blocks.m24;

import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class M24WinchBlock extends DirectionalAxisKineticBlock
        implements IBE<M24WinchBlockEntity>, BlockSubLevelCollisionShape {

    public M24WinchBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<M24WinchBlockEntity> getBlockEntityClass() {
        return M24WinchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends M24WinchBlockEntity> getBlockEntityType() {
        return SimulatedBlockEntityTypes.ROPE_WINCH.get();
    }

    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos,
                                 final Player player, final InteractionHand hand, final BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        return this.onBlockEntityUse(level, pos, component -> {
            component.cycleManualState(player);
            return InteractionResult.CONSUME;
        });
    }

    @Override
    public void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                final net.minecraft.world.level.block.Block block, final BlockPos fromPos,
                                final boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        this.withBlockEntityDo(level, pos, M24WinchBlockEntity::onNeighborSignalChanged);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter blockGetter, final BlockState state) {
        return switch (state.getValue(FACING)) {
            case DOWN -> box(1.0D, 15.75D, 1.0D, 15.0D, 16.0D, 15.0D);
            case UP -> box(1.0D, 0.0D, 1.0D, 15.0D, 0.25D, 15.0D);
            case NORTH -> box(1.0D, 1.0D, 15.75D, 15.0D, 15.0D, 16.0D);
            case SOUTH -> box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 0.25D);
            case WEST -> box(15.75D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);
            case EAST -> box(0.0D, 1.0D, 1.0D, 0.25D, 15.0D, 15.0D);
        };
    }
}
