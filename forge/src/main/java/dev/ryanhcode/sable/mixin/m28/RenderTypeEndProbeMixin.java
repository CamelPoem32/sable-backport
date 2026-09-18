package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.ryanhcode.sable.forge.SableM28BatchTrace;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures the RenderedBuffer before upload without changing render-state ordering. */
@Mixin(RenderType.class)
public abstract class RenderTypeEndProbeMixin {
    @WrapOperation(method = "end", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;end()"
                    + "Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;"))
    private BufferBuilder.RenderedBuffer sable$recordFinalizedBuffer(final BufferBuilder builder,
                                                                    final Operation<BufferBuilder.RenderedBuffer> original) {
        final BufferBuilder.RenderedBuffer rendered = original.call(builder);
        SableM28BatchTrace.noteRenderedBuffer((RenderType) (Object) this, builder, rendered);
        return rendered;
    }

    @WrapOperation(method = "end", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader"
                    + "(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"))
    private void sable$recordPreDraw(final BufferBuilder.RenderedBuffer rendered,
                                     final Operation<Void> original) {
        SableM28BatchTrace.beforeGpuDraw(rendered);
        original.call(rendered);
    }
}
