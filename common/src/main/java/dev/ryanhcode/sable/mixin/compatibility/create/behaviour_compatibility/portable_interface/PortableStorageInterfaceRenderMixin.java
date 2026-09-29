package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.portable_interface;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses Create's native animated CPU interface renderer inside Sable's visible-space bridge. */
@Mixin(value = PortableStorageInterfaceMovement.class, remap = false)
public class PortableStorageInterfaceRenderMixin {
    @WrapOperation(
            method = "renderInContraption(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$selectVisibleInterfaceRenderer(final LevelAccessor level,
                                                         final Operation<Boolean> original,
                                                         final MovementContext context,
                                                         final VirtualRenderWorld renderWorld,
                                                         final ContraptionMatrices matrices,
                                                         final MultiBufferSource buffers) {
        final boolean visualizationSupported = original.call(level);
        final AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
        return entity == null || SableCreateContraptionContext.getContainingSubLevel(entity) == null
                ? visualizationSupported : false;
    }
}
