package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import dev.ryanhcode.sable.compatibility.create.render.SableCreateRenderBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterRenderer", remap = false)
public class MechanicalCrafterRendererMixin {
    @WrapOperation(
            method = "renderFast(Lcom/simibubi/create/content/kinetics/crafter/MechanicalCrafterBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowCrafterBer(final LevelAccessor level,
                                          final Operation<Boolean> original,
                                          final MechanicalCrafterBlockEntity blockEntity,
                                          final float partialTick,
                                          final PoseStack poseStack,
                                          final MultiBufferSource bufferSource,
                                          final int packedLight) {
        return SableCreateRenderBridge.visualizationSupportedForSableBer(level,
                original.call(level),
                blockEntity,
                "MechanicalCrafterRenderer",
                "Create_MechanicalCrafterRenderer_internalGearPartials");
    }
}
