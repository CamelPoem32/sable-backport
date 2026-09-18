package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import dev.ryanhcode.sable.forge.SableM28ControlledSailFlywheelTrace;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Traces every Create contraption renderer invocation, independent of its dispatcher owner. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public abstract class ContraptionEntityRendererGlobalProbeMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final boolean SABLE$M28_PROBE_APPLIED =
            SableM28VisualOwnershipTrace.markContraptionRendererProbeApplied();

    @Inject(method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void sable$beginM28GlobalContraptionTrace(final AbstractContraptionEntity entity,
                                                       final float yaw, final float partialTick,
                                                       final PoseStack poseStack,
                                                       final MultiBufferSource bufferSource,
                                                       final int packedLight,
                                                       final CallbackInfo ci) {
        SableM28VisualOwnershipTrace.beginContraptionRenderer(
                entity, partialTick, poseStack, bufferSource);
        if (SableM28ControlledSailFlywheelTrace.enabled()) {
            SableM28ControlledSailFlywheelTrace.observe(entity, partialTick);
        }
    }

    @Inject(method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void sable$endM28GlobalContraptionTrace(final AbstractContraptionEntity entity,
                                                     final float yaw, final float partialTick,
                                                     final PoseStack poseStack,
                                                     final MultiBufferSource bufferSource,
                                                     final int packedLight,
                                                     final CallbackInfo ci) {
        SableM28VisualOwnershipTrace.endContraptionRenderer(
                entity, partialTick, poseStack, bufferSource);
    }
}
