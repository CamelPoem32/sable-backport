package dev.eriksonn.aeronautics.content.propulsion;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

public final class WoodenPropellerBlock extends DirectionalKineticBlock
        implements IBE<WoodenPropellerBlockEntity> {
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    public WoodenPropellerBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(REVERSED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REVERSED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        Direction preferred = this.getPreferredFacing(context);
        if (preferred == null) {
            preferred = context.getClickedFace().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING,
                context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                        ? preferred : preferred.getOpposite());
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, final UseOnContext context) {
        final Vec3 diff = context.getClickLocation().subtract(context.getClickedPos().getCenter());
        final Direction facing = state.getValue(FACING);
        final Vec3i normal = facing.getNormal();
        if (context.getClickedFace() == facing
                || diff.dot(new Vec3(normal.getX(), normal.getY(), normal.getZ())) > 0.0D) {
            state = state.cycle(REVERSED);
            context.getLevel().setBlock(context.getClickedPos(), state, 3);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        return super.onWrenched(state, context);
    }

    @Override
    public Class<WoodenPropellerBlockEntity> getBlockEntityClass() {
        return WoodenPropellerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WoodenPropellerBlockEntity> getBlockEntityType() {
        return AeroPropulsionRegistries.WOODEN_PROPELLER_BE.get();
    }
}
