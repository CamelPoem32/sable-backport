package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionBlockOwnership;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionBlockOwnership.Ownership;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixin.m28.StaticBufferBuilderProbeAccessor;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immediate-mode vanilla renderer for small basic sub-levels.
 */
public class VanillaSingleSubLevelRenderData implements SubLevelRenderData {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final SingleBlockSubLevelWrapper LEVEL_WRAPPER = new SingleBlockSubLevelWrapper();
    private static final Set<String> LOGGED_BLOCK_ENTITY_SCAN = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_CREATE_PISTON_MODEL = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_CREATE_PISTON_DRAW = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_SKIPPED_BLOCKS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_M28_STATIC_SAIL_SUPPRESSION =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Integer> LAST_CONTRAPTION_OWNER = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_FORCED_INVALIDATION = new ConcurrentHashMap<>();
    private static final boolean TRACE_M28_STATIC_CACHE = Boolean.getBoolean("sable.m28.visualOwnershipTrace");
    private static final boolean FORCE_CAPTURED_STATIC_INVALIDATE =
            Boolean.getBoolean("sable.m28.forceCapturedStaticInvalidate");
    private static final boolean SUPPRESS_STATIC_SYMMETRIC_SAILS =
            Boolean.getBoolean("sable.m28.suppressStaticSymmetricSails");

    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;

    /**
     * Cached non-air plot blocks inside the current bounded sub-level region.
     */
    private final List<RenderBlock> renderBlocks = new ArrayList<>();
    private final List<BlockEntity> renderBlockEntities = new ArrayList<>();
    private final Set<RenderType> loggedM10RenderLayers = new HashSet<>();
    private boolean loggedState = false;
    private boolean loggedDraw = false;
    private int visibleSectionCount = 0;
    private long snapshotGeneration;

    /**
     * Creates a new renderer for the given sub-level
     *
     * @param subLevel the sub-level to render
     */
    public VanillaSingleSubLevelRenderData(final ClientSubLevel subLevel) {
        this.subLevel = subLevel;
        this.rebuild();
    }

    private void rebuildBlockEntities() {
        this.renderBlockEntities.clear();

        for (final RenderBlock block : this.renderBlocks) {
            if (!block.state().hasBlockEntity()) {
                continue;
            }

            final BlockEntity blockEntity = SubLevelBlockStateLookup.getBlockEntity(this.subLevel, block.pos());
            if (blockEntity == null) {
                continue;
            }

            final BlockEntityRenderer<?> blockEntityRenderer = Minecraft.getInstance()
                    .getBlockEntityRenderDispatcher()
                    .getRenderer(blockEntity);
            this.logBlockEntityScan(blockEntity, blockEntityRenderer);
            if (blockEntityRenderer != null) {
                this.renderBlockEntities.add(blockEntity);
            }
        }
    }

    private void logBlockEntityScan(final BlockEntity blockEntity, @Nullable final BlockEntityRenderer<?> blockEntityRenderer) {
        final BlockPos plotPos = blockEntity.getBlockPos();
        final BlockPos localPos = plotPos.subtract(this.subLevel.getPlot().getCenterBlock());
        final String key = this.subLevel.getUniqueId() + ":" + plotPos.asLong() + ":" + blockEntity.getClass().getName();
        if (LOGGED_BLOCK_ENTITY_SCAN.add(key)) {
            Sable.LOGGER.info("SABLE_M11_RENDER_BE id={} posLocal={} posPlot={} class={} rendererClass={} rendererPresent={} dispatched=false",
                    this.subLevel.getUniqueId(), localPos, plotPos, blockEntity.getClass().getName(),
                    blockEntityRenderer == null ? "null" : blockEntityRenderer.getClass().getName(),
                    blockEntityRenderer != null);
        }
    }

    public int renderSingleBlock(final RenderType layer, final VertexConsumer consumer, final Matrix4f modelView,
                                 final double camX, final double camY, final double camZ) {
        final Minecraft client = Minecraft.getInstance();
        if (this.renderBlocks.isEmpty()) {
            this.rebuild();
        }

        int renderedBlocks = 0;
        Map<BlockPos, Ownership> contraptionOwnerships =
                SableCreateContraptionBlockOwnership.index(this.subLevel);
        if (this.forceCapturedStaticInvalidation(contraptionOwnerships)) {
            contraptionOwnerships = SableCreateContraptionBlockOwnership.index(this.subLevel);
        }
        this.logActiveContraptionOwnership(contraptionOwnerships, layer);
        for (final RenderBlock block : this.renderBlocks) {
            final BlockState blockState = block.state();
            final Ownership ownership = contraptionOwnerships.get(block.pos());
            if (ownership != null) {
                continue;
            }
            if (SUPPRESS_STATIC_SYMMETRIC_SAILS && isSymmetricSail(blockState)) {
                final String key = this.subLevel.getUniqueId() + ":" + block.pos().asLong() + ":" + layer;
                if (LOGGED_M28_STATIC_SAIL_SUPPRESSION.add(key)) {
                    Sable.LOGGER.info("SABLE_M29_STATIC_SAIL_SUPPRESSION subLevel={} plotPos={} localPos={} "
                                    + "blockState={} renderLayer={} scope=VANILLA_SINGLE_SUBLEVEL_STATIC_ONLY",
                            this.subLevel.getUniqueId(), block.pos(),
                            block.pos().subtract(this.subLevel.getPlot().getCenterBlock()), blockState, layer);
                }
                continue;
            }
            final boolean createPistonModelFallback = isCreatePistonModelFallback(blockState);
            if (blockState.getRenderShape() != RenderShape.MODEL && !createPistonModelFallback) {
                this.logStaticModelDecision(block, blockState, layer, "SKIPPED_RENDER_SHAPE", false, 0);
                continue;
            }

            final BakedModel bakedModel = client.getBlockRenderer().getBlockModel(blockState);
            final int visibleQuadCount = countVisibleQuads(bakedModel, blockState, block.seed());
            final Pose3dc renderPose = this.subLevel.renderPose();
            final Vector3dc renderPos = renderPose.position();
            LEVEL_WRAPPER.setup(this.subLevel, this.subLevel.getLevel(),
                    renderPos.x(), renderPos.y(), renderPos.z(), block.pos(), blockState);

            RANDOM.setSeed(block.seed());
            final List<RenderType> renderLayers = SableSubLevelRenderPlatform.INSTANCE.getRenderLayers(
                    LEVEL_WRAPPER, bakedModel, blockState, block.pos(), RANDOM);
            final boolean pistonSolidFallback = createPistonModelFallback
                    && RenderType.solid().equals(layer)
                    && visibleQuadCount > 0;
            if (createPistonModelFallback) {
                final String key = this.subLevel.getUniqueId() + ":" + block.pos().asLong() + ":" + blockState;
                if (LOGGED_CREATE_PISTON_MODEL.add(key)) {
                    Sable.LOGGER.info("SABLE_M14_PISTON_STATIC_MODEL stage=MODEL_FALLBACK_ENTERED id={} localPos={} "
                                    + "blockId={} state={} renderShape={} modelClass={} visibleQuadCount={} "
                                    + "renderLayers={} renderer=BlockRenderDispatcher",
                            this.subLevel.getUniqueId(), block.pos().subtract(this.subLevel.getPlot().getCenterBlock()),
                            BuiltInRegistries.BLOCK.getKey(blockState.getBlock()), blockState,
                            blockState.getRenderShape(), bakedModel.getClass().getName(), visibleQuadCount, renderLayers);
                }
            }
            if (!renderLayers.contains(layer) && !pistonSolidFallback) {
                this.logStaticModelDecision(block, blockState, layer, "SKIPPED_RENDER_LAYER", createPistonModelFallback,
                        visibleQuadCount);
                LEVEL_WRAPPER.clear();
                continue;
            }

            if (!this.loggedDraw) {
                this.loggedDraw = true;
                Sable.LOGGER.info("SABLE_RENDER phase=draw id={} name={} storedBlocks={} firstPos={} state={} layer={}",
                        this.subLevel.getUniqueId(), this.subLevel.getName(), this.renderBlocks.size(), block.pos(),
                        blockState.getBlock(), layer);
            }

            final PoseStack stack = new PoseStack();
            VanillaSubLevelRenderTransforms.applyBlockTransform(stack, modelView, renderPose, block.pos(), camX, camY, camZ);
            final int vertexStart = staticVertexCount(consumer);
            SableSubLevelRenderPlatform.INSTANCE.tesselateBlock(
                    LEVEL_WRAPPER, bakedModel, blockState, block.pos(), stack, consumer, RANDOM, block.seed(),
                    OverlayTexture.NO_OVERLAY, layer);
            final int vertexEnd = staticVertexCount(consumer);
            this.logRestoredStaticBlock(block, blockState, layer, vertexStart, vertexEnd, bakedModel);
            if (createPistonModelFallback) {
                final String key = this.subLevel.getUniqueId() + ":" + block.pos().asLong() + ":" + layer;
                if (LOGGED_CREATE_PISTON_DRAW.add(key)) {
                    Sable.LOGGER.info("SABLE_M14_PISTON_STATIC_MODEL stage=MODEL_DRAW_CALLED id={} localPos={} "
                                    + "blockId={} layer={} forcedSolidLayer={} visibleQuadCount={} "
                                    + "geometryEmitted={}",
                            this.subLevel.getUniqueId(), block.pos().subtract(this.subLevel.getPlot().getCenterBlock()),
                            BuiltInRegistries.BLOCK.getKey(blockState.getBlock()), layer, pistonSolidFallback,
                            visibleQuadCount, visibleQuadCount > 0);
                }
            }
            LEVEL_WRAPPER.clear();
            renderedBlocks++;
        }

        if (this.renderBlocks.size() > 1 && renderedBlocks > 0 && this.loggedM10RenderLayers.add(layer)) {
            Sable.LOGGER.info("SABLE_M10_RENDER id={} storedBlocks={} renderedBlocks={} layer={}",
                    this.subLevel.getUniqueId(), this.renderBlocks.size(), renderedBlocks, layer);
        }

        return renderedBlocks;
    }

    private void logActiveContraptionOwnership(final Map<BlockPos, Ownership> ownerships,
                                                final RenderType layer) {
        for (final Map.Entry<BlockPos, Ownership> entry : ownerships.entrySet()) {
            final BlockPos sourcePlotPos = entry.getKey();
            final Ownership ownership = entry.getValue();
            final RenderBlock storedBlock = this.renderBlocks.stream()
                    .filter(block -> block.pos().equals(sourcePlotPos))
                    .findFirst()
                    .orElse(null);
            final String key = this.subLevel.getUniqueId() + ":" + sourcePlotPos.asLong();
            final Integer previousOwner = LAST_CONTRAPTION_OWNER.put(key, ownership.entityId());
            if (previousOwner != null && previousOwner == ownership.entityId()) {
                continue;
            }
            final BlockState liveState = SubLevelBlockStateLookup.getBlockStateOrAir(this.subLevel, sourcePlotPos);
            final BlockState storedState = storedBlock == null ? null : storedBlock.state();
            Sable.LOGGER.info("SABLE_M28_RENDER_OWNERSHIP path=STATIC subLevel={} staticLocalPos={} "
                            + "staticPlotPos={} staticBlockId={} staticBlockState={} liveSubLevelState={} "
                            + "storedSnapshotPresent={} storedSnapshotState={} activeContraptionEntityId={} "
                            + "controllerPos={} contraptionAnchor={} capturedBlockLocalPos={} "
                            + "capturedBlockState={} capturedSourcePlotPositions={} "
                            + "positionMatchesCapturedBlock=true renderLayer={} geometryEmitted=false decision={}",
                    this.subLevel.getUniqueId(), sourcePlotPos.subtract(this.subLevel.getPlot().getCenterBlock()),
                    sourcePlotPos,
                    storedState == null ? "air" : BuiltInRegistries.BLOCK.getKey(storedState.getBlock()),
                    storedState, liveState, storedBlock != null, storedState, ownership.entityId(),
                    ownership.controllerPos(), ownership.contraptionAnchor(), ownership.capturedLocalPos(),
                    ownership.capturedState(), ownership.capturedSourcePlotPositions(), layer,
                    storedBlock == null ? "CONTRAPTION_ONLY" : "SUPPRESSED_ACTIVE_CONTRAPTION_OWNER");
            if (TRACE_M28_STATIC_CACHE || FORCE_CAPTURED_STATIC_INVALIDATE) {
                Sable.LOGGER.info("SABLE_M28_STATIC_DRAW_CONTENT frame=unavailable subLevel={} "
                                + "renderDataIdentity={} snapshotGeneration={} staticMeshIdentity=NONE_IMMEDIATE_MODE "
                                + "capturedSourcePos={} currentBlockState={} cachedAtBuildBlockState={} "
                                + "vertexRange=EMPTY vertexCount=0 modelIdentity=none "
                                + "stillPresentInStaticMesh={} drawReached=false",
                        this.subLevel.getUniqueId(), System.identityHashCode(this), this.snapshotGeneration,
                        sourcePlotPos, liveState, storedState, storedBlock != null);
                Sable.LOGGER.info("SABLE_M28_VISIBLE_OWNER source=SABLE_STATIC subLevel={} entityId={} "
                                + "sourcePosition={} snapshotGeneration={} renderLayer={} vertexRange=EMPTY "
                                + "drawReached=false ownershipDecision={}",
                        this.subLevel.getUniqueId(), ownership.entityId(), sourcePlotPos,
                        this.snapshotGeneration, layer,
                        storedBlock == null ? "NOT_IN_SNAPSHOT" : "SUPPRESSED_ACTIVE_CONTRAPTION_OWNER");
            }
        }
    }

    private void logRestoredStaticBlock(final RenderBlock block, final BlockState blockState,
                                        final RenderType layer, final int vertexStart,
                                        final int vertexEnd, final BakedModel bakedModel) {
        final String key = this.subLevel.getUniqueId() + ":" + block.pos().asLong();
        final Integer previousOwner = LAST_CONTRAPTION_OWNER.remove(key);
        if (previousOwner == null) {
            return;
        }
        Sable.LOGGER.info("SABLE_M28_RENDER_OWNERSHIP path=STATIC subLevel={} staticLocalPos={} "
                        + "staticPlotPos={} staticBlockId={} liveSubLevelState={} storedSnapshotState={} "
                        + "previousContraptionEntityId={} activeContraptionEntityId=none "
                        + "positionMatchesCapturedBlock=false renderLayer={} geometryEmitted=true decision={}",
                this.subLevel.getUniqueId(), block.pos().subtract(this.subLevel.getPlot().getCenterBlock()),
                block.pos(), BuiltInRegistries.BLOCK.getKey(blockState.getBlock()),
                SubLevelBlockStateLookup.getBlockStateOrAir(this.subLevel, block.pos()), blockState,
                previousOwner, layer, "RESTORED_STATIC_OWNER");
        if (TRACE_M28_STATIC_CACHE || FORCE_CAPTURED_STATIC_INVALIDATE) {
            Sable.LOGGER.info("SABLE_M28_STATIC_DRAW_CONTENT frame=unavailable subLevel={} "
                            + "renderDataIdentity={} snapshotGeneration={} staticMeshIdentity=NONE_IMMEDIATE_MODE "
                            + "capturedSourcePos={} currentBlockState={} cachedAtBuildBlockState={} "
                            + "vertexRange={}..{} vertexCount={} modelIdentity={} "
                            + "stillPresentInStaticMesh=true drawReached=true",
                    this.subLevel.getUniqueId(), System.identityHashCode(this), this.snapshotGeneration,
                    block.pos(), SubLevelBlockStateLookup.getBlockStateOrAir(this.subLevel, block.pos()),
                    blockState, vertexStart, vertexEnd, java.lang.Math.max(0, vertexEnd - vertexStart),
                    System.identityHashCode(bakedModel));
            Sable.LOGGER.info("SABLE_M28_VISIBLE_OWNER source=SABLE_STATIC subLevel={} entityId=none "
                            + "sourcePosition={} snapshotGeneration={} renderLayer={} vertexRange={}..{} "
                            + "drawReached=true ownershipDecision=RESTORED_STATIC_OWNER",
                    this.subLevel.getUniqueId(), block.pos(), this.snapshotGeneration, layer,
                    vertexStart, vertexEnd);
        }
    }

    private boolean forceCapturedStaticInvalidation(final Map<BlockPos, Ownership> ownerships) {
        if (!FORCE_CAPTURED_STATIC_INVALIDATE || ownerships.isEmpty()) {
            return false;
        }
        boolean rebuild = false;
        for (final Map.Entry<BlockPos, Ownership> entry : ownerships.entrySet()) {
            final String key = this.subLevel.getUniqueId() + ":" + entry.getKey().asLong();
            final Integer previous = LAST_FORCED_INVALIDATION.put(key, entry.getValue().entityId());
            rebuild |= previous == null || previous != entry.getValue().entityId();
        }
        if (!rebuild) {
            return false;
        }
        final long oldGeneration = this.snapshotGeneration;
        this.rebuild();
        Sable.LOGGER.info("SABLE_M28_STATIC_CACHE_LIFECYCLE event=FORCED_CAPTURE_INVALIDATION "
                        + "subLevel={} renderDataIdentity={} oldSnapshotGeneration={} "
                        + "newSnapshotGeneration={} invalidationRequested=true oldMeshIdentity=NONE_IMMEDIATE_MODE "
                        + "oldMeshDisposed=NOT_APPLICABLE rebuildStarted=true",
                this.subLevel.getUniqueId(), System.identityHashCode(this), oldGeneration,
                this.snapshotGeneration);
        return true;
    }

    private static int staticVertexCount(final VertexConsumer consumer) {
        return consumer instanceof final StaticBufferBuilderProbeAccessor access
                ? access.sable$getVertices() : -1;
    }

    private void logStaticModelDecision(final RenderBlock block, final BlockState blockState, final RenderType layer,
                                        final String decision, final boolean fallbackAttempted,
                                        final int visibleQuadCount) {
        final String key = this.subLevel.getUniqueId() + ":" + block.pos().asLong() + ":" + layer + ":" + decision;
        if (!LOGGED_SKIPPED_BLOCKS.add(key)) {
            return;
        }
        Sable.LOGGER.info("SABLE_M14_STATIC_BLOCK_DECISION id={} localPos={} blockId={} state={} "
                        + "renderShape={} layer={} decision={} fallbackAttempted={} visibleQuadCount={}",
                this.subLevel.getUniqueId(),
                block.pos().subtract(this.subLevel.getPlot().getCenterBlock()),
                BuiltInRegistries.BLOCK.getKey(blockState.getBlock()),
                blockState,
                blockState.getRenderShape(),
                layer,
                decision,
                fallbackAttempted,
                visibleQuadCount);
    }

    private static int countVisibleQuads(final BakedModel model, final BlockState blockState, final long seed) {
        int quads = 0;
        RANDOM.setSeed(seed);
        quads += model.getQuads(blockState, null, RANDOM).size();
        for (final Direction direction : Direction.values()) {
            RANDOM.setSeed(seed);
            quads += model.getQuads(blockState, direction, RANDOM).size();
        }
        return quads;
    }

    /** Create's piston BER draws the kinetic shaft; retain its baked casing model for every piston state. */
    private static boolean isCreatePistonModelFallback(final BlockState blockState) {
        final String namespace = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).getNamespace();
        final String path = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).getPath();
        return "create".equals(namespace)
                && ("mechanical_piston".equals(path) || "sticky_mechanical_piston".equals(path));
    }

    private static boolean isSymmetricSail(final BlockState blockState) {
        final net.minecraft.resources.ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        return "simulated".equals(id.getNamespace()) && "white_symmetric_sail".equals(id.getPath());
    }

    public @Nullable BlockEntity getRenderBlockEntity() {
        if (this.renderBlocks.isEmpty()) {
            this.rebuild();
        }
        return this.getRenderBlockEntities().stream().findFirst().orElse(null);
    }

    public Collection<BlockEntity> getRenderBlockEntities() {
        if (this.renderBlocks.isEmpty()) {
            this.rebuild();
        }
        final Map<BlockPos, Ownership> ownerships = SableCreateContraptionBlockOwnership.index(this.subLevel);
        if (ownerships.isEmpty()) {
            return this.renderBlockEntities;
        }
        return this.renderBlockEntities.stream()
                .filter(blockEntity -> !ownerships.containsKey(blockEntity.getBlockPos()))
                .toList();
    }

    @Override
    public void rebuild() {
        final long oldGeneration = this.snapshotGeneration;
        final int oldBlockCount = this.renderBlocks.size();
        this.renderBlocks.clear();
        this.renderBlockEntities.clear();
        this.loggedM10RenderLayers.clear();
        this.visibleSectionCount = 0;

        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();
        if (bounds != null && bounds != BoundingBox3i.EMPTY && bounds.volume() > 0) {
            final Set<SectionPos> visibleSections = new HashSet<>();
            final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            for (final PlotChunkHolder holder : this.subLevel.getPlot().getLoadedChunks()) {
                final LevelChunk chunk = holder.getChunk();
                final int chunkMinX = chunk.getPos().getMinBlockX();
                final int chunkMaxX = chunkMinX + 15;
                final int chunkMinZ = chunk.getPos().getMinBlockZ();
                final int chunkMaxZ = chunkMinZ + 15;

                for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                    final LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir()) {
                        continue;
                    }

                    final int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                    final int sectionMinY = sectionY << SectionPos.SECTION_BITS;
                    final int sectionMaxY = sectionMinY + 15;
                    final int minX = java.lang.Math.max(bounds.minX(), chunkMinX);
                    final int maxX = java.lang.Math.min(bounds.maxX(), chunkMaxX);
                    final int minY = java.lang.Math.max(bounds.minY(), sectionMinY);
                    final int maxY = java.lang.Math.min(bounds.maxY(), sectionMaxY);
                    final int minZ = java.lang.Math.max(bounds.minZ(), chunkMinZ);
                    final int maxZ = java.lang.Math.min(bounds.maxZ(), chunkMaxZ);

                    if (minX > maxX || minY > maxY || minZ > maxZ) {
                        continue;
                    }

                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                final BlockState blockState = section.getBlockState(
                                        x & SectionPos.SECTION_MASK,
                                        y & SectionPos.SECTION_MASK,
                                        z & SectionPos.SECTION_MASK);
                                if (blockState.isAir()) {
                                    continue;
                                }

                                final BlockPos pos = mutablePos.set(x, y, z).immutable();
                                this.renderBlocks.add(new RenderBlock(pos, blockState, blockState.getSeed(pos)));
                                visibleSections.add(SectionPos.of(pos));
                            }
                        }
                    }
                }
            }
            this.visibleSectionCount = visibleSections.size();
        }

        if (!this.loggedState || !this.renderBlocks.isEmpty()) {
            this.loggedState = true;
            Sable.LOGGER.info("SABLE_RENDER phase=state id={} name={} storedBlocks={} bounds={}",
                    this.subLevel.getUniqueId(), this.subLevel.getName(), this.renderBlocks.size(), bounds);
        }

        this.rebuildBlockEntities();
        this.snapshotGeneration++;

        if (TRACE_M28_STATIC_CACHE || FORCE_CAPTURED_STATIC_INVALIDATE) {
            Sable.LOGGER.info("SABLE_M28_STATIC_CACHE_LIFECYCLE event=SNAPSHOT_REBUILT subLevel={} "
                            + "renderDataIdentity={} oldSnapshotGeneration={} newSnapshotGeneration={} "
                            + "oldStoredBlockCount={} newStoredBlockCount={} persistentGpuMesh=false "
                            + "oldMeshIdentity=NONE_IMMEDIATE_MODE newMeshIdentity=NONE_IMMEDIATE_MODE "
                            + "oldMeshDisposed=NOT_APPLICABLE rebuildStarted=true",
                    this.subLevel.getUniqueId(), System.identityHashCode(this), oldGeneration,
                    this.snapshotGeneration, oldBlockCount, this.renderBlocks.size());
        }

        for (final BlockEntity blockEntity : this.renderBlockEntities) {
            SableSubLevelRenderPlatform.INSTANCE.tryAddFlywheelVisual(blockEntity);
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
    }

    @Override
    public int getVisibleSectionCount() {
        return java.lang.Math.max(1, this.visibleSectionCount);
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        this.rebuild();
    }

    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        return true;
    }

    @Override
    public void close() {
        this.renderBlocks.clear();
        this.renderBlockEntities.clear();
    }

    public long getSnapshotGeneration() {
        return this.snapshotGeneration;
    }

    public @Nullable BlockState getSnapshotState(final BlockPos pos) {
        return this.renderBlocks.stream()
                .filter(block -> block.pos().equals(pos))
                .map(RenderBlock::state)
                .findFirst()
                .orElse(null);
    }

    private record RenderBlock(BlockPos pos, BlockState state, long seed) {
    }
}
