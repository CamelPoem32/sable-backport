package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;

public final class SableChunkEventPlatformImpl implements SableChunkEventPlatform {
    @Override
    public void onClientChunkPacketReplaced(final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }

    @Override
    public void onOldChunkInvalid(final LevelChunk chunk) {
    }

    @Override
    public void onPlotChunkLoaded(final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }
}
