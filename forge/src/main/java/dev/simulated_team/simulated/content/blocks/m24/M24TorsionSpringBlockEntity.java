package dev.simulated_team.simulated.content.blocks.m24;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Frozen-Simulated two-port Torsion Spring adapted to Create 6.0.8. */
public final class M24TorsionSpringBlockEntity extends M24PhysicalBlockEntity implements ExtraKinetics {
    private static final String NBT_ANGLE_LIMIT = "TorsionAngleLimit";
    private static final String NBT_OUTPUT = "TorsionSpringOutput";

    private final Output springOutput;
    private double angleLimit = 90.0D;
    private long diagnosticTick;
    private String lastDiagnosticState = "";

    public M24TorsionSpringBlockEntity(final BlockPos pos, final BlockState state) {
        super(M24Family.TORSION_SPRING, pos, state);
        this.springOutput = new Output(this.getType(), new ExtraBlockPos(pos), state, this);
    }

    @Override
    public void tick() {
        super.tick();
        this.springOutput.tick();
        this.logTick();
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        // The ordinary Create BE ticker owns this family, matching frozen upstream.
    }

    @Override
    public void onSpeedChanged(final float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        this.springOutput.updateParentSpeed(previousSpeed, this.getSpeed());
    }

    @Override
    public double simulated$getTorsionAngle() {
        return this.springOutput.angle;
    }

    public double simulated$getTorsionTargetAngle() {
        return this.springOutput.targetAngle;
    }

    public double simulated$getTorsionOutputPortAngle() {
        return this.springOutput.angle;
    }

    public float simulated$getInterpolatedTorsionAngle(final float partialTick) {
        return (float) Mth.lerp(partialTick, this.springOutput.oldAngle, this.springOutput.angle);
    }

    public float simulated$getTorsionOutputSpeed() {
        return this.springOutput.getSpeed();
    }

    @Override
    public double simulated$getTorsionAngleLimit() {
        return this.angleLimit;
    }

    @Override
    public void simulated$setTorsionAngleLimit(final double degrees) {
        this.angleLimit = Mth.clamp(degrees, 1.0D, 360.0D);
        this.setChanged();
        this.sendData();
    }

    @Override
    public int getRedstoneSignal() {
        return (int) Math.round(Mth.clamp(Math.abs(this.springOutput.angle / this.angleLimit), 0.0D, 1.0D) * 15.0D);
    }

    @Override
    public void onNeighborSignalChanged() {
        if (this.level != null) {
            final boolean powered = this.level.hasNeighborSignal(this.worldPosition);
            if (this.getBlockState().hasProperty(M24TorsionSpringBlock.POWERED)
                    && this.getBlockState().getValue(M24TorsionSpringBlock.POWERED) != powered) {
                this.level.setBlock(this.worldPosition,
                        this.getBlockState().setValue(M24TorsionSpringBlock.POWERED, powered), 2);
            }
            this.setChanged();
            this.sendData();
        }
    }

    @Override
    public String inspect() {
        return "family=torsion_spring"
                + " runtimeState=" + (this.level == null ? "NOT_LOADED" : "ACTIVE")
                + " upstreamMode=CREATE_EXTRA_KINETICS_TWO_PORT"
                + " expectedBodies=1"
                + " inputSpeed=" + this.getSpeed()
                + " outputSpeed=" + this.springOutput.getSpeed()
                + " angleLimit=" + this.angleLimit
                + " targetAngle=" + this.springOutput.targetAngle
                + " currentAngle=" + this.springOutput.angle
                + " outputPortAngle=" + this.springOutput.angle
                + " redstonePowered=" + this.isPowered()
                + " returningToZero=" + this.springOutput.returningToZero
                + " inputNetworkId=" + Objects.toString(this.network, "none")
                + " outputNetworkId=" + Objects.toString(this.springOutput.network, "none")
                + " outputPortPresent=true"
                + " outputPortKineticAttached=" + this.springOutput.hasNetwork()
                + " disposition=CREATE_KINETIC_ANGLE_LIMIT_AND_RETURN";
    }

    @Override
    public KineticBlockEntity getExtraKinetics() {
        return this.springOutput;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return NBT_OUTPUT;
    }

    @Override
    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putDouble(NBT_ANGLE_LIMIT, this.angleLimit);
    }

    @Override
    protected void read(final CompoundTag tag, final boolean clientPacket) {
        super.read(tag, clientPacket);
        this.angleLimit = tag.contains(NBT_ANGLE_LIMIT) ? tag.getDouble(NBT_ANGLE_LIMIT) : 90.0D;
    }

    private boolean isPowered() {
        return this.getBlockState().hasProperty(M24TorsionSpringBlock.POWERED)
                && this.getBlockState().getValue(M24TorsionSpringBlock.POWERED);
    }

    private void logTick() {
        if (!SableDiagnosticFlags.TRACE_M24 || this.level == null || this.level.isClientSide) {
            return;
        }
        final long tick = this.diagnosticTick++;
        final String state = this.getSpeed() + ":" + this.springOutput.getSpeed() + ":"
                + this.springOutput.targetAngle + ":" + this.springOutput.angle + ":" + this.isPowered()
                + ":" + this.springOutput.hasNetwork();
        if (tick != 0L && tick != 1L && tick != 2L && tick != 5L && tick != 20L
                && state.equals(this.lastDiagnosticState)) {
            return;
        }
        this.lastDiagnosticState = state;
        final ServerSubLevel owner = this.resolveOwner();
        Sable.LOGGER.info("SABLE_M24_TORSION_TICK tickerInvoked=true sableId={} localPos={} inputSpeed={} outputSpeed={} angleLimit={} targetAngle={} currentAngle={} outputPortAngle={} redstonePowered={} returningToZero={} inputNetworkId={} outputNetworkId={} outputPortPresent=true outputPortKineticAttached={} skipReason=none",
                owner == null ? "static_world" : owner.getUniqueId(),
                owner == null ? this.worldPosition : this.worldPosition.subtract(owner.getPlot().getCenterBlock()),
                this.getSpeed(), this.springOutput.getSpeed(), this.angleLimit, this.springOutput.targetAngle,
                this.springOutput.angle, this.springOutput.angle, this.isPowered(), this.springOutput.returningToZero,
                Objects.toString(this.network, "none"), Objects.toString(this.springOutput.network, "none"),
                this.springOutput.hasNetwork());
    }

    private ServerSubLevel resolveOwner() {
        if (!(this.level != null && SubLevelContainer.getContainer(this.level) instanceof final ServerSubLevelContainer container)) {
            return null;
        }
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (SimAssemblyHelper.collectBlocks((net.minecraft.server.level.ServerLevel) this.level, subLevel)
                    .contains(this.worldPosition)) {
                return subLevel;
            }
        }
        return null;
    }

    public static class Output extends GeneratingKineticBlockEntity implements ExtraKinetics.ExtraKineticsBlockEntity {
        public static final IRotate CONFIG = new IRotate() {
            @Override
            public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                            final Direction face) {
                return face == state.getValue(M24TorsionSpringBlock.FACING);
            }

            @Override
            public Direction.Axis getRotationAxis(final BlockState state) {
                return state.getValue(M24TorsionSpringBlock.FACING).getAxis();
            }
        };

        private final M24TorsionSpringBlockEntity parent;
        private double oldAngle;
        private double angle;
        private double targetAngle;
        private float lastSpringSpeed;
        private float generatedSpeed;
        private float queuedSpeed;
        private boolean turning;
        private boolean returningToZero;
        private int customValidationCountdown;

        private Output(final BlockEntityType<?> type, final ExtraBlockPos pos, final BlockState state,
                       final M24TorsionSpringBlockEntity parent) {
            super(type, pos, state);
            this.parent = parent;
        }

        @Override
        public void initialize() {
            super.initialize();
            this.reActivateSource = true;
            this.updateSpeed = true;
        }

        @Override
        public void tick() {
            ((KineticBlockEntityExtension) this).simulated$setValidationCountdown(Integer.MAX_VALUE);
            if (this.customValidationCountdown-- <= 0) {
                this.customValidationCountdown = 100;
                this.validateExtraSource();
            }
            this.generatedSpeed = this.queuedSpeed;
            super.tick();
            this.oldAngle = this.angle;
            final boolean powered = this.parent.isPowered();
            final float parentSpeed = this.parent.getSpeed();

            if (this.turning) {
                final double step = Math.abs(KineticBlockEntity.convertToAngular(this.getSpeed()));
                final double remaining = this.targetAngle - this.angle;
                if (step <= 1.0E-7D || Math.abs(remaining) <= step) {
                    this.angle = this.targetAngle;
                    this.stopTurning();
                } else {
                    this.angle += Math.copySign(step, remaining);
                }
                if (parentSpeed == 0.0F && (this.targetAngle != 0.0D || powered)) {
                    this.stopTurning();
                } else if (parentSpeed != 0.0F
                        && this.targetAngle != this.parent.angleLimit * Math.signum(parentSpeed)) {
                    this.stopTurning();
                }
            } else if (parentSpeed == 0.0F && !powered && this.angle != 0.0D) {
                this.beginTurnTo(0.0D, true);
            } else if (parentSpeed != 0.0F) {
                this.beginTurnTo(this.parent.angleLimit * Math.signum(parentSpeed), false);
            }
            if (this.level != null && this.angle != this.oldAngle) {
                this.level.updateNeighborsAt(this.getBlockPos(), this.parent.getBlockState().getBlock());
                this.parent.setChanged();
                this.parent.sendData();
            }
        }

        private void updateParentSpeed(final float previousSpeed, final float currentSpeed) {
            if (currentSpeed != 0.0F) {
                this.lastSpringSpeed = currentSpeed;
            } else if (previousSpeed != 0.0F) {
                this.lastSpringSpeed = previousSpeed;
            }
        }

        private void beginTurnTo(final double target, final boolean returning) {
            final double delta = target - this.angle;
            if (Math.abs(delta) <= 1.0E-7D) {
                return;
            }
            float speed = Math.abs(this.lastSpringSpeed);
            if (speed == 0.0F) {
                speed = 16.0F;
            }
            this.targetAngle = target;
            this.queuedSpeed = (float) Math.copySign(speed, delta);
            this.generatedSpeed = this.queuedSpeed;
            this.turning = true;
            this.returningToZero = returning;
            this.reActivateSource = true;
            this.updateSpeed = true;
        }

        private void stopTurning() {
            this.queuedSpeed = 0.0F;
            this.generatedSpeed = 0.0F;
            this.turning = false;
            this.returningToZero = false;
            this.reActivateSource = true;
            this.updateSpeed = true;
        }

        private void validateExtraSource() {
            if (!this.hasSource()) {
                return;
            }
            if (!this.hasNetwork()) {
                this.removeSource();
                return;
            }
            if (!this.level.isLoaded(this.source)) {
                return;
            }
            BlockEntity sourceEntity = this.level.getBlockEntity(this.source);
            if (sourceEntity instanceof final ExtraKinetics extra
                    && ((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {
                sourceEntity = extra.getExtraKinetics();
            }
            if (!(sourceEntity instanceof final KineticBlockEntity kinetic) || kinetic.getTheoreticalSpeed() == 0.0F) {
                this.removeSource();
                this.detachKinetics();
            }
        }

        @Override
        public float getGeneratedSpeed() {
            return this.generatedSpeed;
        }

        @Override
        public float calculateStressApplied() {
            return 0.0F;
        }

        @Override
        protected void write(final CompoundTag tag, final boolean clientPacket) {
            super.write(tag, clientPacket);
            tag.putDouble("OldAngle", this.oldAngle);
            tag.putDouble("Angle", this.angle);
            tag.putDouble("TargetAngle", this.targetAngle);
            tag.putFloat("LastSpringSpeed", this.lastSpringSpeed);
            tag.putFloat("GeneratedSpeed", this.generatedSpeed);
            tag.putFloat("QueuedSpeed", this.queuedSpeed);
            tag.putBoolean("Turning", this.turning);
            tag.putBoolean("ReturningToZero", this.returningToZero);
        }

        @Override
        protected void read(final CompoundTag tag, final boolean clientPacket) {
            super.read(tag, clientPacket);
            this.oldAngle = tag.getDouble("OldAngle");
            this.angle = tag.getDouble("Angle");
            this.targetAngle = tag.getDouble("TargetAngle");
            this.lastSpringSpeed = tag.getFloat("LastSpringSpeed");
            this.generatedSpeed = tag.getFloat("GeneratedSpeed");
            this.queuedSpeed = tag.getFloat("QueuedSpeed");
            this.turning = tag.getBoolean("Turning");
            this.returningToZero = tag.getBoolean("ReturningToZero");
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parent;
        }
    }
}
