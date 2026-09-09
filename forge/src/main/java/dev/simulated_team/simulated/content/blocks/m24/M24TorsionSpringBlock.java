package dev.simulated_team.simulated.content.blocks.m24;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class M24TorsionSpringBlock extends DirectionalKineticBlock
        implements IBE<M24TorsionSpringBlockEntity>, BlockSubLevelCollisionShape,
        dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics.ExtraKineticsBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public M24TorsionSpringBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(POWERED));
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face.getOpposite() == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<M24TorsionSpringBlockEntity> getBlockEntityClass() {
        return M24TorsionSpringBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends M24TorsionSpringBlockEntity> getBlockEntityType() {
        return SimulatedBlockEntityTypes.TORSION_SPRING.get();
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
        final boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
            this.withBlockEntityDo(level, pos, M24TorsionSpringBlockEntity::onNeighborSignalChanged);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        return this.getBlockEntityOptional(level, pos).map(M24TorsionSpringBlockEntity::getRedstoneSignal).orElse(0);
    }

    @Override
    public com.simibubi.create.content.kinetics.base.IRotate getExtraKineticsRotationConfiguration() {
        return M24TorsionSpringBlockEntity.Output.CONFIG;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter blockGetter, final BlockState state) {
        return state.getCollisionShape(blockGetter, BlockPos.ZERO);
    }
}
