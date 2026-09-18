package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.forge.SableM28ContraptionBufferProbe;
import net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer;
import net.createmod.catnip.render.TemplateMesh;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Intercepts the exact Catnip 1.20.1 shaded-mesh vertex transfer. */
@Mixin(value = ShadeSeparatingSuperByteBuffer.class, remap = false)
public abstract class ShadeSeparatingSuperByteBufferVertexProbeMixin {
    @Shadow
    @Final
    private TemplateMesh template;

    @Shadow
    @Final
    private int[] shadeSwapVertices;

    @Shadow
    @Final
    private Matrix4f modelMat;

    @WrapOperation(method = "renderInto",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                            + "vertex(FFFFFFFFFIIFFF)V", remap = true))
    private void sable$recordActualShadedVertex(final VertexConsumer consumer,
                                                final float x, final float y, final float z,
                                                final float red, final float green, final float blue,
                                                final float alpha, final float u, final float v,
                                                final int overlay, final int light,
                                                final float normalX, final float normalY, final float normalZ,
                                                final Operation<Void> original,
                                                @Local(index = 19) final int vertexIndex) {
        SableM28ContraptionBufferProbe.beforeVertex(consumer, this.template,
                this.shadeSwapVertices, this.modelMat, vertexIndex);
        try {
            original.call(consumer, x, y, z, red, green, blue, alpha, u, v,
                    overlay, light, normalX, normalY, normalZ);
        } finally {
            SableM28ContraptionBufferProbe.afterVertex(consumer, this.template, vertexIndex,
                    x, y, z);
        }
    }
}
