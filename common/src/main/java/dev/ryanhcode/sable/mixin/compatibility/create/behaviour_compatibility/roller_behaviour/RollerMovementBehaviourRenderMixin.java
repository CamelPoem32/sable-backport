package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.roller_behaviour;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Selects Create's animated CPU Roller actor renderer inside Sable's visible-space contraption bridge. */
@Mixin(value = RollerMovementBehaviour.class, remap = false)
public class RollerMovementBehaviourRenderMixin {
    private static final Set<MovementContext> SABLE$LOGGED_DECISIONS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<MovementContext> SABLE$LOGGED_CPU_RENDER =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @WrapOperation(
            method = "renderInContraption(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$selectRollerActorRenderer(final LevelAccessor level,
                                                    final Operation<Boolean> original,
                                                    final MovementContext context,
                                                    final VirtualRenderWorld renderWorld,
                                                    final ContraptionMatrices matrices,
                                                    final MultiBufferSource buffers) {
        final AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
        final SubLevel containing = entity == null ? null : SableCreateContraptionContext.getContainingSubLevel(entity);
        final boolean originalVisualizationSupported = original.call(level);
        final boolean returnedVisualizationSupported = containing == null && originalVisualizationSupported;
        if (containing != null && SableDiagnosticFlags.TRACE_CREATE_ACTORS && SABLE$LOGGED_DECISIONS.add(context)) {
            Sable.LOGGER.info("SABLE_M35_ROLLER_RENDER stage=VISUALIZATION_DECISION entityId={} entityUuid={} "
                            + "subLevel={} actorLocal={} rawActorPosition={} renderWorld={} "
                            + "originalVisualizationSupported={} returnedVisualizationSupported={} "
                            + "chosenRenderPath=RollerRenderer.renderInContraption hiddenPlotPoseTranslation=false",
                    entity.getId(), entity.getUUID(), containing.getUniqueId(), context.localPos, context.position,
                    renderWorld.getClass().getName(), originalVisualizationSupported, returnedVisualizationSupported);
        }
        return returnedVisualizationSupported;
    }

    @WrapOperation(
            method = "renderInContraption(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/roller/RollerRenderer;renderInContraption(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void sable$traceCpuRollerRender(final MovementContext context,
                                           final VirtualRenderWorld renderWorld,
                                           final ContraptionMatrices matrices,
                                           final MultiBufferSource buffers,
                                           final Operation<Void> original) {
        original.call(context, renderWorld, matrices, buffers);
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        final AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
        final SubLevel containing = entity == null ? null : SableCreateContraptionContext.getContainingSubLevel(entity);
        if (containing != null && SABLE$LOGGED_CPU_RENDER.add(context)) {
            Sable.LOGGER.info("SABLE_M35_ROLLER_RENDER stage=CPU_RENDER_EXIT entityId={} entityUuid={} "
                            + "subLevel={} actorLocal={} nativeRenderer=RollerRenderer.renderInContraption",
                    entity.getId(), entity.getUUID(), containing.getUniqueId(), context.localPos);
        }
    }
}
