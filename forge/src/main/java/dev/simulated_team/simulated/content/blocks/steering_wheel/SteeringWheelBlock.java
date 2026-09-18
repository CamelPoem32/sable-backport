package dev.simulated_team.simulated.content.blocks.steering_wheel;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Frozen Simulated positional Create generator used as an onboard control wheel. */
public final class SteeringWheelBlock extends HorizontalDirectionalBlock
        implements IBE<SteeringWheelBlockEntity>, IRotate, BlockSubLevelCollisionShape {

    public static final BooleanProperty ON_FLOOR = BooleanProperty.create("on_floor");

    public SteeringWheelBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(ON_FLOOR, true));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON_FLOOR);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction clickedFace = context.getClickedFace();
        final boolean floor = clickedFace == Direction.UP || clickedFace.getAxis().isHorizontal()
                && Arrays.stream(context.getNearestLookingDirections())
                .filter(direction -> direction.getAxis().isVertical())
                .findFirst()
                .orElse(Direction.DOWN) == Direction.DOWN;
        final Direction horizontal = Arrays.stream(context.getNearestLookingDirections())
                .filter(direction -> direction.getAxis().isHorizontal())
                .findFirst()
                .orElse(Direction.NORTH);
        return this.defaultBlockState().setValue(FACING, horizontal.getOpposite()).setValue(ON_FLOOR, floor);
    }

    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos,
                                 final Player player, final InteractionHand hand, final BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SteeringWheelClientControl.begin(pos, hand));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                         final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            final SteeringWheelBlockEntity wheel = level.getBlockEntity(pos) instanceof final SteeringWheelBlockEntity found
                    ? found : null;
            SteeringWheelDiagnostics.recordRemoval(level, pos, state, newState, movedByPiston, wheel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face == (state.getValue(ON_FLOOR) ? Direction.DOWN : Direction.UP);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof final SteeringWheelBlockEntity wheel)) {
            return 0;
        }
        return Math.min(15, Math.round(Math.abs(wheel.getAngle()) / wheel.getAngleLimit() * 15.0F));
    }

    @Override
    public Class<SteeringWheelBlockEntity> getBlockEntityClass() {
        return SteeringWheelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteeringWheelBlockEntity> getBlockEntityType() {
        return SimulatedBlockEntityTypes.STEERING_WHEEL.get();
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(final BlockGetter blockGetter, final BlockState state) {
        return state.getCollisionShape(blockGetter, BlockPos.ZERO);
    }
}
