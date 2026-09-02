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

    private boolean primaryAssembler;
    private String lastLifecycleState = "PARENT_WORLD";
    private String lastFailure = "none";
    private int lastCandidateBlocks;
    private int lastAcceptedBlocks;
    private int lastRejectedBlocks;
    private int lastBlockEntityCount;
    private double lastVisibleDeltaAfterAssembly;

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
            this.lastFailure = exception.getMessage();
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
        this.lastFailure = "none";
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
                        SimAssemblyHelper.rotationFrom90DegRots(0));
        this.lastAcceptedBlocks = result.restoredBlockCount();
        this.lastBlockEntityCount = result.restoredBlockEntityCount();
        this.lastLifecycleState = "DISASSEMBLED";
        this.primaryAssembler = false;
        this.lastFailure = "none";
        this.setChanged();
    }

    protected void setParent(final Level level) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, this.getBlockPos());
        this.lastLifecycleState = subLevel instanceof ServerSubLevel ? "ASSEMBLED" : "PARENT_WORLD";
        this.setChanged();
    }

    public String inspect() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final MassData mass = serverSubLevel.getMassTracker();
            final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverSubLevel.getLevel());
            final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(serverSubLevel);
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
                    + " handleValid=" + (handle != null && handle.isValid());
        }

        return "state=" + this.lastLifecycleState
                + " assemblerPos=" + this.getBlockPos().toShortString()
                + " candidateBlockCount=" + this.lastCandidateBlocks
                + " acceptedBlocks=" + this.lastAcceptedBlocks
                + " rejectedBlocks=" + this.lastRejectedBlocks
                + " blockEntityCount=" + this.lastBlockEntityCount
                + " visibleDeltaAfterAssembly=" + this.lastVisibleDeltaAfterAssembly
                + " lastFailure=" + this.lastFailure;
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
        tag.putBoolean("M22PrimaryAssembler", this.primaryAssembler);
        tag.putString("M22LifecycleState", this.lastLifecycleState);
        tag.putString("M22LastFailure", this.lastFailure);
        tag.putInt("M22CandidateBlocks", this.lastCandidateBlocks);
        tag.putInt("M22AcceptedBlocks", this.lastAcceptedBlocks);
        tag.putInt("M22RejectedBlocks", this.lastRejectedBlocks);
        tag.putInt("M22BlockEntityCount", this.lastBlockEntityCount);
        tag.putDouble("M22VisibleDeltaAfterAssembly", this.lastVisibleDeltaAfterAssembly);
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);
        this.primaryAssembler = tag.getBoolean("M22PrimaryAssembler");
        this.lastLifecycleState = tag.getString("M22LifecycleState");
        this.lastFailure = tag.getString("M22LastFailure");
        this.lastCandidateBlocks = tag.getInt("M22CandidateBlocks");
        this.lastAcceptedBlocks = tag.getInt("M22AcceptedBlocks");
        this.lastRejectedBlocks = tag.getInt("M22RejectedBlocks");
        this.lastBlockEntityCount = tag.getInt("M22BlockEntityCount");
        this.lastVisibleDeltaAfterAssembly = tag.getDouble("M22VisibleDeltaAfterAssembly");
    }
}
