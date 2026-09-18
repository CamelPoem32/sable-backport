package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.platform.Window;
import dev.ryanhcode.sable.forge.SableM28PresentationTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distinguishes actual display presentation from a queued RenderTarget composite. */
@Mixin(Window.class)
public abstract class WindowPresentationProbeMixin {
    @Inject(method = "updateDisplay()V", at = @At("HEAD"))
    private void sable$beforePresentation(final CallbackInfo ci) {
        SableM28PresentationTrace.displayEvent("BEFORE_PRESENT");
    }

    @Inject(method = "updateDisplay()V", at = @At("RETURN"))
    private void sable$afterPresentation(final CallbackInfo ci) {
        SableM28PresentationTrace.displayEvent("PRESENT");
    }
}
