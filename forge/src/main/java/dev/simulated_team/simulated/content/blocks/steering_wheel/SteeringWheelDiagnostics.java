package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded lifecycle evidence for production steering wheels on assembled Sables. */
public final class SteeringWheelDiagnostics {
    private static final int MAX_REMOVALS = 64;
    private static final Map<Key, RemovalSnapshot> REMOVALS = new LinkedHashMap<>();

    private SteeringWheelDiagnostics() {
    }

    public static void recordRemoval(final Level level, final BlockPos rawPos, final BlockState oldState,
                                     final BlockState newState, final boolean movedByPiston,
                                     final SteeringWheelBlockEntity wheel) {
        if (!SableDiagnosticFlags.TRACE_STEERING) {
            return;
        }
        final SubLevel owner = Sable.HELPER.getContaining(level, rawPos);
        if (owner == null) {
            return;
        }
        final BlockPos localPos = rawPos.subtract(owner.getPlot().getCenterBlock());
        final boolean opposingSource = wheel != null && wheel.hasSource()
                && wheel.getSpeed() != 0.0F && wheel.getGeneratedSpeed() != 0.0F
                && Math.abs(wheel.getSpeed()) >= Math.abs(wheel.getGeneratedSpeed())
                && Math.signum(wheel.getSpeed()) != Math.signum(wheel.getGeneratedSpeed());
        final boolean expectedTransfer = movedByPiston;
        final String phase = expectedTransfer ? "BLOCK_TRANSFER" : "UNEXPECTED_BLOCK_REMOVED";
        final String ownerName = expectedTransfer ? "M22_ASSEMBLY_DISASSEMBLY" : "UNKNOWN_RUNTIME_OWNER";
        final String cause = expectedTransfer ? "PISTON_MOVE:SABLE_TRANSFER"
                : !oldState.canSurvive(level, rawPos) ? "SUPPORT_LOST"
                : opposingSource ? "POSSIBLE_CREATE_OPPOSING_SOURCE_CONFLICT" : "UNKNOWN";
        final RemovalSnapshot snapshot = new RemovalSnapshot(phase, ownerName, cause, oldState.toString(),
                newState.toString(), level.getGameTime());
        synchronized (REMOVALS) {
            REMOVALS.put(new Key(owner.getUniqueId(), localPos.immutable()), snapshot);
            while (REMOVALS.size() > MAX_REMOVALS) {
                REMOVALS.remove(REMOVALS.keySet().iterator().next());
            }
        }
        Sable.LOGGER.info("SABLE_M28_STEERING_WHEEL phase={} owner={} sableId={} localPos={}"
                        + " oldState={} newState={} cause={} gameTime={}",
                phase, ownerName, owner.getUniqueId(), localPos, oldState, newState, cause, level.getGameTime());
    }

    public static String removalReason(final UUID sableId, final BlockPos localPos) {
        final RemovalSnapshot snapshot;
        synchronized (REMOVALS) {
            snapshot = REMOVALS.get(new Key(sableId, localPos));
        }
        return snapshot == null ? "none"
                : snapshot.phase() + ":" + snapshot.owner() + ":" + snapshot.cause() + "@" + snapshot.gameTime();
    }

    private record Key(UUID sableId, BlockPos localPos) {
    }

    private record RemovalSnapshot(String phase, String owner, String cause, String oldState, String newState,
                                   long gameTime) {
    }
}
