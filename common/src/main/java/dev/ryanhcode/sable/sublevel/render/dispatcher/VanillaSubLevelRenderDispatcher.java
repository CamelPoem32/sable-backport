package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilRenderBridge;
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
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

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

        final BlockState blockState = subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        return !blockState.is(SableTags.ALWAYS_CHUNK_RENDERING);
    }

    @Override
    public void onResourceManagerReload(@NotNull final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        if (!isSingleBlock(subLevel)) {
            renderData.close();
            return this.createRenderData(subLevel);
        }

        if (!(renderData instanceof VanillaSingleSubLevelRenderData)) {
            renderData.close();
            return new VanillaSingleSubLevelRenderData(subLevel);
        }
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        throw new UnsupportedOperationException("Chunked sub-level rendering is deferred in the Forge 1.20.1 backport");
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final CullFrustum cullFrustum, final boolean isSpectator) {
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

                singleRenderData.renderSingleBlock(layer, consumer, modelView, cameraX, cameraY, cameraZ);
            }

            layer.end(consumer, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        profiler.pop();

        this.singleBlockLayers.clear();
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        final Vector3f cameraPosition = new Vector3f();
        final Vector3d chunkOffset = new Vector3d();
        final Matrix4f transformation = new Matrix4f();
        final Matrix4f transformationInverse = new Matrix4f();
        final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final PoseStack matrices = new PoseStack();
        final MatrixStack matrixStack = VeilRenderBridge.create(matrices);

        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            sublevel.renderPose().rotationPoint().negate(chunkOffset.zero());
            data.getTransformation(cameraX, cameraY, cameraZ, transformation);

            transformation.invert(transformationInverse).transformPosition(cameraPosition.zero());
            dispatcher.sable$setCameraPosition(new Vec3(cameraPosition.x - chunkOffset.x(), cameraPosition.y - chunkOffset.y(), cameraPosition.z - chunkOffset.z()));

            matrixStack.clear();
            matrixStack.position().mul(transformation);
            matrixStack.normal().mul(new Matrix3f(transformation));
            if (data instanceof final VanillaSingleSubLevelRenderData singleRenderData) {
                final BlockEntity renderBlockEntity = singleRenderData.getRenderBlockEntity();
                if (renderBlockEntity != null) {
                    blockEntityRenderer.renderSingleBE(renderBlockEntity, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                }
            }
        }

        dispatcher.sable$setCameraPosition(null);
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
