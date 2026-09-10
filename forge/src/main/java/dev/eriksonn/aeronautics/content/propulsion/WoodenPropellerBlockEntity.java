package dev.eriksonn.aeronautics.content.propulsion;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Frozen Aeronautics 1.3.0 wooden propeller adapted to the 1.20.1 Create kinetic lifecycle. */
public final class WoodenPropellerBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    private float rotationSpeed;
    private float previousAngle;
    private float angle;

    public WoodenPropellerBlockEntity(final BlockPos pos, final BlockState state) {
        super(AeroPropulsionRegistries.WOODEN_PROPELLER_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        float nextSpeed = convertToAngular(this.getSpeed());
        if (this.getSpeed() == 0.0F) {
            nextSpeed = 0.0F;
        }
        this.rotationSpeed = Mth.lerp(0.15F, this.rotationSpeed, nextSpeed);
        this.previousAngle = this.angle;
        this.angle += this.rotationSpeed;
        super.tick();
    }

    @Override
    public WoodenPropellerBlockEntity getPropeller() {
        return this;
    }

    @Override
    public Direction getBlockDirection() {
        return this.getBlockState().getValue(BlockStateProperties.FACING);
    }

    public float getRotationSpeed() {
        return this.rotationSpeed;
    }

    public float getPreviousAngle() {
        return this.previousAngle;
    }

    public float getAngle() {
        return this.angle;
    }

    private float getDirectionIndependentSpeed() {
        return this.getBlockDirection().getAxisDirection().getStep()
                * this.rotationSpeed * (10.0F / 3.0F)
                * (this.getBlockState().getValue(WoodenPropellerBlock.REVERSED) ? -1.0F : 1.0F);
    }

    @Override
    public double getAirflow() {
        return AeroConfig.WOODEN_PROPELLER_AIRFLOW.get() * this.getDirectionIndependentSpeed();
    }

    @Override
    public double getThrust() {
        return AeroConfig.WOODEN_PROPELLER_THRUST.get() * this.getDirectionIndependentSpeed();
    }

    @Override
    public boolean isActive() {
        return Math.abs(this.rotationSpeed) > 0.01F;
    }

    @Override
    protected void write(final CompoundTag tag, final boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putFloat("RotationSpeed", this.rotationSpeed);
        tag.putFloat("PreviousAngle", this.previousAngle);
        tag.putFloat("Angle", this.angle);
    }

    @Override
    protected void read(final CompoundTag tag, final boolean clientPacket) {
        super.read(tag, clientPacket);
        this.rotationSpeed = tag.getFloat("RotationSpeed");
        this.previousAngle = tag.getFloat("PreviousAngle");
        this.angle = tag.getFloat("Angle");
    }
}
