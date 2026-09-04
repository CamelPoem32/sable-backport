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

    public static AssemblyException structureTooLarge(final BlockPos startPos, final int visitedCount,
                                                      final int frontierCount, final int limit,
                                                      final BlockPos firstUnexpectedPos,
                                                      final String firstUnexpectedState,
                                                      final BlockPos enteredFromPos,
                                                      final String enteredFromState,
                                                      final String attachmentReason) {
        return new AssemblyException(Component.literal("SABLE_M22_ASSEMBLY_SELECTION_FAIL"
                + " startPos=" + startPos.toShortString()
                + " visitedCount=" + visitedCount
                + " frontierCount=" + frontierCount
                + " limit=" + limit
                + " firstUnexpectedPos=" + describe(firstUnexpectedPos)
                + " firstUnexpectedState=" + firstUnexpectedState
                + " enteredFromPos=" + describe(enteredFromPos)
                + " enteredFromState=" + enteredFromState
                + " attachmentReason=" + attachmentReason));
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

    public static AssemblyException incompleteSource(final int expected, final int actual) {
        return new AssemblyException(Component.literal("DISASSEMBLY_BLOCKED incompleteSource expectedBlocks="
                + expected + " actualBlocks=" + actual));
    }

    public static AssemblyException moveFailed(final RuntimeException exception) {
        return new AssemblyException(Component.literal("DISASSEMBLY_BLOCKED moveBlocksFailed="
                + exception.getClass().getSimpleName() + " message=" + describe(exception)));
    }

    public static AssemblyException assemblyFailed(final String stage, final String nullOwner) {
        return new AssemblyException(Component.literal("ASSEMBLY_FAILED failureStage=" + stage
                + " nullOwner=" + nullOwner));
    }

    private static String describe(final RuntimeException exception) {
        final String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getName() : message;
    }

    private static String describe(final BlockPos pos) {
        return pos == null ? "none" : pos.toShortString();
    }
}
