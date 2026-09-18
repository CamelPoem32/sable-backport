package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.ryanhcode.sable.forge.SableM28PresentationTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes target switches and final presentation after a tracked M28 draw. */
@Mixin(RenderTarget.class)
public abstract class RenderTargetPresentationProbeMixin {
    @Inject(method = "bindWrite(Z)V", at = @At("RETURN"))
    private void sable$traceFramebufferBind(final boolean setViewport, final CallbackInfo ci) {
        SableM28PresentationTrace.renderTargetEvent("FRAMEBUFFER_SWITCH", (RenderTarget) (Object) this);
    }

    @Inject(method = "unbindWrite()V", at = @At("RETURN"))
    private void sable$traceFramebufferUnbind(final CallbackInfo ci) {
        SableM28PresentationTrace.renderTargetEvent("FRAMEBUFFER_SWITCH", (RenderTarget) (Object) this);
    }

    @Inject(method = "_blitToScreen(IIZ)V", at = @At("HEAD"))
    private void sable$traceFinalComposite(final int width, final int height,
                                           final boolean disableBlend, final CallbackInfo ci) {
        SableM28PresentationTrace.finalComposite((RenderTarget) (Object) this, width, height);
    }

    @Inject(method = "_blitToScreen(IIZ)V", at = @At("RETURN"))
    private void sable$traceCompositeComplete(final int width, final int height,
                                    final boolean disableBlend, final CallbackInfo ci) {
        SableM28PresentationTrace.compositeComplete((RenderTarget) (Object) this);
    }
}
