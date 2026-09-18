package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only access to exact Forge 47.4.20 BufferSource batch ownership. */
@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceProbeAccessor {
    @Accessor("builder")
    BufferBuilder sable$getBuilder();

    @Accessor("fixedBuffers")
    Map<RenderType, BufferBuilder> sable$getFixedBuffers();

    @Accessor("lastState")
    Optional<RenderType> sable$getLastState();

    @Accessor("startedBuffers")
    Set<BufferBuilder> sable$getStartedBuffers();
}
