package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import dev.ryanhcode.sable.forge.SableM28RestoredContraptionClientSync;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Traces Flywheel's actual queue admission and storage insertion for restored CCEs. */
@Mixin(value = VisualManagerImpl.class, remap = false)
public abstract class VisualManagerImplAdmissionProbeMixin {
    @WrapOperation(method = "queueAdd(Ljava/lang/Object;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/impl/visualization/storage/Storage;"
                            + "willAccept(Ljava/lang/Object;)Z"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean sable$traceQueueAdmission(final Storage storage,
                                              final Object value,
                                              final Operation<Boolean> original) {
        final boolean accepted = original.call(storage, value);
        if (value instanceof final Entity entity) {
            SableM28RestoredContraptionClientSync.flywheelAddEvaluated(entity, accepted);
        }
        return accepted;
    }

    @WrapOperation(method = "processQueue(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;F)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/impl/visualization/storage/Storage;"
                            + "add(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                            + "Ljava/lang/Object;F)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sable$traceStorageAdd(final Storage storage,
                                       final VisualizationContext context,
                                       final Object value,
                                       final float partialTick,
                                       final Operation<Void> original) {
        if (value instanceof final Entity entity) {
            SableM28RestoredContraptionClientSync.flywheelAddDequeued(entity);
        }
        original.call(storage, context, value, partialTick);
        if (value instanceof final Entity entity
                && SableM28RestoredContraptionClientSync.isPending(entity)) {
            boolean inserted = false;
            for (final Object candidate : storage.getAllVisuals()) {
                if (candidate instanceof final AbstractEntityVisualAccessor accessor
                        && accessor.sable$getEntity() == entity) {
                    inserted = true;
                    break;
                }
            }
            SableM28RestoredContraptionClientSync.flywheelStorageInserted(entity, inserted);
        }
    }
}
