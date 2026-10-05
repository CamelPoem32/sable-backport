package dev.ryanhcode.sable.mixin.compatibility.create.seat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ContraptionHandlerClient.class, remap = false)
public class SeatInteractionClientMixin {
    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;", remap = true))
    private static AABB sable$visibleSeatBounds(final AbstractContraptionEntity entity, final Operation<AABB> original) {
        return SableSeatPassengers.owner(entity) == null ? original.call(entity) : SableSeatPassengers.visibleBounds(entity);
    }

    @WrapOperation(method = "rayTraceContraption", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$physicalRay(final AbstractContraptionEntity entity, final Vec3 point,
                                         final float partialTicks, final Operation<Vec3> original) {
        final var body = SableSeatPassengers.owner(entity);
        return original.call(entity, body == null ? point : body.logicalPose().transformPositionInverse(point), partialTicks);
    }

    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toGlobalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$physicalHit(final AbstractContraptionEntity entity, final Vec3 point,
                                         final float partialTicks, final Operation<Vec3> original) {
        final Vec3 raw = original.call(entity, point, partialTicks);
        final var body = SableSeatPassengers.owner(entity);
        return body == null ? raw : body.logicalPose().transformPosition(raw);
    }
}
