package dev.ryanhcode.sable.mixin.compatibility.create.seat;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerSubLevelContainer.class, remap = false)
public class SeatOuterRemovalMixin {
    @Inject(method = "removeSubLevel(IILdev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V", at = @At("HEAD"))
    private void sable$detachBeforePlotDeletion(final int x, final int z, final SubLevelRemovalReason reason, final CallbackInfo ci) {
        final var body = ((ServerSubLevelContainer) (Object) this).getSubLevel(x, z);
        if (body instanceof ServerSubLevel serverBody) SableSeatPassengers.detachOuter(serverBody);
    }
}
