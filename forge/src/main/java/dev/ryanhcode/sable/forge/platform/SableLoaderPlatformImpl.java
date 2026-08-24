package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import net.minecraftforge.fml.ModList;

public final class SableLoaderPlatformImpl implements SableLoaderPlatform {
    @Override
    public boolean isModLoaded(final String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String getModVersion(final String modId) {
        return ModList.get().getModContainerById(modId)
                .orElseThrow(() -> new IllegalArgumentException("Mod is not loaded: " + modId))
                .getModInfo()
                .getVersion()
                .toString();
    }
}
