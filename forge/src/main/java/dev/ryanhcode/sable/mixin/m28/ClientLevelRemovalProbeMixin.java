package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM29RestoredCceRemovalTrace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the authoritative ClientLevel entity removal and replacement boundaries. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelRemovalProbeMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void sable$beforeRemoveEntity(final int entityId, final Entity.RemovalReason reason,
                                          final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.clientLevelRemoveEnter(
                (ClientLevel) (Object) this, entityId, reason);
    }

    @Inject(method = "removeEntity", at = @At("RETURN"))
    private void sable$afterRemoveEntity(final int entityId, final Entity.RemovalReason reason,
                                         final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.clientLevelRemoveReturn(
                (ClientLevel) (Object) this, entityId, reason);
    }

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void sable$beforeAddEntity(final int entityId, final Entity entity, final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.entityAdding((ClientLevel) (Object) this, entityId, entity);
    }
}
