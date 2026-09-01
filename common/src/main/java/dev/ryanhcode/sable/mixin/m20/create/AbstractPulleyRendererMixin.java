package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reuses Create's own pulley BER for Sable-contained Rope Pulleys so rope and
 * magnet parts are emitted in the already-transformed visible-space BE pass.
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer", remap = false)
public class AbstractPulleyRendererMixin {
    private static final Set<String> LOGGED_PULLEY_BER = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "renderSafe(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowPulleyVanillaBer(final LevelAccessor level,
                                                final Operation<Boolean> original,
                                                final KineticBlockEntity blockEntity,
                                                final float partialTick,
                                                final PoseStack poseStack,
                                                final MultiBufferSource bufferSource,
                                                final int packedLight,
                                                final int packedOverlay) {
        final boolean originalVisualizationSupported = original.call(level);
        final boolean sableSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        final boolean returnedVisualizationSupported = sableSubLevel ? false : originalVisualizationSupported;
        final String key = blockEntity.getBlockPos().asLong() + ":" + sableSubLevel + ":"
                + originalVisualizationSupported + ":" + returnedVisualizationSupported;
        if (LOGGED_PULLEY_BER.add(key)) {
            Sable.LOGGER.info("SABLE_M20_PULLEY_RENDER renderer={} blockEntityClass={} blockId={} pos={} "
                            + "sableSubLevel={} originalVisualizationSupported={} returnedVisualizationSupported={} "
                            + "pulleySpeed={} animatedOffset=Create_PulleyBlockEntity_getInterpolatedOffset "
                            + "running=Create_PulleyBlockEntity_state rendererPath=Create_AbstractPulleyRenderer "
                            + "ropePathReached=Create_renderRope magnetPathReached=Create_renderMagnet "
                            + "hiddenPlotPoseTranslation=false",
                    "AbstractPulleyRenderer",
                    blockEntity.getClass().getName(),
                    BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()),
                    blockEntity.getBlockPos(),
                    sableSubLevel,
                    originalVisualizationSupported,
                    returnedVisualizationSupported,
                    blockEntity.getSpeed());
        }
        return returnedVisualizationSupported;
    }
}
