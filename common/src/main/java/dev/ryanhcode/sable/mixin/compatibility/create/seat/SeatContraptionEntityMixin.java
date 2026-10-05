package dev.ryanhcode.sable.mixin.compatibility.create.seat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class SeatContraptionEntityMixin {
    @Inject(method = "addSittingPassenger", at = @At("HEAD"), cancellable = true)
    private void sable$parentMountOnly(final Entity passenger, final int seatIndex, final CallbackInfo ci) {
        final AbstractContraptionEntity vehicle = (AbstractContraptionEntity) (Object) this;
        if (SableSeatPassengers.owner(vehicle) != null && !SableSeatPassengers.canMount(vehicle, passenger)) ci.cancel();
    }

    @Inject(method = "addSittingPassenger", at = @At("RETURN"))
    private void sable$mountTrace(final Entity passenger, final int seatIndex, final CallbackInfo ci) {
        final AbstractContraptionEntity vehicle = (AbstractContraptionEntity) (Object) this;
        if (SableSeatPassengers.parentPassenger(vehicle, passenger) && passenger.getVehicle() == vehicle)
            SableSeatPassengers.trace(vehicle, passenger, "MOUNT_SUCCESS", passenger.position());
    }

    @Inject(method = "getPassengerPosition", at = @At("RETURN"), cancellable = true)
    private void sable$physicalSeat(final Entity passenger, final float partialTicks,
                                    final CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(SableSeatPassengers.physicalPosition((AbstractContraptionEntity) (Object) this,
                passenger, cir.getReturnValue(), partialTicks));
    }

    @Inject(method = {"getDismountLocationForPassenger", "m_7688_"}, at = @At("RETURN"), cancellable = true)
    private void sable$safeDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(SableSeatPassengers.dismount((AbstractContraptionEntity) (Object) this,
                passenger, cir.getReturnValue()));
    }

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true)
    private void sable$rejectOtherPlot(final Player player, final BlockPos localPos, final Direction side,
                                       final InteractionHand hand, final CallbackInfoReturnable<Boolean> cir) {
        final AbstractContraptionEntity vehicle = (AbstractContraptionEntity) (Object) this;
        if (SableSeatPassengers.owner(vehicle) != null && vehicle.getContraption().getSeats().contains(localPos)
                && !SableSeatPassengers.canMount(vehicle, player)) cir.setReturnValue(false);
    }

    @Inject(method = "disassemble", at = @At("HEAD"))
    private void sable$detachBeforeSeatRestoration(final CallbackInfo ci) {
        SableSeatPassengers.detach((AbstractContraptionEntity) (Object) this);
    }

    @Inject(method = {"remove", "m_142687_"}, at = @At("HEAD"))
    private void sable$detachBeforeRemoval(final Entity.RemovalReason reason, final CallbackInfo ci) {
        SableSeatPassengers.detach((AbstractContraptionEntity) (Object) this);
    }

    @WrapOperation(method = {"saveWithoutId", "m_20240_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V", remap = true))
    private void sable$neverGatherSeatPassengerIntoPlot(final Entity passenger, final double x,
                                                       final double y, final double z, final Operation<Void> original) {
        if (!SableSeatPassengers.parentPassenger((Entity) (Object) this, passenger)) original.call(passenger, x, y, z);
    }
}
