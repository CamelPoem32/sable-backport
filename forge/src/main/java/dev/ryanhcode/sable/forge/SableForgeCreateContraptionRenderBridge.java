package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs Create's own contraption entity renderer for Sable-contained contraptions whose raw entity
 * coordinates live in hidden plot storage and therefore never reach vanilla's visible entity pass.
 */
public final class SableForgeCreateContraptionRenderBridge {
    private static final boolean ENTITY_PHASE_AB = Boolean.getBoolean("sable.m28.entityPhaseAB");
    private static final boolean SUPPRESS_DYNAMIC_CONTRAPTION =
            Boolean.getBoolean("sable.m28.suppressDynamicContraption");
    private static final BufferBuilder SCOPED_ENTITY_BUILDER = new BufferBuilder(256);
    private static final MultiBufferSource.BufferSource SCOPED_ENTITY_BUFFER_SOURCE =
            MultiBufferSource.immediate(SCOPED_ENTITY_BUILDER);
    private static final Set<String> LOGGED_RENDER_STAGE =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Integer, Boolean> LAST_CONTROL_FRUSTUM_RESULT = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> LOGGED_CONTROL_BOUNDS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> LOGGED_M15_INTERP_SAMPLES = new ConcurrentHashMap<>();
    private static final Map<Integer, Vec3> LAST_M15_VISIBLE_ANCHOR = new ConcurrentHashMap<>();
    private static final Set<Integer> LOGGED_ENTITY_PHASE_AB =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_DYNAMIC_SUPPRESSION =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static boolean entityPhaseRendered;

    private SableForgeCreateContraptionRenderBridge() {
    }

    public static void beginRenderFrame() {
        entityPhaseRendered = false;
        SableM28BatchTrace.beginFrame();
        SableM28VisualOwnershipTrace.beginFrame(SableM28BatchTrace.currentFrame());
    }

    public static String activeMode() {
        return ENTITY_PHASE_AB ? "ENTITY_PHASE_BRIDGE" : "FORGE_STAGE_BRIDGE";
    }

    static void render(final RenderLevelStageEvent event, final ClientLevel level, final Vec3 cameraPosition) {
        if (ENTITY_PHASE_AB) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final float partialTick = event.getPartialTick();
        int renderedEntities = 0;

        SableM28BatchTrace.beginScopedBatch(SCOPED_ENTITY_BUFFER_SOURCE, event.getStage().toString());
        try {
            for (final Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof final AbstractContraptionEntity contraptionEntity)) {
                    continue;
                }

                final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(contraptionEntity);
                if (!(containing instanceof final ClientSubLevel clientSubLevel)) {
                    continue;
                }
                if (!SableCreateContraptionContext.isRawEntityInSubLevelPlot(contraptionEntity, clientSubLevel)) {
                    continue;
                }

            final EntityRenderer<? super AbstractContraptionEntity> renderer = dispatcher.getRenderer(contraptionEntity);
            final Vec3 rawPosition = interpolatedPosition(contraptionEntity, partialTick);
            final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
            final Vec3 visiblePosition = renderPose.transformPosition(rawPosition);
            final AABB rawAabb = renderCullingAabb(contraptionEntity, rawPosition);
            final AABB oldVisibleAabb = visibleAabb(clientSubLevel, rawAabb, partialTick);
            final EightCornerTransformedBounds.Result transformedBounds =
                    transformedContraptionBounds(contraptionEntity, rawPosition, renderPose, partialTick);
            final AABB visibleAabb = transformedBounds == null
                    ? oldVisibleAabb : toAabb(transformedBounds.bounds()).inflate(0.5);
            final double oldRawDispatcherX = rawPosition.x - cameraPosition.x;
            final double oldRawDispatcherY = rawPosition.y - cameraPosition.y;
            final double oldRawDispatcherZ = rawPosition.z - cameraPosition.z;
            final double x = visiblePosition.x - cameraPosition.x;
            final double y = visiblePosition.y - cameraPosition.y;
            final double z = visiblePosition.z - cameraPosition.z;
            final boolean distancePass = renderer != null
                    && contraptionEntity.shouldRenderAtSqrDistance(visiblePosition.distanceToSqr(cameraPosition));
            final Frustum frustum = event.getFrustum();
            final boolean frustumPass = frustum == null || frustum.isVisible(visibleAabb);

            logControlledBounds(contraptionEntity, clientSubLevel, renderPose, partialTick,
                    rawAabb, oldVisibleAabb, visibleAabb, transformedBounds, cameraPosition,
                    frustum, distancePass, frustumPass);

            logRenderStage("CLIENT_ENTITY_EXISTS", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    distancePass, frustumPass, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
            if (!distancePass) {
                continue;
            }
            logRenderStage("VISIBLE_DISTANCE_PASS", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    true, frustumPass, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
            if (!frustumPass) {
                continue;
            }
            logRenderStage("VISIBLE_FRUSTUM_PASS", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    true, true, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
            final int packedLight = dispatcher.getPackedLightCoords(contraptionEntity, partialTick);

            if (suppressDynamicContraption(contraptionEntity, clientSubLevel, event.getStage().toString())) {
                continue;
            }

            final PoseStack poseStack = event.getPoseStack();
            logRenderStage("DISPATCH_BEGIN", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    true, true, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
            logGantryInterpolationSample(contraptionEntity, partialTick, rawPosition, visiblePosition,
                    cameraPosition, x, y, z);
                SableM28BatchTrace.beginDispatch(
                        SCOPED_ENTITY_BUFFER_SOURCE, contraptionEntity.getId(), event.getStage().toString());
                poseStack.pushPose();
                try (SableM28VisualOwnershipTrace.Scope ignored = SableM28VisualOwnershipTrace.enter(
                        SableM28VisualOwnershipTrace.Owner.SABLE_FORGE_STAGE_BRIDGE)) {
                    dispatcher.render(contraptionEntity, x, y, z, contraptionEntity.getYRot(), partialTick,
                            poseStack, SCOPED_ENTITY_BUFFER_SOURCE, packedLight);
                    renderedEntities++;
                } finally {
                    poseStack.popPose();
                    SableM28BatchTrace.endDispatch();
                }
                logRenderStage("DISPATCH_END", contraptionEntity, clientSubLevel, renderer, event,
                        rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                        true, true, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                        x, y, z);
            }
        } finally {
            SableM28BatchTrace.beforeScopedFlush(SCOPED_ENTITY_BUFFER_SOURCE, renderedEntities);
            SCOPED_ENTITY_BUFFER_SOURCE.endBatch();
            SableM28BatchTrace.afterScopedFlush(SCOPED_ENTITY_BUFFER_SOURCE, renderedEntities);
        }
    }

    /** Diagnostic A/B: dispatches contained Create entities from vanilla's active entity phase. */
    public static void renderEntityPhase(final double cameraX, final double cameraY, final double cameraZ,
                                         final float partialTick, final PoseStack poseStack,
                                         final MultiBufferSource bufferSource) {
        if (!ENTITY_PHASE_AB || entityPhaseRendered) {
            return;
        }
        entityPhaseRendered = true;
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        final Vec3 cameraPosition = new Vec3(cameraX, cameraY, cameraZ);
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final Frustum frustum = minecraft.levelRenderer.getFrustum();
        for (final Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof final AbstractContraptionEntity contraptionEntity)) {
                continue;
            }
            final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(contraptionEntity);
            if (!(containing instanceof final ClientSubLevel clientSubLevel)
                    || !SableCreateContraptionContext.isRawEntityInSubLevelPlot(contraptionEntity, clientSubLevel)) {
                continue;
            }
            final EntityRenderer<? super AbstractContraptionEntity> renderer = dispatcher.getRenderer(contraptionEntity);
            final Vec3 rawPosition = interpolatedPosition(contraptionEntity, partialTick);
            final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
            final Vec3 visiblePosition = renderPose.transformPosition(rawPosition);
            final AABB rawAabb = renderCullingAabb(contraptionEntity, rawPosition);
            final EightCornerTransformedBounds.Result transformedBounds =
                    transformedContraptionBounds(contraptionEntity, rawPosition, renderPose, partialTick);
            final AABB visibleAabb = transformedBounds == null
                    ? visibleAabb(clientSubLevel, rawAabb, partialTick)
                    : toAabb(transformedBounds.bounds()).inflate(0.5);
            final boolean distancePass = renderer != null
                    && contraptionEntity.shouldRenderAtSqrDistance(visiblePosition.distanceToSqr(cameraPosition));
            final boolean frustumPass = frustum == null || frustum.isVisible(visibleAabb);
            if (!distancePass || !frustumPass) {
                continue;
            }
            if (suppressDynamicContraption(contraptionEntity, clientSubLevel, "ENTITY_PHASE_BRIDGE")) {
                continue;
            }
            final double x = visiblePosition.x - cameraX;
            final double y = visiblePosition.y - cameraY;
            final double z = visiblePosition.z - cameraZ;
            final int packedLight = dispatcher.getPackedLightCoords(contraptionEntity, partialTick);
            final MultiBufferSource.BufferSource tracedSource = bufferSource instanceof MultiBufferSource.BufferSource found
                    ? found : null;
            if (tracedSource != null) {
                SableM28BatchTrace.beginDispatch(tracedSource, contraptionEntity.getId(), "ENTITY_PHASE_BRIDGE");
            }
            poseStack.pushPose();
            try (SableM28VisualOwnershipTrace.Scope ignored = SableM28VisualOwnershipTrace.enter(
                    SableM28VisualOwnershipTrace.Owner.SABLE_ENTITY_PHASE_BRIDGE)) {
                dispatcher.render(contraptionEntity, x, y, z, contraptionEntity.getYRot(), partialTick,
                        poseStack, bufferSource, packedLight);
            } finally {
                poseStack.popPose();
                if (tracedSource != null) {
                    SableM28BatchTrace.endDispatch();
                }
            }
            if (LOGGED_ENTITY_PHASE_AB.add(contraptionEntity.getId())) {
                Sable.LOGGER.info("SABLE_M28_ENTITY_PHASE_AB mode=ENTITY_PHASE_BRIDGE frame={} entityId={} "
                                + "insertionPoint=LevelRenderer.renderEntity_HEAD bufferSourceClass={} "
                                + "bufferSourceIdentity={} consumerLifecycle=NORMAL_ENTITY_PHASE "
                                + "finalizationObserved=RUNTIME_PENDING drawObserved=RUNTIME_PENDING "
                                + "playerVisibleResult=MANUAL_RUNTIME_REQUIRED",
                        SableM28BatchTrace.currentFrame(), contraptionEntity.getId(),
                        bufferSource.getClass().getName(), System.identityHashCode(bufferSource));
            }
        }
    }

    private static boolean suppressDynamicContraption(final AbstractContraptionEntity entity,
                                                       final ClientSubLevel subLevel,
                                                       final String renderStage) {
        if (!SUPPRESS_DYNAMIC_CONTRAPTION || !(entity instanceof ControlledContraptionEntity)) {
            return false;
        }
        final String key = entity.getId() + ":" + renderStage;
        if (LOGGED_DYNAMIC_SUPPRESSION.add(key)) {
            Sable.LOGGER.info("SABLE_M28_VISIBLE_OWNER source=CREATE_CONTRAPTION subLevel={} "
                            + "entityId={} sourcePosition=contraption_blocks renderStage={} "
                            + "drawReached=false suppressionFlag=sable.m28.suppressDynamicContraption "
                            + "ownershipDecision=DIAGNOSTIC_DYNAMIC_SUPPRESSION",
                    subLevel.getUniqueId(), entity.getId(), renderStage);
        }
        return true;
    }

    private static Vec3 interpolatedPosition(final Entity entity, final float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    private static AABB renderCullingAabb(final Entity entity, final Vec3 rawPosition) {
        AABB rawAabb = entity.getBoundingBoxForCulling().inflate(0.5);
        if (rawAabb.hasNaN() || rawAabb.getSize() == 0.0) {
            rawAabb = new AABB(
                    rawPosition.x - 2.0,
                    rawPosition.y - 2.0,
                    rawPosition.z - 2.0,
                    rawPosition.x + 2.0,
                    rawPosition.y + 2.0,
                    rawPosition.z + 2.0);
        }
        return rawAabb;
    }

    private static AABB visibleAabb(final ClientSubLevel subLevel, final AABB rawAabb, final float partialTick) {
        final BoundingBox3d visibleAabb = new BoundingBox3d(rawAabb);
        visibleAabb.transform(subLevel.renderPose(partialTick), visibleAabb);
        return visibleAabb.toMojang();
    }

    private static EightCornerTransformedBounds.Result transformedContraptionBounds(
            final AbstractContraptionEntity entity, final Vec3 rawPosition,
            final Pose3dc renderPose, final float partialTick) {
        final Contraption contraption = entity.getContraption();
        if (contraption == null || contraption.bounds == null) {
            return null;
        }
        final AABB local = contraption.bounds;
        final Vec3 interpolationOffset = rawPosition.subtract(entity.position());
        try {
            return EightCornerTransformedBounds.transform(
                    new EightCornerTransformedBounds.Bounds(local.minX, local.minY, local.minZ,
                            local.maxX, local.maxY, local.maxZ),
                    corner -> {
                        final Vec3 raw = entity.toGlobalVector(new Vec3(corner.x(), corner.y(), corner.z()),
                                partialTick).add(interpolationOffset);
                        return new EightCornerTransformedBounds.Point(raw.x, raw.y, raw.z);
                    },
                    corner -> {
                        final Vec3 visible = renderPose.transformPosition(new Vec3(corner.x(), corner.y(), corner.z()));
                        return new EightCornerTransformedBounds.Point(visible.x, visible.y, visible.z);
                    });
        } catch (final IllegalArgumentException invalidBounds) {
            return null;
        }
    }

    private static AABB toAabb(final EightCornerTransformedBounds.Bounds bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static void logControlledBounds(final AbstractContraptionEntity entity,
                                            final ClientSubLevel subLevel, final Pose3dc renderPose,
                                            final float partialTick, final AABB rawAabb,
                                            final AABB oldVisibleAabb, final AABB visibleAabb,
                                            final EightCornerTransformedBounds.Result transformedBounds,
                                            final Vec3 cameraPosition, final Frustum frustum,
                                            final boolean distancePass, final boolean frustumPass) {
        if (!(entity instanceof final ControlledContraptionEntity controlled)) {
            return;
        }
        final Boolean previous = LAST_CONTROL_FRUSTUM_RESULT.put(entity.getId(), frustumPass);
        if ((previous != null && previous == frustumPass)
                || LOGGED_CONTROL_BOUNDS.merge(entity.getId(), 1, Integer::sum) > 6) {
            return;
        }
        final Contraption contraption = entity.getContraption();
        Sable.LOGGER.info("SABLE_M13_CONTROL_BOUNDS entityId={} containingSubLevel={} outerRotation={} "
                        + "createRotation={} bearingAngle={} bearingAxis={} partialTick={} "
                        + "rawAabb={} localAabb={} localCorners={} "
                        + "rawCorners={} visibleCorners={} oldVisibleAabb={} rebuiltVisibleAabb={} boundsDiffer={} "
                        + "cameraPos={} oldFrustumPass={} rebuiltFrustumPass={} distancePass={} capturedBlocks={}",
                entity.getId(), subLevel.getUniqueId(), renderPose.orientation(), entity.getRotationState(),
                controlled.getAngle(partialTick), controlled.getRotationAxis(), partialTick,
                rawAabb, contraption == null ? "unavailable" : contraption.bounds,
                transformedBounds == null ? "unavailable" : Arrays.toString(transformedBounds.localCorners()),
                transformedBounds == null ? "unavailable" : Arrays.toString(transformedBounds.rawCorners()),
                transformedBounds == null ? "unavailable" : Arrays.toString(transformedBounds.visibleCorners()),
                oldVisibleAabb, visibleAabb, !oldVisibleAabb.equals(visibleAabb), cameraPosition,
                frustum == null || frustum.isVisible(oldVisibleAabb), frustumPass, distancePass,
                contraption == null ? 0 : contraption.getBlocks().size());
    }

    private static void logRenderStage(final String stage,
                                       final AbstractContraptionEntity entity,
                                       final ClientSubLevel subLevel,
                                       final EntityRenderer<? super AbstractContraptionEntity> renderer,
                                       final RenderLevelStageEvent event,
                                       final Vec3 rawPosition,
                                       final Vec3 visiblePosition,
                                       final Vec3 cameraPosition,
                                       final AABB rawAabb,
                                       final AABB visibleAabb,
                                       final boolean distancePass,
                                       final boolean frustumPass,
                                       final double oldRawDispatcherX,
                                       final double oldRawDispatcherY,
                                       final double oldRawDispatcherZ,
                                       final double dispatcherX,
                                       final double dispatcherY,
                                       final double dispatcherZ) {
        if (!LOGGED_RENDER_STAGE.add(entity.getId() + ":" + stage)) {
            return;
        }

        Sable.LOGGER.info("SABLE_M13_RENDER stage={} entityId={} containingSubLevel={} "
                        + "rawEntityPos={} interpolatedRawPos={} visibleExpectedPos={} interpolatedVisiblePos={} "
                        + "cameraPos={} oldRawDispatcherXYZ=({},{},{}) newVisibleDispatcherXYZ=({},{},{}) "
                        + "rawAabb={} visibleExpectedAabb={} rendererFound={} rendererClass={} "
                        + "visibleDistancePass={} visibleFrustumPass={} renderPath=forge_stage_bridge "
                        + "createAngle={} capturedBlocks={}",
                stage,
                entity.getId(),
                subLevel.getUniqueId(),
                rawPosition,
                rawPosition,
                visiblePosition,
                visiblePosition,
                cameraPosition,
                oldRawDispatcherX,
                oldRawDispatcherY,
                oldRawDispatcherZ,
                dispatcherX,
                dispatcherY,
                dispatcherZ,
                rawAabb,
                visibleAabb,
                renderer != null,
                renderer == null ? "none" : renderer.getClass().getName(),
                distancePass,
                frustumPass,
                entity.getRotationState(),
                entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size());
    }

    private static void logGantryInterpolationSample(final AbstractContraptionEntity entity,
                                                     final float partialTick,
                                                     final Vec3 rawPosition,
                                                     final Vec3 visiblePosition,
                                                     final Vec3 cameraPosition,
                                                     final double dispatcherX,
                                                     final double dispatcherY,
                                                     final double dispatcherZ) {
        if (!entity.getClass().getName().contains("GantryContraptionEntity")) {
            return;
        }
        final int sample = LOGGED_M15_INTERP_SAMPLES.merge(entity.getId(), 1, Integer::sum);
        if (sample > 12) {
            return;
        }
        final Vec3 previousVisible = LAST_M15_VISIBLE_ANCHOR.put(entity.getId(), visiblePosition);
        final double visibleDelta = previousVisible == null ? 0.0 : visiblePosition.distanceTo(previousVisible);
        Sable.LOGGER.info("SABLE_M15_INTERP sample={} entityId={} partialTick={} axisMotion={} clientOffsetDiff={} "
                        + "movementAxis={} rawPrevious=({},{},{}) rawCurrent={} interpolatedRaw={} "
                        + "cameraPos={} visibleAnchor={} deltaFromPreviousVisibleAnchor={} "
                        + "dispatcherXYZ=({},{},{}) hiddenPlotPoseTranslation=false",
                sample,
                entity.getId(),
                partialTick,
                readFieldRaw(entity, "axisMotion"),
                readFieldRaw(entity, "clientOffsetDiff"),
                readFieldRaw(entity, "movementAxis"),
                entity.xOld,
                entity.yOld,
                entity.zOld,
                entity.position(),
                rawPosition,
                cameraPosition,
                visiblePosition,
                visibleDelta,
                dispatcherX,
                dispatcherY,
                dispatcherZ);
    }

    private static Object readFieldRaw(final Object target, final String fieldName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (final NoSuchFieldException ignored) {
                // Gantry interpolation fields live on the concrete Create entity class.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return "unavailable";
            }
        }
        return "unavailable";
    }
}
