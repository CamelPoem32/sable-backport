package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * An allocated & reserved space in a level belonging to a {@link SubLevel}, holding its own chunk grid.
 */
public class ClientLevelPlot extends LevelPlot {
    private static final boolean TRACE_M28_STATIC_CACHE = Boolean.getBoolean("sable.m28.visualOwnershipTrace")
            || Boolean.getBoolean("sable.m28.forceCapturedStaticInvalidate");
    /**
     * Creates a new plot at the given plot coordinate.
     *
     * @param plotContainer the parent plot container of this level plot
     * @param x             the global X coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param z             the global Z coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param logSize       the log_2 of the side length of a plot
     * @param subLevel      the sub-level using this plot
     */
    public ClientLevelPlot(final SubLevelContainer plotContainer, final int x, final int z, final int logSize, final ClientSubLevel subLevel) {
        super(plotContainer, x, z, logSize, subLevel);
    }

    /**
     * Returns the lighting engine this sub-level should use.
     * This is done due to {@link ServerSubLevel ServerSubLevels} having their own lighting engine,
     *
     * @return the lighting engine for this plot, or null if not set
     */
    @Override
    public LevelLightEngine getLightEngine() {
        return this.getSubLevel().getLevel().getLightEngine();
    }

    /**
     * @return the sub-level using this plot.
     */
    @Override
    public ClientSubLevel getSubLevel() {
        return (ClientSubLevel) super.getSubLevel();
    }

    @Override
    protected void onRemoveChunkHolder(final LevelChunk levelChunk) {
        ((ClientLevel) levelChunk.getLevel()).unload(levelChunk);
    }

    @Override
    public void addChunkHolder(final ChunkPos localChunkPos, final PlotChunkHolder holder, final boolean initializeLighting) {
        super.addChunkHolder(localChunkPos, holder, initializeLighting);

        for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
            final BlockEntitySubLevelActor actor = blockEntity instanceof BlockEntitySubLevelActor ? (BlockEntitySubLevelActor) blockEntity : null;

            if (actor != null) {
                this.blockEntityActors.put(blockEntity.getBlockPos(), actor);
            }
        }
    }

    @Override
    public void onBlockChange(final BlockPos pos, final BlockState state) {
        super.onBlockChange(pos, state);

        final ClientSubLevel subLevel = this.getSubLevel();
        if (subLevel.isFinalized()) {
            final SubLevelRenderData oldRenderData = subLevel.getRenderData();
            final VanillaSingleSubLevelRenderData oldSingle = oldRenderData instanceof VanillaSingleSubLevelRenderData found
                    ? found : null;
            final BlockState oldSnapshotState = oldSingle == null ? null : oldSingle.getSnapshotState(pos);
            final long oldGeneration = oldSingle == null ? -1L : oldSingle.getSnapshotGeneration();
            final int oldIdentity = oldRenderData == null ? 0 : System.identityHashCode(oldRenderData);
            subLevel.updateRenderData();
            final SubLevelRenderData newRenderData = subLevel.getRenderData();
            final VanillaSingleSubLevelRenderData newSingle = newRenderData instanceof VanillaSingleSubLevelRenderData found
                    ? found : null;
            if (TRACE_M28_STATIC_CACHE) {
                Sable.LOGGER.info("SABLE_M28_STATIC_CACHE_LIFECYCLE event=CLIENT_BLOCK_CHANGE "
                                + "frame=unavailable subLevel={} capturedSourcePos={} oldState={} newState={} "
                                + "invalidationRequested=true invalidatedSection={} oldRenderDataIdentity={} "
                                + "newRenderDataIdentity={} oldSnapshotGeneration={} newSnapshotGeneration={} "
                                + "newSnapshotState={} oldMeshIdentity=NONE_IMMEDIATE_MODE "
                                + "newMeshIdentity=NONE_IMMEDIATE_MODE oldMeshDisposed=NOT_APPLICABLE",
                        subLevel.getUniqueId(), pos, oldSnapshotState, state,
                        net.minecraft.core.SectionPos.of(pos), oldIdentity, System.identityHashCode(newRenderData),
                        oldGeneration, newSingle == null ? -1L : newSingle.getSnapshotGeneration(),
                        newSingle == null ? null : newSingle.getSnapshotState(pos));
            }
            Sable.LOGGER.info("SABLE_M10 phase=client_render_invalidated id={} pos={} state={}",
                    subLevel.getUniqueId(), pos, state);
        }
    }
}
