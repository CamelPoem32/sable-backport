package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import net.minecraft.world.level.Level;

public final class SableAssemblyPlatformImpl implements SableAssemblyPlatform {
    @Override
    public void setIgnoreOnPlace(final Level level, final boolean ignore) {
        level.captureBlockSnapshots = ignore;
    }
}
