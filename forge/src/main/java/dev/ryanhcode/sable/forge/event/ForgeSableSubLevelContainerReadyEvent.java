package dev.ryanhcode.sable.forge.event;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

public final class ForgeSableSubLevelContainerReadyEvent extends Event {
    private final Level level;
    private final SubLevelContainer container;

    public ForgeSableSubLevelContainerReadyEvent(final Level level, final SubLevelContainer container) {
        this.level = level;
        this.container = container;
    }

    public Level getLevel() {
        return this.level;
    }

    public SubLevelContainer getContainer() {
        return this.container;
    }
}
