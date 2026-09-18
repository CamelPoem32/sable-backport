package dev.simulated_team.simulated.content.blocks.steering_wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.MechanicalBearingBlockEntityAccessor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SteeringWheelBlockEntity extends GeneratingKineticBlockEntity {

    public static final float RPM = 16.0F;
    private static final double MAX_CONTROL_DISTANCE_SQUARED = 64.0D;

    private ScrollValueBehaviour angleInput;
    private float angle;
    private float previousClientAngle;
    private float clientAngle;
    private float targetAngle;
    private float requestedAngle;
    private float generatedSpeed;
    private float logicalSpeed;
    private int inUse;
    private double sequencedAngleLimit;
    private boolean held;
    private @Nullable UUID controller;
    private @Nullable UUID controllerSableId;
    private @Nullable BlockPos controllerLocalPos;
    private @Nullable InteractionHand controllerHand;
    private long controllerSessionToken;
    private float lastDiagnosticGenerated = Float.NaN;
    private @Nullable Long lastDiagnosticNetwork;
    private @Nullable BlockPos diagnosticBearingPos;
    private int lastDiagnosticContraptionId = -1;
    private int contraptionCreateCount;
    private int contraptionRemoveCount;
    private String controlLifecycleState = "DISASSEMBLED";
    private float lastLoggedBearingAngle = Float.NaN;
    private float lastLoggedContraptionAngle = Float.NaN;
    private float previousTickBearingAngle = Float.NaN;
    private float previousTickContraptionAngle = Float.NaN;
    private int loggedRotationSamples;

    public SteeringWheelBlockEntity(final BlockPos pos, final BlockState state) {
        super(SimulatedBlockEntityTypes.STEERING_WHEEL.get(), pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.angleInput = new ScrollValueBehaviour(
                Component.translatable("simulated.steering_wheel.angle_limit"), this, new AngleLimitTransform())
                .between(1, 360)
                .withFormatter(value -> Math.abs(value) + " degrees");
        this.angleInput.value = 180;
        behaviours.add(this.angleInput);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide) {
            this.previousClientAngle = this.clientAngle;
            this.clientAngle += (this.requestedAngle - this.clientAngle) * 0.35F;
            return;
        }

        this.validateController();
        if (this.getGeneratedSpeed() != 0.0F) {
            this.integrateAngle();
        }
        final float previousDesiredRpm = this.getGeneratedSpeed();
        final float previousActualSpeed = this.getSpeed();
        final Long previousNetwork = this.network;
        final float requested = Mth.clamp(this.requestedAngle, -this.getAngleLimit(), this.getAngleLimit());
        final SteeringWheelDriveDecision.Result decision = SteeringWheelDriveDecision.evaluate(
                requested, this.targetAngle, this.angle, this.inUse, this.getGeneratedSpeed());
        switch (decision.action()) {
            case RETARGET, RESUME_AFTER_ERROR -> this.beginMove(requested, decision.signedError());
            case MOVING_TO_TARGET -> this.inUse--;
            case WITHIN_STOP_TOLERANCE -> {
                this.sequenceContext = null;
                this.stopMove();
            }
            case NO_CONTROL_INPUT -> this.targetAngle = requested;
        }
        this.logStateChanges(decision, previousDesiredRpm, previousActualSpeed, previousNetwork);
        this.trackControlContraption();
    }

    public void acceptControl(final ServerPlayer player, final @Nullable UUID sableId, final BlockPos localPos,
                              final InteractionHand hand, final long sessionToken, final float requested,
                              final boolean stop) {
        if (stop) {
            if (this.controller != null && this.controller.equals(player.getUUID())
                    && this.controllerSessionToken == sessionToken) {
                this.clearController();
                this.notifyUpdate();
            }
            return;
        }
        if (this.controller != null && (!this.controller.equals(player.getUUID())
                || this.controllerSessionToken != sessionToken)) {
            return;
        }
        this.controller = player.getUUID();
        this.controllerSableId = sableId;
        this.controllerLocalPos = localPos.immutable();
        this.controllerHand = hand;
        this.controllerSessionToken = sessionToken;
        this.held = true;
        this.requestedAngle = Mth.clamp(requested, -this.getAngleLimit(), this.getAngleLimit());
        this.notifyUpdate();
    }

    public void simulated$setClientTarget(final float requested, final boolean held) {
        this.requestedAngle = Mth.clamp(requested, -this.getAngleLimit(), this.getAngleLimit());
        this.held = held;
    }

    public float directionConvert(final float value) {
        return -KineticBlockEntity.convertToDirection(value,
                this.getBlockState().getValue(SteeringWheelBlock.FACING));
    }

    public float getAngle() {
        return this.angle;
    }

    public float getTargetAngle() {
        return this.requestedAngle;
    }

    public float getAngleLimit() {
        return this.angleInput == null ? 180.0F : this.angleInput.getValue();
    }

    public float getRenderAngle(final float partialTick) {
        float renderAngle = Mth.lerp(partialTick, this.previousClientAngle, this.clientAngle);
        final Direction facing = this.getBlockState().getValue(SteeringWheelBlock.FACING);
        if (facing == Direction.NORTH || facing == Direction.WEST) {
            renderAngle = -renderAngle;
        }
        return (float) Math.toRadians(renderAngle);
    }

    public boolean isHeld() {
        return this.held;
    }

    public @Nullable UUID getController() {
        return this.controller;
    }

    public @Nullable UUID getControllerSableId() {
        return this.controllerSableId;
    }

    public @Nullable BlockPos getControllerLocalPos() {
        return this.controllerLocalPos;
    }

    public @Nullable InteractionHand getControllerHand() {
        return this.controllerHand;
    }

    public long getControllerSessionToken() {
        return this.controllerSessionToken;
    }

    public ControlLifecycleSnapshot getControlLifecycleSnapshot() {
        final MechanicalBearingBlockEntity bearing = this.resolveDiagnosticBearing();
        final ControlledContraptionEntity contraption = bearing == null ? null : bearing.getMovedContraption();
        final int capturedBlocks = contraption == null || contraption.getContraption() == null
                ? 0 : contraption.getContraption().getBlocks().size();
        final boolean controlSailPresent = contraption != null && contraption.getContraption() != null
                && contraption.getContraption().getBlocks().values().stream()
                .map(StructureTemplate.StructureBlockInfo::state)
                .anyMatch(state -> state.is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get()));
        return new ControlLifecycleSnapshot(
                this.diagnosticBearingPos,
                bearing == null ? null : bearing.getSpeed(),
                bearing == null ? null : bearing.getInterpolatedAngle(1.0F),
                bearing == null ? null : movementMode(bearing),
                contraption == null ? null : contraption.getId(),
                contraption != null,
                bearing != null && bearing.isRunning(),
                capturedBlocks,
                controlSailPresent,
                this.contraptionCreateCount,
                this.contraptionRemoveCount,
                this.controlLifecycleState);
    }

    @Override
    public float getGeneratedSpeed() {
        return this.inUse == 0 ? 0.0F : this.generatedSpeed;
    }

    @Override
    protected void copySequenceContextFrom(final KineticBlockEntity source) {
    }

    @Override
    protected Block getStressConfigKey() {
        return this.getBlockState().getBlock();
    }

    public double getRegisteredStressCapacity() {
        return BlockStressValues.getCapacity(this.getStressConfigKey());
    }

    public double getRegisteredStressImpact() {
        return BlockStressValues.getImpact(this.getStressConfigKey());
    }

    public float getNetworkCapacitySnapshot() {
        return this.capacity;
    }

    public float getNetworkStressSnapshot() {
        return this.stress;
    }

    @Override
    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putFloat("Angle", this.angle);
        tag.putFloat("TargetAngle", this.targetAngle);
        tag.putFloat("RequestedAngle", this.requestedAngle);
        tag.putFloat("GeneratedSpeed", this.generatedSpeed);
        tag.putFloat("LogicalSpeed", this.logicalSpeed);
        tag.putInt("InUse", this.inUse);
        tag.putDouble("SequencedAngleLimit", this.sequencedAngleLimit);
        tag.putBoolean("Held", this.held);
    }

    @Override
    protected void read(final CompoundTag tag, final boolean clientPacket) {
        super.read(tag, clientPacket);
        this.angle = tag.getFloat("Angle");
        this.targetAngle = tag.getFloat("TargetAngle");
        this.requestedAngle = tag.getFloat("RequestedAngle");
        this.generatedSpeed = tag.getFloat("GeneratedSpeed");
        this.logicalSpeed = tag.getFloat("LogicalSpeed");
        this.inUse = tag.getInt("InUse");
        this.sequencedAngleLimit = tag.getDouble("SequencedAngleLimit");
        this.held = clientPacket && tag.getBoolean("Held");
        if (clientPacket) {
            this.previousClientAngle = this.clientAngle;
            this.clientAngle = this.angle;
        } else {
            this.clearController();
        }
    }

    private void beginMove(final float requested, final float difference) {
        final float previousTarget = this.targetAngle;
        final float previousLogicalSpeed = this.logicalSpeed;
        final float previousActualSpeed = this.getSpeed();
        final Long previousNetwork = this.network;
        final MechanicalBearingBlockEntity bearing = this.resolveDiagnosticBearing();
        final int previousContraptionId = bearing == null || bearing.getMovedContraption() == null
                ? -1 : bearing.getMovedContraption().getId();
        final double previousBearingTravel = bearing == null ? -1.0D
                : ((MechanicalBearingBlockEntityAccessor) bearing).sable$getSequencedAngleLimit();
        this.targetAngle = requested;
        final float rotationSpeed = Math.copySign(RPM, difference);
        final double degreesPerTick = KineticBlockEntity.convertToAngular(rotationSpeed);
        this.inUse = (int) Math.ceil(difference / degreesPerTick) + 2;
        this.sequenceContext = new SequencedGearshiftBlockEntity.SequenceContext(
                SequencerInstructions.TURN_ANGLE, difference / rotationSpeed);
        this.sequencedAngleLimit = Math.abs(difference);
        this.logicalSpeed = rotationSpeed;
        final Direction facing = this.getBlockState().getValue(SteeringWheelBlock.FACING);
        final boolean floor = this.getBlockState().getValue(SteeringWheelBlock.ON_FLOOR);
        this.generatedSpeed = (facing == Direction.NORTH || facing == Direction.WEST) == floor
                ? -this.logicalSpeed : this.logicalSpeed;
        this.updateGeneratedRotation();
        if (bearing != null && previousBearingTravel >= 0.0D
                && previousLogicalSpeed * this.logicalSpeed > 0.0F
                && previousActualSpeed == this.getSpeed()
                && java.util.Objects.equals(previousNetwork, this.network)
                && (bearing.getMovedContraption() == null ? -1 : bearing.getMovedContraption().getId())
                == previousContraptionId
                && this.isDownstreamBearing(bearing)) {
            final double additionalTravel = SteeringWheelDriveDecision.additionalBearingTravel(
                    requested, previousTarget, this.logicalSpeed, bearing.getSpeed());
            final MechanicalBearingBlockEntityAccessor access = (MechanicalBearingBlockEntityAccessor) bearing;
            access.sable$setSequencedAngleLimit(Math.max(0.0D, previousBearingTravel + additionalTravel));
            bearing.sequenceContext = this.sequenceContext;
            bearing.setChanged();
        }
        this.setChanged();
        this.sendData();
    }

    private boolean isDownstreamBearing(final MechanicalBearingBlockEntity bearing) {
        if (this.level == null || this.network == null || !this.network.equals(bearing.network)) {
            return false;
        }
        KineticBlockEntity member = bearing;
        for (int depth = 0; depth < 32 && member.hasSource(); depth++) {
            final BlockEntity source = this.level.getBlockEntity(member.source);
            if (source == this) {
                return true;
            }
            if (!(source instanceof final KineticBlockEntity kinetic)) {
                return false;
            }
            member = kinetic;
        }
        return false;
    }

    private void integrateAngle() {
        float angularSpeed = KineticBlockEntity.convertToAngular(this.logicalSpeed);
        if (this.getSpeed() == 0.0F || this.logicalSpeed == 0.0F) {
            angularSpeed = 0.0F;
        }
        angularSpeed = (float) Mth.clamp(angularSpeed, -this.sequencedAngleLimit, this.sequencedAngleLimit);
        this.sequencedAngleLimit = Math.max(0.0D, this.sequencedAngleLimit - Math.abs(angularSpeed));
        this.angle += angularSpeed;
        this.setChanged();
        this.sendData();
    }

    private void stopMove() {
        if (this.generatedSpeed == 0.0F && this.logicalSpeed == 0.0F) {
            return;
        }
        this.generatedSpeed = 0.0F;
        this.logicalSpeed = 0.0F;
        this.inUse = 0;
        this.sequencedAngleLimit = 0.0D;
        this.updateGeneratedRotation();
        this.setChanged();
        this.sendData();
    }

    private void validateController() {
        if (this.controller == null || !(this.level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.controller);
        final SubLevel owner = Sable.HELPER.getContaining(this);
        final Vec3 visibleCenter = owner == null ? Vec3.atCenterOf(this.worldPosition)
                : owner.logicalPose().transformPosition(Vec3.atCenterOf(this.worldPosition));
        final boolean valid = player != null && player.isAlive()
                && player.level() == this.level
                && (owner == null || Sable.HELPER.getTrackingSubLevel(player) == owner)
                && java.util.Objects.equals(this.controllerSableId, owner == null ? null : owner.getUniqueId())
                && this.controllerLocalPos != null
                && this.controllerLocalPos.equals(owner == null ? this.worldPosition
                : this.worldPosition.subtract(owner.getPlot().getCenterBlock()))
                && player.getEyePosition().distanceToSqr(visibleCenter) <= MAX_CONTROL_DISTANCE_SQUARED;
        if (!valid) {
            this.clearController();
            this.notifyUpdate();
        }
    }

    private void clearController() {
        this.controller = null;
        this.controllerSableId = null;
        this.controllerLocalPos = null;
        this.controllerHand = null;
        this.controllerSessionToken = 0L;
        this.held = false;
    }

    private void logStateChanges(final SteeringWheelDriveDecision.Result decision,
                                 final float previousDesiredRpm, final float previousActualSpeed,
                                 final @Nullable Long previousNetwork) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        final float currentGenerated = this.getGeneratedSpeed();
        final boolean controlChanged = Float.compare(this.lastDiagnosticGenerated, currentGenerated) != 0;
        final boolean networkChanged = !java.util.Objects.equals(this.lastDiagnosticNetwork, this.network);
        if (!controlChanged && !networkChanged) {
            return;
        }
        final SubLevel owner = Sable.HELPER.getContaining(this);
        final BlockPos localPos = owner == null ? this.worldPosition
                : this.worldPosition.subtract(owner.getPlot().getCenterBlock());
        final MechanicalBearingBlockEntity bearing = this.resolveDiagnosticBearing();
        final boolean sourceUpdateRequested = decision.action() == SteeringWheelDriveDecision.Action.RETARGET
                || decision.action() == SteeringWheelDriveDecision.Action.RESUME_AFTER_ERROR
                || decision.action() == SteeringWheelDriveDecision.Action.WITHIN_STOP_TOLERANCE;
        final String reason = previousDesiredRpm * currentGenerated < 0.0F
                ? "DIRECTION_REVERSAL" : decision.action().name();
        Sable.LOGGER.info("SABLE_M28_STEERING_WHEEL phase={} sableId={} localPos={} targetAngle={} activeTargetAngle={} currentAngle={} signedError={} absoluteError={} stopTolerance={} decisionReason={} previousDesiredRpm={} generatedRpm={} previousActualSpeed={} actualSpeed={} previousNetworkId={} networkId={} sourceUpdateRequested={} bearingSpeed={} contraptionEntityId={} controlLifecycleState={} outputAxis=Y",
                controlChanged ? "CONTROL_UPDATE" : "NETWORK_ATTACHMENT",
                owner == null ? "static_world" : owner.getUniqueId(), localPos, this.requestedAngle,
                this.targetAngle, this.angle, decision.signedError(), Math.abs(decision.signedError()),
                SteeringWheelDriveDecision.STOP_TOLERANCE, reason, previousDesiredRpm, currentGenerated,
                previousActualSpeed, this.getSpeed(), previousNetwork == null ? "none" : previousNetwork,
                this.network == null ? "none" : this.network, sourceUpdateRequested,
                bearing == null ? "unavailable" : bearing.getSpeed(),
                bearing == null || bearing.getMovedContraption() == null
                ? "unavailable" : bearing.getMovedContraption().getId(), this.controlLifecycleState);
        this.lastDiagnosticGenerated = currentGenerated;
        this.lastDiagnosticNetwork = this.network;
    }

    private void trackControlContraption() {
        final MechanicalBearingBlockEntity bearing = this.resolveDiagnosticBearing();
        if (bearing == null) {
            return;
        }
        this.diagnosticBearingPos = bearing.getBlockPos().immutable();
        final ControlledContraptionEntity contraption = bearing.getMovedContraption();
        this.traceCreateRotation(bearing, contraption);
        final int entityId = contraption == null ? -1 : contraption.getId();
        if (entityId != this.lastDiagnosticContraptionId) {
            if (this.lastDiagnosticContraptionId != -1) {
                this.contraptionRemoveCount++;
            }
            if (entityId != -1) {
                this.contraptionCreateCount++;
            }
        }
        final RotationMode mode = movementMode(bearing);
        final String lifecycle = contraption == null ? "DISASSEMBLED"
                : bearing.getSpeed() != 0.0F ? "MOVING"
                : Math.abs(this.requestedAngle - this.angle) > SteeringWheelDriveDecision.STOP_TOLERANCE
                ? "AWAITING_KINETIC_SOURCE"
                : mode == RotationMode.ROTATE_NEVER_PLACE ? "HOLDING_TARGET" : "ASSEMBLED";
        if (entityId == this.lastDiagnosticContraptionId && lifecycle.equals(this.controlLifecycleState)) {
            return;
        }
        final int capturedBlocks = contraption == null || contraption.getContraption() == null
                ? 0 : contraption.getContraption().getBlocks().size();
        final boolean controlSailPresent = contraption != null && contraption.getContraption() != null
                && contraption.getContraption().getBlocks().values().stream()
                .map(StructureTemplate.StructureBlockInfo::state)
                .anyMatch(state -> state.is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get()));
        final SubLevel owner = Sable.HELPER.getContaining(this);
        final BlockPos wheelLocal = owner == null ? this.worldPosition
                : this.worldPosition.subtract(owner.getPlot().getCenterBlock());
        final BlockPos bearingLocal = owner == null ? bearing.getBlockPos()
                : bearing.getBlockPos().subtract(owner.getPlot().getCenterBlock());
        Sable.LOGGER.info("SABLE_M28_CONTROL_CONTRAPTION wheelLocalPos={} bearingLocalPos={} targetAngle={}"
                        + " currentAngle={} generatedRpm={} bearingSpeed={} movementMode={} contraptionEntityId={}"
                        + " contraptionPresent={} contraptionAssembled={} capturedBlockCount={}"
                        + " controlSailPresent={} lifecycleEvent={} contraptionCreateCount={} contraptionRemoveCount={}",
                wheelLocal, bearingLocal, this.requestedAngle, this.angle, this.getGeneratedSpeed(),
                bearing.getSpeed(), mode, entityId == -1 ? "unavailable" : entityId,
                contraption != null, bearing.isRunning(), capturedBlocks, controlSailPresent, lifecycle,
                this.contraptionCreateCount, this.contraptionRemoveCount);
        this.lastDiagnosticContraptionId = entityId;
        this.controlLifecycleState = lifecycle;
    }

    private void traceCreateRotation(final MechanicalBearingBlockEntity bearing,
                                     final @Nullable ControlledContraptionEntity contraption) {
        if (contraption == null || this.loggedRotationSamples >= 24) {
            return;
        }
        final float bearingAngle = bearing.getInterpolatedAngle(0.0F);
        final float contraptionAngle = contraption.getAngle(1.0F);
        final float bearingPreviousTick = this.previousTickBearingAngle;
        final float contraptionPreviousTick = this.previousTickContraptionAngle;
        this.previousTickBearingAngle = bearingAngle;
        this.previousTickContraptionAngle = contraptionAngle;
        if (Float.isFinite(this.lastLoggedBearingAngle) && Float.isFinite(this.lastLoggedContraptionAngle)
                && Math.abs(bearingAngle - this.lastLoggedBearingAngle) < 1.0F
                && Math.abs(contraptionAngle - this.lastLoggedContraptionAngle) < 1.0F) {
            return;
        }
        final SubLevel owner = Sable.HELPER.getContaining(this);
        final List<String> capturedSourceStates = contraption.getContraption() == null
                || contraption.getContraption().anchor == null || this.level == null
                ? List.of()
                : contraption.getContraption().getBlocks().values().stream()
                        .map(info -> {
                            final BlockPos sourcePos = info.pos().offset(contraption.getContraption().anchor);
                            return "local=" + info.pos() + ",source=" + sourcePos + ",captured=" + info.state()
                                    + ",live=" + this.level.getBlockState(sourcePos);
                        })
                        .toList();
        Sable.LOGGER.info("SABLE_M28_CREATE_ROTATION side=server sableId={} wheelPos={} bearingPos={} "
                        + "bearingAxis={} bearingSpeed={} bearingRunning={} movementMode={} "
                        + "bearingAnglePreviousTick={} bearingAngleCurrent={} bearingDeltaThisTick={} "
                        + "contraptionEntityId={} contraptionAlive={} capturedBlocks={} "
                        + "contraptionAnglePreviousTick={} contraptionAngleCurrent={} contraptionDeltaThisTick={} "
                        + "rotationState={} rawAnchor={} visibleAnchor={} capturedSourceStates={}",
                owner == null ? "none" : owner.getUniqueId(), this.worldPosition, bearing.getBlockPos(),
                contraption.getRotationAxis(), bearing.getSpeed(), bearing.isRunning(), movementMode(bearing),
                bearingPreviousTick, bearingAngle, bearingAngle - bearingPreviousTick,
                contraption.getId(), !contraption.isRemoved(),
                contraption.getContraption() == null ? 0 : contraption.getContraption().getBlocks().size(),
                contraptionPreviousTick, contraptionAngle,
                contraptionAngle - contraptionPreviousTick, contraption.getRotationState(),
                contraption.getAnchorVec(), owner == null ? "unavailable"
                        : owner.logicalPose().transformPosition(contraption.getAnchorVec()),
                capturedSourceStates);
        this.lastLoggedBearingAngle = bearingAngle;
        this.lastLoggedContraptionAngle = contraptionAngle;
        this.loggedRotationSamples++;
    }

    private @Nullable MechanicalBearingBlockEntity resolveDiagnosticBearing() {
        if (this.level == null) {
            return null;
        }
        if (this.diagnosticBearingPos != null
                && this.level.getBlockEntity(this.diagnosticBearingPos)
                instanceof final MechanicalBearingBlockEntity bearing) {
            return bearing;
        }
        if (this.network == null || !this.hasNetwork()) {
            return null;
        }
        return this.getOrCreateNetwork().members.keySet().stream()
                .filter(member -> member instanceof MechanicalBearingBlockEntity)
                .map(member -> (MechanicalBearingBlockEntity) member)
                .sorted(java.util.Comparator.comparingLong(member -> member.getBlockPos().asLong()))
                .findFirst()
                .orElse(null);
    }

    private static RotationMode movementMode(final MechanicalBearingBlockEntity bearing) {
        final var behaviour = ((MechanicalBearingBlockEntityAccessor) bearing).sable$getMovementMode();
        return behaviour == null ? RotationMode.ROTATE_PLACE : behaviour.get();
    }

    public record ControlLifecycleSnapshot(@Nullable BlockPos bearingRawPos, @Nullable Float bearingSpeed,
                                           @Nullable Float bearingAngle, @Nullable RotationMode movementMode,
                                           @Nullable Integer contraptionEntityId, boolean contraptionPresent,
                                           boolean contraptionAssembled, int capturedBlockCount,
                                           boolean controlSailPresent, int contraptionCreateCount,
                                           int contraptionRemoveCount, String lifecycleState) {
    }

    private static final class AngleLimitTransform extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(final LevelAccessor level, final BlockPos pos, final BlockState state) {
            return new Vec3(0.5D, state.getValue(SteeringWheelBlock.ON_FLOOR) ? 0.85D : 0.15D, 0.5D);
        }

        @Override
        public void rotate(final LevelAccessor level, final BlockPos pos, final BlockState state,
                           final PoseStack poseStack) {
        }
    }
}
