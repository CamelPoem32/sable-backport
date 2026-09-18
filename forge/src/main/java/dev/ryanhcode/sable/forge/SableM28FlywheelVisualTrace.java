package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** M28.8 diagnostics for Create's independent Flywheel contraption visual. */
public final class SableM28FlywheelVisualTrace {
    public static final String SUPPRESS_PROPERTY = "sable.m28.suppressFlywheelTargetContraption";

    private static final float MIN_ANGLE_DELTA = 5.0F;
    private static final int MAX_TRANSFORM_SAMPLES = 24;
    private static final boolean TRACE = Boolean.getBoolean(SableM28VisualOwnershipTrace.TRACE_PROPERTY);
    private static final boolean SUPPRESS = Boolean.getBoolean(SUPPRESS_PROPERTY);
    private static final Map<Integer, TransformSample> LAST_TRANSFORMS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> TRANSFORM_COUNTS = new ConcurrentHashMap<>();
    private static final Set<Integer> SUPPRESSION_LOGGED = ConcurrentHashMap.newKeySet();

    private SableM28FlywheelVisualTrace() {
    }

    public static boolean suppressVisual(final Entity entity) {
        if (!SUPPRESS || !(entity instanceof final ControlledContraptionEntity controlled)) {
            return false;
        }
        final ClientSubLevel subLevel = containingClientSubLevel(controlled);
        if (subLevel == null) {
            return false;
        }
        if (SUPPRESSION_LOGGED.add(System.identityHashCode(entity))) {
            logLifecycle("CREATE_SUPPRESSED", controlled, null, null,
                    "visualizer=Create SimpleEntityVisualizer decision=ENTITY_STORAGE_WILL_ACCEPT_FALSE");
        }
        return true;
    }

    public static void logCreate(final AbstractContraptionEntity entity, final Object visual,
                                 final VisualizationContext context, final float partialTick,
                                 @Nullable final TransformedInstance structure) {
        if (!isTarget(entity)) {
            return;
        }
        logLifecycle("CREATE", entity, visual, context,
                "partialTick=" + partialTick
                        + " renderOrigin=" + context.renderOrigin()
                        + " structureIdentity=" + identity(structure)
                        + " structurePose=" + (structure == null ? "none" : structure.pose));
    }

    public static void logDelete(final AbstractContraptionEntity entity, final Object visual,
                                 @Nullable final TransformedInstance structure) {
        if (!isTarget(entity)) {
            return;
        }
        logLifecycle("DELETE", entity, visual, null,
                "structureIdentity=" + identity(structure));
        LAST_TRANSFORMS.remove(System.identityHashCode(visual));
        TRANSFORM_COUNTS.remove(System.identityHashCode(visual));
    }

    public static void logStructure(final AbstractContraptionEntity entity, final Object visual,
                                    final Object builder, final SimpleModel model) {
        if (!isTarget(entity)) {
            return;
        }
        final String blocks = capturedBlocks(entity);
        Sable.LOGGER.info("SABLE_M28_FLYWHEEL_STRUCTURE frame={} entityId={} visualIdentity={} "
                        + "contraptionIdentity={} capturedBlocks={} symmetricSailIncluded={} "
                        + "modelBuilderIdentity={} modelIdentity={} meshCount={} boundingSphere={} "
                        + "modelOrigin=CONTRAPTION_LOCAL threadName={}",
                SableM28VisualOwnershipTrace.currentFrame(), entity.getId(), System.identityHashCode(visual),
                identity(entity.getContraption()), blocks, blocks.contains("simulated:white_symmetric_sail"),
                System.identityHashCode(builder), System.identityHashCode(model), model.meshes().size(),
                model.boundingSphere(), Thread.currentThread().getName());
    }

    public static void logTransform(final ControlledContraptionEntity entity, final Object visual,
                                    final float partialTick, final Vec3i renderOrigin,
                                    final Matrix4f embeddingMatrix,
                                    @Nullable final TransformedInstance structure) {
        final ClientSubLevel subLevel = containingClientSubLevel(entity);
        if ((!TRACE && !SUPPRESS) || subLevel == null) {
            return;
        }
        final int visualIdentity = System.identityHashCode(visual);
        final float angle = entity.getAngle(partialTick);
        final TransformSample previous = LAST_TRANSFORMS.get(visualIdentity);
        final boolean bearingChanged = previous == null
                || Math.abs(net.minecraft.util.Mth.wrapDegrees(angle - previous.angle)) >= MIN_ANGLE_DELTA;
        final boolean transformChanged = previous == null || !previous.matrix.equals(embeddingMatrix, 1.0E-5F);
        if (!bearingChanged && !transformChanged) {
            return;
        }
        final int sample = TRANSFORM_COUNTS.merge(visualIdentity, 1, Integer::sum);
        if (sample > MAX_TRANSFORM_SAMPLES) {
            return;
        }
        LAST_TRANSFORMS.put(visualIdentity, new TransformSample(angle, new Matrix4f(embeddingMatrix)));

        final Pose3dc outerPose = subLevel.renderPose(partialTick);
        final Vec3 visiblePosition = outerPose.transformPosition(entity.position());
        final String camera = RenderSystem.isOnRenderThread()
                ? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toString()
                : "UNAVAILABLE_OFF_RENDER_THREAD";
        Sable.LOGGER.info("SABLE_M28_FLYWHEEL_TRANSFORM frame={} sample={} entityId={} visualIdentity={} "
                        + "rawPlotPosition={} sableVisibleEntityPosition={} outerSablePosition={} "
                        + "outerSableRotation={} bearingAngle={} bearingAxis={} createRotationState={} "
                        + "partialTick={} flywheelEmbeddingMatrix={} structureIdentity={} structurePose={} "
                        + "renderOrigin={} cameraPosition={} bearingRotationChanged={} "
                        + "flywheelTransformChanged={} threadName={} renderThread={}",
                SableM28VisualOwnershipTrace.currentFrame(), sample, entity.getId(), visualIdentity,
                entity.position(), visiblePosition, outerPose.position(), outerPose.orientation(), angle,
                entity.getRotationAxis(), entity.getRotationState(), partialTick, embeddingMatrix,
                identity(structure), structure == null ? "none" : structure.pose, renderOrigin, camera,
                bearingChanged, transformChanged, Thread.currentThread().getName(), RenderSystem.isOnRenderThread());
    }

    private static boolean isTarget(final AbstractContraptionEntity entity) {
        return (TRACE || SUPPRESS) && entity instanceof ControlledContraptionEntity
                && containingClientSubLevel(entity) != null;
    }

    private static @Nullable ClientSubLevel containingClientSubLevel(final AbstractContraptionEntity entity) {
        return SableCreateContraptionContext.getContainingSubLevel(entity) instanceof final ClientSubLevel subLevel
                ? subLevel : null;
    }

    private static void logLifecycle(final String event, final AbstractContraptionEntity entity,
                                     @Nullable final Object visual,
                                     @Nullable final VisualizationContext context,
                                     final String details) {
        final ClientSubLevel subLevel = containingClientSubLevel(entity);
        Sable.LOGGER.info("SABLE_M28_FLYWHEEL_VISUAL_LIFECYCLE event={} entityId={} UUID={} "
                        + "identityHashCode={} entityClass={} rawEntityPosition={} containingSableSubLevel={} "
                        + "controllerPosition={} capturedBlockCount={} capturedBlockStates={} visualIdentity={} "
                        + "visualizerClass={} visualizationLevelIdentity={} visualizationContextIdentity={} "
                        + "threadName={} threadIsRenderThread={} {}",
                event, entity.getId(), entity.getUUID(), System.identityHashCode(entity),
                entity.getClass().getName(), entity.position(),
                subLevel == null ? "none" : subLevel.getUniqueId(),
                SableCreateContraptionContext.getControllerPos(entity),
                entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size(),
                capturedBlocks(entity), identity(visual), "com.simibubi.create.content.contraptions.render.ContraptionVisual",
                System.identityHashCode(entity.level()), identity(context), Thread.currentThread().getName(),
                RenderSystem.isOnRenderThread(), details);
    }

    private static String capturedBlocks(final AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) {
            return "[]";
        }
        final StringBuilder result = new StringBuilder("[");
        int count = 0;
        for (final Map.Entry<BlockPos, StructureBlockInfo> entry : entity.getContraption().getBlocks().entrySet()) {
            if (count++ > 0) {
                result.append(',');
            }
            if (count > 32) {
                result.append("...");
                break;
            }
            result.append(entry.getKey()).append('=')
                    .append(BuiltInRegistries.BLOCK.getKey(entry.getValue().state().getBlock()));
        }
        return result.append(']').toString();
    }

    private static String identity(@Nullable final Object value) {
        return value == null ? "none" : Integer.toString(System.identityHashCode(value));
    }

    private record TransformSample(float angle, Matrix4f matrix) {
    }
}
