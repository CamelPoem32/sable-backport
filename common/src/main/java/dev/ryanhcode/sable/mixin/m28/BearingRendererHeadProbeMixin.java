package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28BearingHeadRenderTrace;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes Create's exact BER/Flywheel ownership of the Mechanical Bearing top. */
@Mixin(targets = "com.simibubi.create.content.contraptions.bearing.BearingRenderer", remap = false)
public abstract class BearingRendererHeadProbeMixin {
    @WrapOperation(method = "renderSafe",
            at = @At(value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$visualizationDecision(final LevelAccessor level, final Operation<Boolean> original,
                                                final KineticBlockEntity bearing, final float partialTick,
                                                final PoseStack poseStack, final MultiBufferSource bufferSource,
                                                final int light, final int overlay) {
        final boolean visualizationSupported = original.call(level);
        final boolean useFlywheel = visualizationSupported
                && !SableM28BearingHeadRenderTrace.isSableContained(bearing);
        return SableM28BearingHeadRenderTrace.visualizationDecision(
                bearing, partialTick, poseStack, visualizationSupported, useFlywheel);
    }

    @WrapOperation(method = "renderSafe",
            at = @At(value = "INVOKE",
                    target = "Lnet/createmod/catnip/render/CachedBuffers;partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer sable$partialSelected(final PartialModel partial, final BlockState state,
                                                  final Operation<SuperByteBuffer> original) {
        SableM28BearingHeadRenderTrace.partialSelected(partial);
        return original.call(partial, state);
    }

    @WrapOperation(method = "renderSafe",
            at = @At(value = "INVOKE",
                    target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void sable$partialRendered(final SuperByteBuffer buffer, final PoseStack poseStack,
                                       final VertexConsumer consumer, final Operation<Void> original) {
        SableM28BearingHeadRenderTrace.partialRendered(poseStack);
        try {
            original.call(buffer, poseStack, consumer);
        } finally {
            SableM28BearingHeadRenderTrace.finish(poseStack);
        }
    }
}
