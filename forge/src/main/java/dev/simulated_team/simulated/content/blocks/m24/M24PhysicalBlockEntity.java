package dev.simulated_team.simulated.content.blocks.m24;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class M24PhysicalBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor, M24ActiveConstraintProvider {
    private static final String NBT_PARTNER = "M24Partner";
    private static final String NBT_PARTNER_SABLE = "M24PartnerSable";
    private static final String NBT_CONTROLLER = "M24Controller";
    private static final String NBT_ENABLED = "M24Enabled";
    private static final String NBT_TARGET = "M24Target";
    private static final String NBT_LAST_SIGNAL = "M24LastSignal";
    private static final String NBT_TORSION_ANGLE = "M24TorsionAngle";
    private static final String NBT_TORSION_ANGLE_LIMIT = "M24TorsionAngleLimit";
    private static final String NBT_DOCKING_POWERED = "M24DockingPowered";
    private static final String NBT_DOCKING_PAIR_ELIGIBLE = "M24DockingPairEligible";

    private final M24Family family;
    private @Nullable BlockPos partnerPos;
    private @Nullable UUID partnerSableId;
    private boolean controller;
    private boolean enabled = true;
    private double targetValue;
    private double lastSignal;
    private transient @Nullable PhysicsConstraintHandle backendHandle;
    private transient @Nullable RopePhysicsObject ropeObject;
    private transient boolean postCreateValidationPending;
    private transient boolean postCreateValidated;
    private transient boolean postCreateInvalid;
    private transient int postCreateValidationTicks;
    private transient double ropeInitialFirstSegmentLength;
    private transient int ropeFixedSegmentCount;
    private transient double ropeInitialVisibleDistance;
    private transient double ropeAppliedFirstSegmentLength = Double.NaN;
    private transient int ropeBackendLengthWriteCount;
    private transient String ropeLastLengthWriteOwner = "none";
    private transient long winchProductionTickCount;
    private transient double winchLastLoggedTarget = Double.NaN;
    private transient long lastPairDiscoveryLogGameTime = Long.MIN_VALUE;
    private transient @Nullable Vector3d postCreateBodyAPositionBefore;
    private transient @Nullable Vector3d postCreateBodyBPositionBefore;
    private transient long swivelCreatedGameTime = Long.MIN_VALUE;
    private transient int swivelCreateCount;
    private transient int swivelRemoveCount;
    private transient int swivelRecreateCount;
    private transient int swivelConfigurationUpdateCount;
    private transient boolean swivelLastBodyAExists;
    private transient boolean swivelLastBodyBExists;
    private transient boolean swivelLastJointExists;
    private double torsionAngleLimit = 90.0D;
    private double torsionAngle;
    private double torsionTargetAngle;
    private boolean dockingPowered;
    private boolean dockingPairEligible;
    private transient boolean dockingNextTickEligibilityTrace;

    public M24PhysicalBlockEntity(final M24Family family,
                                  final BlockPos pos,
                                  final BlockState state) {
        super(typeFor(family), pos, state);
        this.family = family;
    }

    public M24Family family() {
        return this.family;
    }

    public @Nullable BlockPos simulated$getPartnerPos() {
        return this.partnerPos;
    }

    public @Nullable UUID simulated$getPartnerSableId() {
        return this.partnerSableId;
    }

    public boolean simulated$isController() {
        return this.controller;
    }

    public boolean simulated$isEnabled() {
        return this.enabled;
    }

    public double simulated$getLastSignal() {
        return this.lastSignal;
    }

    public float simulated$getKineticSpeed() {
        return this.getSpeed();
    }

    public double simulated$getRopeTargetLength() {
        return this.ropeFixedSegmentCount + (Double.isNaN(this.ropeAppliedFirstSegmentLength)
                ? this.ropeInitialFirstSegmentLength
                : this.ropeAppliedFirstSegmentLength);
    }

    public double simulated$getRopeCurrentLength() {
        return this.ropeObject == null ? Double.NaN : ropeLength(this.ropeObject.getPoints());
    }

    public double simulated$getRopeFirstSegmentExtension() {
        return Double.isNaN(this.ropeAppliedFirstSegmentLength)
                ? this.ropeInitialFirstSegmentLength
                : this.ropeAppliedFirstSegmentLength;
    }

    public int simulated$getRopeFixedSegmentCount() {
        return this.ropeFixedSegmentCount;
    }

    public boolean simulated$isDockingPowered() {
        return this.dockingPowered;
    }

    public boolean simulated$isDockingPairEligible() {
        return this.dockingPairEligible;
    }

    public double simulated$getTorsionAngle() {
        return this.torsionAngle;
    }

    public double simulated$getTorsionAngleLimit() {
        return this.torsionAngleLimit;
    }

    public void simulated$setTorsionAngleLimit(final double degrees) {
        this.torsionAngleLimit = Math.max(1.0D, Math.min(360.0D, degrees));
        this.sync();
    }

    public Vector3d simulated$getEndpointRaw(final BlockPos pos, final BlockState state) {
        return this.backendAnchorRaw(pos, state);
    }

    public void cycleManualState(final Player player) {
        this.enabled = !this.enabled;
        this.sync();
        player.displayClientMessage(Component.literal("SABLE_M24_COMPONENT family=" + this.family.id()
                + " enabled=" + this.enabled
                + " status=" + (this.simulated$hasActiveConstraint() ? "ACTIVE" : "READY")), false);
    }

    public void onNeighborSignalChanged() {
        if (this.level != null) {
            final boolean signal = this.level.hasNeighborSignal(this.worldPosition);
            if (this.isDockingFamily()) {
                final boolean previouslyPowered = this.dockingPowered;
                final boolean disconnectActive = this.dockingPowered && !signal && this.simulated$hasActiveConstraint();
                final M24PhysicalBlockEntity partner = this.resolvePartnerComponent();
                if (!previouslyPowered && signal) {
                    this.dockingPairEligible = true;
                    this.logDockingPairEligibility("AFTER_REARM_EVENT", partner, previouslyPowered,
                            this.dockingCandidateGeometryValid(partner), false);
                } else if (previouslyPowered && !signal) {
                    this.logDockingPairEligibility("BEFORE_DISCONNECT", partner, previouslyPowered,
                            this.dockingCandidateGeometryValid(partner), false);
                    this.dockingPairEligible = false;
                    if (partner != null) {
                        partner.dockingPairEligible = partner.dockingPowered;
                        partner.sync();
                    }
                    this.dockingPowered = false;
                    if (disconnectActive) {
                        Sable.LOGGER.info("SABLE_M24_DOCKING phase=DISCONNECT reason=upstream_redstone_power_removed endpoint={}",
                                this.worldPosition);
                        this.clearBackend(true);
                    }
                    this.dockingNextTickEligibilityTrace = true;
                    this.logDockingPairEligibility("AFTER_DISCONNECT", partner, previouslyPowered,
                            this.dockingCandidateGeometryValid(partner), false);
                }
                this.dockingPowered = signal;
            }
            this.sync();
        }
    }

    /** Frozen upstream owns Winch actuation in RopeWinchBlockEntity.tick(), not in relation maintenance. */
    protected final void simulated$tickWinchProduction() {
        if (this.family != M24Family.ROPE_WINCH || this.level == null || this.level.isClientSide) {
            return;
        }
        final long tick = this.winchProductionTickCount++;
        final double speed = this.getSpeed();
        final double converted = KineticBlockEntity.convertToLinear((float) speed);
        final double clamped = Math.max(-0.49D, Math.min(0.49D, converted));
        final double before = this.targetValue;
        final boolean ropeResolved = this.ropeObject != null && this.ropeObject.isActive();
        if (ropeResolved) {
            this.updateWinchFromCreateKinetics();
            this.applyRopeFirstSegmentLength(this.targetValue, "WINCH_DEDICATED_TICK");
        }
        final boolean changed = Double.compare(before, this.targetValue) != 0;
        if (SableDiagnosticFlags.TRACE_M24 && (tick == 0L || tick == 1L || tick == 2L || tick == 5L || tick == 20L
                || changed && Double.compare(this.winchLastLoggedTarget, this.targetValue) != 0)) {
            this.winchLastLoggedTarget = this.targetValue;
            final ServerSubLevel owner = this.resolveOwnerSubLevel();
            Sable.LOGGER.info("SABLE_M24_WINCH_TICK sableId={} localPos={} logicalRopeId={} tickerInvoked=true kineticSpeed={} convertedLinearSpeed={} clampedLinearSpeed={} geometricVisibleLength={} totalLogicalLength={} firstSegmentExtension={} fixedSegmentCount={} backendCurrentLength={} backendTargetLength={} currentLength={} targetLengthBefore={} targetLengthAfter={} ropeResolved={} controller={} updateApplied={} skipReason={} configurationUpdateCount={}",
                    owner == null ? "static_world" : owner.getUniqueId(),
                    owner == null ? this.worldPosition : localPos(owner, this.worldPosition),
                    this.simulated$getLogicalConstraintId(), speed, converted, clamped,
                    this.simulated$getRopeCurrentLength(), this.simulated$getRopeTargetLength(),
                    this.simulated$getRopeFirstSegmentExtension(), this.ropeFixedSegmentCount,
                    this.simulated$getRopeCurrentLength(), this.simulated$getRopeTargetLength(),
                    this.simulated$getRopeCurrentLength(), before, this.targetValue, ropeResolved,
                    this.controller, changed, ropeResolved ? (clamped == 0.0D ? "zero_speed" : "none") : "rope_unresolved",
                    this.ropeBackendLengthWriteCount);
        }
    }

    public int getRedstoneSignal() {
        return Math.max(0, Math.min(15, (int) Math.round(this.lastSignal)));
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (!SimulatedConfig.ENABLE_M24_SIMULATED_SYSTEMS.get()) {
            return;
        }
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.family.isSensorOrControl()) {
            this.tickSensorOrControl(subLevel);
            return;
        }
        if (!this.family.isConstraintBacked()) {
            this.tickTorsionStructural(subLevel);
            return;
        }
        if (this.isDockingFamily() && this.dockingNextTickEligibilityTrace) {
            this.dockingNextTickEligibilityTrace = false;
            this.logDockingPairEligibility("NEXT_TICK", null, this.dockingPowered, false, false);
        }
        if (this.family == M24Family.SWIVEL_BEARING && this.controller) {
            this.traceSwivelLifecycle(subLevel);
        }
        if (this.postCreateValidationPending && this.ownsActiveBackend()) {
            this.samplePostCreateConstraintState(subLevel);
        }
        if (this.partnerPos == null || this.partnerSableId == null || !this.partnerStillValid(subLevel)) {
            this.clearBackend(true);
            this.findPartner(subLevel);
        }
        if (this.controller && this.partnerPos != null && this.partnerSableId != null) {
            this.ensureBackend(subLevel);
        }
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (this.family == M24Family.TORSION_SPRING && SimulatedConfig.ENABLE_M24_SIMULATED_SYSTEMS.get()) {
            this.lastSignal = Math.min(15.0D, handle.getAngularVelocity(new Vector3d()).length() * 3.0D);
        }
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        return partner == null ? null : List.of(partner);
    }

    @Override
    public boolean simulated$hasActiveConstraint() {
        if (this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK && this.partnerPos != null && this.level != null
                && this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity partner) {
            return partner.family == M24Family.SWIVEL_BEARING
                    && partner.controller
                    && partner.ownsActiveBackend()
                    && this.worldPosition.equals(partner.partnerPos);
        }
        if (!this.family.isConstraintBacked()) {
            return false;
        }
        if (this.ownsActiveBackend()) {
            return true;
        }
        if (!this.controller && this.partnerPos != null && this.level != null
                && this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity partner) {
            return partner.controller && partner.ownsActiveBackend()
                    && this.worldPosition.equals(partner.partnerPos);
        }
        return false;
    }

    private boolean ownsActiveBackend() {
        if (this.ropeObject != null && this.ropeObject.isActive()) {
            return true;
        }
        return this.backendHandle != null && this.backendHandle.isValid();
    }

    @Override
    public String simulated$getLogicalConstraintId() {
        final String partner = this.partnerPos == null ? "unpaired" : Long.toString(this.partnerPos.asLong());
        return "m24:" + this.family.id() + ":" + this.worldPosition.asLong() + ":" + partner;
    }

    @Override
    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);
        if (this.partnerPos != null) {
            tag.putLong(NBT_PARTNER, this.partnerPos.asLong());
        }
        if (this.partnerSableId != null) {
            tag.putUUID(NBT_PARTNER_SABLE, this.partnerSableId);
        }
        tag.putBoolean(NBT_CONTROLLER, this.controller);
        tag.putBoolean(NBT_ENABLED, this.enabled);
        tag.putDouble(NBT_TARGET, this.targetValue);
        tag.putDouble(NBT_LAST_SIGNAL, this.lastSignal);
        tag.putDouble(NBT_TORSION_ANGLE, this.torsionAngle);
        tag.putDouble(NBT_TORSION_ANGLE_LIMIT, this.torsionAngleLimit);
        tag.putBoolean(NBT_DOCKING_POWERED, this.dockingPowered);
        tag.putBoolean(NBT_DOCKING_PAIR_ELIGIBLE, this.dockingPairEligible);
    }

    @Override
    protected void read(final CompoundTag tag, final boolean clientPacket) {
        super.read(tag, clientPacket);
        this.partnerPos = tag.contains(NBT_PARTNER, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(NBT_PARTNER)) : null;
        this.partnerSableId = tag.hasUUID(NBT_PARTNER_SABLE) ? tag.getUUID(NBT_PARTNER_SABLE) : null;
        this.controller = tag.getBoolean(NBT_CONTROLLER);
        this.enabled = !tag.contains(NBT_ENABLED, Tag.TAG_BYTE) || tag.getBoolean(NBT_ENABLED);
        this.targetValue = tag.getDouble(NBT_TARGET);
        this.lastSignal = tag.getDouble(NBT_LAST_SIGNAL);
        this.torsionAngle = tag.getDouble(NBT_TORSION_ANGLE);
        this.torsionAngleLimit = tag.contains(NBT_TORSION_ANGLE_LIMIT, Tag.TAG_DOUBLE)
                ? tag.getDouble(NBT_TORSION_ANGLE_LIMIT)
                : 90.0D;
        this.dockingPowered = tag.getBoolean(NBT_DOCKING_POWERED);
        this.dockingPairEligible = tag.contains(NBT_DOCKING_PAIR_ELIGIBLE)
                ? tag.getBoolean(NBT_DOCKING_PAIR_ELIGIBLE)
                : this.dockingPowered;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return super.getUpdateTag();
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void remove() {
        this.clearBackend(true);
        super.remove();
    }

    public String inspect() {
        final ServerSubLevel owner = this.resolveOwnerSubLevel();
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        final RigidBodyHandle ownerHandle = owner == null ? null : RigidBodyHandle.of(owner);
        final RigidBodyHandle partnerHandle = partner == null ? null : RigidBodyHandle.of(partner);
        final String mode = this.constraintMode(owner, partner);
        return "family=" + this.family.id()
                + " runtimeState=" + this.runtimeState(owner, partner, mode)
                + " upstreamMode=" + this.family.upstreamRole()
                + " upstreamRole=" + this.family.upstreamRole()
                + " constraintMode=" + mode
                + " logicalConstraintId=" + this.simulated$getLogicalConstraintId()
                + " ownerSable=" + (owner == null ? "static_world" : owner.getUniqueId())
                + " partnerSable=" + (partner == null ? "unresolved" : partner.getUniqueId())
                + " sameBody=" + (owner != null && owner == partner)
                + " ownerHandleValid=" + (ownerHandle != null && ownerHandle.isValid())
                + " partnerHandleValid=" + (partnerHandle != null && partnerHandle.isValid())
                + " endpointA=" + this.worldPosition.toShortString()
                + " endpointB=" + shortPos(this.partnerPos)
                + " endpointALocal=" + shortPos(localPos(owner, this.worldPosition))
                + " endpointBLocal=" + shortPos(localPos(partner, this.partnerPos))
                + " active=" + this.simulated$hasActiveConstraint()
                + " backendHandleValid=" + (this.backendHandle != null && this.backendHandle.isValid())
                + " postSolverValidation=" + this.postSolverValidationState()
                + " enabled=" + this.enabled
                + " signal=" + this.lastSignal
                + " dockingPowered=" + this.dockingPowered
                + " dockingPairEligible=" + this.dockingPairEligible
                + " dockingLifecycle=" + this.dockingLifecycleState()
                + " controllerLogicalConstraintPresent=" + (this.partnerPos != null)
                + " partnerLogicalConstraintPresent=" + (this.resolvePartnerComponent() != null
                        && this.resolvePartnerComponent().partnerPos != null)
                + " controllerActiveConstraintPresent=" + this.simulated$hasActiveConstraint()
                + " partnerActiveConstraintPresent=" + (this.resolvePartnerComponent() != null
                        && this.resolvePartnerComponent().simulated$hasActiveConstraint())
                + " controllerDisassemblyBlocked=" + this.simulated$hasActiveConstraint()
                + " partnerDisassemblyBlocked=" + (this.resolvePartnerComponent() != null
                        && this.resolvePartnerComponent().simulated$hasActiveConstraint())
                + " upstreamLocking=" + (this.enabled && this.targetValue != 0.0D)
                + " kineticSpeed=" + this.getSpeed()
                + " createCount=" + this.swivelCreateCount
                + " removeCount=" + this.swivelRemoveCount
                + " recreateCount=" + this.swivelRecreateCount
                + " configurationUpdateCount=" + this.swivelConfigurationUpdateCount
                + " torsionAngle=" + this.torsionAngle
                + " torsionAngleLimit=" + this.torsionAngleLimit
                + " disposition=" + this.disposition();
    }

    private void tickTorsionStructural(final ServerSubLevel subLevel) {
        final double speed = this.getSpeed();
        final boolean powered = this.level != null && this.level.hasNeighborSignal(this.worldPosition);
        if (speed != 0.0D) {
            this.torsionTargetAngle = this.torsionAngleLimit * Math.signum(speed);
        } else if (!powered) {
            this.torsionTargetAngle = 0.0D;
        } else {
            this.torsionTargetAngle = this.torsionAngle;
        }
        final double step = Math.abs(KineticBlockEntity.convertToAngular((float) speed));
        if (step > 0.0D) {
            this.torsionAngle = approach(this.torsionAngle, this.torsionTargetAngle, step);
        } else if (!powered) {
            this.torsionAngle = approach(this.torsionAngle, 0.0D, 3.0D);
        }
        this.lastSignal = Math.min(15.0D, Math.abs(this.torsionAngle / this.torsionAngleLimit) * 15.0D);
    }

    private void tickSensorOrControl(final ServerSubLevel subLevel) {
        if (this.level == null) {
            return;
        }
        if (this.family == M24Family.ALTITUDE_SENSOR) {
            this.lastSignal = Math.max(0.0D, Math.min(15.0D, this.visibleCenter(subLevel, this.worldPosition).y / 16.0D));
            return;
        }
        if (this.family == M24Family.VELOCITY_SENSOR) {
            final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            this.lastSignal = handle == null ? 0.0D : Math.min(15.0D, handle.getLinearVelocity(new Vector3d()).length() * 2.0D);
            return;
        }
        if (this.family == M24Family.OPTICAL_SENSOR) {
            final Vec3 start = this.visibleCenter(subLevel, this.worldPosition);
            final Direction facing = this.getBlockState().getValue(DirectionalBlock.FACING);
            final Vec3 end = start.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(16.0D));
            final BlockHitResult hit = this.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, null));
            this.lastSignal = hit.getType() == HitResult.Type.BLOCK
                    ? Math.max(0.0D, 15.0D - hit.getLocation().distanceTo(start))
                    : 0.0D;
            return;
        }
        if (this.family == M24Family.STEERING_WHEEL) {
            this.lastSignal = this.enabled ? 15.0D : 0.0D;
        }
    }

    private void findPartner(final ServerSubLevel owner) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }
        if (this.family == M24Family.SWIVEL_BEARING || this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK) {
            this.findSwivelPartner(owner, container, serverLevel);
            return;
        }
        if (this.isDockingFamily()
                && (this.family != M24Family.DOCKING_CONNECTOR
                        || !this.dockingPowered
                        || !this.dockingPairEligible)) {
            return;
        }
        final Vec3 ownVisible = this.visibleCenter(owner, this.worldPosition);
        M24PhysicalBlockEntity best = null;
        ServerSubLevel bestSubLevel = null;
        double bestDistance = Double.MAX_VALUE;
        for (final ServerSubLevel candidateSubLevel : container.getAllSubLevels()) {
            if (candidateSubLevel == owner) {
                continue;
            }
            for (final BlockPos block : SimAssemblyHelper.collectBlocks(serverLevel, candidateSubLevel)) {
                final BlockEntity blockEntity = serverLevel.getBlockEntity(block);
                if (!(blockEntity instanceof final M24PhysicalBlockEntity component)
                        || component == this
                        || !this.canPairWith(component)) {
                    continue;
                }
                if (this.isDockingFamily()
                        && (!component.dockingPowered
                                || !component.dockingPairEligible
                                || !this.dockingCandidateGeometryValid(owner, component, candidateSubLevel))) {
                    continue;
                }
                final double distance = ownVisible.distanceToSqr(component.visibleCenter(candidateSubLevel, block));
                if (distance < bestDistance) {
                    best = component;
                    bestSubLevel = candidateSubLevel;
                    bestDistance = distance;
                }
            }
        }
        final double maxDistance = SimulatedConfig.M24_PAIRING_RANGE.get();
        if (best == null || bestSubLevel == null || bestDistance > maxDistance * maxDistance) {
            return;
        }
        if (this.isDockingFamily()) {
            this.logDockingPairEligibility("PAIR_ATTEMPT", best, this.dockingPowered, true, true);
            best.dockingPairEligible = best.dockingPowered;
        }
        this.partnerPos = best.getBlockPos().immutable();
        this.partnerSableId = bestSubLevel.getUniqueId();
        best.partnerPos = this.worldPosition.immutable();
        best.partnerSableId = owner.getUniqueId();
        this.assignPairController(owner, best, bestSubLevel);
        this.sync();
        best.sync();
        Sable.LOGGER.info("SABLE_M24_CONSTRAINT phase=PAIR family={} owner={} partner={} controller={}",
                this.family.id(), owner.getUniqueId(), bestSubLevel.getUniqueId(), this.controller);
        if (this.family == M24Family.SWIVEL_BEARING || best.family == M24Family.SWIVEL_BEARING) {
            Sable.LOGGER.info("SABLE_M24_SWIVEL phase=PAIR_FOUND owner={} partner={} bearingController={} linkController={}",
                    this.family == M24Family.SWIVEL_BEARING ? owner.getUniqueId() : bestSubLevel.getUniqueId(),
                    this.family == M24Family.SWIVEL_BEARING ? bestSubLevel.getUniqueId() : owner.getUniqueId(),
                    this.family == M24Family.SWIVEL_BEARING ? this.controller : best.controller,
                    this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK ? this.controller : best.controller);
        }
    }

    private void findSwivelPartner(final ServerSubLevel owner, final ServerSubLevelContainer container,
                                   final ServerLevel serverLevel) {
        final List<String> candidateSables = new ArrayList<>();
        final List<String> candidateDistances = new ArrayList<>();
        final List<String> candidateFacingMatches = new ArrayList<>();
        M24PhysicalBlockEntity best = null;
        ServerSubLevel bestSubLevel = null;
        double bestDistance = Double.MAX_VALUE;
        for (final ServerSubLevel candidateSubLevel : container.getAllSubLevels()) {
            if (candidateSubLevel == owner) {
                continue;
            }
            for (final BlockPos block : SimAssemblyHelper.collectBlocks(serverLevel, candidateSubLevel)) {
                final BlockEntity blockEntity = serverLevel.getBlockEntity(block);
                if (!(blockEntity instanceof final M24PhysicalBlockEntity component)
                        || component == this
                        || !this.canPairWith(component)) {
                    continue;
                }
                final double distance = this.swivelEndpointDistance(owner, component, candidateSubLevel);
                final boolean facingMatch = this.swivelFacingMatches(component);
                candidateSables.add(candidateSubLevel.getUniqueId().toString());
                candidateDistances.add(Double.toString(distance));
                candidateFacingMatches.add(Boolean.toString(facingMatch));
                if (facingMatch && distance <= this.swivelPairingTolerance() && distance < bestDistance) {
                    best = component;
                    bestSubLevel = candidateSubLevel;
                    bestDistance = distance;
                }
            }
        }
        final boolean shouldLog = SableDiagnosticFlags.TRACE_M24 && !candidateSables.isEmpty()
                && this.level != null
                && this.level.getGameTime() - this.lastPairDiscoveryLogGameTime >= 20L;
        if (shouldLog) {
            this.lastPairDiscoveryLogGameTime = this.level.getGameTime();
            Sable.LOGGER.info("SABLE_M24_SWIVEL_PAIR_DISCOVERY bearingSable={} candidateCount={} candidateSables={} candidateDistances={} candidateFacingMatches={} selectedPartner={} selectionReason={}",
                    this.family == M24Family.SWIVEL_BEARING ? owner.getUniqueId() : "waiting_for_bearing_owner",
                    candidateSables.size(), candidateSables, candidateDistances, candidateFacingMatches,
                    bestSubLevel == null ? "none" : bestSubLevel.getUniqueId(),
                    bestSubLevel == null ? "no_upstream_topology_match" : "visible_anchor_and_opposing_facing_match");
        }
        if (best == null || bestSubLevel == null) {
            return;
        }
        this.partnerPos = best.getBlockPos().immutable();
        this.partnerSableId = bestSubLevel.getUniqueId();
        best.partnerPos = this.worldPosition.immutable();
        best.partnerSableId = owner.getUniqueId();
        this.assignPairController(owner, best, bestSubLevel);
        this.sync();
        best.sync();
        Sable.LOGGER.info("SABLE_M24_CONSTRAINT phase=PAIR family={} owner={} partner={} controller={}",
                this.family.id(), owner.getUniqueId(), bestSubLevel.getUniqueId(), this.controller);
        Sable.LOGGER.info("SABLE_M24_SWIVEL phase=PAIR_FOUND owner={} partner={} bearingController={} linkController={}",
                this.family == M24Family.SWIVEL_BEARING ? owner.getUniqueId() : bestSubLevel.getUniqueId(),
                this.family == M24Family.SWIVEL_BEARING ? bestSubLevel.getUniqueId() : owner.getUniqueId(),
                this.family == M24Family.SWIVEL_BEARING ? this.controller : best.controller,
                this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK ? this.controller : best.controller);
    }

    private void assignPairController(final ServerSubLevel owner,
                                      final M24PhysicalBlockEntity partner,
                                      final ServerSubLevel partnerSubLevel) {
        if (this.family == M24Family.SWIVEL_BEARING || partner.family == M24Family.SWIVEL_BEARING) {
            this.controller = this.family == M24Family.SWIVEL_BEARING;
            partner.controller = partner.family == M24Family.SWIVEL_BEARING;
            return;
        }
        if (this.family == M24Family.ROPE_WINCH || partner.family == M24Family.ROPE_WINCH) {
            this.controller = this.family == M24Family.ROPE_WINCH;
            partner.controller = partner.family == M24Family.ROPE_WINCH;
            return;
        }
        if (this.isDockingFamily() || partner.isDockingFamily()) {
            this.controller = this.family == M24Family.DOCKING_CONNECTOR;
            partner.controller = partner.family == M24Family.DOCKING_CONNECTOR;
            return;
        }
        final int order = owner.getUniqueId().toString().compareTo(partnerSubLevel.getUniqueId().toString());
        this.controller = order < 0 || (order == 0 && this.worldPosition.asLong() <= partner.getBlockPos().asLong());
        partner.controller = !this.controller;
    }

    private boolean canPairWith(final M24PhysicalBlockEntity other) {
        if (this.family == M24Family.ROPE_WINCH) {
            return other.family == M24Family.ROPE_CONNECTOR;
        }
        if (this.family == M24Family.ROPE_CONNECTOR) {
            return other.family == M24Family.ROPE_CONNECTOR || other.family == M24Family.ROPE_WINCH;
        }
        if (this.family == M24Family.DOCKING_CONNECTOR) {
            return other.family == M24Family.PAIRED_DOCKING_CONNECTOR;
        }
        if (this.family == M24Family.PAIRED_DOCKING_CONNECTOR) {
            return other.family == M24Family.DOCKING_CONNECTOR;
        }
        if (this.family == M24Family.SWIVEL_BEARING) {
            return other.family == M24Family.SWIVEL_BEARING_LINK_BLOCK;
        }
        if (this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK) {
            return other.family == M24Family.SWIVEL_BEARING;
        }
        return this.family == other.family;
    }

    private boolean partnerStillValid(final ServerSubLevel owner) {
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        if (partner == null || partner == owner || this.partnerPos == null || this.level == null) {
            return false;
        }
        final BlockEntity blockEntity = this.level.getBlockEntity(this.partnerPos);
        if (!(blockEntity instanceof final M24PhysicalBlockEntity component) || !this.canPairWith(component)) {
            return false;
        }
        if (this.family == M24Family.SWIVEL_BEARING || this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK) {
            // Upstream discovers the plate geometrically once, then retains explicit ownership.
            // A running hinge must be allowed to correct temporary anchor displacement itself.
            return this.swivelFacingMatches(component)
                    && component.partnerSableId != null
                    && component.partnerSableId.equals(owner.getUniqueId())
                    && this.worldPosition.equals(component.partnerPos);
        }
        return true;
    }

    private void ensureBackend(final ServerSubLevel owner) {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        if (partner == null || partner == owner || this.partnerPos == null) {
            return;
        }
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverLevel);
        if (physicsSystem == null) {
            return;
        }
        if ((this.backendHandle != null && this.backendHandle.isValid())
                || (this.ropeObject != null && this.ropeObject.isActive())) {
            this.updateBackend();
            return;
        }
        final RigidBodyHandle ownerHandle = physicsSystem.getPhysicsHandle(owner);
        final RigidBodyHandle partnerHandle = physicsSystem.getPhysicsHandle(partner);
        if (ownerHandle == null || partnerHandle == null || !ownerHandle.isValid() || !partnerHandle.isValid()) {
            this.logConstraintRejected(owner, partner, "INVALID_BODY_HANDLE");
            return;
        }
        final Vector3d anchorA = this.backendAnchorRaw(this.worldPosition, this.getBlockState());
        final Vector3d anchorB = this.backendAnchorRaw(this.partnerPos, this.level.getBlockState(this.partnerPos));
        final Vec3 visibleA = this.visiblePoint(owner, anchorA);
        final Vec3 visibleB = this.visiblePoint(partner, anchorB);
        final double visibleEndpointDistance = visibleA.distanceTo(visibleB);
        final double rawEndpointDistance = anchorA.distance(anchorB);
        final double localAnchorMagnitudeA = this.localAnchorMagnitude(owner, anchorA);
        final double localAnchorMagnitudeB = this.localAnchorMagnitude(partner, anchorB);
        final Vector3d axisA = normal(this.getBlockState());
        final Vector3d axisB = normal(this.level.getBlockState(this.partnerPos));
        final boolean finiteFrame = finite(anchorA)
                && finite(anchorB)
                && finite(axisA)
                && finite(axisB)
                && Double.isFinite(visibleEndpointDistance)
                && Double.isFinite(rawEndpointDistance)
                && Double.isFinite(localAnchorMagnitudeA)
                && Double.isFinite(localAnchorMagnitudeB);
        this.logConstraintFrame("BEFORE_CREATE", owner, partner, anchorA, anchorB, visibleA, visibleB,
                axisA, axisB, visibleEndpointDistance, rawEndpointDistance, localAnchorMagnitudeA,
                localAnchorMagnitudeB);
        if (!finiteFrame || this.unsatisfiedRigidJoint(visibleEndpointDistance)) {
            this.logConstraintRejected(owner, partner, !finiteFrame ? "NON_FINITE_CONSTRAINT_FRAME" : "INVALID_CONSTRAINT_FRAME");
            return;
        }
        if (this.family == M24Family.SWIVEL_BEARING) {
            this.logSwivelActivation("ACTIVATION_EVALUATED", owner, partner);
            Sable.LOGGER.info("SABLE_M24_SWIVEL phase=BACKEND_CREATE_REQUEST owner={} partner={} rawAnchorA={} rawAnchorB={} axisA={} axisB={}",
                    owner.getUniqueId(), partner.getUniqueId(), anchorA, anchorB, axisA, axisB);
            this.logSwivelDebug("PRE_CREATE", owner, partner, ownerHandle, partnerHandle, anchorA, anchorB,
                    visibleA, visibleB, axisA, axisB);
            this.backendHandle = physicsSystem.getPipeline().addConstraint(owner, partner,
                    new RotaryConstraintConfiguration(anchorA, anchorB, axisA, axisB));
            this.swivelCreateCount++;
            if (this.swivelCreatedGameTime != Long.MIN_VALUE) {
                this.swivelRecreateCount++;
            }
            this.swivelCreatedGameTime = this.level.getGameTime();
            this.updateBackend();
            Sable.LOGGER.info("SABLE_M24_SWIVEL phase=BACKEND_CREATED owner={} partner={} backendHandleValid={} visibleJointPoint={}",
                    owner.getUniqueId(), partner.getUniqueId(),
                    this.backendHandle != null && this.backendHandle.isValid(), visibleA);
        } else if (this.family == M24Family.DOCKING_CONNECTOR || this.family == M24Family.PAIRED_DOCKING_CONNECTOR) {
            final Quaterniond relativeOrientation = new Quaterniond(owner.logicalPose().orientation())
                    .conjugate()
                    .mul(new Quaterniond(partner.logicalPose().orientation()))
                    .normalize();
            this.backendHandle = physicsSystem.getPipeline().addConstraint(owner, partner,
                    new FixedConstraintConfiguration(anchorA, anchorB, relativeOrientation));
            this.logDockingDebug(owner, partner, ownerHandle, partnerHandle);
            this.updateBackend();
        } else if (this.family == M24Family.ROPE_CONNECTOR || this.family == M24Family.ROPE_WINCH) {
            final ObjectArrayList<Vector3d> points = this.createUpstreamRopePoints(visibleA, visibleB);
            if (points.size() < 2) {
                this.logConstraintRejected(owner, partner, "ROPE_ENDPOINT_DISTANCE_INVALID");
                return;
            }
            this.ropeInitialVisibleDistance = visibleEndpointDistance;
            this.ropeInitialFirstSegmentLength = points.get(0).distance(points.get(1));
            this.ropeFixedSegmentCount = Math.max(0, points.size() - 2);
            this.logRopeDebug("CREATE", owner, partner, ownerHandle, partnerHandle, visibleA, visibleB, points);
            this.ropeObject = new RopePhysicsObject(points, 0.125D);
            this.logRopeBackendConstruct(owner, partner, ownerHandle, partnerHandle, anchorA, anchorB,
                    visibleA, visibleB, points);
            physicsSystem.addObject(this.ropeObject);
            this.ropeObject.setAttachment(RopeHandle.AttachmentPoint.START, anchorA, owner);
            this.ropeObject.setAttachment(RopeHandle.AttachmentPoint.END, anchorB, partner);
            this.ropeAppliedFirstSegmentLength = this.ropeInitialFirstSegmentLength;
            if (this.family == M24Family.ROPE_WINCH) {
                this.targetValue = this.ropeInitialFirstSegmentLength;
            }
            this.ropeLastLengthWriteOwner = "CREATE_CONSTRUCTOR";
            this.updateBackend();
        }
        if (this.simulated$hasActiveConstraint()) {
            this.postCreateValidationPending = true;
            this.postCreateValidated = false;
            this.postCreateInvalid = false;
            this.postCreateValidationTicks = 0;
            this.postCreateBodyAPositionBefore = new Vector3d(owner.logicalPose().position());
            this.postCreateBodyBPositionBefore = new Vector3d(partner.logicalPose().position());
            Sable.LOGGER.info("SABLE_M24_CONSTRAINT phase=POST_HANDLE_CREATE family={} logicalId={} owner={} partner={} active={}",
                    this.family.id(), this.simulated$getLogicalConstraintId(), owner.getUniqueId(), partner.getUniqueId(),
                    this.simulated$hasActiveConstraint());
        }
    }

    private void updateBackend() {
        if (this.backendHandle instanceof final RotaryConstraintHandle rotary) {
            if (this.family == M24Family.SWIVEL_BEARING) {
                final double target = this.enabled && this.targetValue != 0.0D ? this.targetValue : 0.0D;
                final double stiffness = this.enabled && this.targetValue != 0.0D ? 24.0D : 0.0D;
                final double damping = this.enabled && this.targetValue != 0.0D ? 5.0D : 1.3D;
                rotary.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, target, stiffness, damping, false, 0.0D);
                this.swivelConfigurationUpdateCount++;
            }
            rotary.setContactsEnabled(false);
        }
        if (this.ropeObject != null && this.ropeObject.isActive()) {
            final double length = this.family == M24Family.ROPE_WINCH
                    ? this.targetValue
                    : this.ropeInitialFirstSegmentLength;
            this.applyRopeFirstSegmentLength(length,
                    this.family == M24Family.ROPE_WINCH ? "WINCH_TARGET_UPDATE" : "ROPE_STATIC_TARGET");
        }
    }

    private void applyRopeFirstSegmentLength(final double length, final String owner) {
        if (this.ropeObject == null || !this.ropeObject.isActive() || !Double.isFinite(length)) {
            return;
        }
        if (!Double.isNaN(this.ropeAppliedFirstSegmentLength)
                && Math.abs(length - this.ropeAppliedFirstSegmentLength) <= 1.0E-6D) {
            return;
        }
        final double before = this.ropeAppliedFirstSegmentLength;
        this.ropeObject.setFirstSegmentLength(length);
        this.ropeAppliedFirstSegmentLength = length;
        this.ropeBackendLengthWriteCount++;
        this.ropeLastLengthWriteOwner = owner;
        if (SableDiagnosticFlags.TRACE_M24) {
            Sable.LOGGER.info("SABLE_M24_ROPE_DEBUG phase=LENGTH_WRITE family={} logicalId={} configuredLengthBefore={} configuredLengthAfter={} writeOwner={} writeCount={}",
                    this.family.id(), this.simulated$getLogicalConstraintId(), before, length, owner,
                    this.ropeBackendLengthWriteCount);
        }
    }

    private void updateWinchFromCreateKinetics() {
        if (this.ropeObject == null || !this.ropeObject.isActive()) {
            return;
        }
        final double movement = Math.max(-0.49D,
                Math.min(0.49D, KineticBlockEntity.convertToLinear(this.getSpeed())));
        if (movement == 0.0D) {
            return;
        }
        this.ropeObject.updatePose();
        double extension = this.targetValue + movement;
        while (extension < 0.0D && this.ropeObject.getPoints().size() > 2) {
            this.ropeObject.removeFirstPoint();
            this.ropeFixedSegmentCount = Math.max(0, this.ropeFixedSegmentCount - 1);
            extension += 1.0D;
        }
        while (extension > 1.0D) {
            final Vec3 start = this.resolveOwnerSubLevel() == null
                    ? Vec3.atCenterOf(this.worldPosition)
                    : this.visibleCenter(this.resolveOwnerSubLevel(), this.worldPosition);
            this.ropeObject.addPoint(new Vector3d(start.x, start.y, start.z));
            this.ropeFixedSegmentCount++;
            extension -= 1.0D;
        }
        if (this.ropeObject.getPoints().size() <= 2) {
            extension = Math.max(1.0D, extension);
        }
        this.targetValue = extension;
        this.sync();
    }

    private void clearBackend(final boolean clearPartner) {
        if (this.family == M24Family.SWIVEL_BEARING && this.backendHandle != null) {
            this.swivelRemoveCount++;
            Sable.LOGGER.info("SABLE_M24_SWIVEL phase=BACKEND_REMOVED logicalId={} createCount={} removeCount={} recreateCount={} configurationUpdateCount={} clearPartner={}",
                    this.simulated$getLogicalConstraintId(), this.swivelCreateCount, this.swivelRemoveCount,
                    this.swivelRecreateCount, this.swivelConfigurationUpdateCount, clearPartner);
        }
        if (this.backendHandle != null && this.backendHandle.isValid()) {
            this.backendHandle.remove();
        }
        this.backendHandle = null;
        if (this.ropeObject != null && this.level instanceof final ServerLevel serverLevel) {
            final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverLevel);
            if (physicsSystem != null) {
                physicsSystem.removeObject(this.ropeObject);
            }
        }
        this.ropeObject = null;
        this.postCreateValidationPending = false;
        this.postCreateValidated = false;
        this.postCreateInvalid = false;
        this.postCreateValidationTicks = 0;
        this.postCreateBodyAPositionBefore = null;
        this.postCreateBodyBPositionBefore = null;
        this.ropeInitialFirstSegmentLength = 0.0D;
        this.ropeFixedSegmentCount = 0;
        this.ropeInitialVisibleDistance = 0.0D;
        this.ropeAppliedFirstSegmentLength = Double.NaN;
        this.ropeBackendLengthWriteCount = 0;
        this.ropeLastLengthWriteOwner = "none";
        if (clearPartner && this.partnerPos != null && this.level != null
                && this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity partner) {
            partner.partnerPos = null;
            partner.partnerSableId = null;
            partner.controller = false;
            partner.clearBackend(false);
            partner.sync();
        }
        this.partnerPos = null;
        this.partnerSableId = null;
        this.controller = false;
        this.sync();
    }

    private @Nullable ServerSubLevel resolvePartnerSubLevel() {
        if (this.partnerSableId == null || this.level == null) {
            return null;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (!(container instanceof final ServerSubLevelContainer serverContainer)) {
            return null;
        }
        for (final ServerSubLevel subLevel : serverContainer.getAllSubLevels()) {
            if (subLevel.getUniqueId().equals(this.partnerSableId)) {
                return subLevel;
            }
        }
        return null;
    }

    private @Nullable ServerSubLevel resolveOwnerSubLevel() {
        if (!(this.level instanceof final ServerLevel serverLevel)) {
            return null;
        }
        final Object containing = Sable.HELPER.getContaining(this.level, this.worldPosition);
        if (containing instanceof final ServerSubLevel subLevel) {
            return subLevel;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (!(container instanceof final ServerSubLevelContainer serverContainer)) {
            return null;
        }
        for (final ServerSubLevel subLevel : serverContainer.getAllSubLevels()) {
            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (contains(bounds, this.worldPosition)
                    && SimAssemblyHelper.collectBlocks(serverLevel, subLevel).contains(this.worldPosition)) {
                return subLevel;
            }
        }
        return null;
    }

    private static BlockEntityType<? extends M24PhysicalBlockEntity> typeFor(final M24Family family) {
        return switch (family) {
            case TORSION_SPRING -> SimulatedBlockEntityTypes.TORSION_SPRING.get();
            case SWIVEL_BEARING -> SimulatedBlockEntityTypes.SWIVEL_BEARING.get();
            case SWIVEL_BEARING_LINK_BLOCK -> SimulatedBlockEntityTypes.SWIVEL_BEARING_LINK_BLOCK.get();
            case ROPE_CONNECTOR -> SimulatedBlockEntityTypes.ROPE_CONNECTOR.get();
            case ROPE_WINCH -> SimulatedBlockEntityTypes.ROPE_WINCH.get();
            case DOCKING_CONNECTOR -> SimulatedBlockEntityTypes.DOCKING_CONNECTOR.get();
            case PAIRED_DOCKING_CONNECTOR -> SimulatedBlockEntityTypes.PAIRED_DOCKING_CONNECTOR.get();
            case ALTITUDE_SENSOR -> SimulatedBlockEntityTypes.ALTITUDE_SENSOR.get();
            case VELOCITY_SENSOR -> SimulatedBlockEntityTypes.VELOCITY_SENSOR.get();
            case OPTICAL_SENSOR -> SimulatedBlockEntityTypes.OPTICAL_SENSOR.get();
            case STEERING_WHEEL -> throw new IllegalArgumentException(
                    "Steering Wheel uses its dedicated kinetic block entity");
        };
    }

    private String constraintMode(final @Nullable ServerSubLevel owner, final @Nullable ServerSubLevel partner) {
        if (owner == null && partner == null) {
            return "STATIC_WORLD";
        }
        if (owner != null && partner != null && owner != partner) {
            return "SABLE_TO_SABLE";
        }
        if (owner != null && owner == partner) {
            return "ERROR_SAME_BODY";
        }
        return "PARTIAL";
    }

    private String runtimeState(final @Nullable ServerSubLevel owner,
                                final @Nullable ServerSubLevel partner,
                                final String mode) {
        if (this.family.isSensorOrControl() || !this.family.isConstraintBacked()) {
            return owner == null ? "WAITING_FOR_VALID_BODIES" : "READY";
        }
        if ("ERROR_SAME_BODY".equals(mode)) {
            return "ERROR_SAME_BODY";
        }
        if (this.isDockingFamily() && !this.dockingPairEligible && !this.simulated$hasActiveConstraint()) {
            return "DISCONNECTED";
        }
        if ("STATIC_WORLD".equals(mode)) {
            return "WAITING_FOR_VALID_BODIES";
        }
        if ("PARTIAL".equals(mode)) {
            return "PARTIAL_ENDPOINT_RESOLUTION";
        }
        if (!this.simulated$hasActiveConstraint()) {
            return "WAITING_FOR_BACKEND";
        }
        final M24PhysicalBlockEntity controllerComponent = this.activeControllerComponent();
        final boolean invalid = controllerComponent == null ? this.postCreateInvalid : controllerComponent.postCreateInvalid;
        final boolean validated = controllerComponent == null ? this.postCreateValidated : controllerComponent.postCreateValidated;
        if (invalid) {
            return "FIRST_STEP_INVALID";
        }
        return validated ? "ACTIVE" : "WAITING_FOR_POST_SOLVER_VALIDATION";
    }

    private String postSolverValidationState() {
        if (!this.simulated$hasActiveConstraint()) {
            return "NOT_STARTED";
        }
        final M24PhysicalBlockEntity controllerComponent = this.activeControllerComponent();
        if (controllerComponent == null) {
            return "UNRESOLVED_CONTROLLER";
        }
        if (controllerComponent.postCreateInvalid) {
            return "FAIL";
        }
        if (controllerComponent.postCreateValidated) {
            return "PASS";
        }
        return controllerComponent.postCreateValidationPending ? "WAITING" : "NOT_STARTED";
    }

    private @Nullable M24PhysicalBlockEntity activeControllerComponent() {
        if (this.controller && this.ownsActiveBackend()) {
            return this;
        }
        if (this.partnerPos != null && this.level != null
                && this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity partner
                && partner.controller
                && partner.ownsActiveBackend()
                && this.worldPosition.equals(partner.partnerPos)) {
            return partner;
        }
        return null;
    }

    private String disposition() {
        if (this.family == M24Family.TORSION_SPRING) {
            return "STRUCTURAL_ONBOARD_CREATE_KINETIC_BACKPORT";
        }
        if (this.family == M24Family.STEERING_WHEEL) {
            return "ONBOARD_INPUT_ONLY_AERONAUTICS_DEFERRED";
        }
        if (this.family.isSensorOrControl()) {
            return "VISIBLE_COORDINATE_SENSOR_READY";
        }
        return this.family.upstreamRole();
    }

    private Vec3 visibleCenter(final ServerSubLevel subLevel, final BlockPos raw) {
        return subLevel.logicalPose().transformPosition(raw.getCenter());
    }

    private Vec3 visiblePoint(final ServerSubLevel subLevel, final Vector3d raw) {
        return subLevel.logicalPose().transformPosition(new Vec3(raw.x, raw.y, raw.z));
    }

    private Vector3d backendAnchorRaw(final BlockPos pos, final BlockState state) {
        final Vector3d center = center(pos);
        if (state.is(SimulatedBlocks.SWIVEL_BEARING.get())) {
            final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                    ? state.getValue(DirectionalBlock.FACING)
                    : Direction.UP;
            return center(pos.relative(facing));
        }
        if (state.is(SimulatedBlocks.SWIVEL_BEARING_LINK_BLOCK.get())) {
            final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                    ? state.getValue(DirectionalBlock.FACING)
                    : Direction.UP;
            return center(pos.relative(facing)).sub(facing.getStepX() * 0.001D,
                    facing.getStepY() * 0.001D,
                    facing.getStepZ() * 0.001D);
        }
        if (this.family == M24Family.DOCKING_CONNECTOR || this.family == M24Family.PAIRED_DOCKING_CONNECTOR) {
            final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                    ? state.getValue(DirectionalBlock.FACING)
                    : Direction.UP;
            return center.add(facing.getStepX() * 1.5D, facing.getStepY() * 1.5D, facing.getStepZ() * 1.5D);
        }
        if (this.family == M24Family.ROPE_CONNECTOR || this.family == M24Family.ROPE_WINCH) {
            final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                    ? state.getValue(DirectionalBlock.FACING)
                    : Direction.UP;
            return center.add(facing.getStepX() * -3.0D / 16.0D,
                    facing.getStepY() * -3.0D / 16.0D,
                    facing.getStepZ() * -3.0D / 16.0D);
        }
        return center;
    }

    private static Vector3d center(final BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private double localAnchorMagnitude(final ServerSubLevel subLevel, final Vector3d rawAnchor) {
        if (subLevel.getMassTracker().getCenterOfMass() == null) {
            return Double.NaN;
        }
        return rawAnchor.sub(subLevel.getMassTracker().getCenterOfMass(), new Vector3d()).length();
    }

    private boolean unsatisfiedRigidJoint(final double visibleEndpointDistance) {
        return (this.family == M24Family.SWIVEL_BEARING
                || this.family == M24Family.DOCKING_CONNECTOR
                || this.family == M24Family.PAIRED_DOCKING_CONNECTOR)
                && visibleEndpointDistance > 0.75D;
    }

    private double swivelEndpointDistance(final ServerSubLevel owner,
                                          final M24PhysicalBlockEntity candidate,
                                          final ServerSubLevel candidateSubLevel) {
        final Vector3d anchorA = this.backendAnchorRaw(this.worldPosition, this.getBlockState());
        final Vector3d anchorB = candidate.backendAnchorRaw(candidate.getBlockPos(), candidate.getBlockState());
        return this.visiblePoint(owner, anchorA).distanceTo(candidate.visiblePoint(candidateSubLevel, anchorB));
    }

    private boolean swivelFacingMatches(final M24PhysicalBlockEntity candidate) {
        if (!((this.family == M24Family.SWIVEL_BEARING && candidate.family == M24Family.SWIVEL_BEARING_LINK_BLOCK)
                || (this.family == M24Family.SWIVEL_BEARING_LINK_BLOCK && candidate.family == M24Family.SWIVEL_BEARING))) {
            return false;
        }
        final Vector3d ownAxis = normal(this.getBlockState()).normalize(new Vector3d());
        final Vector3d candidateAxis = normal(candidate.getBlockState()).normalize(new Vector3d());
        return ownAxis.dot(candidateAxis) <= -0.98D;
    }

    private double swivelPairingTolerance() {
        return 0.125D;
    }

    private boolean isDockingFamily() {
        return this.family == M24Family.DOCKING_CONNECTOR
                || this.family == M24Family.PAIRED_DOCKING_CONNECTOR;
    }

    private String dockingLifecycleState() {
        if (!this.isDockingFamily()) {
            return "not_applicable";
        }
        if (this.simulated$hasActiveConstraint()) {
            return "PAIRED_ACTIVE";
        }
        if (!this.dockingPairEligible) {
            return "DISCONNECTED_NOT_ELIGIBLE";
        }
        return this.dockingPowered ? "REARMED_ELIGIBLE" : "UNPAIRED_ELIGIBLE";
    }

    private @Nullable M24PhysicalBlockEntity resolvePartnerComponent() {
        if (this.partnerPos == null || this.level == null) {
            return null;
        }
        return this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity component
                ? component
                : null;
    }

    private boolean dockingCandidateGeometryValid(final @Nullable M24PhysicalBlockEntity candidate) {
        final ServerSubLevel owner = this.resolveOwnerSubLevel();
        final ServerSubLevel partner = candidate == null ? null : candidate.resolveOwnerSubLevel();
        return owner != null && partner != null && this.dockingCandidateGeometryValid(owner, candidate, partner);
    }

    private boolean dockingCandidateGeometryValid(final ServerSubLevel owner,
                                                   final M24PhysicalBlockEntity candidate,
                                                   final ServerSubLevel candidateSubLevel) {
        if (!this.isDockingFamily() || !this.canPairWith(candidate)) {
            return false;
        }
        final Vector3d ownFacing = normal(this.getBlockState()).normalize(new Vector3d());
        final Vector3d candidateFacing = normal(candidate.getBlockState()).normalize(new Vector3d());
        if (ownFacing.dot(candidateFacing) > -0.98D) {
            return false;
        }
        final Vector3d anchorA = this.backendAnchorRaw(this.worldPosition, this.getBlockState());
        final Vector3d anchorB = candidate.backendAnchorRaw(candidate.getBlockPos(), candidate.getBlockState());
        return this.visiblePoint(owner, anchorA).distanceTo(candidate.visiblePoint(candidateSubLevel, anchorB)) <= 0.75D;
    }

    private void logDockingPairEligibility(final String phase,
                                           final @Nullable M24PhysicalBlockEntity candidate,
                                           final boolean previouslyPowered,
                                           final boolean candidateGeometryValid,
                                           final boolean pairAttempted) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final ServerSubLevel owner = this.resolveOwnerSubLevel();
        final ServerSubLevel candidateOwner = candidate == null ? null : candidate.resolveOwnerSubLevel();
        final boolean partnerLogical = candidate != null && candidate.partnerPos != null;
        final boolean partnerActive = candidate != null && candidate.simulated$hasActiveConstraint();
        Sable.LOGGER.info("SABLE_M24_DOCKING_PAIR_ELIGIBILITY phase={} ownerSable={} partnerSable={} controllerPowered={} partnerPowered={} controllerBlockState={} partnerBlockState={} pairedState={} previousPowered={} pairEligible={} pairEligibilityReason={} existingLogicalRelation={} existingActiveConstraint={} controllerLogical={} partnerLogical={} controllerActive={} partnerActive={} backendHandleValid={} candidatePartner={} candidateGeometryValid={} pairAttempted={}",
                phase,
                owner == null ? "static_world" : owner.getUniqueId(),
                candidateOwner == null ? "unresolved" : candidateOwner.getUniqueId(),
                this.dockingPowered,
                candidate != null && candidate.dockingPowered,
                this.getBlockState(),
                candidate == null ? "unavailable" : candidate.getBlockState(),
                this.dockingLifecycleState(),
                previouslyPowered,
                this.dockingPairEligible,
                this.dockingPairEligible ? "powered_and_extended_or_rising_edge" : "unpowered_after_falling_edge",
                this.partnerPos != null,
                this.simulated$hasActiveConstraint(),
                this.partnerPos != null,
                partnerLogical,
                this.simulated$hasActiveConstraint(),
                partnerActive,
                this.backendHandle != null && this.backendHandle.isValid(),
                candidateOwner == null ? "none" : candidateOwner.getUniqueId(),
                candidateGeometryValid,
                pairAttempted);
    }

    private static Vector3d normal(final BlockState state) {
        if (state.hasProperty(DirectionalBlock.FACING)) {
            final Direction direction = state.getValue(DirectionalBlock.FACING);
            return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }
        return new Vector3d(0.0D, 1.0D, 0.0D);
    }

    private ObjectArrayList<Vector3d> createUpstreamRopePoints(final Vec3 visibleA, final Vec3 visibleB) {
        final double distance = visibleA.distanceTo(visibleB);
        final ObjectArrayList<Vector3d> points = new ObjectArrayList<>();
        if (!Double.isFinite(distance) || distance <= 1.0E-6D) {
            return points;
        }
        final int oneLongSegments = (int) Math.floor(distance);
        final int pointCount = Math.max(1, oneLongSegments + 1);
        final Vec3 diff = visibleB.subtract(visibleA).normalize();
        final double shortSegmentLength = distance - oneLongSegments;
        points.add(new Vector3d(visibleA.x, visibleA.y, visibleA.z));
        for (int i = 0; i < pointCount; i++) {
            final Vec3 point = visibleA.add(diff.scale(i + shortSegmentLength));
            points.add(new Vector3d(point.x, point.y, point.z));
        }
        return points;
    }

    private static boolean contains(final BoundingBox3ic bounds, final BlockPos pos) {
        return bounds != null
                && pos.getX() >= bounds.minX()
                && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY()
                && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ()
                && pos.getZ() <= bounds.maxZ();
    }

    private void sync() {
        this.setChanged();
        if (this.level instanceof final ServerLevel serverLevel) {
            final BlockState state = this.getBlockState();
            serverLevel.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    private void logSwivelActivation(final String phase, final ServerSubLevel owner, final ServerSubLevel partner) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final BlockState ownerState = this.getBlockState();
        final BlockState partnerState = this.partnerPos == null ? ownerState : this.level.getBlockState(this.partnerPos);
        final Direction ownerFacing = ownerState.hasProperty(DirectionalBlock.FACING)
                ? ownerState.getValue(DirectionalBlock.FACING)
                : Direction.UP;
        final Direction linkFacing = partnerState.hasProperty(DirectionalBlock.FACING)
                ? partnerState.getValue(DirectionalBlock.FACING)
                : Direction.UP;
        final boolean predicateResult = this.controller
                && this.family == M24Family.SWIVEL_BEARING
                && this.partnerPos != null
                && this.partnerSableId != null
                && owner != partner;
        Sable.LOGGER.info("SABLE_M24_SWIVEL phase={} owner={} partner={} ownerFacing={} linkFacing={} controllerOwner=bearing"
                        + " upstreamActivationPredicate=assembled_bearing_with_distinct_plate_sublevel enabled={} signal={} kineticSpeed=0.0 redstoneState={}"
                        + " predicateResult={} reason={}",
                phase, owner.getUniqueId(), partner.getUniqueId(), ownerFacing, linkFacing, this.enabled,
                this.lastSignal, this.level != null && this.level.hasNeighborSignal(this.worldPosition),
                predicateResult, predicateResult ? "paired_distinct_sable_bearing_owns_backend" : "invalid_bearing_link_pair");
    }

    private void traceSwivelLifecycle(final ServerSubLevel owner) {
        if (!SableDiagnosticFlags.TRACE_M24 || this.swivelCreatedGameTime == Long.MIN_VALUE || this.level == null) {
            return;
        }
        final long elapsed = this.level.getGameTime() - this.swivelCreatedGameTime;
        if (elapsed < 0L || elapsed > 100L) {
            return;
        }
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        final SubLevelPhysicsSystem physicsSystem = this.level instanceof final ServerLevel serverLevel
                ? SubLevelPhysicsSystem.get(serverLevel)
                : null;
        final RigidBodyHandle ownerHandle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(owner);
        final RigidBodyHandle partnerHandle = partner == null || physicsSystem == null
                ? null
                : physicsSystem.getPhysicsHandle(partner);
        final boolean bodyAExists = ownerHandle != null && ownerHandle.isValid()
                && physicsSystem.getPipeline().isBodyRegistered(owner);
        final boolean bodyBExists = partnerHandle != null && partnerHandle.isValid()
                && physicsSystem.getPipeline().isBodyRegistered(partner);
        final boolean jointExists = this.backendHandle != null && this.backendHandle.isValid();
        final boolean changed = bodyAExists != this.swivelLastBodyAExists
                || bodyBExists != this.swivelLastBodyBExists
                || jointExists != this.swivelLastJointExists;
        if (changed || elapsed == 0L || elapsed == 1L || elapsed == 2L || elapsed == 5L
                || elapsed == 20L || elapsed == 100L) {
            Sable.LOGGER.info("SABLE_M24_SWIVEL phase=POST_ACTIVE_LIFECYCLE tick={} logicalConstraintId={} ownerSable={} partnerSable={}"
                            + " bodyAHandle={} bodyBHandle={} rotaryJointHandle={} bodyAExists={} bodyBExists={}"
                            + " rapierBodyAExists={} rapierBodyBExists={} jointExists={} active={} enabled={} signal={}"
                            + " createCount={} removeCount={} recreateCount={} configurationUpdateCount={}",
                    elapsed, this.simulated$getLogicalConstraintId(), owner.getUniqueId(),
                    partner == null ? "unresolved" : partner.getUniqueId(), owner.getRuntimeId(),
                    partner == null ? "unavailable" : partner.getRuntimeId(),
                    jointExists ? "backend_wrapper_valid" : "unavailable", bodyAExists, bodyBExists,
                    bodyAExists, bodyBExists, jointExists, this.simulated$hasActiveConstraint(), this.enabled,
                    this.lastSignal, this.swivelCreateCount, this.swivelRemoveCount, this.swivelRecreateCount,
                    this.swivelConfigurationUpdateCount);
        }
        this.swivelLastBodyAExists = bodyAExists;
        this.swivelLastBodyBExists = bodyBExists;
        this.swivelLastJointExists = jointExists;
    }

    private void logConstraintFrame(final String phase, final ServerSubLevel owner, final ServerSubLevel partner,
                                    final Vector3d anchorA, final Vector3d anchorB,
                                    final Vec3 visibleA, final Vec3 visibleB,
                                    final Vector3d axisA, final Vector3d axisB,
                                    final double visibleEndpointDistance, final double rawEndpointDistance,
                                    final double localAnchorMagnitudeA, final double localAnchorMagnitudeB) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        Sable.LOGGER.info("SABLE_M24_CONSTRAINT_FRAME family={} phase={} bodyA={} bodyB={}"
                        + " bodyAPoseVisible={} bodyBPoseVisible={}"
                        + " endpointARaw={} endpointBRaw={}"
                        + " endpointAVisible={} endpointBVisible={}"
                        + " endpointABodyLocal={} endpointBBodyLocal={}"
                        + " axisAInput={} axisBInput={}"
                        + " configAnchorA={} configAnchorB={}"
                        + " configAxisA={} configAxisB={}"
                        + " configAnchorASpace=SABLE_RAW_PLOT configAnchorBSpace=SABLE_RAW_PLOT"
                        + " configAxisASpace=RIGID_BODY_LOCAL configAxisBSpace=RIGID_BODY_LOCAL"
                        + " visibleEndpointDistance={} rawEndpointDistance={}"
                        + " localAnchorMagnitudeA={} localAnchorMagnitudeB={}"
                        + " computedBackendAnchorError={}",
                this.family.id(), phase, owner.getUniqueId(), partner.getUniqueId(),
                owner.logicalPose().position(), partner.logicalPose().position(),
                anchorA, anchorB, visibleA, visibleB,
                anchorA.sub(owner.logicalPose().rotationPoint(), new Vector3d()),
                anchorB.sub(partner.logicalPose().rotationPoint(), new Vector3d()),
                axisA, axisB, anchorA, anchorB, axisA, axisB,
                visibleEndpointDistance, rawEndpointDistance, localAnchorMagnitudeA, localAnchorMagnitudeB,
                visibleEndpointDistance);
    }

    private void logSwivelDebug(final String phase, final ServerSubLevel owner, final ServerSubLevel partner,
                                final RigidBodyHandle ownerHandle, final RigidBodyHandle partnerHandle,
                                final Vector3d anchorA, final Vector3d anchorB,
                                final Vec3 visibleA, final Vec3 visibleB,
                                final Vector3d axisA, final Vector3d axisB) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final Vector3d axisAVisible = owner.logicalPose().orientation().transform(axisA, new Vector3d());
        final Vector3d axisBVisible = partner.logicalPose().orientation().transform(axisB, new Vector3d());
        final double linearError = visibleA.distanceTo(visibleB);
        final double angularError = 1.0D - Math.abs(axisAVisible.normalize(new Vector3d()).dot(axisBVisible.normalize(new Vector3d())));
        Sable.LOGGER.info("SABLE_M24_SWIVEL_DEBUG phase={} bodyAId={} bodyBId={}"
                        + " bodyAPose={} bodyBPose={}"
                        + " bodyALinearVelocity={} bodyBLinearVelocity={}"
                        + " bodyAAngularVelocity={} bodyBAngularVelocity={}"
                        + " anchorARaw={} anchorBRaw={} anchorABodyLocal={} anchorBBodyLocal={}"
                        + " anchorAVisibleReconstructed={} anchorBVisibleReconstructed={}"
                        + " axisABodyLocal={} axisBBodyLocal={} axisAVisible={} axisBVisible={}"
                        + " visibleAnchorDelta={} initialLinearConstraintError={} initialAngularConstraintError={}"
                        + " configurationEnabled={} configurationLimits=none configurationMotor={}",
                phase, owner.getUniqueId(), partner.getUniqueId(), owner.logicalPose(), partner.logicalPose(),
                ownerHandle.getLinearVelocity(new Vector3d()), partnerHandle.getLinearVelocity(new Vector3d()),
                ownerHandle.getAngularVelocity(new Vector3d()), partnerHandle.getAngularVelocity(new Vector3d()),
                anchorA, anchorB,
                anchorA.sub(owner.getMassTracker().getCenterOfMass(), new Vector3d()),
                anchorB.sub(partner.getMassTracker().getCenterOfMass(), new Vector3d()),
                visibleA, visibleB, axisA, axisB, axisAVisible, axisBVisible,
                visibleA.subtract(visibleB), linearError, angularError, this.enabled,
                this.targetValue == 0.0D ? "passive_friction_only" : "target_angle");
    }

    private void logRopeDebug(final String phase, final ServerSubLevel owner, final ServerSubLevel partner,
                              final RigidBodyHandle ownerHandle, final RigidBodyHandle partnerHandle,
                              final Vec3 visibleA, final Vec3 visibleB, final List<Vector3d> points) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final double currentLength = ropeLength(points);
        final double firstSegment = points.size() < 2 ? 0.0D : points.get(0).distance(points.get(1));
        final int fixedSegmentCount = Math.max(0, points.size() - 2);
        final double configuredTotalLength = firstSegment + fixedSegmentCount;
        final double targetFirstSegment = this.family == M24Family.ROPE_WINCH && this.targetValue > 0.0D
                ? this.targetValue
                : firstSegment;
        final double targetTotalLength = targetFirstSegment + fixedSegmentCount;
        Sable.LOGGER.info("SABLE_M24_ROPE_DEBUG phase={} family={} bodyA={} bodyB={}"
                        + " endpointAVisible={} endpointBVisible={} visibleDistance={}"
                        + " firstSegmentExtension={} fixedSegmentCount={}"
                        + " configuredLength={} restLength={} storedConfiguredLength={}"
                        + " maximumLength=runtime_config minimumLength=1.0 targetLength={}"
                        + " currentLength={} backendCurrentLength={} geometricVisibleDistance={}"
                        + " initialConstraintError={} slack={} tension=runtime_backend"
                        + " stiffness=runtime_backend damping=runtime_backend"
                        + " bodyAVelocity={} bodyBVelocity={} backendHandleValid={}"
                        + " backendLengthWriteCount={} lastBackendLengthWriteOwner={}",
                phase, this.family.id(), owner.getUniqueId(), partner.getUniqueId(), visibleA, visibleB,
                visibleA.distanceTo(visibleB), firstSegment, fixedSegmentCount,
                configuredTotalLength, configuredTotalLength, configuredTotalLength,
                targetTotalLength, currentLength, currentLength, visibleA.distanceTo(visibleB),
                Math.abs(configuredTotalLength - currentLength),
                Math.max(0.0D, currentLength - configuredTotalLength),
                ownerHandle.getLinearVelocity(new Vector3d()), partnerHandle.getLinearVelocity(new Vector3d()),
                this.ropeObject != null && this.ropeObject.isActive(),
                this.ropeBackendLengthWriteCount, this.ropeLastLengthWriteOwner);
    }

    private void logRopeBackendConstruct(final ServerSubLevel owner, final ServerSubLevel partner,
                                         final RigidBodyHandle ownerHandle, final RigidBodyHandle partnerHandle,
                                         final Vector3d anchorA, final Vector3d anchorB,
                                         final Vec3 visibleA, final Vec3 visibleB,
                                         final List<Vector3d> points) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final double constructorLength = points.size() < 2 ? Double.NaN : points.get(0).distance(points.get(1));
        final double solverAttachmentDistance = visibleA.distanceTo(new Vec3(points.get(0).x, points.get(0).y, points.get(0).z))
                + visibleB.distanceTo(new Vec3(points.get(points.size() - 1).x, points.get(points.size() - 1).y,
                        points.get(points.size() - 1).z));
        final Vector3d comA = owner.getMassTracker().getCenterOfMass() == null
                ? new Vector3d(Double.NaN, Double.NaN, Double.NaN)
                : new Vector3d(owner.getMassTracker().getCenterOfMass());
        final Vector3d comB = partner.getMassTracker().getCenterOfMass() == null
                ? new Vector3d(Double.NaN, Double.NaN, Double.NaN)
                : new Vector3d(partner.getMassTracker().getCenterOfMass());
        final Vector3d momentArmA = anchorA.sub(comA, new Vector3d());
        final Vector3d momentArmB = anchorB.sub(comB, new Vector3d());
        Sable.LOGGER.info("SABLE_ROPE_BACKEND_CONSTRUCT family={} logicalId={} bodyA={} bodyB={}"
                        + " rawAnchorA={} rawAnchorB={}"
                        + " localAnchorA={} localAnchorB={}"
                        + " rapierBodySpaceAttachmentA={} rapierBodySpaceAttachmentB={}"
                        + " publicConfiguredLength={} actualConstructorLengthArgument={} actualStoredBackendLength=rapier_native_logged"
                        + " solverAttachmentDistance={} initialSolverError={}"
                        + " segmentCount={} firstSegmentExtension={} anyInternalNodeCount={}"
                        + " bodyATranslation={} bodyBTranslation={} bodyARotation={} bodyBRotation={}"
                        + " bodyALinearVelocity={} bodyBLinearVelocity={}"
                        + " bodyAAngularVelocity={} bodyBAngularVelocity={}"
                        + " momentArmA={} momentArmB={}"
                        + " stiffness=rapier_native_joint_spring damping=rapier_native_joint_spring compliance=softness restLength={} maxLength=per_joint_limits minLength=0.0",
                this.family.id(), this.simulated$getLogicalConstraintId(), owner.getUniqueId(), partner.getUniqueId(),
                anchorA, anchorB, momentArmA, momentArmB, momentArmA, momentArmB,
                ropeLength(points), constructorLength, solverAttachmentDistance, solverAttachmentDistance,
                Math.max(0, points.size() - 1), constructorLength, Math.max(0, points.size() - 2),
                owner.logicalPose().position(), partner.logicalPose().position(),
                owner.logicalPose().orientation(), partner.logicalPose().orientation(),
                ownerHandle.getLinearVelocity(new Vector3d()), partnerHandle.getLinearVelocity(new Vector3d()),
                ownerHandle.getAngularVelocity(new Vector3d()), partnerHandle.getAngularVelocity(new Vector3d()),
                momentArmA, momentArmB, constructorLength);
    }

    private void logDockingDebug(final ServerSubLevel owner, final ServerSubLevel partner,
                                 final RigidBodyHandle ownerHandle, final RigidBodyHandle partnerHandle) {
        if (!SableDiagnosticFlags.TRACE_M24) {
            return;
        }
        final boolean ownerActive = this.ownsActiveBackend();
        final boolean partnerActive = this.partnerPos != null && this.level != null
                && this.level.getBlockEntity(this.partnerPos) instanceof final M24PhysicalBlockEntity component
                && component.simulated$hasActiveConstraint();
        Sable.LOGGER.info("SABLE_M24_DOCKING_DEBUG controllerSable={} partnerSable={}"
                        + " controllerEndpoint={} partnerEndpoint={}"
                        + " controllerLogicalConstraintPresent={} partnerLogicalConstraintPresent={}"
                        + " controllerActiveConstraintPresent={} partnerActiveConstraintPresent={}"
                        + " backendHandleValid={} controllerDisassemblyBlocked={} partnerDisassemblyBlocked={} connected={}",
                owner.getUniqueId(), partner.getUniqueId(), this.worldPosition, this.partnerPos,
                this.partnerPos != null, this.partnerPos != null, ownerActive, partnerActive,
                this.backendHandle != null && this.backendHandle.isValid(), ownerActive, partnerActive,
                this.partnerPos != null && ownerHandle.isValid() && partnerHandle.isValid());
    }

    private void logConstraintRejected(final ServerSubLevel owner, final ServerSubLevel partner, final String reason) {
        Sable.LOGGER.info("SABLE_M24_CONSTRAINT phase=CREATE_REJECTED family={} logicalId={} owner={} partner={} reason={}",
                this.family.id(), this.simulated$getLogicalConstraintId(), owner.getUniqueId(), partner.getUniqueId(), reason);
    }

    private void samplePostCreateConstraintState(final ServerSubLevel owner) {
        final ServerSubLevel partner = this.resolvePartnerSubLevel();
        final RigidBodyHandle ownerHandle = RigidBodyHandle.of(owner);
        final RigidBodyHandle partnerHandle = partner == null ? null : RigidBodyHandle.of(partner);
        if (ownerHandle == null || partner == null || partnerHandle == null) {
            this.postCreateInvalid = true;
            this.postCreateValidationPending = false;
            return;
        }
        this.postCreateValidationTicks++;
        final boolean shouldLog = SableDiagnosticFlags.TRACE_M24 && (this.postCreateValidationTicks == 1
                || this.postCreateValidationTicks == 2
                || this.postCreateValidationTicks == 5
                || this.postCreateValidationTicks == 20);
        final Vector3d beforeA = this.postCreateBodyAPositionBefore == null
                ? new Vector3d(owner.logicalPose().position())
                : this.postCreateBodyAPositionBefore;
        final Vector3d beforeB = this.postCreateBodyBPositionBefore == null
                ? new Vector3d()
                : this.postCreateBodyBPositionBefore;
        final Vector3d afterA = new Vector3d(owner.logicalPose().position());
        final Vector3d afterB = new Vector3d(partner.logicalPose().position());
        final Vector3d linearVelocityA = ownerHandle.getLinearVelocity(new Vector3d());
        final Vector3d angularVelocityA = ownerHandle.getAngularVelocity(new Vector3d());
        final Vector3d linearVelocityB = partnerHandle.getLinearVelocity(new Vector3d());
        final Vector3d angularVelocityB = partnerHandle.getAngularVelocity(new Vector3d());
        final boolean finite = finite(afterA) && finite(afterB) && finite(linearVelocityA)
                && finite(linearVelocityB) && finite(angularVelocityA) && finite(angularVelocityB);
        if (shouldLog) {
            Sable.LOGGER.info("SABLE_M24_CONSTRAINT_FRAME family={} phase=AFTER_PHYSICS_STEP_{} bodyA={} bodyB={}"
                            + " bodyAPositionBefore={} bodyBPositionBefore={}"
                            + " bodyAPositionAfter={} bodyBPositionAfter={}"
                            + " bodyALinearVelocity={} bodyBLinearVelocity={}"
                            + " bodyAAngularVelocity={} bodyBAngularVelocity={}"
                            + " finite={} translationDeltaA={} translationDeltaB={} active={}",
                    this.family.id(), this.postCreateValidationTicks, owner.getUniqueId(), partner.getUniqueId(),
                    beforeA, beforeB, afterA, afterB, linearVelocityA, linearVelocityB, angularVelocityA,
                    angularVelocityB, finite, afterA.distance(beforeA), afterB.distance(beforeB),
                    this.simulated$hasActiveConstraint());
            if (this.family == M24Family.SWIVEL_BEARING) {
                this.logSwivelDebug("POST_STEP_" + this.postCreateValidationTicks, owner, partner, ownerHandle,
                        partnerHandle, this.backendAnchorRaw(this.worldPosition, this.getBlockState()),
                        this.backendAnchorRaw(this.partnerPos, this.level.getBlockState(this.partnerPos)),
                        this.visibleCenter(owner, this.worldPosition), this.visibleCenter(partner, this.partnerPos),
                        normal(this.getBlockState()), normal(this.level.getBlockState(this.partnerPos)));
            } else if (this.family == M24Family.ROPE_CONNECTOR || this.family == M24Family.ROPE_WINCH) {
                if (this.ropeObject != null) {
                    this.ropeObject.updatePose();
                    this.logRopeDebug("PHYSICS_TICK_" + this.postCreateValidationTicks, owner, partner, ownerHandle,
                            partnerHandle, this.visibleCenter(owner, this.worldPosition),
                            this.visibleCenter(partner, this.partnerPos), this.ropeObject.getPoints());
                }
            }
        }
        this.postCreateInvalid = this.postCreateInvalid || !finite || !this.simulated$hasActiveConstraint();
        final int requiredTicks = this.family == M24Family.ROPE_CONNECTOR || this.family == M24Family.ROPE_WINCH ? 20 : 5;
        if (this.postCreateValidationTicks >= requiredTicks) {
            this.postCreateValidationPending = false;
            this.postCreateValidated = !this.postCreateInvalid && this.simulated$hasActiveConstraint();
            Sable.LOGGER.info("SABLE_M24_CONSTRAINT phase={} family={} logicalId={} owner={} partner={} active={}",
                    this.postCreateValidated ? "ACTIVE" : "FIRST_STEP_INVALID", this.family.id(),
                    this.simulated$getLogicalConstraintId(), owner.getUniqueId(), partner.getUniqueId(),
                    this.simulated$hasActiveConstraint());
        }
    }

    private static boolean finite(final Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double approach(final double current, final double target, final double step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static double ropeLength(final List<Vector3d> points) {
        double length = 0.0D;
        for (int i = 0; i < points.size() - 1; i++) {
            length += points.get(i).distance(points.get(i + 1));
        }
        return length;
    }

    private static @Nullable BlockPos localPos(final @Nullable ServerSubLevel subLevel, final @Nullable BlockPos raw) {
        return subLevel == null || raw == null ? null : raw.subtract(subLevel.getPlot().getCenterBlock());
    }

    private static String shortPos(final @Nullable BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }
}
