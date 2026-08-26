package dev.ryanhcode.sable.mixin.plot;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapStatusChangeInvoker {

    @Invoker("onFullChunkStatusChange")
    void sable$callOnFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus fullChunkStatus);
}
