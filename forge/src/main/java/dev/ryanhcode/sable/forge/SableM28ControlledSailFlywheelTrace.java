package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** M28.10 observations only: never changes a transform or requests a kinetic update. */
public final class SableM28ControlledSailFlywheelTrace {
    public static final String TRACE_PROPERTY = "sable.m28.traceControlledSailFlywheel";
    public static final String SUPPRESS_PROPERTY = "sable.m28.suppressControlledSailFlywheel";
    public static final String OVERLAY_PROPERTY = "sable.m28.showControlledSailFlywheelOverlay";
    private static final boolean ENABLED = Boolean.getBoolean(SableM28VisualOwnershipTrace.TRACE_PROPERTY)
            && (Boolean.getBoolean(TRACE_PROPERTY) || Boolean.getBoolean(SUPPRESS_PROPERTY));
    private static final Map<ControlledContraptionEntity, Sample> ENTITIES = new WeakHashMap<>();
    private static final Map<Object, Update> EMBEDDINGS = new IdentityHashMap<>();

    private SableM28ControlledSailFlywheelTrace() { }

    public static boolean enabled() { return ENABLED; }

    // Resolve ownership from the actual controller, not a nearby bearing or a remembered runtime ID.
    private static boolean candidate(final ControlledContraptionEntity entity) {
        if (!ENABLED || entity.isRemoved() || SableCreateContraptionContext.getContainingSubLevel(entity) != null
                || entity.getContraption() == null) {
            return false;
        }
        final BlockPos controller = SableCreateContraptionContext.getControllerPos(entity);
        return controller != null && entity.level().getBlockEntity(controller)
                instanceof MechanicalBearingBlockEntity
                && entity.getContraption().getBlocks().values().stream().anyMatch(block ->
                BuiltInRegistries.BLOCK.getKey(block.state().getBlock()).toString()
                        .equals("simulated:white_symmetric_sail"));
    }

    public static synchronized void observe(final AbstractContraptionEntity raw, final float partialTick) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !candidate(entity)) { return; }
        final Sample sample = ENTITIES.computeIfAbsent(entity, ignored -> new Sample());
        final float angle = entity.getAngle(partialTick);
        final float previous = sample.angle;
        final long frame = SableM28BatchTrace.currentFrame();
        sample.moved |= sample.frame != frame && Float.isFinite(previous)
                && Math.abs(Mth.wrapDegrees(angle - previous)) > 0.01F;
        sample.angle = angle;
        sample.frame = frame;
        // Admission happens before first motion. Remove an already admitted visual once the semantic target is proven.
        if (sample.moved && Boolean.getBoolean(SUPPRESS_PROPERTY) && !sample.removalQueued
                && RenderSystem.isOnRenderThread()) {
            final VisualizationManager manager = VisualizationManager.get(entity.level());
            if (manager != null) {
                sample.removalQueued = true;
                manager.entities().queueRemove(entity);
                Sable.LOGGER.info("SABLE_M30_FLYWHEEL_SUPPRESSION entityId={} uuid={} "
                                + "decision=QUEUE_REMOVE_PRE_MOTION_ADMISSION cpuFallbackGuaranteed=false",
                        entity.getId(), entity.getUUID());
            }
        }
        if (sample.moved && (sample.logs == 0 || Math.abs(Mth.wrapDegrees(angle - sample.loggedAngle)) >= 5.0F)
                && sample.logs++ < 80) {
            sample.loggedAngle = angle;
            Sable.LOGGER.info("SABLE_M30_CONTROLLED_SAIL_TARGET frame={} entityId={} uuid={} entityIdentity={} "
                            + "containingSubLevel=none controllerPos={} rawPosition={} capturedStates={} "
                            + "previousAngle={} currentAngle={} interpolatedAngle={} partialTick={} "
                            + "reasons=NORMAL_WORLD,MECHANICAL_BEARING_CONTROLLER,SYMMETRIC_SAIL,OBSERVED_ROTATION",
                    SableM28BatchTrace.currentFrame(), entity.getId(), entity.getUUID(), System.identityHashCode(entity),
                    SableCreateContraptionContext.getControllerPos(entity), entity.position(),
                    capturedStates(entity), previous, entity.getAngle(1.0F), angle, partialTick);
        }
    }

    public static synchronized boolean suppress(final Entity raw) {
        if (!Boolean.getBoolean(SUPPRESS_PROPERTY) || !(raw instanceof final ControlledContraptionEntity entity)
                || !candidate(entity)) { return false; }
        observe(entity, 1.0F);
        final Sample sample = ENTITIES.get(entity);
        if (!sample.moved) { return false; }
        if (!sample.suppressionLogged) {
            sample.suppressionLogged = true;
            Sable.LOGGER.info("SABLE_M30_FLYWHEEL_SUPPRESSION entityId={} uuid={} decision=OBSERVED_NORMAL_WORLD_CONTROLLED_SAIL "
                            + "cpuFallbackGuaranteed=false", entity.getId(), entity.getUUID());
        }
        return true;
    }

    public static synchronized void lifecycle(final AbstractContraptionEntity raw, final Object visual,
                                               final String event) {
        if (event.equals("DELETE")) { EMBEDDINGS.values().removeIf(update -> update.visual == visual); }
        if (!(raw instanceof final ControlledContraptionEntity entity) || !candidate(entity)) { return; }
        observe(entity, 1.0F);
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM event={} entityId={} uuid={} visualIdentity={} "
                        + "capturedStates={} thread={} renderThread={}", event, entity.getId(), entity.getUUID(),
                System.identityHashCode(visual), capturedStates(entity),
                Thread.currentThread().getName(), RenderSystem.isOnRenderThread());
    }

    // Register the exact VisualEmbedding before its production transforms() call.
    public static synchronized void begin(final AbstractContraptionEntity raw, final Object visual,
                                          final VisualEmbedding embedding, final float partialTick,
                                          final Vec3i origin) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !candidate(entity)) { return; }
        observe(entity, partialTick);
        EMBEDDINGS.values().removeIf(update -> update.entity.isRemoved() || update.entity.level() != entity.level());
        if (!EMBEDDINGS.containsKey(embedding) && EMBEDDINGS.size() >= 32) { return; }
        if (!EMBEDDINGS.containsKey(embedding)) {
            Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM event=EMBEDDING_BIND entityId={} visualIdentity={} "
                            + "embeddingIdentity={} embeddingClass={} levelClass={} levelIdentity={} "
                            + "origin={} thread={}", entity.getId(), System.identityHashCode(visual),
                    System.identityHashCode(embedding), embedding.getClass().getName(), entity.level().getClass().getName(),
                    System.identityHashCode(entity.level()), origin, Thread.currentThread().getName());
        }
        final Update update = EMBEDDINGS.computeIfAbsent(embedding, ignored -> new Update(entity, visual, embedding));
        update.partialTick = partialTick;
        update.origin = origin;
        final long frame = SableM28BatchTrace.currentFrame();
        update.invocations = update.frame == frame ? update.invocations + 1 : 1;
        update.frame = frame;
        update.pending = true;
    }

    public static synchronized void storedBefore(final Object embedding, final Matrix4fc matrix) {
        final Update update = EMBEDDINGS.get(embedding);
        if (update != null && update.pending) { update.before.set(matrix); }
    }

    public static synchronized void storedAfter(final Object embedding, final Matrix4fc matrix) {
        final Update update = EMBEDDINGS.get(embedding);
        if (update == null || !update.pending) { return; }
        update.pending = false;
        update.actual.set(matrix);
        final Sample sample = ENTITIES.get(update.entity);
        final float angle = update.entity.getAngle(update.partialTick);
        if (!sample.moved || update.logs >= 80 || (update.logs > 0
                && Math.abs(Mth.wrapDegrees(angle - update.loggedAngle)) < 5.0F
                && update.frame - update.loggedFrame < 60)) { return; }
        update.logs++;
        final float previousAngle = update.loggedAngle;
        update.loggedAngle = angle;
        update.loggedFrame = update.frame;
        final Matrix4f expected = expected(update);
        final Vector3f point = new Vector3f(1.25F, 0.75F, -0.25F);
        final Vector3f actualProbe = update.actual.transformPosition(new Vector3f(point));
        final Vector3f expectedProbe = expected.transformPosition(new Vector3f(point));
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM frame={} entityId={} uuid={} visualIdentity={} embeddingIdentity={} "
                        + "partialTick={} previousAngle={} currentAngle={} entityInterpolatedAngle={} bearingAngle={} axis={} rotationState={} position={} renderOrigin={} before={} after={} "
                        + "previousSampleMatrix={} angleChanged={} embeddingChanged={} transformedProbe={} "
                        + "updateInvocationCount={} thread={}", update.frame, update.entity.getId(), update.entity.getUUID(),
                System.identityHashCode(update.visual), System.identityHashCode(embedding), update.partialTick,
                update.entity.getAngle(0.0F), update.entity.getAngle(1.0F), angle, bearingAngle(update.entity, update.partialTick),
                update.entity.getRotationAxis(),
                update.entity.getRotationState(), update.entity.position(), update.origin, update.before, update.actual, update.previous,
                Math.abs(Mth.wrapDegrees(angle - previousAngle)) > 0.01F,
                delta(update.previous, update.actual) > 0.00001F, actualProbe, update.invocations,
                Thread.currentThread().getName());
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_EXPECTED frame={} entityId={} actualEmbedding={} expectedMatrix={} "
                        + "maxMatrixDelta={} actualProbe={} expectedProbe={} probeDelta={} bearingAngle={} entityInterpolatedAngle={}",
                update.frame, update.entity.getId(), update.actual, expected, delta(update.actual, expected),
                actualProbe, expectedProbe, actualProbe.distance(expectedProbe), bearingAngle(update.entity, update.partialTick), angle);
        update.previous.set(update.actual);
        update.stageLog = true;
        update.uniformLog = true;
    }

    private static Matrix4f expected(final Update update) {
        final ControlledContraptionEntity entity = update.entity;
        final float partial = update.partialTick;
        final PoseStack reference = new PoseStack();
        reference.translate((entity.isPrevPosInvalid() ? entity.getX() : Mth.lerp(partial, entity.xo, entity.getX())) - update.origin.getX(),
                (entity.isPrevPosInvalid() ? entity.getY() : Mth.lerp(partial, entity.yo, entity.getY())) - update.origin.getY(),
                (entity.isPrevPosInvalid() ? entity.getZ() : Mth.lerp(partial, entity.zo, entity.getZ())) - update.origin.getZ());
        entity.applyLocalTransforms(reference, partial);
        return new Matrix4f(reference.last().pose());
    }

    // flush() writes the composed pose to Flywheel's CPU arena on every flush, without a dirty flag.
    public static synchronized void staged(final Object embedding, final int slot, final Matrix4fc composed,
                                            final long pointer) {
        final Update update = EMBEDDINGS.get(embedding);
        if (update == null || !update.stageLog) { return; }
        update.stageLog = false;
        final Matrix4f stored = new Matrix4f();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                stored.set(column, row, MemoryUtil.memGetFloat(pointer + (column * 4L + row) * 4L));
            }
        }
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_UPLOAD frame={} entityId={} visualIdentity={} embeddingIdentity={} "
                        + "embeddingMatrixIndex={} event=CPU_ARENA_WRITE cpuMatrixHash={} stagedMatrixHash={} "
                        + "stagingDelta={} uploadOccurred=UNKNOWN renderedAfterward=UNKNOWN dirtyFlag=NOT_USED_FOR_EMBEDDINGS",
                SableM28BatchTrace.currentFrame(), update.entity.getId(), System.identityHashCode(update.visual),
                System.identityHashCode(embedding), slot, new Matrix4f(composed).hashCode(), stored.hashCode(), delta(composed, stored));
    }

    public static synchronized void uniformSubmitted(final Object embedding, final Matrix4fc composed,
                                                      final Object program) {
        final Update update = EMBEDDINGS.get(embedding);
        if (update == null || !update.uniformLog) { return; }
        update.uniformLog = false;
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_UPLOAD frame={} entityId={} visualIdentity={} embeddingIdentity={} "
                        + "event=INSTANCING_EMBEDDING_UNIFORM_SUBMITTED programIdentity={} cpuMatrixHash={} "
                        + "uniformArgumentMatrix={} uploadOccurred=SET_MAT4_CALL_RETURNED renderedAfterward=UNKNOWN",
                SableM28BatchTrace.currentFrame(), update.entity.getId(), System.identityHashCode(update.visual),
                System.identityHashCode(embedding), System.identityHashCode(program), new Matrix4f(composed).hashCode(), composed);
    }

    private static float delta(final Matrix4fc a, final Matrix4fc b) {
        float maximum = 0;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) { maximum = Math.max(maximum, Math.abs(a.get(column, row) - b.get(column, row))); }
        }
        return maximum;
    }

    private static float bearingAngle(final ControlledContraptionEntity entity, final float partialTick) {
        final BlockPos controller = SableCreateContraptionContext.getControllerPos(entity);
        if (controller != null && entity.level().getBlockEntity(controller)
                instanceof final MechanicalBearingBlockEntity bearing) {
            return bearing.getInterpolatedAngle(partialTick);
        }
        return Float.NaN;
    }

    private static java.util.List<String> capturedStates(final ControlledContraptionEntity entity) {
        return entity.getContraption().getBlocks().entrySet().stream().limit(32)
                .map(entry -> entry.getKey() + "=" + entry.getValue().state()).toList();
    }

    public static synchronized void structure(final AbstractContraptionEntity raw, final Object visual,
                                                final Object builder, final dev.engine_room.flywheel.lib.model.SimpleModel model) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !candidate(entity)) { return; }
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM event=STRUCTURE_MODEL entityId={} visualIdentity={} "
                        + "modelBuilderIdentity={} modelIdentity={} meshes={} bounds={} capturedStates={} thread={}",
                entity.getId(), System.identityHashCode(visual), System.identityHashCode(builder),
                System.identityHashCode(model), model.meshes().size(), model.boundingSphere(), capturedStates(entity),
                Thread.currentThread().getName());
    }

    public static synchronized void structureInstance(final AbstractContraptionEntity raw, final Object visual,
                                                       final dev.engine_room.flywheel.lib.instance.TransformedInstance instance) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !candidate(entity)) { return; }
        Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM event=STRUCTURE_INSTANCE entityId={} visualIdentity={} "
                        + "instanceIdentity={} instancePresent={} instanceLocalPose={} thread={}",
                entity.getId(), System.identityHashCode(visual), System.identityHashCode(instance), instance != null,
                instance == null ? "NONE" : instance.pose, Thread.currentThread().getName());
    }

    public static synchronized void project(final RenderLevelStageEvent event) {
        if (!ENABLED || !Boolean.getBoolean(OVERLAY_PROPERTY) || !RenderSystem.isOnRenderThread()) { return; }
        final var minecraft = Minecraft.getInstance();
        final var camera = event.getCamera().getPosition();
        for (final Update update : EMBEDDINGS.values()) {
            update.box = null;
            if (update.entity.isRemoved() || update.entity.level() != minecraft.level
                    || !ENTITIES.get(update.entity).moved || SableM28BatchTrace.currentFrame() - update.frame > 2) { continue; }
            final float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            int count = 0;
            for (int corner = 0; corner < 8; corner++) {
                final Vector3f local = update.actual.transformPosition(new Vector3f((corner & 1) == 0 ? 0 : 1.5F,
                        (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1));
                local.add((float) (update.origin.getX() - camera.x), (float) (update.origin.getY() - camera.y),
                        (float) (update.origin.getZ() - camera.z));
                final Vector4f clip = new Vector4f(local, 1);
                event.getPoseStack().last().pose().transform(clip);
                event.getProjectionMatrix().transform(clip);
                if (!clip.isFinite() || clip.w <= 0) { continue; }
                final float x = (clip.x / clip.w + 1) * 0.5F * minecraft.getWindow().getGuiScaledWidth();
                final float y = (1 - clip.y / clip.w) * 0.5F * minecraft.getWindow().getGuiScaledHeight();
                box[0] = Math.min(box[0], x); box[1] = Math.min(box[1], y);
                box[2] = Math.max(box[2], x); box[3] = Math.max(box[3], y); count++;
            }
            if (count > 0) { update.box = box; }
            if (count > 0 && update.overlayLogFrame != update.loggedFrame) {
                update.overlayLogFrame = update.loggedFrame;
                Sable.LOGGER.info("SABLE_M30_FLYWHEEL_TRANSFORM event=PROJECTED_OVERLAY frame={} entityId={} "
                                + "embeddingIdentity={} guiBounds={} projectedPoints={} behindCamera={} "
                                + "source=ACTUAL_STORED_EMBEDDING", SableM28BatchTrace.currentFrame(), update.entity.getId(),
                        update.embeddingIdentity, java.util.Arrays.toString(box), count, 8 - count);
            }
        }
    }

    public static synchronized void overlay(final GuiGraphics graphics) {
        if (!ENABLED || !Boolean.getBoolean(OVERLAY_PROPERTY)) { return; }
        final var window = Minecraft.getInstance().getWindow();
        for (final Update update : EMBEDDINGS.values()) {
            if (update.box == null) { continue; }
            if (update.box[2] < 0 || update.box[3] < 0 || update.box[0] >= window.getGuiScaledWidth()
                    || update.box[1] >= window.getGuiScaledHeight()) { continue; }
            final int x0 = Mth.clamp((int) update.box[0], 0, window.getGuiScaledWidth() - 1);
            final int y0 = Mth.clamp((int) update.box[1], 0, window.getGuiScaledHeight() - 1);
            final int x1 = Mth.clamp((int) update.box[2], x0, window.getGuiScaledWidth() - 1);
            final int y1 = Mth.clamp((int) update.box[3], y0, window.getGuiScaledHeight() - 1);
            graphics.fill(x0, y0, x1 + 1, y0 + 1, 0xFF00FFFF);
            graphics.fill(x0, y1, x1 + 1, y1 + 1, 0xFF00FFFF);
            graphics.fill(x0, y0, x0 + 1, y1 + 1, 0xFF00FFFF);
            graphics.fill(x1, y0, x1 + 1, y1 + 1, 0xFF00FFFF);
            graphics.drawString(Minecraft.getInstance().font, "M30 Flywheel entity=" + update.entity.getId()
                    + " angle=" + update.entity.getAngle(update.partialTick), x0, y0 - 10, 0xFF00FFFF);
        }
    }

    private static final class Sample {
        private float angle = Float.NaN, loggedAngle;
        private long frame = -1;
        private boolean moved, suppressionLogged, removalQueued;
        private int logs;
    }

    private static final class Update {
        private final ControlledContraptionEntity entity;
        private final Object visual;
        private final int embeddingIdentity;
        private final Matrix4f before = new Matrix4f(), actual = new Matrix4f(), previous = new Matrix4f();
        private Vec3i origin = Vec3i.ZERO;
        private float partialTick, loggedAngle = Float.NaN;
        private long frame, loggedFrame, overlayLogFrame = -1;
        private int invocations, logs;
        private boolean pending, stageLog, uniformLog;
        private float[] box;
        private Update(final ControlledContraptionEntity entity, final Object visual, final Object embedding) {
            this.entity = entity;
            this.visual = visual;
            this.embeddingIdentity = System.identityHashCode(embedding);
        }
    }
}
