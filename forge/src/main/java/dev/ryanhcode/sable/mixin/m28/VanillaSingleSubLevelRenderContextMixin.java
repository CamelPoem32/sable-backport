package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the existing immediate static Sable renderer for low-level model ownership diagnostics. */
@Mixin(value = VanillaSingleSubLevelRenderData.class, remap = false)
public abstract class VanillaSingleSubLevelRenderContextMixin {
    @Unique
    private final ThreadLocal<SableM28VisualOwnershipTrace.Scope> sable$m28StaticScope = new ThreadLocal<>();

    @Inject(method = "renderSingleBlock", at = @At("HEAD"))
    private void sable$beginStaticRenderContext(final RenderType layer, final VertexConsumer consumer,
                                                final Matrix4f modelView,
                                                final double camX, final double camY, final double camZ,
                                                final CallbackInfoReturnable<Integer> cir) {
        this.sable$m28StaticScope.set(
                SableM28VisualOwnershipTrace.enter(SableM28VisualOwnershipTrace.Owner.SABLE_STATIC));
    }

    @Inject(method = "renderSingleBlock", at = @At("RETURN"))
    private void sable$endStaticRenderContext(final RenderType layer, final VertexConsumer consumer,
                                              final Matrix4f modelView,
                                              final double camX, final double camY, final double camZ,
                                              final CallbackInfoReturnable<Integer> cir) {
        final SableM28VisualOwnershipTrace.Scope scope = this.sable$m28StaticScope.get();
        if (scope != null) {
            scope.close();
            this.sable$m28StaticScope.remove();
        }
    }
}
