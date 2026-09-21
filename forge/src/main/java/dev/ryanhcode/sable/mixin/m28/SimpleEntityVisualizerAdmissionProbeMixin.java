package dev.ryanhcode.sable.mixin.m28;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import dev.ryanhcode.sable.forge.SableM28RestoredContraptionClientSync;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the visual factory boundary without affecting Flywheel admission. */
@Mixin(value = SimpleEntityVisualizer.class, remap = false)
public abstract class SimpleEntityVisualizerAdmissionProbeMixin<T extends Entity> {
    @Inject(method = "createVisual(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                    + "Lnet/minecraft/world/entity/Entity;F)Ldev/engine_room/flywheel/api/visual/EntityVisual;",
            at = @At("HEAD"))
    private void sable$traceVisualFactoryEnter(final VisualizationContext context,
                                               final T entity,
                                               final float partialTick,
                                               final CallbackInfoReturnable<EntityVisual<? super T>> cir) {
        SableM28RestoredContraptionClientSync.flywheelCreateVisual(entity, "ENTER", null);
    }

    @Inject(method = "createVisual(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                    + "Lnet/minecraft/world/entity/Entity;F)Ldev/engine_room/flywheel/api/visual/EntityVisual;",
            at = @At("RETURN"))
    private void sable$traceVisualFactoryReturn(final VisualizationContext context,
                                                final T entity,
                                                final float partialTick,
                                                final CallbackInfoReturnable<EntityVisual<? super T>> cir) {
        SableM28RestoredContraptionClientSync.flywheelCreateVisual(entity, "RETURN", cir.getReturnValue());
    }
}
