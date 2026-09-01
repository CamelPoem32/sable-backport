package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import dev.ryanhcode.sable.compatibility.create.render.SableCreateRenderBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceRenderer", remap = false)
public class PortableStorageInterfaceRendererMixin {
    @WrapOperation(
            method = "renderSafe(Lcom/simibubi/create/content/contraptions/actors/psi/PortableStorageInterfaceBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowPortableInterfaceBer(final LevelAccessor level,
                                                    final Operation<Boolean> original,
                                                    final PortableStorageInterfaceBlockEntity blockEntity,
                                                    final float partialTick,
                                                    final PoseStack poseStack,
                                                    final MultiBufferSource bufferSource,
                                                    final int packedLight,
                                                    final int packedOverlay) {
        return SableCreateRenderBridge.visualizationSupportedForSableBer(level,
                original.call(level),
                blockEntity,
                "PortableStorageInterfaceRenderer",
                "Create_PortableStorageInterfaceRenderer_interfacePartials");
    }
}
