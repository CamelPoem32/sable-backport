package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.MechanicalBearingBlockEntityAccessor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Preserves independently owned Mechanical Bearing payloads across M22 block transfer. */
public final class SableNestedBearingOwnershipTransfer {
    public static final String TRACE_PROPERTY = "sable.m28.traceNestedBearingPayload";
    private static final ThreadLocal<TransferSession> ACTIVE = new ThreadLocal<>();
    private static final List<PendingVerification> PENDING = new ArrayList<>();
    private static final AtomicLong TRANSACTION_IDS = new AtomicLong();

    private SableNestedBearingOwnershipTransfer() {
    }

    public static void begin(final ServerLevel sourceLevel, final Collection<BlockPos> selectedBlocks,
                             final String operation) throws AssemblyException {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Nested bearing ownership transfer is already active");
        }
        final TransferSession session = new TransferSession(
                Objects.requireNonNull(sourceLevel, "sourceLevel"), copySelectedBlocks(selectedBlocks),
                Objects.requireNonNull(operation, "operation"));
        session.logTransition("CAPTURE_BEGIN", null, null, 0, null, null, "validationOnly=true");
        try {
            session.capture();
            ACTIVE.set(session);
        } catch (final AssemblyException | RuntimeException exception) {
            session.logTransition("TRANSFER_ABORTED", null, null, 0, null, null,
                    "stage=CAPTURE_VALIDATION exception=" + exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public static void bindTransform(final AssemblyTransform transform) {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            session.transform = transform;
        }
    }

    public static void destinationAllocated(final ServerSubLevel destination) {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            session.destinationAllocated(destination);
        }
    }

    public static void blockCopyComplete(final int copiedBlockCount) {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            session.blockCopyComplete(copiedBlockCount);
        }
    }

    public static void afterBlockTransfer(final Level sourceLevel, final Level targetLevel,
                                          final BlockPos sourcePos, final BlockPos targetPos) {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            if (!(targetLevel instanceof final ServerLevel serverTarget)) {
                throw new IllegalStateException("Nested bearing destination is not a ServerLevel");
            }
            session.restoreBearing(sourceLevel, serverTarget, sourcePos, targetPos);
        }
    }

    public static void beforeSourceDestruction() {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            session.commitSourceOwnership();
        }
    }

    public static void afterBlockMove() {
        final TransferSession session = ACTIVE.get();
        if (session != null) {
            session.verifyImmediateOwnership();
        }
    }

    public static void finish(final boolean success) {
        final TransferSession session = ACTIVE.get();
        if (session == null) {
            return;
        }
        try {
            if (success) {
                session.scheduleNextTickVerification();
                session.logAssemblyTransaction("COMMIT", null);
            } else {
                session.rollbackDestinations();
                session.rollbackOuterAssembly("NESTED_RESTORE_OR_BLOCK_COPY_FAILURE");
                session.logTransition("TRANSFER_ABORTED", null, null, 0, null, null,
                        "stage=BLOCK_TRANSFER sourceOwnershipCommitted=" + session.sourceOwnershipCommitted);
            }
        } finally {
            ACTIVE.remove();
        }
    }

    public static void verifyPending(final ServerLevel level) {
        synchronized (PENDING) {
            PENDING.removeIf(verification -> verification.verifyIfDue(level));
        }
    }

    private static final class TransferSession {
        private final ServerLevel sourceLevel;
        private final Set<BlockPos> selectedBlocks;
        private final String operation;
        private final Map<BlockPos, NestedSnapshot> snapshots = new LinkedHashMap<>();
        private final long transactionId = TRANSACTION_IDS.incrementAndGet();
        private @Nullable AssemblyTransform transform;
        private @Nullable ServerSubLevel destinationSubLevel;
        private boolean sourceOwnershipCommitted;
        private int copiedBlockCount;

        private TransferSession(final ServerLevel sourceLevel, final Set<BlockPos> selectedBlocks,
                                final String operation) {
            this.sourceLevel = sourceLevel;
            this.selectedBlocks = selectedBlocks;
            this.operation = operation;
            this.logAssemblyTransaction("BEGIN", null);
        }

        private void capture() throws AssemblyException {
            final Set<Object> steeringNetworks = this.selectedBlocks.stream()
                    .filter(pos -> BuiltInRegistries.BLOCK.getKey(this.sourceLevel.getBlockState(pos).getBlock())
                            .toString().equals("simulated:steering_wheel"))
                    .map(this.sourceLevel::getBlockEntity)
                    .filter(KineticBlockEntity.class::isInstance)
                    .map(KineticBlockEntity.class::cast)
                    .map(kinetic -> kinetic.network)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (final BlockPos selected : this.selectedBlocks) {
                if (!(this.sourceLevel.getBlockEntity(selected) instanceof final MechanicalBearingBlockEntity bearing)) {
                    continue;
                }
                final Direction facing = bearing.getBlockState().getValue(BearingBlock.FACING);
                final ControlledContraptionEntity existing = bearing.getMovedContraption();
                final BlockPos payloadPos = selected.relative(facing);
                final boolean existingIsBearingContraption = existing != null
                        && existing.getContraption() instanceof BearingContraption;
                final NestedBearingTransactionDecision.SourceFailure sourceFailure =
                        NestedBearingTransactionDecision.sourceFailure(
                                bearing.isRunning(), existing != null, existing != null && existing.isRemoved(),
                                existingIsBearingContraption, this.sourceLevel.getBlockState(payloadPos).isAir());
                if (sourceFailure == NestedBearingTransactionDecision.SourceFailure.DANGLING_DEAD_NESTED_CCE) {
                    this.logTransition("TRANSFER_ABORTED", selected, existing, 0, null, null,
                            "stage=CAPTURE_VALIDATION reason=DANGLING_DEAD_NESTED_CCE");
                    throw new IllegalStateException("DANGLING_DEAD_NESTED_CCE at " + selected
                            + ": bearing references removed entity " + existing.getId());
                }
                if (sourceFailure == NestedBearingTransactionDecision.SourceFailure.INVALID_NESTED_CONTRAPTION) {
                    throw new IllegalStateException("Nested bearing at " + selected
                            + " references an entity without a BearingContraption");
                }
                if (sourceFailure == NestedBearingTransactionDecision.SourceFailure.DANGLING_MISSING_NESTED_CCE) {
                    this.logTransition("TRANSFER_ABORTED", selected, null, 0, null, null,
                            "stage=CAPTURE_VALIDATION reason=DANGLING_MISSING_NESTED_CCE");
                    throw new IllegalStateException("DANGLING_MISSING_NESTED_CCE at " + selected
                            + ": running bearing has no entity and its payload position is air");
                }
                final BearingContraption existingContraption = existing != null && !existing.isRemoved()
                        && existing.getContraption() instanceof final BearingContraption bearingContraption
                        ? bearingContraption : null;
                final NestedBearingCaptureDecision.Action captureDecision = NestedBearingCaptureDecision.evaluate(
                        existingContraption != null, steeringNetworks, bearing.network,
                        this.sourceLevel.getBlockState(payloadPos).isAir());
                if (captureDecision == NestedBearingCaptureDecision.Action.SNAPSHOT_EXISTING) {
                    final BearingContraption contraption = Objects.requireNonNull(existingContraption,
                            "existingContraption");
                    this.addSnapshot(bearing, facing, contraption, existing, SourceOwnership.NESTED_ENTITY);
                    continue;
                }
                if (captureDecision != NestedBearingCaptureDecision.Action.DISCOVER_STATIC_PAYLOAD) {
                    this.logTransition("SNAPSHOT_SKIPPED_NO_NESTED_CCE", selected, existing, 0,
                            bearing.network == null ? "bearing.network" : null, bearing.network,
                            "decision=" + captureDecision + " payloadIsAir="
                                    + this.sourceLevel.getBlockState(payloadPos).isAir());
                    continue;
                }
                final BearingContraption discovered = new BearingContraption(false, facing);
                if (!discovered.assemble(this.sourceLevel, selected)) {
                    throw new IllegalStateException("Create rejected nested bearing payload at " + payloadPos);
                }
                this.addSnapshot(bearing, facing, discovered, null, SourceOwnership.STATIC_PAYLOAD);
            }
            this.logTransition("VALIDATION_PASSED", null, null,
                    this.snapshots.values().stream().mapToInt(snapshot -> snapshot.entries.size()).sum(),
                    null, null, "snapshotCount=" + this.snapshots.size());
        }

        private void addSnapshot(final MechanicalBearingBlockEntity bearing, final Direction facing,
                                 final BearingContraption contraption,
                                 final @Nullable ControlledContraptionEntity entity,
                                 final SourceOwnership ownership) {
            final Set<BlockPos> capturedSources = contraption.getBlocks().keySet().stream()
                    .map(local -> contraption.anchor.offset(local).immutable())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            final Set<BlockPos> hullOverlap = NestedBearingCaptureDecision.overlap(
                    capturedSources, this.selectedBlocks);
            if (!hullOverlap.isEmpty()) {
                throw new IllegalStateException("Nested bearing payload overlaps stationary outer hull: " + hullOverlap);
            }
            if (contraption.getBlocks().isEmpty()) {
                throw new IllegalStateException("Nested bearing payload snapshot is empty at " + bearing.getBlockPos());
            }

            final MechanicalBearingBlockEntityAccessor accessor = (MechanicalBearingBlockEntityAccessor) bearing;
            final int movementMode = accessor.sable$getMovementMode() == null
                    ? RotationMode.ROTATE_PLACE.ordinal()
                    : accessor.sable$getMovementMode().getValue();
            final float angle = entity == null ? bearing.getInterpolatedAngle(0.0F) : entity.getAngle(0.0F);
            final NestedSnapshot snapshot = new NestedSnapshot(
                    bearing.getBlockPos().immutable(), facing, contraption.anchor.immutable(),
                    contraption.writeNBT(false).copy(), copyEntries(contraption), angle, movementMode,
                    accessor.sable$getSequencedAngleLimit(), ownership, entity, contraption);
            this.snapshots.put(snapshot.sourceBearingPos, snapshot);
            log("SNAPSHOT_CREATED", snapshot, null, null,
                    "capturedSourcePositions=" + capturedSources + " hullOverlap=[]");
            this.logTransition("SNAPSHOT_CREATED", snapshot.sourceBearingPos, entity,
                    snapshot.entries.size(), null, null, "sourceOwnership=" + ownership);
        }

        private void restoreBearing(final Level sourceLevel, final ServerLevel targetLevel,
                                    final BlockPos sourcePos, final BlockPos targetPos) {
            final NestedSnapshot snapshot = this.snapshots.get(sourcePos);
            if (snapshot == null) {
                return;
            }
            this.logTransition("DESTINATION_MAPPING_BEGIN", sourcePos, snapshot.sourceEntity,
                    snapshot.entries.size(), null, null, "destinationBearingPos=" + targetPos);
            if (this.transform == null) {
                throw new IllegalStateException("Nested bearing transfer has no bound assembly transform");
            }
            if (!(targetLevel.getBlockEntity(targetPos) instanceof final MechanicalBearingBlockEntity targetBearing)) {
                throw new IllegalStateException("Destination Mechanical Bearing is missing at " + targetPos);
            }
            final Direction targetFacing = targetBearing.getBlockState().getValue(BearingBlock.FACING);
            final BlockPos targetAnchor = targetPos.relative(targetFacing);
            ControlledContraptionEntity restoredEntity = null;
            boolean addFreshEntityReturn = false;
            NestedBearingRegistrationDecision.Failure registrationFailure =
                    NestedBearingRegistrationDecision.Failure.ADD_REJECTED;
            try {
                this.logAssemblyTransaction("NESTED_RESTORE_BEGIN", null);
                this.logTransition("RESTORE_BEGIN", sourcePos, snapshot.sourceEntity,
                        snapshot.entries.size(), null, null, "destinationAnchor=" + targetAnchor);
                final BearingContraption restored = new BearingContraption();
                restored.readNBT(targetLevel, snapshot.contraptionTag.copy(), false);
                restored.anchor = targetAnchor;
                remapCapturedBlocks(restored, snapshot, targetAnchor, this.transform);
                requireExactCapturedSet(snapshot, restored, targetAnchor, this.transform);

                restoredEntity = ControlledContraptionEntity.create(targetLevel, targetBearing, restored);
                restoredEntity.setPos(targetAnchor.getX(), targetAnchor.getY(), targetAnchor.getZ());
                restoredEntity.setRotationAxis(targetFacing.getAxis());
                restoredEntity.setAngle(snapshot.angle);

                if ("OUTER_DISASSEMBLY".equals(this.operation)) {
                    SableM28NormalWorldCceSync.markBeforeAdd(
                            restoredEntity, targetPos, snapshot.entries.size());
                }

                final MechanicalBearingBlockEntityAccessor accessor =
                        (MechanicalBearingBlockEntityAccessor) targetBearing;
                if (accessor.sable$getMovementMode() != null) {
                    accessor.sable$getMovementMode().setValue(snapshot.movementMode);
                }
                accessor.sable$setSequencedAngleLimit(snapshot.sequencedAngleLimit);
                accessor.sable$setAssembleNextTick(false);
                targetBearing.setAngle(snapshot.angle);

                // Create 6.0.8 publishes movedContraption before addFreshEntity. Matching that order keeps
                // controller validation coherent during EntityJoinLevelEvent and entity-manager callbacks.
                accessor.sable$setMovedContraption(restoredEntity);
                addFreshEntityReturn = targetLevel.addFreshEntity(restoredEntity);
                SableM28NormalWorldCceSync.serverPostAdd(restoredEntity, addFreshEntityReturn);
                if (addFreshEntityReturn) {
                    targetBearing.attach(restoredEntity);
                }
                targetBearing.setChanged();

                final boolean idLookupMatches = targetLevel.getEntity(restoredEntity.getId()) == restoredEntity;
                final boolean uuidLookupMatches = targetLevel.getEntity(restoredEntity.getUUID()) == restoredEntity;
                final SubLevel expectedSubLevel = Sable.HELPER.getContaining(targetLevel, targetPos);
                final SubLevel entitySubLevel = Sable.HELPER.getContaining(restoredEntity);
                final boolean subLevelOwnershipMatches = expectedSubLevel == entitySubLevel;
                final boolean movementModeAvailable = accessor.sable$getMovementMode() != null;
                final boolean movementModeMatches = movementModeAvailable
                        && accessor.sable$getMovementMode().getValue() == snapshot.movementMode;
                final NestedBearingRegistrationDecision.Observation observation =
                        new NestedBearingRegistrationDecision.Observation(
                                addFreshEntityReturn, restoredEntity.isRemoved(), idLookupMatches,
                                uuidLookupMatches, subLevelOwnershipMatches,
                                targetBearing.getMovedContraption() == restoredEntity,
                                Math.abs(restoredEntity.getAngle(1.0F) - snapshot.angle) <= 0.001F,
                                movementModeAvailable, movementModeMatches);
                registrationFailure = NestedBearingRegistrationDecision.firstFailure(observation);
                this.logRegistration("AUTHORITATIVE_LOOKUP", targetLevel, targetBearing, restoredEntity,
                        addFreshEntityReturn, idLookupMatches, uuidLookupMatches,
                        entitySubLevel, registrationFailure);
                if (registrationFailure != NestedBearingRegistrationDecision.Failure.NONE) {
                    throw new IllegalStateException(
                            "Destination nested contraption restore failed: " + registrationFailure);
                }
                snapshot.destination = new RestoredOwnership(targetLevel, targetBearing, restoredEntity,
                        targetAnchor, targetPos.immutable());
                log("DESTINATION_ENTITY_REGISTERED", snapshot, targetPos, restoredEntity,
                        "capturedBlockSetExact=true entityRegistration=true");
                this.logTransition("RESTORE_REGISTERED", sourcePos, restoredEntity,
                        snapshot.entries.size(), null, null, "destinationBearingPos=" + targetPos);
                this.logAssemblyTransaction("NESTED_RESTORE_COMPLETE", null);
            } catch (final RuntimeException exception) {
                if (restoredEntity != null) {
                    this.logRegistration("FAILURE_CLEANUP", targetLevel, targetBearing, restoredEntity,
                            addFreshEntityReturn,
                            targetLevel.getEntity(restoredEntity.getId()) == restoredEntity,
                            targetLevel.getEntity(restoredEntity.getUUID()) == restoredEntity,
                            Sable.HELPER.getContaining(restoredEntity),
                            registrationFailure);
                    resetDestinationBearing(targetBearing, restoredEntity);
                    restoredEntity.discard();
                }
                throw exception;
            }
        }

        private void commitSourceOwnership() {
            if (this.snapshots.values().stream().anyMatch(snapshot -> snapshot.destination == null)) {
                throw new IllegalStateException("Not every nested bearing snapshot was reconstructed");
            }
            for (final NestedSnapshot snapshot : this.snapshots.values()) {
                if (snapshot.sourceOwnership == SourceOwnership.STATIC_PAYLOAD) {
                    snapshot.sourceContraption.removeBlocksFromWorld(this.sourceLevel, BlockPos.ZERO);
                } else if (snapshot.sourceEntity != null) {
                    detachSourceBearing(snapshot.sourceBearingPos);
                    snapshot.sourceEntity.discard();
                }
                this.verifyOwnership(snapshot, "SOURCE_COMMITTED");
            }
            this.sourceOwnershipCommitted = true;
            this.logTransition("TRANSFER_COMMITTED", null, null,
                    this.snapshots.values().stream().mapToInt(snapshot -> snapshot.entries.size()).sum(),
                    null, null, "snapshotCount=" + this.snapshots.size());
        }

        private void detachSourceBearing(final BlockPos sourceBearingPos) {
            if (!(this.sourceLevel.getBlockEntity(sourceBearingPos)
                    instanceof final MechanicalBearingBlockEntity sourceBearing)) {
                return;
            }
            final MechanicalBearingBlockEntityAccessor accessor =
                    (MechanicalBearingBlockEntityAccessor) sourceBearing;
            accessor.sable$setMovedContraption(null);
            accessor.sable$setRunning(false);
            accessor.sable$setAssembleNextTick(false);
            sourceBearing.setChanged();
        }

        private void verifyImmediateOwnership() {
            if (!this.sourceOwnershipCommitted) {
                throw new IllegalStateException("Nested source ownership was not committed");
            }
            for (final NestedSnapshot snapshot : this.snapshots.values()) {
                this.verifyOwnership(snapshot, "AFTER_BLOCK_MOVE");
            }
        }

        private void verifyOwnership(final NestedSnapshot snapshot, final String phase) {
            final RestoredOwnership destination = snapshot.destination;
            if (destination == null) {
                throw new IllegalStateException("Nested destination is missing for " + snapshot.sourceBearingPos);
            }
            int staticOwners = 0;
            int nestedOwners = 0;
            for (final Map.Entry<BlockPos, CapturedEntry> entry : snapshot.entries.entrySet()) {
                final BlockPos targetSource = this.transform.apply(snapshot.sourceAnchor.offset(entry.getKey()));
                final boolean staticOwner = !destination.level.getBlockState(targetSource).isAir();
                if (staticOwner) {
                    staticOwners++;
                }
                final BlockPos targetLocal = targetSource.subtract(destination.targetAnchor);
                final boolean nestedOwner = destination.entity.getContraption().getBlocks().containsKey(targetLocal);
                if (nestedOwner) {
                    nestedOwners++;
                }
                final int ownershipCount = (staticOwner ? 1 : 0) + (nestedOwner ? 1 : 0);
                if (Boolean.getBoolean(TRACE_PROPERTY)) {
                    Sable.LOGGER.info("SABLE_M34_NESTED_OWNERSHIP phase={} operation={} sourceBearing={} "
                                    + "payloadLocal={} payloadTargetSource={} payloadPresentAsOuterStaticBlock={} "
                                    + "payloadPresentInNestedContraption={} ownershipCount={} expectedOwnershipCount=1",
                            phase, this.operation, snapshot.sourceBearingPos, targetLocal, targetSource,
                            staticOwner, nestedOwner, ownershipCount);
                }
                if (ownershipCount != 1 || !nestedOwner) {
                    throw new IllegalStateException("Nested payload owner count at " + targetLocal
                            + " is " + ownershipCount + ", expected nested-only ownership");
                }
            }
            final int expected = snapshot.entries.size();
            final int ownershipCount = staticOwners + nestedOwners;
            logOwnership(phase, snapshot, staticOwners, nestedOwners, ownershipCount, expected);
            if (staticOwners != 0 || nestedOwners != expected || ownershipCount != expected) {
                throw new IllegalStateException("Nested payload ownership invariant failed: static="
                        + staticOwners + " nested=" + nestedOwners + " expected=" + expected);
            }
        }

        private void scheduleNextTickVerification() {
            synchronized (PENDING) {
                for (final NestedSnapshot snapshot : this.snapshots.values()) {
                    if (snapshot.destination != null) {
                        PENDING.add(new PendingVerification(snapshot.destination.level,
                                snapshot.destination.targetBearingPos, snapshot.destination.entity.getUUID(),
                                snapshot.entries.size(), snapshot.angle,
                                snapshot.destination.level.getGameTime() + 1L));
                    }
                }
            }
        }

        private void rollbackDestinations() {
            if (this.sourceOwnershipCommitted) {
                return;
            }
            for (final NestedSnapshot snapshot : this.snapshots.values()) {
                if (snapshot.destination != null) {
                    resetDestinationBearing(snapshot.destination.bearing, snapshot.destination.entity);
                    snapshot.destination.entity.discard();
                    snapshot.destination = null;
                }
            }
        }

        private void destinationAllocated(final ServerSubLevel destination) {
            this.destinationSubLevel = Objects.requireNonNull(destination, "destination");
            this.logAssemblyTransaction("DESTINATION_ALLOCATED", null);
        }

        private void blockCopyComplete(final int copiedBlockCount) {
            this.copiedBlockCount = copiedBlockCount;
            this.logAssemblyTransaction("BLOCK_COPY_COMPLETE", null);
        }

        private void rollbackOuterAssembly(final String reason) {
            if (!NestedBearingTransactionDecision.shouldRollbackDestination(
                    this.sourceOwnershipCommitted, this.destinationSubLevel != null)) {
                return;
            }
            this.logAssemblyTransaction("ROLLBACK_BEGIN", reason);
            final ServerSubLevel destination = this.destinationSubLevel;
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.sourceLevel);
            if (container != null && container.getSubLevel(destination.getUniqueId()) == destination) {
                container.removeSubLevel(destination, SubLevelRemovalReason.REMOVED);
            }
            this.destinationSubLevel = null;
            this.logAssemblyTransaction("ROLLBACK_COMPLETE", reason);
        }

        private void logRegistration(final String phase, final ServerLevel destinationLevel,
                                     final MechanicalBearingBlockEntity bearing,
                                     final ControlledContraptionEntity entity,
                                     final boolean addFreshEntityReturn,
                                     final boolean lookupByIdResult, final boolean lookupByUuidResult,
                                     final @Nullable SubLevel containingSubLevel,
                                     final NestedBearingRegistrationDecision.Failure failure) {
            if (!Boolean.getBoolean(TRACE_PROPERTY)) {
                return;
            }
            Sable.LOGGER.info("SABLE_M35_NESTED_REGISTRATION phase={} destinationSableId={} "
                            + "destinationLevelClass={} destinationLevelIdentity={} entityIdBeforeAdd={} "
                            + "entityUuid={} addFreshEntityReturn={} entityRemovedImmediatelyAfterAdd={} "
                            + "entityRemovalReason={} lookupMethod=PARENT_SERVER_LEVEL_ENTITY_MANAGER "
                            + "lookupByIdResult={} lookupByUuidResult={} inclusiveLookupResult={} "
                            + "sublevelStorageLookupResult={} entityLevelClass={} entityLevelIdentity={} "
                            + "entityContainingSubLevel={} bearingPos={} bearingControllerEntityReference={} "
                            + "entityAlive={} entityRemoved={} entityRemovalReasonAfterLookup={} "
                            + "firstFailure={} discardCaller={} callerStackFingerprint={}",
                    phase, containingSubLevel == null ? "none" : containingSubLevel.getUniqueId(),
                    destinationLevel.getClass().getName(), System.identityHashCode(destinationLevel), entity.getId(),
                    entity.getUUID(), addFreshEntityReturn, entity.isRemoved(), entity.getRemovalReason(),
                    lookupByIdResult, lookupByUuidResult, lookupByIdResult && lookupByUuidResult,
                    lookupByIdResult, entity.level().getClass().getName(), System.identityHashCode(entity.level()),
                    containingSubLevel != null, bearing.getBlockPos(), bearing.getMovedContraption() == entity,
                    entity.isAlive(), entity.isRemoved(), entity.getRemovalReason(), failure,
                    "FAILURE_CLEANUP".equals(phase) ? "M28_14_2_RESTORE_FAILURE_CLEANUP" : "none",
                    "FAILURE_CLEANUP".equals(phase) ? stackFingerprint() : "not_applicable");
        }

        private void logAssemblyTransaction(final String phase, final @Nullable String rollbackReason) {
            if (!Boolean.getBoolean(TRACE_PROPERTY)) {
                return;
            }
            Sable.LOGGER.info("SABLE_M35_ASSEMBLY_TRANSACTION transactionId={} phase={} operation={} "
                            + "sourceAssemblerPos={} destinationSableId={} selectedBlockCount={} copiedBlockCount={} "
                            + "nestedSnapshotCount={} restoredNestedCount={} rollbackReason={}",
                    this.transactionId, phase, this.operation, "unavailable",
                    this.destinationSubLevel == null ? "none" : this.destinationSubLevel.getUniqueId(),
                    this.selectedBlocks.size(), this.copiedBlockCount, this.snapshots.size(),
                    this.snapshots.values().stream().filter(snapshot -> snapshot.destination != null).count(),
                    rollbackReason == null ? "none" : rollbackReason);
        }

        private void log(final String phase, final NestedSnapshot snapshot,
                         final @Nullable BlockPos destinationBearing,
                         final @Nullable ControlledContraptionEntity entity, final String detail) {
            if (!Boolean.getBoolean(TRACE_PROPERTY)) {
                return;
            }
            Sable.LOGGER.info("SABLE_M34_NESTED_OWNERSHIP phase={} operation={} sourceBearing={} "
                            + "destinationBearing={} sourceOwnership={} snapshotCapturedBlockCount={} "
                            + "restoredEntityId={} angle={} movementMode={} {}",
                    phase, this.operation, snapshot.sourceBearingPos, destinationBearing,
                    snapshot.sourceOwnership, snapshot.entries.size(), entity == null ? "none" : entity.getId(),
                    snapshot.angle, snapshot.movementMode, detail);
        }

        private void logOwnership(final String phase, final NestedSnapshot snapshot, final int staticOwners,
                                  final int nestedOwners, final int ownershipCount, final int expected) {
            if (!Boolean.getBoolean(TRACE_PROPERTY)) {
                return;
            }
            Sable.LOGGER.info("SABLE_M34_NESTED_OWNERSHIP phase={} operation={} sourceBearing={} "
                            + "destinationBearing={} payloadPresentAsOuterStaticBlock={} "
                            + "payloadPresentInNestedContraption={} ownershipCount={} expectedOwnershipCount={} "
                            + "capturedBlockSetExact={}",
                    phase, this.operation, snapshot.sourceBearingPos,
                    snapshot.destination == null ? "none" : snapshot.destination.targetBearingPos,
                    staticOwners, nestedOwners, ownershipCount, expected,
                    staticOwners == 0 && nestedOwners == expected && ownershipCount == expected);
        }

        private void logTransition(final String phase, final @Nullable BlockPos sourceBearingPos,
                                   final @Nullable ControlledContraptionEntity sourceEntity,
                                   final int capturedBlockCount, final @Nullable String nullableFieldName,
                                   final @Nullable Object nullableFieldValue, final String detail) {
            if (!Boolean.getBoolean(TRACE_PROPERTY)) {
                return;
            }
            Sable.LOGGER.info("SABLE_M34_TRANSFER_SESSION phase={} operation={} sourceBearingPos={} "
                            + "sourceNestedEntityId={} capturedBlockCount={} nullableFieldName={} "
                            + "nullableFieldValue={} outerSelectedSetSize={} {}",
                    phase, this.operation, sourceBearingPos,
                    sourceEntity == null ? "none" : sourceEntity.getId(), capturedBlockCount,
                    nullableFieldName == null ? "none" : nullableFieldName,
                    nullableFieldName == null ? "not_applicable" : String.valueOf(nullableFieldValue),
                    this.selectedBlocks.size(), detail);
        }
    }

    private static void resetDestinationBearing(final MechanicalBearingBlockEntity bearing,
                                                final ControlledContraptionEntity entity) {
        final MechanicalBearingBlockEntityAccessor accessor = (MechanicalBearingBlockEntityAccessor) bearing;
        if (bearing.getMovedContraption() == entity || bearing.getMovedContraption() == null
                || bearing.getMovedContraption().isRemoved()) {
            accessor.sable$setMovedContraption(null);
            accessor.sable$setRunning(false);
            accessor.sable$setAssembleNextTick(false);
            bearing.setChanged();
        }
    }

    private static String stackFingerprint() {
        return StackWalker.getInstance().walk(stream -> stream.limit(10)
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(java.util.stream.Collectors.joining(" <- ")));
    }

    private static Set<BlockPos> copySelectedBlocks(final Collection<BlockPos> selectedBlocks) {
        Objects.requireNonNull(selectedBlocks, "selectedBlocks");
        final Set<BlockPos> copy = new LinkedHashSet<>();
        int index = 0;
        for (final BlockPos selectedBlock : selectedBlocks) {
            if (selectedBlock == null) {
                throw new IllegalArgumentException("Outer selected block position at index " + index
                        + " must not be null");
            }
            copy.add(selectedBlock.immutable());
            index++;
        }
        return Set.copyOf(copy);
    }

    private static void remapCapturedBlocks(final BearingContraption restored, final NestedSnapshot snapshot,
                                            final BlockPos targetAnchor, final AssemblyTransform transform) {
        final Map<BlockPos, StructureTemplate.StructureBlockInfo> remapped = new HashMap<>();
        for (final Map.Entry<BlockPos, CapturedEntry> entry : snapshot.entries.entrySet()) {
            final BlockPos sourceAbsolute = snapshot.sourceAnchor.offset(entry.getKey());
            final BlockPos targetAbsolute = transform.apply(sourceAbsolute);
            final BlockPos targetLocal = targetAbsolute.subtract(targetAnchor);
            final CapturedEntry captured = entry.getValue();
            final CompoundTag tag = captured.tag == null ? null : captured.tag.copy();
            if (tag != null) {
                tag.putInt("x", targetLocal.getX());
                tag.putInt("y", targetLocal.getY());
                tag.putInt("z", targetLocal.getZ());
            }
            remapped.put(targetLocal, new StructureTemplate.StructureBlockInfo(
                    targetLocal, transform.apply(captured.state), tag));
        }
        restored.getBlocks().clear();
        restored.getBlocks().putAll(remapped);
    }

    private static void requireExactCapturedSet(final NestedSnapshot snapshot, final BearingContraption restored,
                                                final BlockPos targetAnchor, final AssemblyTransform transform) {
        if (restored.getBlocks().size() != snapshot.entries.size()) {
            throw new IllegalStateException("Nested captured block count changed from " + snapshot.entries.size()
                    + " to " + restored.getBlocks().size());
        }
        for (final Map.Entry<BlockPos, CapturedEntry> source : snapshot.entries.entrySet()) {
            final BlockPos targetAbsolute = transform.apply(snapshot.sourceAnchor.offset(source.getKey()));
            final BlockPos targetLocal = targetAbsolute.subtract(targetAnchor);
            final StructureTemplate.StructureBlockInfo restoredInfo = restored.getBlocks().get(targetLocal);
            final BlockState expectedState = transform.apply(source.getValue().state);
            final CompoundTag expectedTag = source.getValue().tag == null ? null : source.getValue().tag.copy();
            if (expectedTag != null) {
                expectedTag.putInt("x", targetLocal.getX());
                expectedTag.putInt("y", targetLocal.getY());
                expectedTag.putInt("z", targetLocal.getZ());
            }
            if (restoredInfo == null || !restoredInfo.state().equals(expectedState)
                    || !java.util.Objects.equals(restoredInfo.nbt(), expectedTag)) {
                throw new IllegalStateException("Nested captured block set/state changed at " + targetLocal);
            }
        }
    }

    private static Map<BlockPos, CapturedEntry> copyEntries(final BearingContraption contraption) {
        final Map<BlockPos, CapturedEntry> result = new LinkedHashMap<>();
        contraption.getBlocks().entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getKey().asLong()))
                .forEach(entry -> result.put(entry.getKey().immutable(),
                        new CapturedEntry(entry.getValue().state(),
                                entry.getValue().nbt() == null ? null : entry.getValue().nbt().copy())));
        return Map.copyOf(result);
    }

    private static final class PendingVerification {
        private final ServerLevel level;
        private final BlockPos bearingPos;
        private final UUID entityUuid;
        private final int expectedCapturedBlocks;
        private final float expectedAngle;
        private final long dueGameTime;

        private PendingVerification(final ServerLevel level, final BlockPos bearingPos, final UUID entityUuid,
                                    final int expectedCapturedBlocks, final float expectedAngle,
                                    final long dueGameTime) {
            this.level = level;
            this.bearingPos = bearingPos;
            this.entityUuid = entityUuid;
            this.expectedCapturedBlocks = expectedCapturedBlocks;
            this.expectedAngle = expectedAngle;
            this.dueGameTime = dueGameTime;
        }

        private boolean verifyIfDue(final ServerLevel tickingLevel) {
            if (this.level != tickingLevel || tickingLevel.getGameTime() < this.dueGameTime) {
                return false;
            }
            final BlockEntity blockEntity = tickingLevel.getBlockEntity(this.bearingPos);
            final ControlledContraptionEntity entity = tickingLevel.getEntity(this.entityUuid)
                    instanceof final ControlledContraptionEntity controlled ? controlled : null;
            final boolean survived = entity != null && !entity.isRemoved()
                    && entity.getContraption() != null
                    && entity.getContraption().getBlocks().size() == this.expectedCapturedBlocks
                    && Math.abs(entity.getAngle(1.0F) - this.expectedAngle) <= 0.001F
                    && blockEntity instanceof final MechanicalBearingBlockEntity bearing
                    && bearing.getMovedContraption() == entity;
            Sable.LOGGER.info("SABLE_M34_NESTED_OWNERSHIP phase=NEXT_TICK_SURVIVAL bearingPos={} "
                            + "entityUuid={} survived={} expectedCapturedBlockCount={} actualCapturedBlockCount={} "
                            + "expectedAngle={} actualAngle={}",
                    this.bearingPos, this.entityUuid, survived, this.expectedCapturedBlocks,
                    entity == null || entity.getContraption() == null
                            ? 0 : entity.getContraption().getBlocks().size(),
                    this.expectedAngle, entity == null ? "none" : entity.getAngle(1.0F));
            if (Boolean.getBoolean(TRACE_PROPERTY)) {
                Sable.LOGGER.info("SABLE_M34_TRANSFER_SESSION phase=RESTORE_SURVIVED_NEXT_TICK "
                                + "operation=DEFERRED sourceBearingPos={} sourceNestedEntityId={} "
                                + "capturedBlockCount={} nullableFieldName=none nullableFieldValue=not_applicable "
                                + "outerSelectedSetSize=unavailable survived={}",
                        this.bearingPos, entity == null ? "none" : entity.getId(),
                        this.expectedCapturedBlocks, survived);
            }
            if (!survived) {
                Sable.LOGGER.error("M28.14 nested bearing restoration did not survive its next tick at {}",
                        this.bearingPos);
            }
            return true;
        }
    }

    private enum SourceOwnership {
        NESTED_ENTITY,
        STATIC_PAYLOAD
    }

    private record CapturedEntry(BlockState state, @Nullable CompoundTag tag) {
    }

    private static final class NestedSnapshot {
        private final BlockPos sourceBearingPos;
        private final Direction sourceFacing;
        private final BlockPos sourceAnchor;
        private final CompoundTag contraptionTag;
        private final Map<BlockPos, CapturedEntry> entries;
        private final float angle;
        private final int movementMode;
        private final double sequencedAngleLimit;
        private final SourceOwnership sourceOwnership;
        private final @Nullable ControlledContraptionEntity sourceEntity;
        private final BearingContraption sourceContraption;
        private @Nullable RestoredOwnership destination;

        private NestedSnapshot(final BlockPos sourceBearingPos, final Direction sourceFacing,
                               final BlockPos sourceAnchor, final CompoundTag contraptionTag,
                               final Map<BlockPos, CapturedEntry> entries, final float angle,
                               final int movementMode, final double sequencedAngleLimit,
                               final SourceOwnership sourceOwnership,
                               final @Nullable ControlledContraptionEntity sourceEntity,
                               final BearingContraption sourceContraption) {
            this.sourceBearingPos = sourceBearingPos;
            this.sourceFacing = sourceFacing;
            this.sourceAnchor = sourceAnchor;
            this.contraptionTag = contraptionTag;
            this.entries = entries;
            this.angle = angle;
            this.movementMode = movementMode;
            this.sequencedAngleLimit = sequencedAngleLimit;
            this.sourceOwnership = sourceOwnership;
            this.sourceEntity = sourceEntity;
            this.sourceContraption = sourceContraption;
        }
    }

    private record RestoredOwnership(ServerLevel level, MechanicalBearingBlockEntity bearing,
                                     ControlledContraptionEntity entity, BlockPos targetAnchor,
                                     BlockPos targetBearingPos) {
    }
}
