package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/** Read-only access to the actual destination bytes written by BufferBuilder. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderProbeAccessor {
    @Accessor("buffer")
    ByteBuffer sable$getBuffer();

    @Accessor("vertices")
    int sable$getVertices();

    @Accessor("nextElementByte")
    int sable$getNextElementByte();

    @Accessor("format")
    VertexFormat sable$getFormat();
}
