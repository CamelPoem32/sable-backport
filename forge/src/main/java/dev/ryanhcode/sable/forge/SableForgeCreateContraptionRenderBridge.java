package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs Create's own contraption entity renderer for Sable-contained contraptions whose raw entity
 * coordinates live in hidden plot storage and therefore never reach vanilla's visible entity pass.
 */
final class SableForgeCreateContraptionRenderBridge {
    private static final Set<String> LOGGED_RENDER_STAGE =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Integer, Integer> LOGGED_M15_INTERP_SAMPLES = new ConcurrentHashMap<>();
    private static final Map<Integer, Vec3> LAST_M15_VISIBLE_ANCHOR = new ConcurrentHashMap<>();

    private SableForgeCreateContraptionRenderBridge() {
    }

    static void render(final RenderLevelStageEvent event, final ClientLevel level, final Vec3 cameraPosition) {
        final Minecraft minecraft = Minecraft.getInstance();
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        final float partialTick = event.getPartialTick();

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
            final Vec3 visiblePosition = clientSubLevel.renderPose(partialTick).transformPosition(rawPosition);
            final AABB rawAabb = renderCullingAabb(contraptionEntity, rawPosition);
            final AABB visibleAabb = visibleAabb(clientSubLevel, rawAabb, partialTick);
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

            final PoseStack poseStack = event.getPoseStack();
            logRenderStage("DISPATCH_BEGIN", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    true, true, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
            logGantryInterpolationSample(contraptionEntity, partialTick, rawPosition, visiblePosition,
                    cameraPosition, x, y, z);
            poseStack.pushPose();
            dispatcher.render(contraptionEntity, x, y, z, contraptionEntity.getYRot(), partialTick,
                    poseStack, bufferSource, packedLight);
            poseStack.popPose();
            logRenderStage("DISPATCH_END", contraptionEntity, clientSubLevel, renderer, event,
                    rawPosition, visiblePosition, cameraPosition, rawAabb, visibleAabb,
                    true, true, oldRawDispatcherX, oldRawDispatcherY, oldRawDispatcherZ,
                    x, y, z);
        }
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
