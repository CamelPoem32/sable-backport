package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionControllerLookup;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.createmod.catnip.animation.AnimationTickHolder;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Unique
    private static final Map<Integer, Float> SABLE$LAST_ENTITY_ANGLE = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Integer, Float> SABLE$LAST_BEARING_ANGLE = new ConcurrentHashMap<>();

    @Unique
    private static final Map<Integer, Integer> SABLE$ROTATION_SAMPLES = new ConcurrentHashMap<>();

    @Unique
    private static final Set<Integer> SABLE$LOGGED_CONTRAPTION_OWNERSHIP =
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
        SableM28NormalWorldCceSync.firstRender(entity);
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
            sable$traceControlledRotation(entity, clientSubLevel, partialTick, poseStack);
            sable$logContraptionGeometryOwnership(entity, clientSubLevel);
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
    private static void sable$logContraptionGeometryOwnership(final AbstractContraptionEntity entity,
                                                               final ClientSubLevel subLevel) {
        final Contraption contraption = entity.getContraption();
        if (contraption == null || contraption.anchor == null
                || !SABLE$LOGGED_CONTRAPTION_OWNERSHIP.add(entity.getId())) {
            return;
        }
        final List<BlockPos> capturedLocalPositions = contraption.getBlocks().values().stream()
                .map(info -> info.pos().immutable())
                .toList();
        final List<BlockPos> capturedSourcePlotPositions = contraption.getBlocks().values().stream()
                .map(info -> info.pos().offset(contraption.anchor).immutable())
                .toList();
        final List<String> capturedBlockStates = contraption.getBlocks().values().stream()
                .map(info -> info.state().toString())
                .toList();
        Sable.LOGGER.info("SABLE_M28_RENDER_OWNERSHIP path=CONTRAPTION subLevel={} "
                        + "activeContraptionEntityId={} controllerPos={} contraptionAnchor={} "
                        + "capturedBlockLocalPositions={} capturedSourcePlotPositions={} capturedBlockStates={} "
                        + "renderLayer=create_buffer geometryEmitted=true decision=ACTIVE_CONTRAPTION_OWNER",
                subLevel.getUniqueId(), entity.getId(), SableCreateContraptionContext.getControllerPos(entity),
                contraption.anchor, capturedLocalPositions, capturedSourcePlotPositions, capturedBlockStates);
    }

    @Unique
    private static void sable$traceControlledRotation(final AbstractContraptionEntity entity,
                                                       final ClientSubLevel subLevel, final float partialTick,
                                                       final PoseStack poseStack) {
        if (!(entity instanceof final ControlledContraptionEntity controlled)
                || controlled.getContraption() == null) {
            return;
        }
        final BlockPos controllerPos = ((ControlledContraptionEntityAccessor) controlled).sable$getControllerPos();
        final var controller = controllerPos == null ? null
                : SableCreateContraptionControllerLookup.getControllerBlockEntity(controlled.level(), controllerPos);
        final MechanicalBearingBlockEntity bearing = controller instanceof MechanicalBearingBlockEntity
                ? (MechanicalBearingBlockEntity) controller : null;
        final float entityAngle = controlled.getAngle(1.0F);
        final float bearingAngle = bearing == null ? Float.NaN : bearing.getInterpolatedAngle(1.0F);
        final Float previousEntity = SABLE$LAST_ENTITY_ANGLE.get(controlled.getId());
        final Float previousBearing = SABLE$LAST_BEARING_ANGLE.get(controlled.getId());
        if (previousEntity != null && Math.abs(entityAngle - previousEntity) < 1.0F
                && (bearing == null || previousBearing != null && Math.abs(bearingAngle - previousBearing) < 1.0F)) {
            return;
        }
        if (SABLE$ROTATION_SAMPLES.merge(controlled.getId(), 1, Integer::sum) > 24) {
            return;
        }
        SABLE$LAST_ENTITY_ANGLE.put(controlled.getId(), entityAngle);
        SABLE$LAST_BEARING_ANGLE.put(controlled.getId(), bearingAngle);
        final Matrix4f innerModel = new Matrix4f(controlled.getContraption()
                .getOrCreateClientContraptionLazy().getMatrices().getModel().last().pose());
        final Matrix4f outerAndDispatcher = new Matrix4f(poseStack.last().pose());
        final float createPartialTick = AnimationTickHolder.getPartialTicks();
        final Vector3f probe = new Vector3f(1.5F, 1.5F, 1.5F);
        final Vector3f innerProbe = innerModel.transformPosition(new Vector3f(probe));
        final Vector3f finalProbe = outerAndDispatcher.mul(innerModel).transformPosition(new Vector3f(probe));
        final Pose3dc outerPose = subLevel.renderPose(partialTick);
        Sable.LOGGER.info("SABLE_M28_RENDER_ROTATION side=client entityId={} subLevel={} controllerPos={} "
                        + "bearingPresent={} bearingSpeed={} bearingRunning={} bearingAnglePrevious={} "
                        + "bearingAngleCurrent={} entityAnglePrevious={} entityAngleCurrent={} "
                        + "entityInterpolatedAngle={} modelInterpolatedAngle={} rotationAxis={} "
                        + "rotationState={} partialTick={} createPartialTick={} "
                        + "rawAnchor={} visibleAnchor={} createPivot=(0.5,0.5,0.5) outerPivot={} outerRotation={} "
                        + "innerModel={} innerProbe={} cameraRelativeFinalProbe={} capturedBlocks={}",
                controlled.getId(), subLevel.getUniqueId(), controllerPos, bearing != null,
                bearing == null ? "unavailable" : bearing.getSpeed(),
                bearing != null && bearing.isRunning(), previousBearing, bearingAngle, previousEntity,
                entityAngle, controlled.getAngle(partialTick), controlled.getAngle(createPartialTick),
                controlled.getRotationAxis(), controlled.getRotationState(), partialTick, createPartialTick,
                controlled.getAnchorVec(),
                outerPose.transformPosition(controlled.getAnchorVec()), outerPose.rotationPoint(),
                outerPose.orientation(), innerModel, innerProbe, finalProbe,
                controlled.getContraption().getBlocks().size());
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
