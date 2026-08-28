package dev.ryanhcode.sable.forge.plugin;

import dev.ryanhcode.sable.mixin.AbstractSableMixinPlugin;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

/** Forge entry point for the shared optional-mod compatibility mixin gating. */
public final class SableMixinPlugin extends AbstractSableMixinPlugin {
    @Override
    protected boolean isModLoadedAtMixinSelection(final String modId) {
        return findEarlyModFile(modId) != null;
    }

    @Override
    protected String getModVersionAtMixinSelection(final String modId) {
        final ModFileInfo modFileInfo = findEarlyModFile(modId);
        if (modFileInfo == null) {
            throw new IllegalArgumentException("Mod is not loaded: " + modId);
        }

        for (final IModInfo modInfo : modFileInfo.getMods()) {
            if (modId.equals(modInfo.getModId())) {
                return modInfo.getVersion().toString();
            }
        }
        return modFileInfo.versionString();
    }

    private static ModFileInfo findEarlyModFile(final String modId) {
        final LoadingModList loadingModList = FMLLoader.getLoadingModList();
        return loadingModList == null ? null : loadingModList.getModFileById(modId);
    }
}
