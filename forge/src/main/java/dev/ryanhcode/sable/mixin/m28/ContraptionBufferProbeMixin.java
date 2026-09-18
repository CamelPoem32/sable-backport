package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.forge.SableM28ContraptionBufferProbe;
import dev.ryanhcode.sable.forge.SableM28BatchTrace;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scopes vertex diagnostics to Create's real CPU render of a Sable contraption. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public abstract class ContraptionBufferProbeMixin {
    @WrapOperation(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer"
                            + "(Lnet/minecraft/client/renderer/RenderType;)"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    remap = true))
    private VertexConsumer sable$rememberRenderLayer(final MultiBufferSource source,
                                                      final RenderType layer,
                                                      final Operation<VertexConsumer> original,
                                                      final AbstractContraptionEntity entity) {
        SableM28ContraptionBufferProbe.noteLayer(entity, layer);
        final VertexConsumer consumer = original.call(source, layer);
        SableM28BatchTrace.noteBuffer(layer, consumer);
        return consumer;
    }

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
        SableM28ContraptionBufferProbe.begin(entity, buffer, rendererPose, consumer);
        SableM28VisualOwnershipTrace.markContraptionGeometryEmission();
        try {
            original.call(buffer, rendererPose, consumer);
        } finally {
            SableM28ContraptionBufferProbe.end(consumer);
        }
    }
}
