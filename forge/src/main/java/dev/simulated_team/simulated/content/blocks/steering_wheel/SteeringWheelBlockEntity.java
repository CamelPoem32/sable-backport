package dev.simulated_team.simulated.content.blocks.steering_wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    private boolean held;
    private @Nullable UUID controller;

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
        if (Math.abs(this.requestedAngle - this.targetAngle) > 0.001F) {
            this.beginMove(this.requestedAngle);
        }
        if (this.generatedSpeed != 0.0F) {
            this.integrateAngle();
        }
    }

    public void acceptControl(final ServerPlayer player, final float requested, final boolean stop) {
        if (stop) {
            if (this.controller == null || this.controller.equals(player.getUUID())) {
                this.held = false;
                this.controller = null;
                this.notifyUpdate();
            }
            return;
        }
        this.controller = player.getUUID();
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

    @Override
    public float getGeneratedSpeed() {
        return this.generatedSpeed;
    }

    @Override
    protected Block getStressConfigKey() {
        return this.getBlockState().getBlock();
    }

    @Override
    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putFloat("Angle", this.angle);
        tag.putFloat("TargetAngle", this.targetAngle);
        tag.putFloat("RequestedAngle", this.requestedAngle);
        tag.putFloat("GeneratedSpeed", this.generatedSpeed);
        tag.putFloat("LogicalSpeed", this.logicalSpeed);
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
        this.held = clientPacket && tag.getBoolean("Held");
        this.controller = null;
        if (clientPacket) {
            this.previousClientAngle = this.clientAngle;
            this.clientAngle = this.angle;
        }
    }

    private void beginMove(final float requested) {
        this.targetAngle = Mth.clamp(requested, -this.getAngleLimit(), this.getAngleLimit());
        final float difference = this.targetAngle - this.angle;
        if (Math.abs(difference) <= 0.001F) {
            this.stopMove();
            return;
        }
        this.logicalSpeed = Math.copySign(RPM, difference);
        final Direction facing = this.getBlockState().getValue(SteeringWheelBlock.FACING);
        final boolean floor = this.getBlockState().getValue(SteeringWheelBlock.ON_FLOOR);
        this.generatedSpeed = (facing == Direction.NORTH || facing == Direction.WEST) == floor
                ? -this.logicalSpeed : this.logicalSpeed;
        this.updateGeneratedRotation();
        this.setChanged();
        this.sendData();
    }

    private void integrateAngle() {
        final float step = Math.abs(KineticBlockEntity.convertToAngular(this.logicalSpeed));
        final float remaining = this.targetAngle - this.angle;
        if (step <= 1.0E-6F || Math.abs(remaining) <= step) {
            this.angle = this.targetAngle;
            this.stopMove();
        } else {
            this.angle += Math.copySign(step, remaining);
        }
        this.setChanged();
        this.sendData();
    }

    private void stopMove() {
        if (this.generatedSpeed == 0.0F && this.logicalSpeed == 0.0F) {
            return;
        }
        this.generatedSpeed = 0.0F;
        this.logicalSpeed = 0.0F;
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
                && (owner == null || Sable.HELPER.getTrackingSubLevel(player) == owner)
                && player.getEyePosition().distanceToSqr(visibleCenter) <= MAX_CONTROL_DISTANCE_SQUARED;
        if (!valid) {
            this.controller = null;
            this.held = false;
            this.notifyUpdate();
        }
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
