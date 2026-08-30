package dev.ryanhcode.sable.mixin.m17.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reuses Create's own SplitShaft BER partial rendering for Sable-contained Clutch/Gearshift BEs. */
@Mixin(targets = "com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer", remap = false)
public class SplitShaftRendererMixin {

    private static final Set<String> LOGGED_SPECIALIZED_BER = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "renderSafe(Lcom/simibubi/create/content/kinetics/transmission/SplitShaftBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowSplitShaftVanillaBer(final LevelAccessor level, final Operation<Boolean> original,
                                                    final SplitShaftBlockEntity blockEntity, final float partialTick,
                                                    final PoseStack poseStack, final MultiBufferSource bufferSource,
                                                    final int packedLight, final int packedOverlay) {
        final boolean originalVisualizationSupported = original.call(level);
        final boolean sableSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        final boolean returnedVisualizationSupported = sableSubLevel ? false : originalVisualizationSupported;
        final String key = blockEntity.getClass().getName() + ":" + blockEntity.getBlockPos().asLong() + ":"
                + sableSubLevel + ":" + originalVisualizationSupported + ":" + returnedVisualizationSupported;
        if (LOGGED_SPECIALIZED_BER.add(key)) {
            Sable.LOGGER.info("SABLE_M17_SPECIALIZED_BER renderer={} blockEntityClass={} blockId={} pos={} sableSubLevel={} originalVisualizationSupported={} returnedVisualizationSupported={} kineticSpeed={} hiddenPlotPoseTranslation=false renderPath=Create_SplitShaftRenderer",
                    "SplitShaftRenderer", blockEntity.getClass().getName(),
                    BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()), blockEntity.getBlockPos(),
                    sableSubLevel, originalVisualizationSupported, returnedVisualizationSupported,
                    blockEntity.getSpeed());
        }
        return returnedVisualizationSupported;
    }
}
