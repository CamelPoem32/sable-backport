package dev.ryanhcode.sable.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.CompletableFuture;

/** Minecraft 1.20 chunk-future values used by Sable's plot holders. */
public final class SableChunkFutures {
    private SableChunkFutures() {
    }

    public static Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> loaded(final ChunkAccess chunk) {
        return Either.left(chunk);
    }

    public static CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> loadedLevelChunk(final LevelChunk chunk) {
        return CompletableFuture.completedFuture(Either.left(chunk));
    }
}
