package dev.simulated_team.simulated.content.blocks.spring;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimulatedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class SpringBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Size> SIZE = EnumProperty.create("size", Size.class);

    public SpringBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(SIZE, Size.MEDIUM));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new SpringBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemStack getCloneItemStack(final BlockGetter level, final BlockPos pos, final BlockState state) {
        return SimulatedItems.SPRING.get().getDefaultInstance();
    }

    @Override
    public boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        return canAttach(level, pos, state.getValue(FACING).getOpposite());
    }

    @Override
    public BlockState updateShape(final BlockState state, final Direction facing, final BlockState facingState,
                                  final LevelAccessor level, final BlockPos currentPos, final BlockPos facingPos) {
        return state.getValue(FACING).getOpposite() == facing && !state.canSurvive(level, currentPos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                         final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof final SpringBlockEntity spring) {
            spring.breakPair();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static boolean canAttach(final LevelReader level, final BlockPos pos, final Direction direction) {
        final BlockPos support = pos.relative(direction);
        return level.getBlockState(support).isFaceSturdy(level, support, direction.getOpposite());
    }

    @Override
    public void beforeMove(final ServerLevel originLevel, final ServerLevel resultingLevel,
                           final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final SpringBlockEntity spring) {
            spring.setAssembling(true);
        }
    }

    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel,
                          final BlockState state, final BlockPos oldPos, final BlockPos newPos) {
        if (resultingLevel.getBlockEntity(newPos) instanceof final SpringBlockEntity spring) {
            spring.setAssembling(false);
            final SpringBlockEntity partner = spring.getPairedSpring();
            if (partner != null) {
                final SubLevel subLevel = Sable.HELPER.getContaining(resultingLevel, newPos);
                partner.setPartnerPos(newPos, subLevel == null ? null : subLevel.getUniqueId());
                partner.sendData();
            }
        }
    }

    @Override
    public boolean triggerEvent(final BlockState state, final Level level, final BlockPos pos,
                                final int id, final int param) {
        super.triggerEvent(state, level, pos, id, param);
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    @Override
    public int getLightBlock(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return 0;
    }

    public enum Size implements StringRepresentable {
        SMALL("small", 0.5D),
        MEDIUM("medium", 1.0D),
        LARGE("large", 8.0D);

        private static final Size[] VALUES = values();
        private final String name;
        private final double forceScale;

        Size(final String name, final double forceScale) {
            this.name = name;
            this.forceScale = forceScale;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Size cycle() {
            return VALUES[(this.ordinal() + 1) % VALUES.length];
        }

        public double forceScale() {
            return this.forceScale;
        }
    }
}
