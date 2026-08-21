package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;

public final class SablePlotPlatformImpl implements SablePlotPlatform {
    private static final String FORGE_CAPS_KEY = "ForgeCaps";

    @Override
    public void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // Forge 1.20.1 has no NeoForge auxiliary-light attachment. Retained core callers use vanilla light data.
    }

    @Override
    public void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess,
                                     final LevelChunk chunk) {
        if (tag.contains(FORGE_CAPS_KEY, Tag.TAG_COMPOUND)) {
            chunk.readCapsFromNBT(tag.getCompound(FORGE_CAPS_KEY));
        }
    }

    @Override
    public void postLoad(final CompoundTag tag, final LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, tag, ChunkStatus.ChunkType.LEVELCHUNK));
    }

    @Override
    public void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        // See readLightData: auxiliary light belongs to deferred advanced rendering.
    }

    @Override
    public void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess,
                                      final LevelChunk chunk) {
        try {
            final CompoundTag caps = chunk.writeCapsToNBT();
            if (caps != null) {
                tag.put(FORGE_CAPS_KEY, caps);
            }
        } catch (final RuntimeException exception) {
            Sable.LOGGER.error("Failed to write chunk capabilities for a Sable plot", exception);
        }
    }
}
