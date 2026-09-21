package dev.ryanhcode.sable.forge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.EntityStorage;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.RestoredContraptionClientSyncDecision;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.mixin.m28.AbstractEntityVisualAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Repairs and verifies Flywheel admission after Create has decoded a restored spawn payload. */
public final class SableM28RestoredContraptionClientSync {
    private static final Map<UUID, PendingAdmission> PENDING = new ConcurrentHashMap<>();
    private static boolean clientEventsRegistered;

    private SableM28RestoredContraptionClientSync() {
    }

    /** Installs the admission driver from Sable's guaranteed physical-client bootstrap. */
    public static synchronized void registerClientEvents() {
        if (clientEventsRegistered) {
            return;
        }
        clientEventsRegistered = true;
        MinecraftForge.EVENT_BUS.addListener(SableM28RestoredContraptionClientSync::clientTick);
    }

    public static void afterSpawnData(final AbstractContraptionEntity raw) {
        if (!(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final int expectedBlocks = SableM28NormalWorldCceSync.expectedCapturedBlocks(entity);
        final int actualBlocks = capturedBlocks(entity);
        final int expectedSails = SableM28NormalWorldCceSync.expectedSymmetricSails(entity);
        final int actualSails = SableM28NormalWorldCceSync.symmetricSailCount(entity);
        final RestoredContraptionClientSyncDecision.Decision decision =
                RestoredContraptionClientSyncDecision.evaluateDetailed(
                        SableM28NormalWorldCceSync.isTarget(entity), entity.level().isClientSide,
                        SableCreateContraptionContext.getContainingSubLevel(entity) == null,
                        entity.isRemoved(), expectedBlocks, actualBlocks, expectedSails, actualSails);
        trace(entity, "CLIENT_REFRESH_EVALUATED", "decision=" + decision.action()
                + " reason=" + decision.reason() + ' ' + stateDetails(entity, null));
        if (decision.action() == RestoredContraptionClientSyncDecision.Action.NONE) {
            trace(entity, "CLIENT_REFRESH_SKIPPED", "reason=" + decision.reason());
            return;
        }
        if (decision.action() == RestoredContraptionClientSyncDecision.Action.FAIL_PAYLOAD_VALIDATION) {
            trace(entity, "CLIENT_REFRESH_SKIPPED", "reason=" + decision.reason());
            Sable.LOGGER.error("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage=CLIENT_PAYLOAD_INVALID entityId={} "
                            + "entityUuid={} reason={} expectedCapturedBlockCount={} actualCapturedBlockCount={} "
                            + "expectedSymmetricSailCount={} actualSymmetricSailCount={}",
                    entity.getId(), entity.getUUID(), decision.reason(), expectedBlocks, actualBlocks,
                    expectedSails, actualSails);
            return;
        }

        final PendingAdmission pending = new PendingAdmission(entity, expectedBlocks, expectedSails);
        final PendingAdmission replaced = PENDING.put(entity.getUUID(), pending);
        if (replaced != null) {
            removePending(replaced, RestoredContraptionVisualAdmissionState.TerminalReason.STATE_REPLACED,
                    "replacementEntityIdentity=" + System.identityHashCode(entity));
        }
        trace(entity, "CLIENT_ADMISSION_REGISTERED", stateDetails(entity, pending));
        trace(entity, "CLIENT_REFRESH_ACCEPTED", stateDetails(entity, pending));

        final VisualizationManager manager = VisualizationManager.get(entity.level());
        final boolean visualPresent = visualPresent(manager, entity);
        final Prerequisites prerequisites = prerequisites(manager, entity, expectedBlocks, expectedSails);
        observePrerequisites(pending, prerequisites, "POST_DECODE");
        final RestoredContraptionVisualAdmissionState.Transition transition =
                pending.state.afterDecode(manager != null, prerequisites.flywheelWillAccept(), visualPresent);
        if (transition == RestoredContraptionVisualAdmissionState.Transition.VISUAL_ALREADY_PRESENT) {
            trace(entity, "CLIENT_VISUAL_PRESENT_AFTER_REFRESH",
                    "source=EXISTING_VISUAL " + stateDetails(entity, pending));
        } else if (transition == RestoredContraptionVisualAdmissionState.Transition.REQUEST_INITIAL_REFRESH) {
            requestRefresh(pending, manager, prerequisites, "POST_DECODE");
        } else {
            trace(entity, "CLIENT_REFRESH_SKIPPED",
                    "reason=" + (manager == null ? "VISUAL_MANAGER_MISSING" : "FLYWHEEL_ADMISSION_NOT_READY")
                            + " retryPending=true " + stateDetails(entity, pending));
        }
    }

    /** Runs at a client tick boundary, after spawn decoding and alongside Flywheel's queue lifecycle. */
    private static void clientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        advanceClientAdmissions();
    }

    static void advanceClientAdmissions() {
        final Level currentLevel = Minecraft.getInstance().level;
        if (currentLevel == null) {
            removeAll(RestoredContraptionVisualAdmissionState.TerminalReason.LEVEL_UNLOAD,
                    "reason=NO_CURRENT_CLIENT_LEVEL");
            return;
        }
        for (final PendingAdmission pending : PENDING.values()) {
            final ControlledContraptionEntity entity = pending.entity;
            if (entity.level() != currentLevel) {
                removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.LEVEL_UNLOAD,
                        "reason=CLIENT_LEVEL_CHANGED");
                continue;
            }
            if (pending.state.markFirstTick()) {
                trace(entity, "CLIENT_ADMISSION_FIRST_TICK", stateDetails(entity, pending));
            }
            final boolean entityAlive = entity.isAlive() && !entity.isRemoved();
            final boolean payloadValid = capturedBlocks(entity) == pending.expectedBlocks
                    && (pending.expectedSails <= 0
                    || SableM28NormalWorldCceSync.symmetricSailCount(entity) == pending.expectedSails);
            final VisualizationManager manager = VisualizationManager.get(currentLevel);
            final boolean visualPresent = visualPresent(manager, entity);
            final Prerequisites prerequisites =
                    prerequisites(manager, entity, pending.expectedBlocks, pending.expectedSails);
            observePrerequisites(pending, prerequisites, "CLIENT_TICK");
            final RestoredContraptionVisualAdmissionState.Transition transition =
                    pending.state.clientTick(entityAlive, payloadValid, manager != null,
                            prerequisites.flywheelWillAccept(), visualPresent);
            switch (transition) {
                case REQUEST_INITIAL_REFRESH -> {
                    trace(entity, "CLIENT_ADMISSION_RETRY",
                            "kind=FIRST_READY_BOUNDARY " + stateDetails(entity, pending));
                    requestRefresh(pending, manager, prerequisites, "FIRST_READY_CLIENT_TICK");
                }
                case REQUEST_SAFE_BOUNDARY_RETRY -> {
                    trace(entity, "CLIENT_ADMISSION_RETRY",
                            "kind=SAFE_BOUNDARY_RETRY " + stateDetails(entity, pending));
                    requestRefresh(pending, manager, prerequisites, "SAFE_BOUNDARY_RETRY");
                }
                case VISUAL_PRESENT -> trace(entity, "CLIENT_VISUAL_PRESENT_AFTER_REFRESH",
                        "source=FLYWHEEL_STORAGE " + stateDetails(entity, pending));
                case ENTITY_RETIRED -> {
                    trace(entity, "CLIENT_REFRESH_SKIPPED",
                            "reason=ENTITY_REMOVED " + stateDetails(entity, pending));
                    removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.ENTITY_REMOVED,
                            "source=CLIENT_TICK");
                }
                case PAYLOAD_REJECTED -> {
                    trace(entity, "CLIENT_REFRESH_SKIPPED",
                            "reason=PAYLOAD_COUNT_MISMATCH " + stateDetails(entity, pending));
                    removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.PAYLOAD_INVALID,
                            "source=CLIENT_TICK");
                }
                case TIMED_OUT -> {
                    trace(entity, "CLIENT_ADMISSION_TIMEOUT", stateDetails(entity, pending));
                    trace(entity, "CLIENT_POST_DECODE_VISUAL_TIMEOUT", stateDetails(entity, pending));
                    removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.TIMEOUT,
                            "source=CLIENT_TICK");
                }
                default -> {
                }
            }
        }
    }

    /** Records the actual ContraptionVisual constructor, independently of semantic target matching. */
    public static void visualCreated(final AbstractContraptionEntity raw, final Object visual) {
        if (!(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final PendingAdmission pending = PENDING.get(entity.getUUID());
        if (pending == null) {
            return;
        }
        pending.visualIdentity = System.identityHashCode(visual);
        trace(entity, "FLYWHEEL_CONTRAPTION_VISUAL_CONSTRUCTED",
                "visualIdentity=" + pending.visualIdentity + ' ' + stateDetails(entity, pending));
        if (pending.state.visualCreated() == RestoredContraptionVisualAdmissionState.Transition.VISUAL_PRESENT) {
            trace(entity, "CLIENT_VISUAL_PRESENT_AFTER_REFRESH",
                    "source=CONTRAPTION_VISUAL_CONSTRUCTOR " + stateDetails(entity, pending));
        }
    }

    /** The first embedding update proves that the admitted visual entered Flywheel's frame plan. */
    public static void visualFrame(final AbstractContraptionEntity raw, final Object visual) {
        if (!(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final PendingAdmission pending = PENDING.get(entity.getUUID());
        if (pending == null) {
            return;
        }
        pending.visualIdentity = System.identityHashCode(visual);
        if (pending.state.geometryObserved() == RestoredContraptionVisualAdmissionState.Transition.FIRST_GEOMETRY) {
            trace(entity, "CLIENT_FIRST_GEOMETRY",
                    "source=FLYWHEEL_EMBEDDING_UPDATE " + stateDetails(entity, pending));
            trace(entity, "CLIENT_ADMISSION_COMPLETED",
                    "reason=SUCCESS " + stateDetails(entity, pending));
            removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.SUCCESS,
                    "source=FLYWHEEL_EMBEDDING_UPDATE");
        }
    }

    public static void visualRemoved(final AbstractContraptionEntity raw, final Object visual) {
        if (!(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final PendingAdmission pending = PENDING.get(entity.getUUID());
        if (pending != null && pending.visualIdentity == System.identityHashCode(visual)) {
            pending.visualIdentity = 0;
        }
    }

    public static void entityLeft(final Entity raw, final Level level) {
        if (!level.isClientSide || !(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        SableM29RestoredControllerSyncGuard.entityLeft(entity);
        final PendingAdmission pending = PENDING.remove(entity.getUUID());
        if (pending != null) {
            pending.state.retire(RestoredContraptionVisualAdmissionState.TerminalReason.ENTITY_REMOVED);
            trace(entity, "CLIENT_ADMISSION_REMOVED",
                    "reason=ENTITY_REMOVED source=ENTITY_LEAVE_LEVEL " + stateDetails(entity, pending));
        }
    }

    public static void levelUnloaded(final Level level) {
        if (level.isClientSide) {
            SableM29RestoredControllerSyncGuard.levelUnloaded(level);
            for (final PendingAdmission pending : PENDING.values()) {
                if (pending.entity.level() == level) {
                    removePending(pending, RestoredContraptionVisualAdmissionState.TerminalReason.LEVEL_UNLOAD,
                            "source=LEVEL_UNLOAD");
                }
            }
        }
    }

    private static void requestRefresh(final PendingAdmission pending,
                                       final @Nullable VisualizationManager manager,
                                       final Prerequisites prerequisites,
                                       final String boundary) {
        final ControlledContraptionEntity entity = pending.entity;
        if (manager == null) {
            trace(entity, "CLIENT_REFRESH_SKIPPED",
                    "reason=VISUAL_MANAGER_MISSING boundary=" + boundary + ' ' + stateDetails(entity, pending));
            return;
        }
        if (!prerequisites.flywheelWillAccept()) {
            trace(entity, "CLIENT_REFRESH_SKIPPED",
                    "reason=FLYWHEEL_ADMISSION_NOT_READY boundary=" + boundary + ' '
                            + prerequisites.details() + ' ' + stateDetails(entity, pending));
            return;
        }
        trace(entity, "CLIENT_VISUAL_REMOVE_REQUESTED",
                "boundary=" + boundary + ' ' + stateDetails(entity, pending));
        manager.entities().queueRemove(entity);
        trace(entity, "CLIENT_VISUAL_ADD_REQUESTED",
                "boundary=" + boundary + ' ' + stateDetails(entity, pending));
        manager.entities().queueAdd(entity);
        trace(entity, "CLIENT_VISUAL_QUEUE_COMPLETE",
                "meaning=REQUEST_SUBMITTED_NOT_ADMISSION_SUCCESS boundary=" + boundary + ' '
                        + stateDetails(entity, pending));
        if (SableM28NormalWorldCceSync.enabled()) {
            Sable.LOGGER.info("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage=CLIENT_VISUAL_REQUEUED entityId={} "
                            + "entityUuid={} boundary={} expectedCapturedBlockCount={} actualCapturedBlockCount={}",
                    entity.getId(), entity.getUUID(), boundary, pending.expectedBlocks, capturedBlocks(entity));
        }
    }

    private static boolean visualPresent(final @Nullable VisualizationManager manager,
                                         final ControlledContraptionEntity entity) {
        if (manager == null || !(manager.entities() instanceof final VisualManagerImpl<?, ?> implementation)) {
            return false;
        }
        for (final Visual visual : implementation.getStorage().getAllVisuals()) {
            if (visual instanceof final AbstractEntityVisualAccessor accessor
                    && accessor.sable$getEntity() == entity) {
                return true;
            }
        }
        return false;
    }

    /** Flywheel queue probes use this to remain scoped to active reverse restores. */
    public static boolean isPending(final Object value) {
        return value instanceof final ControlledContraptionEntity entity
                && PENDING.get(entity.getUUID()) != null;
    }

    public static void flywheelAddEvaluated(final Entity raw, final boolean accepted) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !isPending(entity)) {
            return;
        }
        final EntityVisualizer<? super ControlledContraptionEntity> visualizer =
                VisualizationHelper.getVisualizer(entity);
        trace(entity, accepted ? "FLYWHEEL_ADD_ACCEPTED" : "FLYWHEEL_CREATE_VISUAL_REJECTED",
                "stage=VisualManagerImpl.queueAdd/willAccept accepted=" + accepted
                        + " entityAlive=" + entity.isAlive()
                        + " entityRemoved=" + entity.isRemoved()
                        + " levelPresent=" + (entity.level() != null)
                        + " visualizerFound=" + (visualizer != null));
    }

    public static void flywheelAddDequeued(final Entity raw) {
        if (raw instanceof final ControlledContraptionEntity entity && isPending(entity)) {
            trace(entity, "FLYWHEEL_ADD_DEQUEUED", "stage=VisualManagerImpl.processQueue/Storage.add");
        }
    }

    public static void flywheelVisualizerFound(final Entity raw, final boolean found) {
        if (raw instanceof final ControlledContraptionEntity entity && isPending(entity)) {
            trace(entity, found ? "FLYWHEEL_VISUALIZER_FOUND" : "FLYWHEEL_CREATE_VISUAL_REJECTED",
                    "stage=EntityStorage.createRaw visualizerFound=" + found);
        }
    }

    public static void flywheelCreateVisual(final Entity raw, final String phase, final @Nullable Object visual) {
        if (!(raw instanceof final ControlledContraptionEntity entity) || !isPending(entity)) {
            return;
        }
        trace(entity, "ENTER".equals(phase)
                        ? "FLYWHEEL_CREATE_VISUAL_ENTER"
                        : visual == null ? "FLYWHEEL_CREATE_VISUAL_REJECTED" : "FLYWHEEL_CREATE_VISUAL_RETURNED",
                "stage=SimpleEntityVisualizer.createVisual phase=" + phase
                        + " visualClass=" + (visual == null ? "none" : visual.getClass().getName()));
    }

    public static void flywheelStorageInserted(final Entity raw, final boolean inserted) {
        if (raw instanceof final ControlledContraptionEntity entity && isPending(entity)) {
            trace(entity, inserted ? "FLYWHEEL_STORAGE_INSERTED" : "FLYWHEEL_CREATE_VISUAL_REJECTED",
                    "stage=Storage.add inserted=" + inserted);
        }
    }

    private static Prerequisites prerequisites(final @Nullable VisualizationManager manager,
                                               final ControlledContraptionEntity entity,
                                               final int expectedBlocks,
                                               final int expectedSails) {
        final boolean entityLookupPresent = entity.level().getEntity(entity.getId()) == entity;
        final boolean payloadPresent = capturedBlocks(entity) == expectedBlocks
                && (expectedSails <= 0
                || SableM28NormalWorldCceSync.symmetricSailCount(entity) == expectedSails);
        final BlockPos controllerPos = SableCreateContraptionContext.getControllerPos(entity);
        final BlockPos bearingPos = SableM28NormalWorldCceSync.bearingPos(entity);
        final boolean bearingBlockEntityPresent = bearingPos != null
                && entity.level().getBlockEntity(bearingPos) instanceof MechanicalBearingBlockEntity;
        final boolean visualizerFound = VisualizationHelper.getVisualizer(entity) != null;
        final boolean flywheelWillAccept = manager != null
                && manager.entities() instanceof final VisualManagerImpl<?, ?> implementation
                && implementation.getStorage() instanceof final EntityStorage storage
                && storage.willAccept(entity);
        return new Prerequisites(entityLookupPresent, entity.isAlive(), entity.isRemoved(), payloadPresent,
                visualizerFound, flywheelWillAccept, controllerPos != null, bearingBlockEntityPresent,
                bearingPos == null ? "none" : entity.level().getBlockState(bearingPos).toString());
    }

    private static void observePrerequisites(final PendingAdmission pending,
                                             final Prerequisites prerequisites,
                                             final String boundary) {
        if (prerequisites.equals(pending.lastPrerequisites)) {
            return;
        }
        pending.lastPrerequisites = prerequisites;
        pending.prerequisiteRevision++;
        traceTransition(pending.entity, "CLIENT_ADMISSION_PREREQUISITES",
                "CLIENT_ADMISSION_PREREQUISITES:" + pending.prerequisiteRevision,
                "boundary=" + boundary + ' ' + prerequisites.details());
    }

    private static void removeAll(final RestoredContraptionVisualAdmissionState.TerminalReason reason,
                                  final String details) {
        for (final PendingAdmission pending : PENDING.values()) {
            removePending(pending, reason, details);
        }
    }

    private static void removePending(final PendingAdmission pending,
                                      final RestoredContraptionVisualAdmissionState.TerminalReason reason,
                                      final String details) {
        final boolean detached = PENDING.remove(pending.entity.getUUID(), pending);
        if (!detached && reason != RestoredContraptionVisualAdmissionState.TerminalReason.STATE_REPLACED) {
            return;
        }
        pending.state.retire(reason);
        trace(pending.entity, "CLIENT_ADMISSION_REMOVED",
                "reason=" + reason + ' ' + details + ' ' + stateDetails(pending.entity, pending));
    }

    private static int capturedBlocks(final AbstractContraptionEntity entity) {
        return entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size();
    }

    private static String stateDetails(final ControlledContraptionEntity entity,
                                       final @Nullable PendingAdmission pending) {
        final BlockPos controllerPos = SableCreateContraptionContext.getControllerPos(entity);
        final BlockPos bearingPos = SableM28NormalWorldCceSync.bearingPos(entity);
        final boolean bearingPresent = bearingPos != null
                && entity.level().getBlockEntity(bearingPos) instanceof MechanicalBearingBlockEntity;
        return "controllerPos=" + controllerPos
                + " bearingPos=" + bearingPos
                + " bearingBlockEntityPresent=" + bearingPresent
                + " restoreMarkerPresent=" + SableM28NormalWorldCceSync.isTarget(entity)
                + " visualManagerPresent=" + (VisualizationManager.get(entity.level()) != null)
                + " visualPresent=" + (pending != null && pending.state.visualPresent())
                + " refreshRequested=" + (pending != null && pending.state.refreshRequests() > 0)
                + " refreshCompleted=" + (pending != null && pending.state.hasObservedGeometry())
                + " clientTickAgeSinceDecode=" + (pending == null ? 0 : pending.state.clientTickAge())
                + " visualIdentity=" + (pending == null ? 0 : pending.visualIdentity);
    }

    private static void trace(final ControlledContraptionEntity entity,
                              final String event,
                              final String details) {
        SableM28NormalWorldCceSync.traceM29Entity(event, entity, details);
    }

    private static void traceTransition(final ControlledContraptionEntity entity,
                                        final String event,
                                        final String dedupeKey,
                                        final String details) {
        SableM28NormalWorldCceSync.traceM29Entity(event, dedupeKey, entity, details);
    }

    private record Prerequisites(boolean entityLookupPresent,
                                 boolean entityAlive,
                                 boolean entityRemoved,
                                 boolean payloadPresent,
                                 boolean visualizerFound,
                                 boolean flywheelWillAccept,
                                 boolean controllerRelationPresent,
                                 boolean bearingBlockEntityPresent,
                                 String bearingBlockState) {
        private String details() {
            return "entityLookupPresent=" + this.entityLookupPresent
                    + " entityAlive=" + this.entityAlive
                    + " entityRemoved=" + this.entityRemoved
                    + " payloadPresent=" + this.payloadPresent
                    + " visualizerFound=" + this.visualizerFound
                    + " flywheelWillAccept=" + this.flywheelWillAccept
                    + " controllerRelationPresent=" + this.controllerRelationPresent
                    + " bearingBlockEntityPresent=" + this.bearingBlockEntityPresent
                    + " bearingBlockState=" + this.bearingBlockState;
        }
    }

    private static final class PendingAdmission {
        private final ControlledContraptionEntity entity;
        private final int expectedBlocks;
        private final int expectedSails;
        private final RestoredContraptionVisualAdmissionState state =
                new RestoredContraptionVisualAdmissionState();
        private volatile int visualIdentity;
        private volatile int prerequisiteRevision;
        private volatile Prerequisites lastPrerequisites;

        private PendingAdmission(final ControlledContraptionEntity entity,
                                 final int expectedBlocks,
                                 final int expectedSails) {
            this.entity = entity;
            this.expectedBlocks = expectedBlocks;
            this.expectedSails = expectedSails;
        }
    }
}
