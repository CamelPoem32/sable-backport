package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM28BatchTrace;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Follows tracked sail ranges through the real BufferSource finalization call. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceFlushProbeMixin {
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void sable$beforeEndBatch(final RenderType type, final CallbackInfo ci) {
        SableM28BatchTrace.beforeEndBatch((MultiBufferSource.BufferSource) (Object) this, type);
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void sable$afterEndBatch(final RenderType type, final CallbackInfo ci) {
        SableM28BatchTrace.afterEndBatch();
    }
}
