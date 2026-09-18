package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.ryanhcode.sable.forge.SableM28ContraptionBufferProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads destination bytes only while the scoped Catnip shaded-mesh call is active. */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderVertexWriteProbeMixin {
    @Shadow
    private int vertices;

    @Shadow
    private int nextElementByte;

    @Inject(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("HEAD"))
    private void sable$beforeCatnipDestinationVertex(final float x, final float y, final float z,
                                                     final float red, final float green, final float blue,
                                                     final float alpha, final float u, final float v,
                                                     final int overlay, final int light,
                                                     final float normalX, final float normalY, final float normalZ,
                                                     final CallbackInfo ci) {
        if (SableM28ContraptionBufferProbe.hasCurrentVertex()) {
            SableM28ContraptionBufferProbe.beforeDestinationWrite((BufferBuilder) (Object) this,
                    this.vertices, this.nextElementByte);
        }
    }

    @Inject(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("RETURN"))
    private void sable$afterCatnipDestinationVertex(final float x, final float y, final float z,
                                                    final float red, final float green, final float blue,
                                                    final float alpha, final float u, final float v,
                                                    final int overlay, final int light,
                                                    final float normalX, final float normalY, final float normalZ,
                                                    final CallbackInfo ci) {
        if (SableM28ContraptionBufferProbe.hasCurrentVertex()) {
            SableM28ContraptionBufferProbe.afterDestinationWrite((BufferBuilder) (Object) this,
                    this.vertices, this.nextElementByte);
        }
    }
}
