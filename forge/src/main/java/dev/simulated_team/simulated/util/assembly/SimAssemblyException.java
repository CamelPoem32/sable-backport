package dev.simulated_team.simulated.util.assembly;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.simulated_team.simulated.index.SimulatedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

public final class SimAssemblyException {

    private SimAssemblyException() {
    }

    public static AssemblyException structureTooLarge() {
        return new AssemblyException(Component.literal("Simulated M22 structure exceeds maxBlocksMoved="
                + SimulatedConfig.M22_MAX_BLOCKS_MOVED.get()));
    }

    public static AssemblyException unmovableBlock(final BlockPos pos, final BlockState state) {
        return new AssemblyException(Component.literal("Simulated M22 cannot move "
                + state.getBlock().builtInRegistryHolder().key().location() + " at " + pos.toShortString()));
    }

    public static AssemblyException sameSubLevel() {
        return new AssemblyException(Component.literal("Simulated M22 assembler target is already in the same sub-level."));
    }

    public static AssemblyException tooFast() {
        return new AssemblyException(Component.literal("Simulated M22 disassembly blocked: sub-level is moving too fast."));
    }

    public static AssemblyException occupied(final Collection<BlockPos> positions) {
        return new AssemblyException(Component.literal("DISASSEMBLY_BLOCKED occupied=" + positions));
    }

    public static AssemblyException activeConstraint(final Collection<String> logicalIds) {
        return new AssemblyException(Component.literal("DISASSEMBLY_BLOCKED activeConstraints=" + logicalIds));
    }
}
