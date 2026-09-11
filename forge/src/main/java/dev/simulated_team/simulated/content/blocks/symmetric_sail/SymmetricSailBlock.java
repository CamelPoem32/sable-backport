package dev.simulated_team.simulated.content.blocks.symmetric_sail;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class SymmetricSailBlock extends RotatedPillarBlock implements BlockSubLevelLiftProvider {
    public SymmetricSailBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public float sable$getLiftScalar() {
        return 0.0F;
    }

    @Override
    public float sable$getParallelDragScalar() {
        return 1.75F;
    }

    @Override
    public @NotNull Direction sable$getNormal(final BlockState state) {
        return Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
    }
}
