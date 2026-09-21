package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bridges Create's vanilla contraption renderer into Sable's moving parent coordinate frame. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public class ContraptionEntityRendererMixin {
    @Inject(method = "shouldRender(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true)
    private void sable$shouldRenderContainingSubLevelContraption(final AbstractContraptionEntity entity,
                                                                 final Frustum frustum,
                                                                 final double cameraX,
                                                                 final double cameraY,
                                                                 final double cameraZ,
                                                                 final CallbackInfoReturnable<Boolean> cir) {
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
        if (containing != null
                && entity.getContraption() != null
                && entity.isAliveOrStale()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void sable$applyContainingSubLevelTransform(final AbstractContraptionEntity entity,
                                                        final float yaw,
                                                        final float partialTick,
                                                        final PoseStack poseStack,
                                                        final MultiBufferSource bufferSource,
                                                        final int packedLight,
                                                        final CallbackInfo ci) {
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
        if (!(containing instanceof final ClientSubLevel clientSubLevel)) {
            return;
        }
        if (!SableCreateContraptionContext.isRawEntityInSubLevelPlot(entity, clientSubLevel)) {
            return;
        }

        final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
        poseStack.mulPose(new Quaternionf(renderPose.orientation()));
    }

    @WrapOperation(
            method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowVanillaContraptionRender(final LevelAccessor level,
                                                        final Operation<Boolean> original,
                                                        final AbstractContraptionEntity entity,
                                                        final float yaw,
                                                        final float partialTick,
                                                        final PoseStack poseStack,
                                                        final MultiBufferSource bufferSource,
                                                        final int packedLight) {
        final boolean originalVisualizationSupported = original.call(level);
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
        final boolean sableContraption = containing != null;
        final boolean returnedVisualizationSupported = sableContraption ? false : originalVisualizationSupported;
        return returnedVisualizationSupported;
    }
}
