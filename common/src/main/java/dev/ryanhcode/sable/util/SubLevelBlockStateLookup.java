package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SubLevelBlockStateLookup {

    private SubLevelBlockStateLookup() {
    }

    public static @NotNull BlockState getBlockStateOrAir(final SubLevel subLevel, final BlockPos plotBlockPos) {
        final LevelChunk chunk = getPlotChunk(subLevel, plotBlockPos);
        if (chunk == null) {
            return Blocks.AIR.defaultBlockState();
        }

        return chunk.getBlockState(plotBlockPos);
    }

    public static @NotNull BlockState getBlockStateOrLevel(final BlockGetter blockGetter, final BlockPos blockPos) {
        if (blockGetter instanceof final Level level) {
            final SubLevel subLevel = Sable.HELPER.getContaining(level, blockPos);
            if (subLevel != null) {
                return getBlockStateOrAir(subLevel, blockPos);
            }
        }

        return blockGetter.getBlockState(blockPos);
    }

    public static @NotNull FluidState getFluidStateOrEmpty(final SubLevel subLevel, final BlockPos plotBlockPos) {
        final LevelChunk chunk = getPlotChunk(subLevel, plotBlockPos);
        if (chunk == null) {
            return Fluids.EMPTY.defaultFluidState();
        }

        return chunk.getFluidState(plotBlockPos);
    }

    public static @NotNull FluidState getFluidStateOrLevel(final BlockGetter blockGetter, final BlockPos blockPos) {
        if (blockGetter instanceof final Level level) {
            final SubLevel subLevel = Sable.HELPER.getContaining(level, blockPos);
            if (subLevel != null) {
                return getFluidStateOrEmpty(subLevel, blockPos);
            }
        }

        return blockGetter.getFluidState(blockPos);
    }

    public static @Nullable BlockEntity getBlockEntity(final SubLevel subLevel, final BlockPos plotBlockPos) {
        final LevelChunk chunk = getPlotChunk(subLevel, plotBlockPos);
        return chunk == null ? null : chunk.getBlockEntity(plotBlockPos);
    }

    private static @Nullable LevelChunk getPlotChunk(final SubLevel subLevel, final BlockPos plotBlockPos) {
        final PlotChunkHolder holder = subLevel.getPlot().getChunkHolder(
                subLevel.getPlot().toLocal(new ChunkPos(plotBlockPos)));
        if (holder == null) {
            return null;
        }

        return holder.getChunk();
    }
}
