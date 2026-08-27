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
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immediate-mode vanilla renderer for small basic sub-levels.
 */
public class VanillaSingleSubLevelRenderData implements SubLevelRenderData {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final SingleBlockSubLevelWrapper LEVEL_WRAPPER = new SingleBlockSubLevelWrapper();
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Vector3d CENTER_OF_ROT = new Vector3d();

    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;

    /**
     * Cached non-air plot blocks inside the current bounded sub-level region.
     */
    private final List<RenderBlock> renderBlocks = new ArrayList<>();
    private final List<BlockEntity> renderBlockEntities = new ArrayList<>();
    private boolean loggedState = false;
    private boolean loggedDraw = false;
    private boolean loggedM10Render = false;
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
            if (blockEntityRenderer != null) {
                this.renderBlockEntities.add(blockEntity);
            }
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
            if (blockState.getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            final BakedModel bakedModel = client.getBlockRenderer().getBlockModel(blockState);
            final Pose3dc renderPose = this.subLevel.renderPose();
            final Vector3dc renderPos = renderPose.position();
            LEVEL_WRAPPER.setup(this.subLevel, this.subLevel.getLevel(),
                    renderPos.x(), renderPos.y(), renderPos.z(), block.pos(), blockState);

            RANDOM.setSeed(block.seed());
            final List<RenderType> renderLayers = SableSubLevelRenderPlatform.INSTANCE.getRenderLayers(
                    LEVEL_WRAPPER, bakedModel, blockState, block.pos(), RANDOM);
            if (!renderLayers.contains(layer)) {
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
            this.applyBlockTransform(stack, modelView, renderPose, block.pos(), camX, camY, camZ);
            SableSubLevelRenderPlatform.INSTANCE.tesselateBlock(
                    LEVEL_WRAPPER, bakedModel, blockState, block.pos(), stack, consumer, RANDOM, block.seed(),
                    OverlayTexture.NO_OVERLAY, layer);
            LEVEL_WRAPPER.clear();
            renderedBlocks++;
        }

        if (!this.loggedM10Render && this.renderBlocks.size() > 1 && renderedBlocks > 0) {
            this.loggedM10Render = true;
            Sable.LOGGER.info("SABLE_M10_RENDER id={} storedBlocks={} renderedBlocks={} layer={}",
                    this.subLevel.getUniqueId(), this.renderBlocks.size(), renderedBlocks, layer);
        }

        return renderedBlocks;
    }

    private void applyBlockTransform(final PoseStack stack, final Matrix4f modelView, final Pose3dc renderPose,
                                     final BlockPos blockPos, final double camX, final double camY,
                                     final double camZ) {
        final Vector3dc renderPos = renderPose.position();
        final double renderX = renderPos.x();
        final double renderY = renderPos.y();
        final double renderZ = renderPos.z();
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3d renderCOR = renderRot.transform(CENTER_OF_ROT.set(renderPose.rotationPoint())
                .sub(blockPos.getX(), blockPos.getY(), blockPos.getZ()));

        renderCOR.negate().add(renderX, renderY, renderZ);

        final Matrix4f transform = TRANSFORM.identity();
        transform.translate((float) (renderCOR.x() - camX), (float) (renderCOR.y() - camY), (float) (renderCOR.z() - camZ));
        transform.rotate(new Quaternionf(renderRot));

        stack.last().pose().mul(modelView).mul(transform);
        transform.normal(stack.last().normal());
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
