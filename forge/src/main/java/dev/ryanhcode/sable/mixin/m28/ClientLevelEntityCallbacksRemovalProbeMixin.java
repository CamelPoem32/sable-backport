package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM29RestoredCceRemovalTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Confirms when the client entity storage actually ends tracking a restored CCE. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
public abstract class ClientLevelEntityCallbacksRemovalProbeMixin {
    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void sable$trackingEnd(final Entity entity, final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.trackingEnd(entity);
    }
}
