package dev.ryanhcode.sable.mixin.compatibility.create.seat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SeatMovementBehaviour.class, remap = false)
public abstract class SeatMovementBehaviourMixin {
    @WrapOperation(method = "visitNewPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", remap = true))
    private BlockState sable$physicalSolidCell(final Level level, final BlockPos raw,
                                               final Operation<BlockState> original, final MovementContext context,
                                               final BlockPos visited) {
        final SubLevel body = context.contraption.entity == null ? null : SableSeatPassengers.owner(context.contraption.entity);
        if (body == null) return original.call(level, raw);
        final BlockPos physical = BlockPos.containing(body.logicalPose().transformPosition(Vec3.atCenterOf(raw)));
        return level.hasChunkAt(physical) ? original.call(level, physical) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @WrapOperation(method = "visitNewPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;teleportTo(DDD)V", remap = true))
    private void sable$physicalForcedDismount(final Entity passenger, final double x, final double y, final double z,
                                             final Operation<Void> original, final MovementContext context,
                                             final BlockPos visited) {
        final AbstractContraptionEntity vehicle = context.contraption.entity;
        if (vehicle == null || !SableSeatPassengers.parentPassenger(vehicle, passenger)) {
            original.call(passenger, x, y, z);
            return;
        }
        // Native stopRiding has already performed the physical collision search. Never teleport back into the plot.
        final Vec3 safe = SableSeatPassengers.dismount(vehicle, (LivingEntity) passenger, passenger.position());
        original.call(passenger, safe.x, safe.y, safe.z);
        SableSeatPassengers.trace(vehicle, passenger, "DISMOUNT_SUCCESS", safe);
    }
}
