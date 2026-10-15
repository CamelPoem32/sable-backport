package dev.ryanhcode.sable.forge.event;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/** Synchronous notification after successful assembly and controller state commitment. */
public final class ForgeSableAssemblyCompletedEvent extends Event {
    private final ServerLevel level;
    private final BlockPos assemblerPos;
    private final ServerSubLevel subLevel;
    private final BlockPos assemblyOffset;

    public ForgeSableAssemblyCompletedEvent(final ServerLevel level, final BlockPos assemblerPos,
                                          final ServerSubLevel subLevel, final BlockPos assemblyOffset) {
        this.level = level;
        this.assemblerPos = assemblerPos.immutable();
        this.subLevel = subLevel;
        this.assemblyOffset = assemblyOffset.immutable();
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public BlockPos getAssemblerPos() {
        return this.assemblerPos;
    }

    public ServerSubLevel getSubLevel() {
        return this.subLevel;
    }

    public BlockPos getAssemblyOffset() {
        return this.assemblyOffset;
    }
}
