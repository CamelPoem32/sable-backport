package dev.ryanhcode.sable.mixinhelpers.clip_overwrite;

import dev.ryanhcode.sable.mixin.clip_overwrite.ClipContextAccessor;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public final class BlockGetterClipHelper {

    private BlockGetterClipHelper() {
    }

    public static @NotNull BlockHitResult originalClip(final BlockGetter level, final ClipContext clipContext) {
        return BlockGetter.traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, (context, blockPos) -> {
            final BlockState blockState = SubLevelBlockStateLookup.getBlockStateOrLevel(level, blockPos);
            final FluidState fluidState = SubLevelBlockStateLookup.getFluidStateOrLevel(level, blockPos);
            final Vec3 from = context.getFrom();
            final Vec3 to = context.getTo();
            final VoxelShape blockShape = context.getBlockShape(blockState, level, blockPos);
            final BlockHitResult blockResult = level.clipWithInteractionOverride(from, to, blockPos, blockShape, blockState);
            final VoxelShape fluidShape = context.getFluidShape(fluidState, level, blockPos);
            final BlockHitResult fluidResult = fluidShape.clip(from, to, blockPos);
            final double blockDistance = blockResult == null ? Double.MAX_VALUE : from.distanceToSqr(blockResult.getLocation());
            final double fluidDistance = fluidResult == null ? Double.MAX_VALUE : from.distanceToSqr(fluidResult.getLocation());
            return blockDistance <= fluidDistance ? blockResult : fluidResult;
        }, context -> {
            final Vec3 difference = context.getFrom().subtract(context.getTo());
            return BlockHitResult.miss(context.getTo(), Direction.getNearest(difference.x, difference.y, difference.z),
                    BlockPos.containing(context.getTo()));
        });
    }

    public static ClipContext copyContext(final ClipContext source, final Vec3 from, final Vec3 to) {
        final ClipContextAccessor accessor = (ClipContextAccessor) source;
        final CollisionContext collisionContext = accessor.sable$getCollisionContext();
        final Entity entity = collisionContext instanceof EntityCollisionContext entityContext
                ? entityContext.getEntity()
                : null;
        return new ClipContext(from, to, accessor.sable$getBlock(), accessor.sable$getFluid(), entity);
    }
}
