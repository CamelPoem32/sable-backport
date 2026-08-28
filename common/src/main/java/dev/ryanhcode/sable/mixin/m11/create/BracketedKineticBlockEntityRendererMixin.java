package dev.ryanhcode.sable.mixin.m11.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Enables Create's own bracketed shaft/cogwheel BER fallback inside Sable client sublevels. */
@Mixin(targets = "com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntityRenderer", remap = false)
public class BracketedKineticBlockEntityRendererMixin {

    private static final Set<String> LOGGED_CREATE_BER = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "renderSafe(Lcom/simibubi/create/content/kinetics/simpleRelays/BracketedKineticBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowVanillaRender(final LevelAccessor level, final Operation<Boolean> original,
                                             final BracketedKineticBlockEntity blockEntity, final float partialTick,
                                             final PoseStack poseStack, final MultiBufferSource bufferSource,
                                             final int packedLight, final int packedOverlay) {
        final boolean originalVisualizationSupported = original.call(level);
        final boolean sableSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        final boolean returnedVisualizationSupported = sableSubLevel ? false : originalVisualizationSupported;
        final String key = blockEntity.getClass().getName() + ":" + blockEntity.getBlockPos().asLong() + ":"
                + sableSubLevel + ":" + originalVisualizationSupported + ":" + returnedVisualizationSupported;
        if (LOGGED_CREATE_BER.add(key)) {
            Sable.LOGGER.info("SABLE_M11_CREATE_BER renderer={} blockEntityClass={} pos={} sableSubLevel={} originalVisualizationSupported={} returnedVisualizationSupported={}",
                    "BracketedKineticBlockEntityRenderer", blockEntity.getClass().getName(), blockEntity.getBlockPos(),
                    sableSubLevel, originalVisualizationSupported, returnedVisualizationSupported);
        }
        return returnedVisualizationSupported;
    }
}
