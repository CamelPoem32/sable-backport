package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import dev.ryanhcode.sable.util.LevelAccelerator;

/**
 * Authoritative plot-local block editing utilities for server sub-level diagnostics and tools.
 */
public final class SubLevelBlockEditHelper {
    private SubLevelBlockEditHelper() {
    }

    /**
     * Converts a user-facing local block offset into the actual plot block position.
     */
    public static BlockPos localOffsetToPlotBlock(final ServerSubLevel subLevel, final BlockPos localOffset) {
        return subLevel.getPlot().getCenterBlock().offset(localOffset);
    }

    /**
     * Sets a block at a user-facing local offset through the normal plot chunk storage path.
     * LevelChunk.setBlockState is intentionally used so Sable's existing block-change mixin drives
     * bounds, mass, collider, heat/floating-block, wake-up, and tracking side effects.
     */
    public static BlockChange setLocalBlock(final ServerSubLevel subLevel, final BlockPos localOffset,
                                            final BlockState newState, final int updateFlags,
                                            final boolean notifyClients) {
        final BlockPos plotBlockPos = localOffsetToPlotBlock(subLevel, localOffset);
        final PlotChunkHolder holder = getOrCreatePlotChunk(subLevel, plotBlockPos);
        final LevelChunk chunk = holder.getChunk();
        final BlockState oldState = chunk.getBlockState(plotBlockPos);
        final BlockState previousState = chunk.setBlockState(plotBlockPos, newState, false);

        if (previousState == null) {
            throw new IllegalStateException("Local block offset " + localOffset + " resolved outside plot chunk at " + plotBlockPos);
        }

        if (notifyClients && !oldState.equals(newState)) {
            subLevel.getLevel().sendBlockUpdated(plotBlockPos, oldState, newState, updateFlags);
        }

        return new BlockChange(localOffset.immutable(), plotBlockPos.immutable(), oldState, newState);
    }

    /**
     * Rebuilds aggregate plot state once after a batch of block writes.
     */
    public static void finalizeBlockChanges(final ServerSubLevel subLevel, final Iterable<BlockChange> changes) {
        final ServerLevelPlot plot = subLevel.getPlot();

        for (final BlockChange change : changes) {
            plot.expandIfNecessary(change.plotBlockPos());
        }

        plot.updateBoundingBox();

        final BoundingBox3ic bounds = plot.getBoundingBox();
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            throw new IllegalStateException("Sub-level block edit produced invalid plot bounds: " + bounds);
        }

        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        if (physicsSystem != null) {
            physicsSystem.finalizeExistingSubLevelStorage(subLevel, false, "block_edit");
        } else {
            subLevel.buildMassTracker();
            subLevel.updateMergedMassData(1.0f);
            subLevel.updateBoundingBox();
        }
    }

    public static MassTracker recomputeSelfMassData(final ServerSubLevel subLevel) {
        return MassTracker.build(new LevelAccelerator(subLevel.getLevel()), subLevel.getPlot().getBoundingBox());
    }

    public static int countNonAirBlocks(final ServerSubLevel subLevel) {
        return countBlocks(subLevel, false);
    }

    public static int countMassContributingBlocks(final ServerSubLevel subLevel) {
        return countBlocks(subLevel, true);
    }

    private static PlotChunkHolder getOrCreatePlotChunk(final ServerSubLevel subLevel, final BlockPos plotBlockPos) {
        final ServerLevelPlot plot = subLevel.getPlot();
        final ChunkPos globalChunk = new ChunkPos(plotBlockPos);

        if (!plot.contains(globalChunk)) {
            throw new IllegalArgumentException("Local block position resolves outside assigned plot: " + plotBlockPos);
        }

        PlotChunkHolder holder = plot.getChunkHolder(plot.toLocal(globalChunk));
        if (holder == null) {
            plot.newEmptyChunk(globalChunk);
            holder = plot.getChunkHolder(plot.toLocal(globalChunk));
        }

        if (holder == null) {
            throw new IllegalStateException("Failed to create plot chunk for local block edit at " + plotBlockPos);
        }

        return holder;
    }

    private static int countBlocks(final ServerSubLevel subLevel, final boolean massContributingOnly) {
        int count = 0;

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                final LevelChunkSection section = chunk.getSection(sectionIndex);
                if (section.hasOnlyAir()) {
                    continue;
                }

                final int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                final int minX = chunk.getPos().getMinBlockX();
                final int minY = sectionY << 4;
                final int minZ = chunk.getPos().getMinBlockZ();
                final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            final BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir()) {
                                continue;
                            }

                            if (massContributingOnly) {
                                pos.set(minX + x, minY + y, minZ + z);
                                if (PhysicsBlockPropertyHelper.getMass(subLevel.getLevel(), pos, state) <= 0.0) {
                                    continue;
                                }
                            }

                            count++;
                        }
                    }
                }
            }
        }

        return count;
    }

    public record BlockChange(BlockPos localOffset, BlockPos plotBlockPos, @Nullable BlockState oldState, BlockState newState) {
    }
}
