package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps Sable-contained bearing tops in the coordinate-safe CPU renderer. */
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
        final boolean sableContained = bearing.getLevel() != null
                && Sable.HELPER.getContaining(bearing.getLevel(), bearing.getBlockPos()) != null;
        return visualizationSupported && !sableContained;
    }
}
