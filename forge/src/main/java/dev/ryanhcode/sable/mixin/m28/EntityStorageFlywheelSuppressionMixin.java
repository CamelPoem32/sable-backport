package dev.ryanhcode.sable.mixin.m28;

import dev.engine_room.flywheel.impl.visualization.storage.EntityStorage;
import dev.ryanhcode.sable.forge.SableM28FlywheelVisualTrace;
import dev.ryanhcode.sable.forge.SableM28ControlledSailFlywheelTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic-only veto at Flywheel's real entity-visual admission boundary. */
@Mixin(value = EntityStorage.class, remap = false)
public abstract class EntityStorageFlywheelSuppressionMixin {
    @Inject(method = "willAccept(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void sable$suppressM28TargetVisual(final Entity entity,
                                               final CallbackInfoReturnable<Boolean> cir) {
        if (SableM28FlywheelVisualTrace.suppressVisual(entity)
                || SableM28ControlledSailFlywheelTrace.suppress(entity)) {
            cir.setReturnValue(false);
        }
    }
}
