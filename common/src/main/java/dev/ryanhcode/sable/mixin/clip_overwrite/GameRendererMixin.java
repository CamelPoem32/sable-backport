package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Supplies render-tick sub-level poses while Minecraft refreshes its crosshair hit result.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"))
    private void sable$renderLevel(final GameRenderer instance, final float f, final Operation<Void> original) {
        if (!(this.minecraft.level instanceof final LevelPoseProviderExtension extension)) {
            original.call(instance, f);
            return;
        }

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(f));
        try {
            original.call(instance, f);
        } finally {
            extension.sable$popPoseSupplier();
        }
    }

}
