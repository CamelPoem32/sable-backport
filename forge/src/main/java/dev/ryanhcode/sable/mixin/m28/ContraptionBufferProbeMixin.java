package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.forge.SableM29SailVisualLifecycle;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.SuperByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Emits bounded lifecycle events around Create's real CPU contraption submission. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public abstract class ContraptionBufferProbeMixin {
    @WrapOperation(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto"
                            + "(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void sable$traceRealBufferSubmission(final SuperByteBuffer buffer,
                                                 final PoseStack rendererPose,
                                                 final VertexConsumer consumer,
                                                 final Operation<Void> original,
                                                 final AbstractContraptionEntity entity) {
        SableM29SailVisualLifecycle.cpuRenderEligible(entity, AnimationTickHolder.getPartialTicks());
        original.call(buffer, rendererPose, consumer);
        SableM29SailVisualLifecycle.cpuGeometrySubmitted(entity);
    }
}
