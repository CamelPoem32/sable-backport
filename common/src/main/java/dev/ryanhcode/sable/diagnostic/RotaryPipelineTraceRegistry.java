package dev.ryanhcode.sable.diagnostic;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-only trace state for the M24 rotary real-body backend canary.
 */
public final class RotaryPipelineTraceRegistry {
    public static final String POST_JOINT_INSERT = "POST_JOINT_INSERT";
    public static final String AFTER_CONSTRAINT_REGISTRATION = "AFTER_CONSTRAINT_REGISTRATION";
    public static final String PRE_PHYSICS_SYSTEM_TICK = "PRE_PHYSICS_SYSTEM_TICK";
    public static final String AFTER_BODY_UPDATE_QUEUE = "AFTER_BODY_UPDATE_QUEUE";
    public static final String BEFORE_BODY_RECREATE = "BEFORE_BODY_RECREATE";
    public static final String AFTER_BODY_RECREATE_IF_ANY = "AFTER_BODY_RECREATE_IF_ANY";
    public static final String AFTER_COLLIDER_UPDATE = "AFTER_COLLIDER_UPDATE";
    public static final String PRE_CONSTRAINT_MAINTENANCE = "PRE_CONSTRAINT_MAINTENANCE";
    public static final String AFTER_CONSTRAINT_MAINTENANCE = "AFTER_CONSTRAINT_MAINTENANCE";
    public static final String PRE_SOLVER = "PRE_SOLVER";
    public static final String POST_RAPIER_SOLVER = "POST_RAPIER_SOLVER";
    public static final String POST_SABLE_SYNC_PRE_CLEANUP = "POST_SABLE_SYNC_PRE_CLEANUP";
    public static final String SAFETY_REMOVAL = "SAFETY_REMOVAL";

    private static final List<String> REQUIRED_PHASES = List.of(PRE_SOLVER, POST_RAPIER_SOLVER, POST_SABLE_SYNC_PRE_CLEANUP);
    private static final Map<String, TraceState> TRACES = new ConcurrentHashMap<>();
    private static final int MAX_SNAPSHOTS_PER_TRACE = 32;

    private RotaryPipelineTraceRegistry() {
    }

    public static void register(final ServerLevel level, final String canarySessionId,
                                final UUID bodyAUuid, final UUID bodyBUuid,
                                final int bodyAId, final int bodyBId, final long constraintHandle,
                                final Vector3dc localAnchorA, final Vector3dc localAnchorB,
                                final Vector3dc localAxisA, final Vector3dc localAxisB) {
        TRACES.put(key(level), new TraceState(level.dimension().location().toString(), canarySessionId,
                bodyAUuid, bodyBUuid, bodyAId, bodyBId, constraintHandle,
                localAnchorA, localAnchorB, localAxisA, localAxisB));
    }

    public static @Nullable TraceState get(final ServerLevel level) {
        return TRACES.get(key(level));
    }

    public static @Nullable PhaseSnapshot recordRapierPhase(final ServerLevel level, final String phase,
                                                           final long gameTime,
                                                           final int bodyAId, final int bodyBId,
                                                           final long constraintHandle,
                                                           final boolean jointHandleValid,
                                                           final boolean rapierBodyAPresent,
                                                           final boolean rapierBodyBPresent,
                                                           final String rapierTranslationA,
                                                           final String rapierTranslationB,
                                                           final String rapierRotationA,
                                                           final String rapierRotationB,
                                                           final String rapierLinearVelocityA,
                                                           final String rapierLinearVelocityB,
                                                           final String rapierAngularVelocityA,
                                                           final String rapierAngularVelocityB,
                                                           final boolean rapierFiniteA,
                                                           final boolean rapierFiniteB,
                                                           final boolean rapierPlausibleA,
                                                           final boolean rapierPlausibleB,
                                                           final boolean sableBodyAPresent,
                                                           final boolean sableBodyBPresent,
                                                           final String logicalPoseA,
                                                           final String logicalPoseB,
                                                           final String visibleBoundsA,
                                                           final String visibleBoundsB,
                                                           final String jointLinearImpulse,
                                                           final String jointAngularImpulse) {
        final TraceState state = get(level);
        if (state == null || !state.matches(bodyAId, bodyBId)) {
            return null;
        }
        return state.recordSnapshot(new PhaseSnapshot(phase, 0, gameTime, bodyAId, bodyBId,
                rapierBodyAPresent, rapierBodyBPresent,
                rapierTranslationA, rapierTranslationB,
                rapierRotationA, rapierRotationB,
                rapierLinearVelocityA, rapierLinearVelocityB,
                rapierAngularVelocityA, rapierAngularVelocityB,
                rapierFiniteA, rapierFiniteB,
                rapierPlausibleA, rapierPlausibleB,
                sableBodyAPresent, sableBodyBPresent,
                logicalPoseA, logicalPoseB,
                true, true, true, true,
                visibleBoundsA, visibleBoundsB,
                true, true,
                jointHandleValid,
                jointLinearImpulse, jointAngularImpulse));
    }

    public static @Nullable LifecycleSnapshot recordLifecyclePhase(final ServerLevel level, final String phase,
                                                                   final long gameTime,
                                                                   final boolean sableBodyAPresent,
                                                                   final boolean sableBodyBPresent,
                                                                   final boolean activeSubLevelsContainsA,
                                                                   final boolean activeSubLevelsContainsB,
                                                                   final boolean rigidBodyMapContainsA,
                                                                   final boolean rigidBodyMapContainsB,
                                                                   final boolean rigidBodySetContainsA,
                                                                   final boolean rigidBodySetContainsB,
                                                                   final long nativeBodyHandleA,
                                                                   final long nativeBodyHandleB,
                                                                   final boolean javaConstraintHandlePresent,
                                                                   final boolean logicalJointMapContainsHandle,
                                                                   final boolean rapierJointSetContainsHandle,
                                                                   final int rigidBodyCount,
                                                                   final int jointCount) {
        final TraceState state = get(level);
        if (state == null) {
            return null;
        }
        return state.recordLifecycleSnapshot(new LifecycleSnapshot(phase, 0, gameTime,
                state.bodyAId(), state.bodyBId(),
                sableBodyAPresent, sableBodyBPresent,
                activeSubLevelsContainsA, activeSubLevelsContainsB,
                rigidBodyMapContainsA, rigidBodyMapContainsB,
                rigidBodySetContainsA, rigidBodySetContainsB,
                nativeBodyHandleA, nativeBodyHandleB,
                javaConstraintHandlePresent, logicalJointMapContainsHandle,
                rapierJointSetContainsHandle, rigidBodyCount, jointCount));
    }

    public static @Nullable RemovalRecord recordBodyRemoval(final ServerLevel level, final long gameTime,
                                                            final UUID bodyUuid, final int bodyId,
                                                            final String ownerClass, final String ownerMethod,
                                                            final String reason) {
        final TraceState state = get(level);
        if (state == null || !state.containsBody(bodyUuid, bodyId)) {
            return null;
        }
        return state.recordBodyRemoval(gameTime, bodyUuid, bodyId, ownerClass, ownerMethod, reason);
    }

    public static @Nullable RemovalRecord recordJointRemoval(final ServerLevel level, final long gameTime,
                                                             final long jointHandle, final String ownerClass,
                                                             final String ownerMethod, final String reason) {
        final TraceState state = get(level);
        if (state == null || state.constraintHandle() != jointHandle) {
            return null;
        }
        return state.recordJointRemoval(gameTime, jointHandle, ownerClass, ownerMethod, reason);
    }

    public static @Nullable RemovalRecord recordJointRemovalByHandle(final long jointHandle,
                                                                     final String ownerClass,
                                                                     final String ownerMethod,
                                                                     final String reason) {
        for (final TraceState state : TRACES.values()) {
            if (state.constraintHandle() == jointHandle) {
                return state.recordJointRemoval(state.latestGameTime(), jointHandle, ownerClass, ownerMethod, reason);
            }
        }
        return null;
    }

    public static void recordBodyRecreated(final ServerLevel level, final UUID bodyUuid,
                                           final int oldBodyId, final int newBodyId, final String reason) {
        final TraceState state = get(level);
        if (state != null && (state.bodyAUuid().equals(bodyUuid) || state.bodyBUuid().equals(bodyUuid))) {
            state.recordBodyRecreated(bodyUuid, oldBodyId, newBodyId, reason);
        }
    }

    public static @Nullable PhaseSnapshot recordPostSableSync(final ServerLevel level, final long gameTime,
                                                             final boolean sableBodyAPresent,
                                                             final boolean sableBodyBPresent,
                                                             final String logicalPoseA,
                                                             final String logicalPoseB,
                                                             final boolean logicalPoseFiniteA,
                                                             final boolean logicalPoseFiniteB,
                                                             final boolean logicalPosePlausibleA,
                                                             final boolean logicalPosePlausibleB,
                                                             final String visibleBoundsA,
                                                             final String visibleBoundsB,
                                                             final boolean boundsPlausibleA,
                                                             final boolean boundsPlausibleB) {
        final TraceState state = get(level);
        if (state == null) {
            return null;
        }
        final PhaseSnapshot postRapier = state.snapshot(POST_RAPIER_SOLVER);
        return state.recordSnapshot(new PhaseSnapshot(POST_SABLE_SYNC_PRE_CLEANUP, 0, gameTime,
                state.bodyAId(), state.bodyBId(),
                postRapier != null && postRapier.rapierBodyAPresent(),
                postRapier != null && postRapier.rapierBodyBPresent(),
                postRapier == null ? "unavailable" : postRapier.rapierTranslationA(),
                postRapier == null ? "unavailable" : postRapier.rapierTranslationB(),
                postRapier == null ? "unavailable" : postRapier.rapierRotationA(),
                postRapier == null ? "unavailable" : postRapier.rapierRotationB(),
                postRapier == null ? "unavailable" : postRapier.rapierLinearVelocityA(),
                postRapier == null ? "unavailable" : postRapier.rapierLinearVelocityB(),
                postRapier == null ? "unavailable" : postRapier.rapierAngularVelocityA(),
                postRapier == null ? "unavailable" : postRapier.rapierAngularVelocityB(),
                postRapier != null && postRapier.rapierFiniteA(),
                postRapier != null && postRapier.rapierFiniteB(),
                postRapier != null && postRapier.rapierPlausibleA(),
                postRapier != null && postRapier.rapierPlausibleB(),
                sableBodyAPresent, sableBodyBPresent,
                logicalPoseA, logicalPoseB,
                logicalPoseFiniteA, logicalPoseFiniteB,
                logicalPosePlausibleA, logicalPosePlausibleB,
                visibleBoundsA, visibleBoundsB,
                boundsPlausibleA, boundsPlausibleB,
                postRapier != null && postRapier.jointHandleValid(),
                postRapier == null ? "unavailable" : postRapier.jointLinearImpulse(),
                postRapier == null ? "unavailable" : postRapier.jointAngularImpulse()));
    }

    public static @Nullable PhaseSnapshot recordSafetyRemoval(final ServerLevel level, final long gameTime,
                                                             final UUID bodyUuid, final int bodyId,
                                                             final String logicalPose,
                                                             final String visibleBounds) {
        final TraceState state = get(level);
        if (state == null || !state.containsBody(bodyUuid, bodyId)) {
            return null;
        }
        return state.recordSafetyRemoval(gameTime, bodyUuid, bodyId, logicalPose, visibleBounds);
    }

    public static void attachConstraintHandle(final ServerLevel level, final int bodyAId, final int bodyBId,
                                              final long constraintHandle) {
        final TraceState state = get(level);
        if (state != null && state.matches(bodyAId, bodyBId)) {
            state.attachConstraintHandle(constraintHandle);
        }
    }

    public static String firstFailureClassification(final ServerLevel level) {
        final TraceState state = get(level);
        return state == null ? "UNKNOWN_FAILURE" : state.firstFailureClassification();
    }

    private static String key(final ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static final class TraceState {
        private final String dimension;
        private final String canarySessionId;
        private final UUID bodyAUuid;
        private final UUID bodyBUuid;
        private final int bodyAId;
        private final int bodyBId;
        private volatile long constraintHandle;
        private final Vector3d localAnchorA;
        private final Vector3d localAnchorB;
        private final Vector3d localAxisA;
        private final Vector3d localAxisB;
        private final Map<String, PhaseSnapshot> latestSnapshots = new LinkedHashMap<>();
        private final Map<String, LifecycleSnapshot> lifecycleSnapshots = new LinkedHashMap<>();
        private final List<String> capturedPhaseOrder = new ArrayList<>();
        private final List<RemovalRecord> removalRecords = new ArrayList<>();
        private int nextSequence = 1;
        private volatile int replacementBodyAId = -1;
        private volatile int replacementBodyBId = -1;
        private volatile String bodyARecreationReason = "none";
        private volatile String bodyBRecreationReason = "none";

        private TraceState(final String dimension, final String canarySessionId,
                           final UUID bodyAUuid, final UUID bodyBUuid,
                           final int bodyAId, final int bodyBId, final long constraintHandle,
                           final Vector3dc localAnchorA, final Vector3dc localAnchorB,
                           final Vector3dc localAxisA, final Vector3dc localAxisB) {
            this.dimension = dimension;
            this.canarySessionId = canarySessionId;
            this.bodyAUuid = bodyAUuid;
            this.bodyBUuid = bodyBUuid;
            this.bodyAId = bodyAId;
            this.bodyBId = bodyBId;
            this.constraintHandle = constraintHandle;
            this.localAnchorA = new Vector3d(localAnchorA);
            this.localAnchorB = new Vector3d(localAnchorB);
            this.localAxisA = new Vector3d(localAxisA);
            this.localAxisB = new Vector3d(localAxisB);
        }

        public String dimension() {
            return this.dimension;
        }

        public String canarySessionId() {
            return this.canarySessionId;
        }

        public UUID bodyAUuid() {
            return this.bodyAUuid;
        }

        public UUID bodyBUuid() {
            return this.bodyBUuid;
        }

        public int bodyAId() {
            return this.bodyAId;
        }

        public int bodyBId() {
            return this.bodyBId;
        }

        public long constraintHandle() {
            return this.constraintHandle;
        }

        public boolean matches(final int bodyAId, final int bodyBId) {
            return this.bodyAId == bodyAId && this.bodyBId == bodyBId;
        }

        public boolean containsBody(final UUID bodyUuid, final int bodyId) {
            return (this.bodyAId == bodyId && this.bodyAUuid.equals(bodyUuid))
                    || (this.bodyBId == bodyId && this.bodyBUuid.equals(bodyUuid));
        }

        public void attachConstraintHandle(final long constraintHandle) {
            this.constraintHandle = constraintHandle;
        }

        public Vector3dc localAnchorA() {
            return this.localAnchorA;
        }

        public Vector3dc localAnchorB() {
            return this.localAnchorB;
        }

        public Vector3dc localAxisA() {
            return this.localAxisA;
        }

        public Vector3dc localAxisB() {
            return this.localAxisB;
        }

        public synchronized boolean postSableSyncLogged() {
            return this.latestSnapshots.containsKey(POST_SABLE_SYNC_PRE_CLEANUP);
        }

        public synchronized boolean traceComplete() {
            return this.latestSnapshots.keySet().containsAll(REQUIRED_PHASES);
        }

        public synchronized List<String> capturedPhases() {
            return new ArrayList<>(this.capturedPhaseOrder);
        }

        public synchronized List<String> missingPhases() {
            final List<String> missing = new ArrayList<>();
            for (final String phase : REQUIRED_PHASES) {
                if (!this.latestSnapshots.containsKey(phase)) {
                    missing.add(phase);
                }
            }
            return missing;
        }

        public synchronized @Nullable PhaseSnapshot snapshot(final String phase) {
            return this.latestSnapshots.get(phase);
        }

        public synchronized @Nullable LifecycleSnapshot lifecycleSnapshot(final String phase) {
            return this.lifecycleSnapshots.get(phase);
        }

        public synchronized String lifecycleSummary(final String phase) {
            final LifecycleSnapshot snapshot = this.lifecycleSnapshots.get(phase);
            return snapshot == null ? phase + "=missing" : snapshot.summary();
        }

        public synchronized List<RemovalRecord> removalRecords() {
            return new ArrayList<>(this.removalRecords);
        }

        public synchronized String firstRemovalPhase() {
            return this.removalRecords.isEmpty() ? "none" : this.removalRecords.get(0).phaseBoundary();
        }

        public synchronized String bodyRemovalReason(final UUID bodyUuid) {
            return this.removalRecords.stream()
                    .filter(record -> bodyUuid.equals(record.bodyUuid()))
                    .map(RemovalRecord::reason)
                    .findFirst()
                    .orElse("none");
        }

        public synchronized String jointRemovalReason() {
            return this.removalRecords.stream()
                    .filter(record -> "JOINT".equals(record.kind()))
                    .map(RemovalRecord::reason)
                    .findFirst()
                    .orElse("none");
        }

        public synchronized long latestGameTime() {
            if (!this.latestSnapshots.isEmpty()) {
                return this.latestSnapshots.values().stream().reduce((first, second) -> second)
                        .map(PhaseSnapshot::gameTime).orElse(-1L);
            }
            if (!this.lifecycleSnapshots.isEmpty()) {
                return this.lifecycleSnapshots.values().stream().reduce((first, second) -> second)
                        .map(LifecycleSnapshot::gameTime).orElse(-1L);
            }
            return -1L;
        }

        public int replacementBodyAId() {
            return this.replacementBodyAId;
        }

        public int replacementBodyBId() {
            return this.replacementBodyBId;
        }

        public String bodyARecreationReason() {
            return this.bodyARecreationReason;
        }

        public String bodyBRecreationReason() {
            return this.bodyBRecreationReason;
        }

        public synchronized String snapshotSummary(final String phase) {
            final PhaseSnapshot snapshot = this.latestSnapshots.get(phase);
            return snapshot == null ? phase + "=missing" : snapshot.summary();
        }

        public synchronized String latestJointLinearImpulse() {
            final PhaseSnapshot snapshot = this.latestSnapshots.get(POST_RAPIER_SOLVER);
            return snapshot == null ? "unavailable" : snapshot.jointLinearImpulse();
        }

        public synchronized String latestJointAngularImpulse() {
            final PhaseSnapshot snapshot = this.latestSnapshots.get(POST_RAPIER_SOLVER);
            return snapshot == null ? "unavailable" : snapshot.jointAngularImpulse();
        }

        public synchronized @Nullable PhaseSnapshot recordSnapshot(final PhaseSnapshot rawSnapshot) {
            if (this.nextSequence > MAX_SNAPSHOTS_PER_TRACE) {
                return null;
            }
            final PhaseSnapshot snapshot = rawSnapshot.withSequence(this.nextSequence++);
            this.latestSnapshots.put(snapshot.phase(), snapshot);
            if (!this.capturedPhaseOrder.contains(snapshot.phase())) {
                this.capturedPhaseOrder.add(snapshot.phase());
            }
            return snapshot;
        }

        public synchronized @Nullable LifecycleSnapshot recordLifecycleSnapshot(final LifecycleSnapshot rawSnapshot) {
            if (this.nextSequence > MAX_SNAPSHOTS_PER_TRACE) {
                return null;
            }
            final LifecycleSnapshot snapshot = rawSnapshot.withSequence(this.nextSequence++);
            this.lifecycleSnapshots.put(snapshot.phase(), snapshot);
            if (!this.capturedPhaseOrder.contains(snapshot.phase())) {
                this.capturedPhaseOrder.add(snapshot.phase());
            }
            return snapshot;
        }

        public synchronized RemovalRecord recordBodyRemoval(final long gameTime, final UUID bodyUuid,
                                                            final int bodyId, final String ownerClass,
                                                            final String ownerMethod, final String reason) {
            final RemovalRecord record = new RemovalRecord("BODY", this.nextSequence++, gameTime,
                    bodyUuid, bodyId, -1L, this.lastCapturedPhase(), ownerClass, ownerMethod, reason);
            this.removalRecords.add(record);
            return record;
        }

        public synchronized RemovalRecord recordJointRemoval(final long gameTime, final long jointHandle,
                                                             final String ownerClass, final String ownerMethod,
                                                             final String reason) {
            final RemovalRecord record = new RemovalRecord("JOINT", this.nextSequence++, gameTime,
                    null, -1, jointHandle, this.lastCapturedPhase(), ownerClass, ownerMethod, reason);
            this.removalRecords.add(record);
            return record;
        }

        public synchronized void recordBodyRecreated(final UUID bodyUuid, final int oldBodyId,
                                                     final int newBodyId, final String reason) {
            if (this.bodyAUuid.equals(bodyUuid)) {
                this.replacementBodyAId = newBodyId;
                this.bodyARecreationReason = reason + ":" + oldBodyId + "->" + newBodyId;
            } else if (this.bodyBUuid.equals(bodyUuid)) {
                this.replacementBodyBId = newBodyId;
                this.bodyBRecreationReason = reason + ":" + oldBodyId + "->" + newBodyId;
            }
        }

        private String lastCapturedPhase() {
            return this.capturedPhaseOrder.isEmpty()
                    ? "AFTER_TRACE_REGISTRATION"
                    : this.capturedPhaseOrder.get(this.capturedPhaseOrder.size() - 1);
        }

        public synchronized @Nullable PhaseSnapshot recordSafetyRemoval(final long gameTime, final UUID bodyUuid,
                                                                        final int bodyId, final String logicalPose,
                                                                        final String visibleBounds) {
            final PhaseSnapshot postRapier = this.latestSnapshots.get(POST_RAPIER_SOLVER);
            final PhaseSnapshot postSync = this.latestSnapshots.get(POST_SABLE_SYNC_PRE_CLEANUP);
            final boolean removingA = this.bodyAId == bodyId && this.bodyAUuid.equals(bodyUuid);
            final PhaseSnapshot snapshot = new PhaseSnapshot(SAFETY_REMOVAL, 0, gameTime,
                    this.bodyAId, this.bodyBId,
                    postRapier != null && postRapier.rapierBodyAPresent(),
                    postRapier != null && postRapier.rapierBodyBPresent(),
                    postRapier == null ? "unavailable" : postRapier.rapierTranslationA(),
                    postRapier == null ? "unavailable" : postRapier.rapierTranslationB(),
                    postRapier == null ? "unavailable" : postRapier.rapierRotationA(),
                    postRapier == null ? "unavailable" : postRapier.rapierRotationB(),
                    postRapier == null ? "unavailable" : postRapier.rapierLinearVelocityA(),
                    postRapier == null ? "unavailable" : postRapier.rapierLinearVelocityB(),
                    postRapier == null ? "unavailable" : postRapier.rapierAngularVelocityA(),
                    postRapier == null ? "unavailable" : postRapier.rapierAngularVelocityB(),
                    postRapier != null && postRapier.rapierFiniteA(),
                    postRapier != null && postRapier.rapierFiniteB(),
                    postRapier != null && postRapier.rapierPlausibleA(),
                    postRapier != null && postRapier.rapierPlausibleB(),
                    removingA || (postSync != null && postSync.sableBodyAPresent()),
                    !removingA || (postSync != null && postSync.sableBodyBPresent()),
                    removingA ? logicalPose : postSync == null ? "unavailable" : postSync.logicalPoseA(),
                    removingA ? postSync == null ? "unavailable" : postSync.logicalPoseB() : logicalPose,
                    false, false, false, false,
                    removingA ? visibleBounds : postSync == null ? "unavailable" : postSync.visibleBoundsA(),
                    removingA ? postSync == null ? "unavailable" : postSync.visibleBoundsB() : visibleBounds,
                    false, false,
                    postRapier != null && postRapier.jointHandleValid(),
                    postRapier == null ? "unavailable" : postRapier.jointLinearImpulse(),
                    postRapier == null ? "unavailable" : postRapier.jointAngularImpulse());
            return this.recordSnapshot(snapshot);
        }

        public synchronized String firstFailureClassification() {
            final LifecycleSnapshot postInsert = this.lifecycleSnapshots.get(POST_JOINT_INSERT);
            final PhaseSnapshot preSolver = this.latestSnapshots.get(PRE_SOLVER);
            if (postInsert != null && postInsert.rigidBodySetContainsA() && postInsert.rigidBodySetContainsB()
                    && preSolver != null && (!preSolver.rapierBodyAPresent() || !preSolver.rapierBodyBPresent())) {
                return "RAPIER_BODY_REMOVED_BEFORE_SOLVER";
            }
            if (!this.traceComplete()) {
                return "TRACE_INCOMPLETE";
            }
            if (preSolver == null || !preSolver.rapierPlausible()) {
                return "UNKNOWN_FAILURE";
            }
            final PhaseSnapshot postRapier = this.latestSnapshots.get(POST_RAPIER_SOLVER);
            final PhaseSnapshot postSync = this.latestSnapshots.get(POST_SABLE_SYNC_PRE_CLEANUP);
            if (postRapier != null && !postRapier.rapierPlausible()) {
                return "RAPIER_SOLVER_EXPLOSION";
            }
            if (postRapier != null && postRapier.rapierPlausible()
                    && postSync != null && !postSync.logicalPosePlausible()) {
                return "RAPIER_TO_SABLE_SYNC_CORRUPTION";
            }
            if (postRapier != null && postRapier.rapierPlausible()
                    && postSync != null && postSync.logicalPosePlausible() && !postSync.boundsPlausible()) {
                return "VISIBLE_BOUNDS_TRANSFORM_CORRUPTION";
            }
            if (postRapier != null && postRapier.rapierPlausible()
                    && postSync != null && postSync.logicalPosePlausible() && postSync.boundsPlausible()) {
                return "NO_FAILURE";
            }
            return "UNKNOWN_FAILURE";
        }
    }

    public record LifecycleSnapshot(String phase, int sequence, long gameTime,
                                    int bodyAHandle, int bodyBHandle,
                                    boolean sableBodyAPresent, boolean sableBodyBPresent,
                                    boolean activeSubLevelsContainsA, boolean activeSubLevelsContainsB,
                                    boolean rigidBodyMapContainsA, boolean rigidBodyMapContainsB,
                                    boolean rigidBodySetContainsA, boolean rigidBodySetContainsB,
                                    long nativeBodyHandleA, long nativeBodyHandleB,
                                    boolean javaConstraintHandlePresent,
                                    boolean logicalJointMapContainsHandle,
                                    boolean rapierJointSetContainsHandle,
                                    int rigidBodyCount, int jointCount) {
        private LifecycleSnapshot withSequence(final int sequence) {
            return new LifecycleSnapshot(this.phase, sequence, this.gameTime,
                    this.bodyAHandle, this.bodyBHandle,
                    this.sableBodyAPresent, this.sableBodyBPresent,
                    this.activeSubLevelsContainsA, this.activeSubLevelsContainsB,
                    this.rigidBodyMapContainsA, this.rigidBodyMapContainsB,
                    this.rigidBodySetContainsA, this.rigidBodySetContainsB,
                    this.nativeBodyHandleA, this.nativeBodyHandleB,
                    this.javaConstraintHandlePresent, this.logicalJointMapContainsHandle,
                    this.rapierJointSetContainsHandle, this.rigidBodyCount, this.jointCount);
        }

        public String summary() {
            return this.phase + "{sequence=" + this.sequence
                    + ",gameTime=" + this.gameTime
                    + ",bodyAHandle=" + this.bodyAHandle
                    + ",bodyBHandle=" + this.bodyBHandle
                    + ",sableBodyAPresent=" + this.sableBodyAPresent
                    + ",sableBodyBPresent=" + this.sableBodyBPresent
                    + ",activeSubLevelsContainsA=" + this.activeSubLevelsContainsA
                    + ",activeSubLevelsContainsB=" + this.activeSubLevelsContainsB
                    + ",rigidBodyMapContainsA=" + this.rigidBodyMapContainsA
                    + ",rigidBodyMapContainsB=" + this.rigidBodyMapContainsB
                    + ",rigidBodySetContainsA=" + this.rigidBodySetContainsA
                    + ",rigidBodySetContainsB=" + this.rigidBodySetContainsB
                    + ",nativeBodyHandleA=" + this.nativeBodyHandleA
                    + ",nativeBodyHandleB=" + this.nativeBodyHandleB
                    + ",javaConstraintHandlePresent=" + this.javaConstraintHandlePresent
                    + ",logicalJointMapContainsHandle=" + this.logicalJointMapContainsHandle
                    + ",rapierJointSetContainsHandle=" + this.rapierJointSetContainsHandle
                    + ",rigidBodyCount=" + this.rigidBodyCount
                    + ",jointCount=" + this.jointCount + "}";
        }
    }

    public record RemovalRecord(String kind, int sequence, long gameTime,
                                @Nullable UUID bodyUuid, int bodyHandle, long jointHandle,
                                String phaseBoundary, String ownerClass, String ownerMethod, String reason) {
    }

    public record PhaseSnapshot(String phase, int sequence, long gameTime,
                                int bodyAHandle, int bodyBHandle,
                                boolean rapierBodyAPresent, boolean rapierBodyBPresent,
                                String rapierTranslationA, String rapierTranslationB,
                                String rapierRotationA, String rapierRotationB,
                                String rapierLinearVelocityA, String rapierLinearVelocityB,
                                String rapierAngularVelocityA, String rapierAngularVelocityB,
                                boolean rapierFiniteA, boolean rapierFiniteB,
                                boolean rapierPlausibleA, boolean rapierPlausibleB,
                                boolean sableBodyAPresent, boolean sableBodyBPresent,
                                String logicalPoseA, String logicalPoseB,
                                boolean logicalPoseFiniteA, boolean logicalPoseFiniteB,
                                boolean logicalPosePlausibleA, boolean logicalPosePlausibleB,
                                String visibleBoundsA, String visibleBoundsB,
                                boolean boundsPlausibleA, boolean boundsPlausibleB,
                                boolean jointHandleValid,
                                String jointLinearImpulse, String jointAngularImpulse) {

        private PhaseSnapshot withSequence(final int sequence) {
            return new PhaseSnapshot(this.phase, sequence, this.gameTime, this.bodyAHandle, this.bodyBHandle,
                    this.rapierBodyAPresent, this.rapierBodyBPresent,
                    this.rapierTranslationA, this.rapierTranslationB,
                    this.rapierRotationA, this.rapierRotationB,
                    this.rapierLinearVelocityA, this.rapierLinearVelocityB,
                    this.rapierAngularVelocityA, this.rapierAngularVelocityB,
                    this.rapierFiniteA, this.rapierFiniteB,
                    this.rapierPlausibleA, this.rapierPlausibleB,
                    this.sableBodyAPresent, this.sableBodyBPresent,
                    this.logicalPoseA, this.logicalPoseB,
                    this.logicalPoseFiniteA, this.logicalPoseFiniteB,
                    this.logicalPosePlausibleA, this.logicalPosePlausibleB,
                    this.visibleBoundsA, this.visibleBoundsB,
                    this.boundsPlausibleA, this.boundsPlausibleB,
                    this.jointHandleValid,
                    this.jointLinearImpulse, this.jointAngularImpulse);
        }

        public boolean rapierPlausible() {
            return this.rapierBodyAPresent && this.rapierBodyBPresent
                    && this.rapierFiniteA && this.rapierFiniteB
                    && this.rapierPlausibleA && this.rapierPlausibleB;
        }

        public boolean logicalPosePlausible() {
            return this.sableBodyAPresent && this.sableBodyBPresent
                    && this.logicalPoseFiniteA && this.logicalPoseFiniteB
                    && this.logicalPosePlausibleA && this.logicalPosePlausibleB;
        }

        public boolean boundsPlausible() {
            return this.boundsPlausibleA && this.boundsPlausibleB;
        }

        public String summary() {
            return this.phase + "{sequence=" + this.sequence
                    + ",gameTime=" + this.gameTime
                    + ",bodyAHandle=" + this.bodyAHandle
                    + ",bodyBHandle=" + this.bodyBHandle
                    + ",rapierBodyAPresent=" + this.rapierBodyAPresent
                    + ",rapierBodyBPresent=" + this.rapierBodyBPresent
                    + ",rapierTranslationA=" + this.rapierTranslationA
                    + ",rapierTranslationB=" + this.rapierTranslationB
                    + ",rapierRotationA=" + this.rapierRotationA
                    + ",rapierRotationB=" + this.rapierRotationB
                    + ",rapierLinearVelocityA=" + this.rapierLinearVelocityA
                    + ",rapierLinearVelocityB=" + this.rapierLinearVelocityB
                    + ",rapierAngularVelocityA=" + this.rapierAngularVelocityA
                    + ",rapierAngularVelocityB=" + this.rapierAngularVelocityB
                    + ",rapierFiniteA=" + this.rapierFiniteA
                    + ",rapierFiniteB=" + this.rapierFiniteB
                    + ",sableBodyAPresent=" + this.sableBodyAPresent
                    + ",sableBodyBPresent=" + this.sableBodyBPresent
                    + ",logicalPoseA=" + this.logicalPoseA
                    + ",logicalPoseB=" + this.logicalPoseB
                    + ",visibleBoundsA=" + this.visibleBoundsA
                    + ",visibleBoundsB=" + this.visibleBoundsB
                    + ",jointHandleValid=" + this.jointHandleValid
                    + ",jointLinearImpulse=" + this.jointLinearImpulse
                    + ",jointAngularImpulse=" + this.jointAngularImpulse
                    + "}";
        }
    }
}
