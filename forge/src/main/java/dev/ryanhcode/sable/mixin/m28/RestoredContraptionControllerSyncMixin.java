package dev.ryanhcode.sable.mixin.m28;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.forge.SableM29RestoredControllerSyncGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defers only Create's controller-null discard while a reverse-restored bearing is synchronizing. */
@Mixin(value = ControlledContraptionEntity.class, remap = false)
public abstract class RestoredContraptionControllerSyncMixin {
    @Inject(method = "tickContraption()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/ControlledContraptionEntity;getController()Lcom/simibubi/create/content/contraptions/IControlContraption;",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    private void sable$waitForRestoredController(final CallbackInfo ci) {
        if (SableM29RestoredControllerSyncGuard.deferMissingController(
                (ControlledContraptionEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
