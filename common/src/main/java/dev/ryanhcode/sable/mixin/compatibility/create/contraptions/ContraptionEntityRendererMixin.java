package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bridges Create's vanilla contraption renderer into Sable's moving parent coordinate frame. */
@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public class ContraptionEntityRendererMixin {
    @Unique
    private static final Set<Integer> SABLE$LOGGED_RENDER_DECISION =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Unique
    private static final Set<Integer> SABLE$LOGGED_RENDER =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Unique
    private static final Set<String> SABLE$LOGGED_RENDER_STAGE =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            if (SABLE$LOGGED_RENDER_DECISION.add(entity.getId())) {
                Sable.LOGGER.info("SABLE_M13_RENDER entityId={} containingSubLevel={} rawEntityPos={} "
                                + "sableWorldTransformKnown={} createRotationState={} renderPath={} "
                                + "renderedBlockCount={} result={}",
                        entity.getId(),
                        containing.getUniqueId(),
                        entity.position(),
                        containing instanceof ClientSubLevel,
                        entity.getRotationState(),
                        "hidden_vanilla_pass",
                        entity.getContraption().getBlocks().size(),
                        "suppressed_for_visible_bridge");
            }
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

        final Vec3 rawPosition = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
        final Vec3 visiblePosition = renderPose.transformPosition(rawPosition);
        sable$logRenderStage("RENDER_METHOD_ENTERED", entity, clientSubLevel, rawPosition, visiblePosition,
                entity.getBoundingBox(), poseStack, renderPose, partialTick, "create_renderer");
        sable$logRenderStage("CREATE_READY_STATE", entity, clientSubLevel, rawPosition, visiblePosition,
                entity.getBoundingBox(), poseStack, renderPose, partialTick, entity.isReadyForRender() ? "ready" : "not_ready");
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
        if (containing instanceof final ClientSubLevel clientSubLevel) {
            sable$logRender(entity, clientSubLevel, entity.position(),
                    returnedVisualizationSupported ? "flywheel" : "vanilla");
            sable$logRenderStage("GEOMETRY_EMISSION_REACHED", entity, clientSubLevel, entity.position(),
                    clientSubLevel.renderPose(partialTick).transformPosition(entity.position()),
                    entity.getBoundingBox(), poseStack, clientSubLevel.renderPose(partialTick), partialTick,
                    returnedVisualizationSupported ? "flywheel" : "vanilla");
        }
        return returnedVisualizationSupported;
    }

    @Unique
    private static void sable$logRender(final AbstractContraptionEntity entity,
                                        final ClientSubLevel subLevel,
                                        final Vec3 rawPosition,
                                        final String renderPath) {
        if (!SABLE$LOGGED_RENDER.add(entity.getId())) {
            return;
        }
        final Contraption contraption = entity.getContraption();
        Sable.LOGGER.info("SABLE_M13_RENDER entityId={} containingSubLevel={} rawEntityPos={} "
                        + "sableWorldTransformKnown={} createRotationState={} renderPath={} renderedBlockCount={} result={}",
                entity.getId(),
                subLevel.getUniqueId(),
                rawPosition,
                true,
                entity.getRotationState(),
                renderPath,
                contraption == null ? 0 : contraption.getBlocks().size(),
                "sable_create_renderer_bridge");
    }

    @Unique
    private static void sable$logRenderStage(final String stage,
                                             final AbstractContraptionEntity entity,
                                             final ClientSubLevel subLevel,
                                             final Vec3 rawPosition,
                                             final Vec3 visiblePosition,
                                             final AABB rawAabb,
                                             final PoseStack poseStack,
                                             final Pose3dc renderPose,
                                             final float partialTick,
                                             final String renderPath) {
        if (!SABLE$LOGGED_RENDER_STAGE.add(entity.getId() + ":" + stage)) {
            return;
        }
        final BoundingBox3d visibleAabb = new BoundingBox3d(rawAabb);
        visibleAabb.transform(subLevel.renderPose(partialTick), visibleAabb);
        final Contraption contraption = entity.getContraption();
        Sable.LOGGER.info("SABLE_M13_RENDER stage={} entityId={} containingSubLevel={} rawEntityPos={} "
                        + "visibleExpectedPos={} rawAabb={} visibleExpectedAabb={} createReady={} "
                        + "poseTranslationAtRendererEntry=({},{},{}) outerRotation={} createRotationState={} "
                        + "renderPath={} capturedBlocks={} result={}",
                stage,
                entity.getId(),
                subLevel.getUniqueId(),
                rawPosition,
                visiblePosition,
                rawAabb,
                visibleAabb.toMojang(),
                entity.isReadyForRender(),
                poseStack.last().pose().m30(),
                poseStack.last().pose().m31(),
                poseStack.last().pose().m32(),
                renderPose.orientation(),
                entity.getRotationState(),
                renderPath,
                contraption == null ? 0 : contraption.getBlocks().size(),
                "create_renderer_stage");
    }
}
