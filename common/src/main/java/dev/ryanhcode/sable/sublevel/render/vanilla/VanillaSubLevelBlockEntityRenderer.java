package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

public class VanillaSubLevelBlockEntityRenderer implements SubLevelRenderDispatcher.BlockEntityRenderer {

    private static final Set<String> LOGGED_DISPATCH = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final RenderBuffers renderBuffers;
    private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    public VanillaSubLevelBlockEntityRenderer(final BlockEntityRenderDispatcher blockEntityRenderDispatcher,
                                              final RenderBuffers renderBuffers,
                                              final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress) {
        this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
        this.renderBuffers = renderBuffers;
        this.destructionProgress = destructionProgress;
    }

    @Override
    public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
        return this.blockEntityRenderDispatcher;
    }

    @Override
    public void renderSingleBE(final BlockEntity blockEntity, final PoseStack poseStack, final float partialTick,
                               final double cameraX, final double cameraY, final double cameraZ) {
        final BlockEntityRenderer<?> renderer = this.blockEntityRenderDispatcher.getRenderer(blockEntity);
        if (renderer == null) {
            return;
        }

        final BlockPos pos = blockEntity.getBlockPos();
        MultiBufferSource source = this.renderBuffers.bufferSource();
        this.logDispatch(blockEntity, renderer);

        poseStack.pushPose();

        final SortedSet<BlockDestructionProgress> destructionProgresses = this.destructionProgress.get(pos.asLong());
        if (destructionProgresses != null && !destructionProgresses.isEmpty()) {

            final int progress = destructionProgresses.last().getProgress();
            if (progress >= 0) {
                final PoseStack.Pose pose = poseStack.last();
                final VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(
                        this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(progress)),
                        pose.pose(),
                        pose.normal(),
                        1.0F);
                source = type -> {
                    final VertexConsumer consumer = this.renderBuffers.bufferSource().getBuffer(type);
                    return type.affectsCrumbling() ? VertexMultiConsumer.create(vertexConsumer, consumer) : consumer;
                };
            }
        }

        this.blockEntityRenderDispatcher.render(blockEntity, partialTick, poseStack, source);

        poseStack.popPose();
    }

    private void logDispatch(final BlockEntity blockEntity, final BlockEntityRenderer<?> renderer) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            return;
        }

        final BlockPos plotPos = blockEntity.getBlockPos();
        final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
        final String key = subLevel.getUniqueId() + ":" + plotPos.asLong() + ":" + blockEntity.getClass().getName();
        if (LOGGED_DISPATCH.add(key)) {
            Sable.LOGGER.info("SABLE_M11_RENDER_BE id={} posLocal={} posPlot={} class={} rendererClass={} rendererPresent=true dispatched=true",
                    subLevel.getUniqueId(), localPos, plotPos, blockEntity.getClass().getName(), renderer.getClass().getName());
        }
    }
}
