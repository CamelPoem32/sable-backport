package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM29RestoredCceRemovalTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the exact removal reason after a correlated restored CCE reaches Entity. */
@Mixin(Entity.class)
public abstract class EntityRemovalProbeMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void sable$setRemoved(final Entity.RemovalReason reason, final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.entitySetRemoved((Entity) (Object) this, reason);
    }
}
