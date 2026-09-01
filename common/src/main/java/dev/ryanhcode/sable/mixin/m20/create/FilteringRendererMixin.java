package dev.ryanhcode.sable.mixin.m20.create;

import dev.ryanhcode.sable.compatibility.create.render.SableCreateRenderBridge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer", remap = false)
public class FilteringRendererMixin {
    @Redirect(
            method = "renderOnBlockEntity(Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;m_82557_(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$distanceToSqr(final Vec3 first, final Vec3 second) {
        return SableCreateRenderBridge.distanceToSqrWithSubLevels("FilteringRenderer", first, second);
    }
}
