package dev.ryanhcode.sable.mixin.compatibility.create.seat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ContraptionInteractionPacket.class, remap = false)
public class SeatInteractionPacketMixin {
    @Shadow private BlockPos localPos;

    @WrapOperation(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", remap = true))
    private double sable$physicalSeatReach(final ServerPlayer player, final Entity target, final Operation<Double> original) {
        if (!(target instanceof AbstractContraptionEntity vehicle) || SableSeatPassengers.owner(vehicle) == null
                || !vehicle.getContraption().getSeats().contains(this.localPos)) return original.call(player, target);
        return SableSeatPassengers.parentPassenger(vehicle, player)
                ? player.position().distanceToSqr(SableSeatPassengers.visibleSeat(vehicle, this.localPos)) : Double.POSITIVE_INFINITY;
    }
}
