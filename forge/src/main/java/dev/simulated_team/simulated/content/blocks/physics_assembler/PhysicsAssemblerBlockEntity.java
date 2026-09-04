package dev.simulated_team.simulated.content.blocks.physics_assembler;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class PhysicsAssemblerBlockEntity extends BlockEntity {

    private static final String STATE_PARENT_WORLD = "PARENT_WORLD";
    private static final String DIAGNOSTIC_NONE = "none";
    private static final String NBT_PRIMARY_ASSEMBLER = "M22PrimaryAssembler";
    private static final String NBT_LIFECYCLE_STATE = "M22LifecycleState";
    private static final String NBT_LAST_FAILURE = "M22LastFailure";
    private static final String NBT_CANDIDATE_BLOCKS = "M22CandidateBlocks";
    private static final String NBT_ACCEPTED_BLOCKS = "M22AcceptedBlocks";
    private static final String NBT_REJECTED_BLOCKS = "M22RejectedBlocks";
    private static final String NBT_BLOCK_ENTITY_COUNT = "M22BlockEntityCount";
    private static final String NBT_VISIBLE_DELTA_AFTER_ASSEMBLY = "M22VisibleDeltaAfterAssembly";
    private static final String NBT_LAST_DISASSEMBLY_RESULT = "M23LastDisassemblyResult";
    private static final String NBT_LAST_ASSEMBLY_FAILURE_STAGE = "M23LastAssemblyFailureStage";

    private boolean primaryAssembler;
    private String lastLifecycleState = STATE_PARENT_WORLD;
    private String lastFailure = DIAGNOSTIC_NONE;
    private int lastCandidateBlocks;
    private int lastAcceptedBlocks;
    private int lastRejectedBlocks;
    private int lastBlockEntityCount;
    private double lastVisibleDeltaAfterAssembly;
    private String lastDisassemblyResult = DIAGNOSTIC_NONE;
    private String lastAssemblyFailureStage = DIAGNOSTIC_NONE;

    public PhysicsAssemblerBlockEntity(final BlockPos pos, final BlockState state) {
        super(SimulatedBlockEntityTypes.PHYSICS_ASSEMBLER.get(), pos, state);
    }

    public void assembleOrDisassemble(@Nullable final Player player) {
        final Level currentLevel = this.getLevel();
        if (!(currentLevel instanceof final ServerLevel serverLevel) || !SimulatedConfig.ENABLE_M22_BASIC_ASSEMBLY.get()) {
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        try {
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                this.disassemble(serverLevel, serverSubLevel);
            } else {
                this.assemble(serverLevel);
            }
            if (player != null) {
                player.displayClientMessage(Component.literal("SABLE_M22_ASSEMBLER status=PASS state="
                        + this.lastLifecycleState), true);
            }
        } catch (final AssemblyException exception) {
            this.lastFailure = describeAssemblyException(exception);
            this.lastAssemblyFailureStage = extractFailureStage(this.lastFailure);
            this.lastLifecycleState = "FAILED";
            this.setChanged();
            if (player != null) {
                player.displayClientMessage(Component.literal("SABLE_M22_ASSEMBLER status=FAIL reason="
                        + this.lastFailure), false);
            }
        }
    }

    private void assemble(final ServerLevel level) throws AssemblyException {
        this.primaryAssembler = true;
        final BlockPos toAssemble = this.getBlockPos().below();
        final SimAssemblyHelper.AssemblyResult result =
                SimAssemblyHelper.assembleFromSingleBlock(level, this.getBlockPos(), toAssemble, true);
        if (result == null) {
            this.primaryAssembler = false;
            this.lastLifecycleState = "NO_BLOCKS";
            return;
        }

        this.lastCandidateBlocks = result.candidateBlocks();
        this.lastAcceptedBlocks = result.acceptedBlocks();
        this.lastRejectedBlocks = result.rejectedBlocks();
        this.lastVisibleDeltaAfterAssembly = result.visibleDeltaAfterAssembly();
        this.lastBlockEntityCount = countBlockEntities(level, result.subLevel());
        this.lastLifecycleState = "ASSEMBLED";
        this.lastFailure = DIAGNOSTIC_NONE;
        this.lastAssemblyFailureStage = DIAGNOSTIC_NONE;
        this.lastDisassemblyResult = DIAGNOSTIC_NONE;
        this.setChanged();

        Sable.LOGGER.info("SABLE_M22_ASSEMBLY_TRANSFORM parentAnchor={} localAnchor={} rawAnchor={} visibleOrigin={} visibleDeltaAfterAssembly={}",
                this.getBlockPos().toShortString(),
                result.offset().toShortString(),
                result.subLevel().getPlot().getCenterBlock().toShortString(),
                result.subLevel().logicalPose().position(),
                result.visibleDeltaAfterAssembly());
    }

    private void disassemble(final ServerLevel level, final ServerSubLevel subLevel) throws AssemblyException {
        final BlockPos goal = SimAssemblyHelper.currentVisibleBlockPos(subLevel, this.getBlockPos());
        final SimAssemblyHelper.DisassemblyResult result =
                SimAssemblyHelper.disassembleSubLevel(level, subLevel, this.getBlockPos(), goal,
                        SimAssemblyHelper.rotationFrom90DegRots(0), this.lastAcceptedBlocks);
        this.lastAcceptedBlocks = result.restoredBlockCount();
        this.lastBlockEntityCount = result.restoredBlockEntityCount();
        this.lastLifecycleState = "DISASSEMBLED";
        this.primaryAssembler = false;
        this.lastFailure = DIAGNOSTIC_NONE;
        this.lastDisassemblyResult = "restoredBlockCount=" + result.restoredBlockCount()
                + " restoredBlockEntityCount=" + result.restoredBlockEntityCount()
                + " oldSableRemoved=" + result.oldSableRemoved();
        this.setChanged();
    }

    protected void setParent(final Level level) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, this.getBlockPos());
        this.lastLifecycleState = subLevel instanceof ServerSubLevel ? "ASSEMBLED" : STATE_PARENT_WORLD;
        this.setChanged();
    }

    public String inspect() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final MassData mass = serverSubLevel.getMassTracker();
            final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverSubLevel.getLevel());
            final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(serverSubLevel);
            final SimAssemblyHelper.BodySnapshot snapshot = SimAssemblyHelper.snapshotBody(
                    serverSubLevel.getLevel(), serverSubLevel, this.lastAcceptedBlocks);
            return "state=ASSEMBLED sableId=" + serverSubLevel.getUniqueId()
                    + " blockCount=" + SimAssemblyHelper.collectBlocks(serverSubLevel.getLevel(), serverSubLevel).size()
                    + " blockEntityCount=" + countBlockEntities(serverSubLevel.getLevel(), serverSubLevel)
                    + " visibleOrigin=" + serverSubLevel.logicalPose().position()
                    + " rawBounds=" + serverSubLevel.getPlot().getBoundingBox()
                    + " mass=" + (mass == null ? "null" : mass.getMass())
                    + " centerOfMass=" + (mass == null ? "null" : mass.getCenterOfMass())
                    + " bodyRegistered=" + (physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(serverSubLevel))
                    + " collisionGeometryPresent=" + (physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(serverSubLevel))
                    + " serializationReady=" + (serverSubLevel.getLastSerializationPointer() != null)
                    + " handleValid=" + (handle != null && handle.isValid())
                    + " activeConstraintCount=" + SimAssemblyHelper.activeSpringConstraintIds(serverSubLevel.getLevel(), serverSubLevel).size()
                    + " springActorCount=" + SimAssemblyHelper.countSpringActors(serverSubLevel)
                    + " trackingPointCount=" + snapshot.trackingPointCount()
                    + " storedBlockCount=" + snapshot.storedBlockCount()
                    + " assemblerPresent=" + snapshot.assemblerPresent()
                    + " disassemblyEligible=" + SimAssemblyHelper.activeSpringConstraintIds(serverSubLevel.getLevel(), serverSubLevel).isEmpty()
                    + " lastDisassemblyResult=" + this.lastDisassemblyResult
                    + " lastAssemblyFailureStage=" + this.lastAssemblyFailureStage;
        }

        return "state=" + this.lastLifecycleState
                + " assemblerPos=" + this.getBlockPos().toShortString()
                + " candidateBlockCount=" + this.lastCandidateBlocks
                + " acceptedBlocks=" + this.lastAcceptedBlocks
                + " rejectedBlocks=" + this.lastRejectedBlocks
                + " blockEntityCount=" + this.lastBlockEntityCount
                + " visibleDeltaAfterAssembly=" + this.lastVisibleDeltaAfterAssembly
                + " lastFailure=" + this.lastFailure
                + " lastDisassemblyResult=" + this.lastDisassemblyResult
                + " lastAssemblyFailureStage=" + this.lastAssemblyFailureStage;
    }

    private static int countBlockEntities(final ServerLevel level, final SubLevel subLevel) {
        int count = 0;
        for (final BlockPos pos : SimAssemblyHelper.collectBlocks(level, subLevel)) {
            if (level.getBlockEntity(pos) != null) {
                count++;
            }
        }
        return count;
    }

    public boolean isPrimaryAssembler() {
        return this.primaryAssembler;
    }

    public Vector3d visiblePosition() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel == null) {
            return new Vector3d(this.getBlockPos().getX() + 0.5D, this.getBlockPos().getY() + 0.5D,
                    this.getBlockPos().getZ() + 0.5D);
        }
        return subLevel.logicalPose().transformPosition(new Vector3d(this.getBlockPos().getX() + 0.5D,
                this.getBlockPos().getY() + 0.5D, this.getBlockPos().getZ() + 0.5D));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(NBT_PRIMARY_ASSEMBLER, this.primaryAssembler);
        putDiagnosticString(tag, NBT_LIFECYCLE_STATE, this.lastLifecycleState, STATE_PARENT_WORLD);
        putDiagnosticString(tag, NBT_LAST_FAILURE, this.lastFailure, DIAGNOSTIC_NONE);
        tag.putInt(NBT_CANDIDATE_BLOCKS, this.lastCandidateBlocks);
        tag.putInt(NBT_ACCEPTED_BLOCKS, this.lastAcceptedBlocks);
        tag.putInt(NBT_REJECTED_BLOCKS, this.lastRejectedBlocks);
        tag.putInt(NBT_BLOCK_ENTITY_COUNT, this.lastBlockEntityCount);
        tag.putDouble(NBT_VISIBLE_DELTA_AFTER_ASSEMBLY, this.lastVisibleDeltaAfterAssembly);
        putDiagnosticString(tag, NBT_LAST_DISASSEMBLY_RESULT, this.lastDisassemblyResult, DIAGNOSTIC_NONE);
        putDiagnosticString(tag, NBT_LAST_ASSEMBLY_FAILURE_STAGE, this.lastAssemblyFailureStage, DIAGNOSTIC_NONE);
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);
        this.primaryAssembler = tag.getBoolean(NBT_PRIMARY_ASSEMBLER);
        this.lastLifecycleState = readDiagnosticString(tag, NBT_LIFECYCLE_STATE, STATE_PARENT_WORLD);
        this.lastFailure = readDiagnosticString(tag, NBT_LAST_FAILURE, DIAGNOSTIC_NONE);
        this.lastCandidateBlocks = tag.getInt(NBT_CANDIDATE_BLOCKS);
        this.lastAcceptedBlocks = tag.getInt(NBT_ACCEPTED_BLOCKS);
        this.lastRejectedBlocks = tag.getInt(NBT_REJECTED_BLOCKS);
        this.lastBlockEntityCount = tag.getInt(NBT_BLOCK_ENTITY_COUNT);
        this.lastVisibleDeltaAfterAssembly = tag.getDouble(NBT_VISIBLE_DELTA_AFTER_ASSEMBLY);
        this.lastDisassemblyResult = readDiagnosticString(tag, NBT_LAST_DISASSEMBLY_RESULT, DIAGNOSTIC_NONE);
        this.lastAssemblyFailureStage = readDiagnosticString(tag, NBT_LAST_ASSEMBLY_FAILURE_STAGE, DIAGNOSTIC_NONE);
    }

    private static void putDiagnosticString(final CompoundTag tag, final String key,
                                            @Nullable final String value, final String defaultValue) {
        final String normalized = normalizeDiagnosticString(value, defaultValue);
        if (!normalized.isEmpty()) {
            tag.putString(key, normalized);
        }
    }

    private static String readDiagnosticString(final CompoundTag tag, final String key, final String defaultValue) {
        return tag.contains(key, Tag.TAG_STRING)
                ? normalizeDiagnosticString(tag.getString(key), defaultValue)
                : defaultValue;
    }

    private static String normalizeDiagnosticString(@Nullable final String value, final String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String describeAssemblyException(final AssemblyException exception) {
        final String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        if (exception.component != null) {
            final String componentText = exception.component.getString();
            if (!componentText.isBlank()) {
                return componentText;
            }
        }
        return "ASSEMBLY_EXCEPTION_WITH_NULL_MESSAGE exceptionClass=" + exception.getClass().getName();
    }

    private static String extractFailureStage(final String failure) {
        if (failure.contains("failureStage=")) {
            return failure.substring(failure.indexOf("failureStage="));
        }
        if (failure.startsWith("DISASSEMBLY_BLOCKED")) {
            return failure;
        }
        return "seeLastFailure";
    }
}
