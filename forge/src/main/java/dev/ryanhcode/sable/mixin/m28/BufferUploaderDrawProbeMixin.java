package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.ryanhcode.sable.forge.SableM28BatchTrace;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes the actual VertexBuffer draw invoked by BufferUploader. */
@Mixin(BufferUploader.class)
public abstract class BufferUploaderDrawProbeMixin {
    @WrapOperation(method = "_drawWithShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader"
                    + "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
                    + "Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private static void sable$recordActualGpuDraw(final VertexBuffer gpuBuffer,
                                                  final Matrix4f modelView, final Matrix4f projection,
                                                  final ShaderInstance shader, final Operation<Void> original,
                                                  final BufferBuilder.RenderedBuffer rendered) {
        SableM28BatchTrace.actualDraw(rendered, gpuBuffer, modelView, projection, shader);
        original.call(gpuBuffer, modelView, projection, shader);
        SableM28BatchTrace.afterGpuDraw();
    }
}
