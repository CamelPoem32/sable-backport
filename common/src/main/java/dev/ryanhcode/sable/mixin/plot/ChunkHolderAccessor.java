package dev.ryanhcode.sable.mixin.plot;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public interface ChunkHolderAccessor {

    @Accessor("entityTickingChunkFuture")
    void sable$setEntityTickingChunkFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> future);

    @Accessor("tickingChunkFuture")
    void sable$setTickingChunkFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> future);

    @Accessor("fullChunkFuture")
    void sable$setFullChunkFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> future);
}
