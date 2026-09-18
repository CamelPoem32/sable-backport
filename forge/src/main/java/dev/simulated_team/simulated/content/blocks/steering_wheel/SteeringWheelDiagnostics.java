package dev.simulated_team.simulated.content.blocks.steering_wheel;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Bounded lifecycle evidence for production steering wheels on assembled Sables. */
public final class SteeringWheelDiagnostics {
    private static final int MAX_REMOVALS = 64;
    private static final Map<Key, RemovalSnapshot> REMOVALS = new LinkedHashMap<>();
    private static final Map<Key, KineticDestruction> KINETIC_DESTRUCTIONS = new LinkedHashMap<>();

    private SteeringWheelDiagnostics() {
    }

    public static void recordRemoval(final Level level, final BlockPos rawPos, final BlockState oldState,
                                     final BlockState newState, final boolean movedByPiston,
                                     final SteeringWheelBlockEntity wheel) {
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
        final KineticDestruction kinetic;
        synchronized (KINETIC_DESTRUCTIONS) {
            kinetic = KINETIC_DESTRUCTIONS.get(new Key(owner.getUniqueId(), localPos));
        }
        final boolean createDestroyed = kinetic != null && level.getGameTime() - kinetic.gameTime() <= 1;
        final String phase = expectedTransfer ? "BLOCK_TRANSFER" : "UNEXPECTED_BLOCK_REMOVED";
        final String ownerName = expectedTransfer ? "M22_ASSEMBLY_DISASSEMBLY"
                : createDestroyed ? kinetic.ownerMethod() : "UNKNOWN_RUNTIME_OWNER";
        final String cause = expectedTransfer ? "PISTON_MOVE:SABLE_TRANSFER"
                : createDestroyed ? "KINETIC_CONFLICT"
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

    public static void recordKineticDestruction(final Level level, final BlockPos rawPos,
                                                final KineticBlockEntity source, final String ownerMethod,
                                                final BlockPos incomingSource,
                                                final float incomingSpeed, final float existingSpeed,
                                                final String conflictType) {
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final SubLevel owner = Sable.HELPER.getContaining(level, rawPos);
        if (owner == null) {
            return;
        }
        final List<SteeringWheelBlockEntity> activeWheels = SimAssemblyHelper.collectBlocks(serverLevel, owner).stream()
                .map(level::getBlockEntity)
                .filter(SteeringWheelBlockEntity.class::isInstance)
                .map(SteeringWheelBlockEntity.class::cast)
                .filter(wheel -> wheel.isHeld() || wheel.getGeneratedSpeed() != 0.0F)
                .toList();
        if (activeWheels.isEmpty()) {
            return;
        }
        final BlockPos localPos = rawPos.subtract(owner.getPlot().getCenterBlock());
        final KineticNetwork network = source.hasNetwork() ? source.getOrCreateNetwork() : null;
        final List<String> members = network == null ? List.of() : network.members.keySet().stream()
                .map(member -> member.getBlockPos().subtract(owner.getPlot().getCenterBlock()) + "@"
                + ForgeRegistries.BLOCKS.getKey(level.getBlockState(member.getBlockPos()).getBlock()))
                .sorted().toList();
        final List<String> generators = network == null ? List.of() : network.sources.keySet().stream()
                .map(member -> member.getBlockPos().subtract(owner.getPlot().getCenterBlock()) + "@"
                + ForgeRegistries.BLOCKS.getKey(level.getBlockState(member.getBlockPos()).getBlock()))
                .sorted().toList();
        synchronized (KINETIC_DESTRUCTIONS) {
            final Key key = new Key(owner.getUniqueId(), localPos);
            final KineticDestruction previous = KINETIC_DESTRUCTIONS.get(key);
            if (previous != null && previous.gameTime() == level.getGameTime()) {
                return;
            }
            KINETIC_DESTRUCTIONS.put(key,
                    new KineticDestruction(ownerMethod, level.getGameTime()));
            while (KINETIC_DESTRUCTIONS.size() > MAX_REMOVALS) {
                KINETIC_DESTRUCTIONS.remove(KINETIC_DESTRUCTIONS.keySet().iterator().next());
            }
        }
        for (final SteeringWheelBlockEntity wheel : activeWheels) {
            Sable.LOGGER.info("SABLE_M28_KINETIC_CONFLICT sableId={} wheelLocalPos={} destroyedLocalPos={}"
                            + " destroyedBlockId={} incomingSource={} incomingSpeed={} existingSource={}"
                            + " existingSpeed={} networkIdBefore={} networkMembersBefore={}"
                            + " generatorMembersBefore={} conflictType={} exactCreateOwnerMethod={}",
                    owner.getUniqueId(), wheel.getBlockPos().subtract(owner.getPlot().getCenterBlock()),
                    localPos, ForgeRegistries.BLOCKS.getKey(level.getBlockState(rawPos).getBlock()),
                    incomingSource == null ? "unavailable"
                            : incomingSource.subtract(owner.getPlot().getCenterBlock()), incomingSpeed,
                    source.getBlockPos().subtract(owner.getPlot().getCenterBlock()), existingSpeed,
                    source.network, members, generators, conflictType, ownerMethod);
        }
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

    private record KineticDestruction(String ownerMethod, long gameTime) {
    }
}
