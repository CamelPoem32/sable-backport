package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes every entity dispatcher entry, including callers outside Sable's known render bridge. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherGlobalProbeMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final boolean SABLE$M28_PROBE_APPLIED =
            SableM28VisualOwnershipTrace.markDispatcherProbeApplied();

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void sable$traceM28Dispatcher(final E entity,
                                                             final double x, final double y, final double z,
                                                             final float yaw, final float partialTick,
                                                             final PoseStack poseStack,
                                                             final MultiBufferSource bufferSource,
                                                             final int packedLight,
                                                             final CallbackInfo ci) {
        if (SableM28VisualOwnershipTrace.beginDispatcher(
                entity, x, y, z, partialTick, poseStack, bufferSource)) {
            ci.cancel();
        }
    }
}
