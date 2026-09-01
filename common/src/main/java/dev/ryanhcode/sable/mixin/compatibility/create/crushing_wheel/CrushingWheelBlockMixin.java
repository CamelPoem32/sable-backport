package dev.ryanhcode.sable.mixin.compatibility.create.crushing_wheel;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Direct upstream Sable Create crusher compatibility port. Create computes the
 * wheel intake impulse from block-local coordinates; entities colliding with a
 * visible Sable must be projected into that Sable before the impulse is
 * calculated and projected back before velocity is applied.
 */
@Mixin(value = CrushingWheelBlock.class, remap = false)
public abstract class CrushingWheelBlockMixin extends RotatedPillarKineticBlock implements IBE<CrushingWheelBlockEntity> {

    public CrushingWheelBlockMixin(final Properties properties) {
        super(properties);
    }

    @Inject(method = {"entityInside", "m_7892_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void sable$entityInside(final BlockState state,
                                    final Level level,
                                    final BlockPos pos,
                                    final Entity entityIn,
                                    final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            return;
        }

        final Vec3 entityPos = subLevel.logicalPose().transformPositionInverse(entityIn.position());
        if (entityPos.y() < pos.getY() + 1.25f || !entityIn.onGround()) {
            ci.cancel();
            return;
        }

        final float speed = this.getBlockEntityOptional(level, pos)
                .map(CrushingWheelBlockEntity::getSpeed)
                .orElse(0f);

        double x = 0;
        double z = 0;
        final double entityX = entityPos.x();
        final double entityZ = entityPos.z();

        if (state.getValue(AXIS) == Direction.Axis.X) {
            z = speed / 20f;
            x += (pos.getX() + .5f - entityX) * .1f;
        }

        if (state.getValue(AXIS) == Direction.Axis.Z) {
            x = speed / -20f;
            z += (pos.getZ() + .5f - entityZ) * .1f;
        }

        final Vec3 impulse = subLevel.logicalPose().transformNormal(new Vec3(x, 0, z));
        entityIn.setDeltaMovement(entityIn.getDeltaMovement().add(impulse));
        ci.cancel();
    }
}
