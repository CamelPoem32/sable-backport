package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
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
        for (final RenderBlock block : this.renderBlocks) {
            final BlockState blockState = block.state();
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
            SableSubLevelRenderPlatform.INSTANCE.tesselateBlock(
                    LEVEL_WRAPPER, bakedModel, blockState, block.pos(), stack, consumer, RANDOM, block.seed(),
                    OverlayTexture.NO_OVERLAY, layer);
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

    public @Nullable BlockEntity getRenderBlockEntity() {
        if (this.renderBlocks.isEmpty()) {
            this.rebuild();
        }
        return this.renderBlockEntities.isEmpty() ? null : this.renderBlockEntities.get(0);
    }

    public Collection<BlockEntity> getRenderBlockEntities() {
        if (this.renderBlocks.isEmpty()) {
            this.rebuild();
        }
        return this.renderBlockEntities;
    }

    @Override
    public void rebuild() {
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

    private record RenderBlock(BlockPos pos, BlockState state, long seed) {
    }
}
