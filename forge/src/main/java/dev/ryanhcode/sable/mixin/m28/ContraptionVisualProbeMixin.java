package dev.ryanhcode.sable.mixin.m28;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.ryanhcode.sable.forge.SableM28RestoredContraptionClientSync;
import dev.ryanhcode.sable.forge.SableM29SailVisualLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Connects restored contraptions to Flywheel admission and optional lifecycle tracing. */
@Mixin(value = ContraptionVisual.class, remap = false)
public abstract class ContraptionVisualProbeMixin<E extends AbstractContraptionEntity> {
    @Inject(method = "<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                    + "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;F)V",
            at = @At("RETURN"))
    private void sable$traceM28FlywheelCreate(final VisualizationContext context, final E entity,
                                               final float partialTick, final CallbackInfo ci) {
        SableM28RestoredContraptionClientSync.visualCreated(entity, this);
        SableM29SailVisualLifecycle.visualCreated(entity, this);
    }

    @Inject(method = "setEmbeddingMatrices(F)V", at = @At("RETURN"))
    private void sable$traceM28FlywheelTransform(final float partialTick, final CallbackInfo ci) {
        SableM28RestoredContraptionClientSync.visualFrame(this.sable$entity(), this);
        SableM29SailVisualLifecycle.visualFrame(this.sable$entity(), this, partialTick);
    }

    @Inject(method = "_delete()V", at = @At("HEAD"))
    private void sable$traceM28FlywheelDelete(final CallbackInfo ci) {
        SableM28RestoredContraptionClientSync.visualRemoved(this.sable$entity(), this);
        SableM29SailVisualLifecycle.visualRemoved(this.sable$entity(), this);
    }

    @SuppressWarnings("unchecked")
    private E sable$entity() {
        return (E) ((AbstractEntityVisualAccessor) this).sable$getEntity();
    }
}
