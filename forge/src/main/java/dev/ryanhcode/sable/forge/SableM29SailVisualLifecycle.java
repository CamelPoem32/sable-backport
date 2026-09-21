package dev.ryanhcode.sable.forge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionControllerLookup;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Event-only ownership and heartbeat trace for semantically matched M28 sail contraptions. */
public final class SableM29SailVisualLifecycle {
    public static final String TRACE_PROPERTY = "sable.m29.traceSailVisualLifecycle";
    private static final boolean ENABLED = Boolean.getBoolean(TRACE_PROPERTY);
    private static final ResourceLocation SYMMETRIC_SAIL =
            new ResourceLocation("simulated", "white_symmetric_sail");
    private static final float ANGLE_EVENT_DELTA = 30.0F;
    private static final double RELEVANT_DISTANCE_SQUARED = 192.0D * 192.0D;
    private static final int MAX_RING_RECORDS = 32;
    private static final int MAX_LIVE_ANGLE_EVENTS = 8;
    private static final long REASSEMBLY_RECOVERY_WINDOW_FRAMES = 300L;
    private static final Map<UUID, Target> TARGETS = new ConcurrentHashMap<>();
    private static final Map<RecoveryKey, RecoveryCandidate> PENDING_RECOVERIES = new ConcurrentHashMap<>();
    private static volatile long frame;

    private SableM29SailVisualLifecycle() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void beginFrame(final long currentFrame, final Vec3 cameraPosition) {
        if (!enabled()) {
            return;
        }
        frame = currentFrame;
        PENDING_RECOVERIES.values().removeIf(
                candidate -> currentFrame - candidate.removedFrame > REASSEMBLY_RECOVERY_WINDOW_FRAMES);
        for (final Target target : TARGETS.values()) {
            final ControlledContraptionEntity entity = target.entity;
            final SubLevel containing = entity == null ? null
                    : SableCreateContraptionContext.getContainingSubLevel(entity);
            final Vec3 visiblePosition = containing instanceof final ClientSubLevel clientSubLevel
                    ? clientSubLevel.renderPose().transformPosition(entity.position())
                    : entity == null ? Vec3.ZERO : entity.position();
            final boolean expectedVisible = entity != null && entity.isAlive() && !entity.isRemoved()
                    && visiblePosition.distanceToSqr(cameraPosition) <= RELEVANT_DISTANCE_SQUARED;
            final SailVisualLifecycleState.Transition transition =
                    target.lifecycle.frame(currentFrame, expectedVisible);
            if (transition == SailVisualLifecycleState.Transition.SUSPECTED_VISUAL_GAP) {
                emit(target, transition, "heartbeatGapFrames="
                        + (currentFrame - target.lifecycle.lastGeometryFrame())
                        + " ring=" + target.ring);
            }
        }
    }

    public static void entityJoined(final Entity raw, final Level level) {
        if (!enabled() || !level.isClientSide || !(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        discover(entity);
    }

    public static void entityLeft(final Entity raw, final Level level) {
        if (!enabled() || !level.isClientSide || !(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final Target target = TARGETS.remove(entity.getUUID());
        if (target != null) {
            if (target.lifecycle.visualGap() && target.controllerPos != null) {
                PENDING_RECOVERIES.put(recoveryKey(entity, target.controllerPos),
                        new RecoveryCandidate(frame, target.lifecycle.visualPresent(),
                                target.lifecycle.payloadPresent(), entity.isAlive() && !entity.isRemoved()));
            }
            emit(target, target.lifecycle.entityRemoved(), "reason=ENTITY_LEAVE_LEVEL");
        }
    }

    public static void visualCreated(final AbstractContraptionEntity raw, final Object visual) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        target.visualIdentity = System.identityHashCode(visual);
        selectOwner(target, SailVisualLifecycleState.Owner.FLYWHEEL,
                "visualIdentity=" + target.visualIdentity);
        target.lastVisualCreateFrame = frame;
        emit(target, target.lifecycle.visualCreated(), "visualIdentity=" + target.visualIdentity);
        SableM28NormalWorldCceSync.traceM29Entity("CLIENT_VISUAL_CREATED", target.entity,
                "visualIdentity=" + target.visualIdentity);
    }

    public static void visualFrame(final AbstractContraptionEntity raw, final Object visual,
                                   final float partialTick) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        target.visualIdentity = System.identityHashCode(visual);
        observe(target, partialTick);
        geometryHeartbeat(target, "FLYWHEEL_EMBEDDING_UPDATE");
    }

    public static void visualRemoved(final AbstractContraptionEntity raw, final Object visual) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        emit(target, target.lifecycle.visualRemoved(),
                "visualIdentity=" + System.identityHashCode(visual));
    }

    public static void cpuRenderEligible(final AbstractContraptionEntity raw, final float partialTick) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        selectOwner(target, SailVisualLifecycleState.Owner.CPU_BRIDGE, "visualIdentity=CPU_BRIDGE");
        if (!target.lifecycle.visualPresent()) {
            target.lastVisualCreateFrame = frame;
            emit(target, target.lifecycle.visualCreated(), "visualIdentity=CPU_BRIDGE");
        }
        if (!target.firstRenderEligible) {
            target.firstRenderEligible = true;
            emit(target, SailVisualLifecycleState.Transition.NONE, "event=FIRST_RENDER_ELIGIBLE");
        }
        observe(target, partialTick);
    }

    public static void cpuGeometrySubmitted(final AbstractContraptionEntity raw) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        selectOwner(target, SailVisualLifecycleState.Owner.CPU_BRIDGE, "visualIdentity=CPU_BRIDGE");
        geometryHeartbeat(target, "CPU_SUPER_BYTE_BUFFER");
    }

    public static void cullState(final AbstractContraptionEntity raw, final boolean visible,
                                 final AABB visibleBounds) {
        final Target target = target(raw);
        if (target == null) {
            return;
        }
        if (target.lastCullResult == null || target.lastCullResult != visible) {
            target.lastCullResult = visible;
            emit(target, SailVisualLifecycleState.Transition.NONE,
                    "event=CULL_STATE_CHANGED frustumResult=" + visible + " visibleAabb=" + visibleBounds);
        }
        if (target.lastVisibleBounds == null || boundsChanged(target.lastVisibleBounds, visibleBounds)) {
            target.lastVisibleBounds = visibleBounds;
            record(target, "BOUNDS_CHANGED visibleAabb=" + visibleBounds);
        }
    }

    private static @Nullable Target target(final AbstractContraptionEntity raw) {
        if (!enabled() || !(raw instanceof final ControlledContraptionEntity entity)
                || !entity.level().isClientSide
                || !SailVisualLifecycleState.canDiscover(entity.isAlive(), entity.isRemoved())) {
            return null;
        }
        final Target existing = TARGETS.get(entity.getUUID());
        if (existing != null) {
            refreshPayload(existing);
            return existing;
        }
        return discover(entity);
    }

    private static @Nullable Target discover(final ControlledContraptionEntity entity) {
        if (!SailVisualLifecycleState.canDiscover(entity.isAlive(), entity.isRemoved())) {
            return null;
        }
        final int sailCount = symmetricSailCount(entity);
        final BlockPos serializedControllerPos = SableCreateContraptionContext.getControllerPos(entity);
        final BlockPos controllerPos = serializedControllerPos != null
                ? serializedControllerPos
                : SableM28NormalWorldCceSync.isTarget(entity)
                ? SableM28NormalWorldCceSync.bearingPos(entity)
                : null;
        final Object controller = controllerPos == null ? null
                : SableCreateContraptionControllerLookup.getControllerBlockEntity(entity.level(), controllerPos);
        if (sailCount == 0 || !(controller instanceof MechanicalBearingBlockEntity)) {
            return null;
        }
        final Target created = new Target(entity, controllerPos, sailCount);
        final Target target = TARGETS.putIfAbsent(entity.getUUID(), created);
        if (target != null) {
            return target;
        }
        emit(created, created.lifecycle.entityDiscovered(true), "targetReason=CONTROLLED_MECHANICAL_BEARING_WITH_SYMMETRIC_SAIL");
        emit(created, SailVisualLifecycleState.Transition.PAYLOAD_PRESENT, "symmetricSailCount=" + sailCount);
        SableM28NormalWorldCceSync.traceM29Entity("CLIENT_TARGET_DISCOVERED", entity,
                "symmetricSailCount=" + sailCount);
        if (controllerPos != null) {
            final RecoveryCandidate recovery = PENDING_RECOVERIES.remove(recoveryKey(entity, controllerPos));
            if (recovery != null && frame - recovery.removedFrame <= REASSEMBLY_RECOVERY_WINDOW_FRAMES) {
                created.recovery = recovery;
            }
        }
        return created;
    }

    private static void refreshPayload(final Target target) {
        final int sailCount = symmetricSailCount(target.entity);
        if (sailCount != target.symmetricSailCount) {
            target.symmetricSailCount = sailCount;
            emit(target, target.lifecycle.payload(sailCount > 0), "symmetricSailCount=" + sailCount);
        }
    }

    private static void observe(final Target target, final float partialTick) {
        refreshPayload(target);
        final float angle = target.entity.getAngle(partialTick);
        final SailVisualLifecycleState.Transition angleTransition =
                target.lifecycle.angle(angle, ANGLE_EVENT_DELTA);
        if (angleTransition == SailVisualLifecycleState.Transition.ANGLE_CHANGED_SIGNIFICANTLY) {
            final float previous = target.lastAngle;
            target.lastAngle = angle;
            record(target, "ANGLE_CHANGED_SIGNIFICANTLY previous=" + previous + " current=" + angle
                    + " partialTick=" + partialTick);
            if (target.liveAngleEvents++ < MAX_LIVE_ANGLE_EVENTS) {
                emit(target, SailVisualLifecycleState.Transition.NONE,
                        "event=ANGLE_CHANGED_SIGNIFICANTLY previous=" + previous + " current=" + angle
                                + " partialTick=" + partialTick);
            }
        }
        final MechanicalBearingBlockEntity bearing = target.controllerPos == null ? null
                : SableCreateContraptionControllerLookup.getControllerBlockEntity(
                        target.entity.level(), target.controllerPos)
                instanceof final MechanicalBearingBlockEntity found ? found : null;
        final boolean running = bearing != null && bearing.isRunning();
        if (target.lifecycle.running(running)
                == SailVisualLifecycleState.Transition.MOVEMENT_STATE_CHANGED) {
            emit(target, SailVisualLifecycleState.Transition.MOVEMENT_STATE_CHANGED,
                    "bearingRunning=" + running);
        }
    }

    private static void geometryHeartbeat(final Target target, final String source) {
        final boolean first = target.lifecycle.lastGeometryFrame() < 0L;
        final SailVisualLifecycleState.Transition transition = target.lifecycle.geometrySubmitted(frame);
        if (first || transition == SailVisualLifecycleState.Transition.GEOMETRY_SUBMISSION_RESUMED) {
            emit(target, transition, "heartbeatSource=" + source);
            if (first) {
                SableM28NormalWorldCceSync.traceM29Entity("CLIENT_FIRST_GEOMETRY", target.entity,
                        "heartbeatSource=" + source);
            }
        } else {
            record(target, "GEOMETRY_HEARTBEAT frame=" + frame + " source=" + source);
        }
        if (target.recovery != null) {
            final RecoveryCandidate recovery = target.recovery;
            target.recovery = null;
            emit(target, SailVisualLifecycleState.Transition.NONE,
                    "event=RECOVERY_AFTER_REASSEMBLY beforeVisualPresent=" + recovery.visualPresent
                            + " beforePayloadPresent=" + recovery.payloadPresent
                            + " beforeEntityAlive=" + recovery.entityAlive
                            + " afterVisualPresent=" + target.lifecycle.visualPresent()
                            + " afterPayloadPresent=" + target.lifecycle.payloadPresent()
                            + " afterGeometrySubmission=true replacementDelayFrames="
                            + (frame - recovery.removedFrame));
        }
    }

    private static void selectOwner(final Target target, final SailVisualLifecycleState.Owner owner,
                                    final String details) {
        if (target.lifecycle.owner(owner)) {
            emit(target, SailVisualLifecycleState.Transition.NONE,
                    "event=VISUAL_OWNER_SELECTED owner=" + owner + ' ' + details);
        }
    }

    private static RecoveryKey recoveryKey(final ControlledContraptionEntity entity,
                                           final BlockPos controllerPos) {
        return new RecoveryKey(System.identityHashCode(entity.level()), controllerPos);
    }

    private static void emit(final Target target, final SailVisualLifecycleState.Transition transition,
                             final String details) {
        if (transition == SailVisualLifecycleState.Transition.NONE && !details.startsWith("event=")) {
            return;
        }
        final String event = transition == SailVisualLifecycleState.Transition.NONE
                ? details.substring("event=".length()).split(" ", 2)[0] : transition.name();
        record(target, event + ' ' + details);
        final ControlledContraptionEntity entity = target.entity;
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
        final MechanicalBearingBlockEntity bearing = target.controllerPos == null ? null
                : SableCreateContraptionControllerLookup.getControllerBlockEntity(entity.level(), target.controllerPos)
                instanceof final MechanicalBearingBlockEntity found ? found : null;
        Sable.LOGGER.info("SABLE_M29_SAIL_VISUAL event={} frame={} gameTime={} entityId={} entityUuid={} "
                        + "containingSableSubLevel={} sableId={} entityAlive={} entityRemoved={} controllerPos={} "
                        + "bearingRunning={} bearingAngle={} capturedBlockCount={} symmetricSailCount={} "
                        + "renderOwner={} flywheelVisualPresent={} cpuRenderPathEligible={} visualIdentity={} "
                        + "entityAabb={} visibleAabb={} frustumResult={} lastGeometrySubmissionFrame={} "
                        + "lastVisualCreateFrame={} details={}",
                event, frame, entity.level().getGameTime(), entity.getId(), entity.getUUID(),
                containing != null, containing == null ? "none" : containing.getUniqueId(),
                entity.isAlive(), entity.isRemoved(), target.controllerPos,
                bearing != null && bearing.isRunning(), entity.getAngle(1.0F), capturedBlockCount(entity),
                target.symmetricSailCount, target.lifecycle.owner(), target.lifecycle.visualPresent(),
                target.lifecycle.owner() == SailVisualLifecycleState.Owner.CPU_BRIDGE, target.visualIdentity,
                entity.getBoundingBox(), target.lastVisibleBounds, target.lastCullResult,
                target.lifecycle.lastGeometryFrame(), target.lastVisualCreateFrame, details);
    }

    private static void record(final Target target, final String value) {
        if (target.ring.size() == MAX_RING_RECORDS) {
            target.ring.removeFirst();
        }
        target.ring.addLast("frame=" + frame + ':' + value);
    }

    private static int symmetricSailCount(final AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) {
            return 0;
        }
        int count = 0;
        for (final StructureTemplate.StructureBlockInfo info : entity.getContraption().getBlocks().values()) {
            if (SYMMETRIC_SAIL.equals(BuiltInRegistries.BLOCK.getKey(info.state().getBlock()))) {
                count++;
            }
        }
        return count;
    }

    private static int capturedBlockCount(final AbstractContraptionEntity entity) {
        return entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size();
    }

    private static boolean boundsChanged(final AABB first, final AABB second) {
        final double epsilon = 0.25D;
        return Math.abs(first.minX - second.minX) > epsilon || Math.abs(first.minY - second.minY) > epsilon
                || Math.abs(first.minZ - second.minZ) > epsilon || Math.abs(first.maxX - second.maxX) > epsilon
                || Math.abs(first.maxY - second.maxY) > epsilon || Math.abs(first.maxZ - second.maxZ) > epsilon;
    }

    private static final class Target {
        private final ControlledContraptionEntity entity;
        private final BlockPos controllerPos;
        private final SailVisualLifecycleState lifecycle = new SailVisualLifecycleState();
        private final Deque<String> ring = new ArrayDeque<>(MAX_RING_RECORDS);
        private int symmetricSailCount;
        private int visualIdentity;
        private int liveAngleEvents;
        private float lastAngle = Float.NaN;
        private long lastVisualCreateFrame = -1L;
        private boolean firstRenderEligible;
        private @Nullable Boolean lastCullResult;
        private @Nullable AABB lastVisibleBounds;
        private @Nullable RecoveryCandidate recovery;

        private Target(final ControlledContraptionEntity entity, final BlockPos controllerPos,
                       final int symmetricSailCount) {
            this.entity = entity;
            this.controllerPos = controllerPos;
            this.symmetricSailCount = symmetricSailCount;
        }
    }

    private record RecoveryKey(int levelIdentity, BlockPos controllerPos) {
    }

    private record RecoveryCandidate(long removedFrame, boolean visualPresent,
                                     boolean payloadPresent, boolean entityAlive) {
    }
}
