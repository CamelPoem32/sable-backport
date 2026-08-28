package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSubLevelRenderTransforms;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

    private static final Set<UUID> LOGGED_DISPATCH = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_BE_TRANSFORM = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<RenderType> singleBlockLayers;

    public VanillaSubLevelRenderDispatcher() {
        this.singleBlockLayers = new LinkedHashSet<>();
    }

    /**
     * Checks if this sub-level is a single block, and therefore can use simpler batched rendering
     */
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final boolean isSingle = bounds != null && bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ();
        if (!isSingle) {
            return false;
        }

        final BlockState blockState = SubLevelBlockStateLookup.getBlockStateOrAir(
                subLevel, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        return !blockState.is(SableTags.ALWAYS_CHUNK_RENDERING);
    }

    @Override
    public void onResourceManagerReload(@NotNull final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        if (!(renderData instanceof VanillaSingleSubLevelRenderData)) {
            renderData.close();
            return new VanillaSingleSubLevelRenderData(subLevel);
        }
        renderData.rebuild();
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        return new VanillaSingleSubLevelRenderData(subLevel);
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Object cullFrustum, final boolean isSpectator) {
        // TODO
    }

    @Override
    public void renderSectionLayer(final Iterable<ClientSubLevel> sublevels, final RenderType renderType, final ShaderInstance shader, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        final FogShape fogShape = RenderSystem.getShaderFogShape();

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(FogShape.SPHERE.getIndex());
            shader.FOG_SHAPE.upload();
        }

        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("sublevel_render");
        for (final ClientSubLevel sublevel : sublevels) {
            if (sublevel.getRenderData() instanceof VanillaSingleSubLevelRenderData) {
                this.singleBlockLayers.add(renderType);
            }
        }
        profiler.pop();

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(fogShape.getIndex());
        }
    }

    @Override
    public void renderAfterSections(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        if (this.singleBlockLayers.isEmpty()) {
            return;
        }

        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("sublevel_render_single");
        for (final RenderType layer : this.singleBlockLayers) {
            final BufferBuilder consumer = Tesselator.getInstance().getBuilder();
            consumer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            for (final ClientSubLevel sublevel : sublevels) {
                final SubLevelRenderData data = sublevel.getRenderData();

                if (!(data instanceof final VanillaSingleSubLevelRenderData singleRenderData)) {
                    continue;
                }

                if (LOGGED_DISPATCH.add(sublevel.getUniqueId())) {
                    Sable.LOGGER.info("SABLE_RENDER phase=dispatch id={} name={} layer={}",
                            sublevel.getUniqueId(), sublevel.getName(), layer);
                }
                singleRenderData.renderSingleBlock(layer, consumer, modelView, cameraX, cameraY, cameraZ);
            }

            layer.end(consumer, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        profiler.pop();

        this.singleBlockLayers.clear();
    }

    @Override
    public void renderBasicSingleBlockLayer(final Iterable<ClientSubLevel> sublevels, final RenderType renderType,
                                            final double cameraX, final double cameraY, final double cameraZ,
                                            final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        profiler.push("sublevel_render_basic");
        for (final ClientSubLevel sublevel : sublevels) {
            if (sublevel.getRenderData() instanceof VanillaSingleSubLevelRenderData) {
                this.singleBlockLayers.add(renderType);
            }
        }
        profiler.pop();

        this.renderAfterSections(sublevels, cameraX, cameraY, cameraZ, modelView, projection, partialTicks);
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer,
                                    final double cameraX, final double cameraY, final double cameraZ,
                                    final Matrix4f modelView, final float partialTick) {
        final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final Vector3d cameraInSubLevel = new Vector3d();

        try {
            for (final ClientSubLevel sublevel : sublevels) {
                final SubLevelRenderData data = sublevel.getRenderData();
                if (!(data instanceof final VanillaSingleSubLevelRenderData singleRenderData)) {
                    continue;
                }

                final Collection<BlockEntity> renderBlockEntities = singleRenderData.getRenderBlockEntities();
                if (renderBlockEntities.isEmpty()) {
                    continue;
                }

                VanillaSubLevelRenderTransforms.cameraInSubLevelCoordinates(
                        sublevel.renderPose(), cameraX, cameraY, cameraZ, cameraInSubLevel);
                dispatcher.sable$setCameraPosition(new Vec3(
                        cameraInSubLevel.x(), cameraInSubLevel.y(), cameraInSubLevel.z()));

                for (final BlockEntity blockEntity : renderBlockEntities) {
                    final PoseStack matrices = new PoseStack();
                    matrices.pushPose();
                    final Vector3d poseBefore = this.extractTranslation(matrices.last().pose(), new Vector3d());
                    VanillaSubLevelRenderTransforms.applyBlockTransform(
                            matrices, modelView, sublevel.renderPose(), blockEntity.getBlockPos(), cameraX, cameraY, cameraZ);
                    this.logBlockEntityTransform(
                            sublevel, blockEntity, cameraX, cameraY, cameraZ, poseBefore, matrices.last().pose());
                    blockEntityRenderer.renderSingleBE(blockEntity, matrices, partialTick, cameraX, cameraY, cameraZ);
                    matrices.popPose();
                }
            }
        } finally {
            dispatcher.sable$setCameraPosition(null);
        }
    }

    private void logBlockEntityTransform(final ClientSubLevel sublevel, final BlockEntity blockEntity,
                                         final double cameraX, final double cameraY, final double cameraZ,
                                         final Vector3d poseBefore, final Matrix4f poseBeforeBer) {
        final BlockPos plotPos = blockEntity.getBlockPos();
        final String key = sublevel.getUniqueId() + ":" + plotPos.asLong() + ":" + blockEntity.getClass().getName();
        if (!LOGGED_BE_TRANSFORM.add(key)) {
            return;
        }

        final BlockPos localPos = plotPos.subtract(sublevel.getPlot().getCenterBlock());
        final Vector3d expectedWorldPosition = VanillaSubLevelRenderTransforms.blockWorldPosition(
                sublevel.renderPose(), plotPos, new Vector3d());
        final Vector3d cameraRelativePosition = expectedWorldPosition.sub(cameraX, cameraY, cameraZ, new Vector3d());
        final Vector3d poseBeforeBerTranslation = this.extractTranslation(poseBeforeBer, new Vector3d());

        Sable.LOGGER.info("SABLE_M11_BE_TRANSFORM id={} cameraWorld=({}, {}, {}) subLevelWorldPosition={} subLevelOrientation={} rawPlotBlockPos={} localBlockPos={} expectedBlockWorldPos={} cameraRelativePos={} renderStage=AFTER_ENTITIES poseTranslationBefore={} poseTranslationAfterSubLevel={} poseTranslationBeforeBER={}",
                sublevel.getUniqueId(), cameraX, cameraY, cameraZ, sublevel.renderPose().position(),
                sublevel.renderPose().orientation(), plotPos, localPos, expectedWorldPosition, cameraRelativePosition,
                poseBefore, poseBeforeBerTranslation, poseBeforeBerTranslation);
    }

    private Vector3d extractTranslation(final Matrix4f matrix, final Vector3d dest) {
        return dest.set(matrix.m30(), matrix.m31(), matrix.m32());
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
