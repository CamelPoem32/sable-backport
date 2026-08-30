package dev.ryanhcode.sable.mixin.m18.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Forces Sable-contained moving Deployers down Create's vanilla actor renderer path. */
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour", remap = false)
public class DeployerMovementBehaviourRenderMixin {
    private static final Set<String> LOGGED_DEPLOYER_ACTOR_RENDER =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "renderInContraption(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowDeployerActorVanillaRender(final LevelAccessor level,
                                                          final Operation<Boolean> original,
                                                          final MovementContext context,
                                                          final VirtualRenderWorld renderWorld,
                                                          final ContraptionMatrices matrices,
                                                          final MultiBufferSource bufferSource) {
        final boolean originalVisualizationSupported = original.call(level);
        final AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
        final SubLevel containing = entity == null ? null : SableCreateContraptionContext.getContainingSubLevel(entity);
        final boolean sableContraption = containing != null;
        final boolean returnedVisualizationSupported = sableContraption ? false : originalVisualizationSupported;
        if (sableContraption) {
            final String key = entity.getId() + ":" + context.localPos.asLong() + ":"
                    + originalVisualizationSupported + ":" + returnedVisualizationSupported;
            if (LOGGED_DEPLOYER_ACTOR_RENDER.add(key)) {
                Sable.LOGGER.info("SABLE_M18_DEPLOYER_RENDER stage=MOVING_ACTOR_VISUAL_DECISION entityId={} subLevel={} actorLocal={} actorState={} renderWorld={} originalVisualizationSupported={} returnedVisualizationSupported={} renderPath=Create_DeployerRenderer_renderInContraption animationSpeed={} hiddenPlotPoseTranslation=false",
                        entity.getId(),
                        containing.getUniqueId(),
                        context.localPos,
                        BuiltInRegistries.BLOCK.getKey(context.state.getBlock()),
                        renderWorld.getClass().getName(),
                        originalVisualizationSupported,
                        returnedVisualizationSupported,
                        context.getAnimationSpeed());
            }
        }
        return returnedVisualizationSupported;
    }
}
