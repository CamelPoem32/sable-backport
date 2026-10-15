package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.residuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.processing.burner.BlazeBurnerMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.residuals.SableBlazeGaze;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlazeBurnerMovementBehaviour.class, remap = false)
public abstract class BlazeBurnerMovementBehaviourMixin {
    @WrapOperation(method = "getTargetAngle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", remap = true))
    private Vec3 sable$physicalGazeBeforeSubtraction(final Vec3 camera, final Vec3 rawActor,
                                                     final Operation<Vec3> original, final MovementContext context) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) return original.call(camera, rawActor);
        return SableBlazeGaze.prepare(context, owner, camera, rawActor);
    }

    @Inject(method = "getTargetAngle", at = @At("RETURN"))
    private void sable$observeNativeGazeAngle(final MovementContext context, final CallbackInfoReturnable<Float> cir) {
        if (SableCreateActorWorldContext.owner(context) != null) SableBlazeGaze.completed(context, cir.getReturnValue());
    }
}
