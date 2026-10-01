package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Bridges Create's vanilla contraption renderer into Sable's moving parent coordinate frame. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public class ContraptionEntityRendererMixin {
    @Unique
    private static final Map<AbstractContraptionEntity, String> SABLE$M37_CHEST_STAGES =
            Collections.synchronizedMap(new WeakHashMap<>());
    @Unique
    private static final Map<AbstractContraptionEntity, Boolean> SABLE$M37_CHEST_ERRORS =
            Collections.synchronizedMap(new WeakHashMap<>());

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

    @WrapOperation(method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/render/BlockEntityRenderHelper;renderBlockEntities(Ljava/util/List;Ljava/util/BitSet;Ljava/util/BitSet;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lnet/minecraft/world/level/Level;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;F)V"))
    private void sable$traceNestedChestRender(final List<BlockEntity> blockEntities, final BitSet eligible,
                                               final BitSet errors, final VirtualRenderWorld renderWorld,
                                               final Level level, final PoseStack matrices, final Matrix4f light,
                                               final MultiBufferSource buffers, final float partialTick,
                                               final Operation<Void> original,
                                               final AbstractContraptionEntity entity) {
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS || !(containing instanceof ClientSubLevel)
                || entity.getContraption() == null) {
            original.call(blockEntities, eligible, errors, renderWorld, level, matrices, light, buffers, partialTick);
            return;
        }
        BlockPos chestLocal = null;
        for (final var entry : entity.getContraption().getBlocks().entrySet()) {
            if (entry.getValue().state().is(Blocks.CHEST)) {
                chestLocal = entry.getKey();
                break;
            }
        }
        if (chestLocal == null) {
            original.call(blockEntities, eligible, errors, renderWorld, level, matrices, light, buffers, partialTick);
            return;
        }
        final ClientContraption client = entity.getContraption().getOrCreateClientContraptionLazy();
        final BlockEntity rebuilt = client.getBlockEntity(chestLocal);
        int chestIndex = -1;
        for (int index = 0; index < blockEntities.size(); index++) {
            if (blockEntities.get(index) instanceof ChestBlockEntity) {
                chestIndex = index;
                break;
            }
        }
        final String stage = rebuilt == null ? "CHEST_BE_MISSING_CLIENT"
                : chestIndex < 0 ? "CHEST_RENDER_SKIPPED_LIST"
                : !eligible.get(chestIndex) ? "CHEST_RENDER_SKIPPED_MASK" : "CHEST_RENDER_DISPATCH";
        final Matrix4f matrix = matrices.last().pose();
        final boolean plotTranslation = Math.max(Math.max(Math.abs(matrix.m30()), Math.abs(matrix.m31())),
                Math.abs(matrix.m32())) > 1_000_000;
        final String key = stage + ":" + chestLocal + ":" + plotTranslation;
        if (!key.equals(SABLE$M37_CHEST_STAGES.put(entity, key))) {
            Sable.LOGGER.info("SABLE_M37_CHEST_RENDER stage={} cceUuid={} subLevel={} chestLocal={} "
                            + "chestBE={} renderListSize={} eligible={} renderWorld={} cameraRelative=({}, {}, {}) "
                            + "visualizationSupported={} hiddenPlotPoseTranslation={}",
                    stage, entity.getUUID(), containing.getUniqueId(), chestLocal,
                    rebuilt == null ? "none" : rebuilt.getClass().getSimpleName(), blockEntities.size(),
                    chestIndex >= 0 && eligible.get(chestIndex), renderWorld.getClass().getSimpleName(),
                    matrix.m30(), matrix.m31(), matrix.m32(),
                    dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(level),
                    plotTranslation);
        }
        original.call(blockEntities, eligible, errors, renderWorld, level, matrices, light, buffers, partialTick);
        if (chestIndex >= 0 && errors.get(chestIndex)
                && SABLE$M37_CHEST_ERRORS.putIfAbsent(entity, true) == null) {
            Sable.LOGGER.warn("SABLE_M37_CHEST_RENDER stage=CHEST_RENDER_ERROR cceUuid={} chestLocal={}",
                    entity.getUUID(), chestLocal);
        }
    }
}
