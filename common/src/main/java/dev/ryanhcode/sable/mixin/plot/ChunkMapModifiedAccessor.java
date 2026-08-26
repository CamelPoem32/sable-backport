package dev.ryanhcode.sable.mixin.plot;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapModifiedAccessor {

    @Accessor("modified")
    void sable$setModified(boolean modified);
}
