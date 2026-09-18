package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only vertex-count access for the immediate Sable static renderer diagnostics. */
@Mixin(BufferBuilder.class)
public interface StaticBufferBuilderProbeAccessor {
    @Accessor("vertices")
    int sable$getVertices();
}
