package dev.simulated_team.simulated.util;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import dev.simulated_team.simulated.util.assembly.SimAssemblyException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.List;

public final class SimAssemblyHelper {

    private SimAssemblyHelper() {
    }

    public static AssemblyResult assembleFromSingleBlock(final Level level, final BlockPos selfPos,
                                                         final BlockPos toAssemble, final boolean includeStart)
            throws AssemblyException {
        if (!(level instanceof final ServerLevel serverLevel) || level.getBlockState(toAssemble).isAir()) {
            return null;
        }

        final SimAssemblyContraption contraption = new SimAssemblyContraption(includeStart ? null : selfPos);
        contraption.searchMovedStructure(level, toAssemble);

        final Collection<BlockPos> blocks = contraption.getBlocks();
        if (blocks.isEmpty()) {
            return null;
        }

        final BoundingBox3i bounds = BoundingBox3i.from(blocks);
        final BlockPos anchor = blocks.stream().findFirst().orElseThrow();
        final Vec3 visibleAnchorBefore = anchor.getCenter();
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchor, blocks, bounds);
        if (subLevel == null) {
            return null;
        }

        final BlockPos rawAnchor = subLevel.getPlot().getCenterBlock();
        final Vector3d visibleAnchorAfter = subLevel.logicalPose().transformPosition(
                new Vector3d(rawAnchor.getX() + 0.5D, rawAnchor.getY() + 0.5D, rawAnchor.getZ() + 0.5D));
        final double visibleDelta = visibleAnchorAfter.distance(visibleAnchorBefore.x, visibleAnchorBefore.y, visibleAnchorBefore.z);
        return new AssemblyResult(subLevel, rawAnchor.subtract(anchor), contraption.getCheckedBlocks(),
                blocks.size(), contraption.getRejectedBlocks(), contraption.getLastRejectedReason(),
                bounds, visibleDelta);
    }

    public static DisassemblyResult disassembleSubLevel(final ServerLevel level, final ServerSubLevel subLevel,
                                                        final BlockPos subLevelAnchor,
                                                        final BlockPos disassemblyGoal,
                                                        final Rotation rotation) throws AssemblyException {
        throwDisassemblyExceptions(level, subLevel);

        final BoundingBox3i plotBounds = new BoundingBox3i(subLevel.getPlot().getBoundingBox());
        final List<BlockPos> blocks = collectBlocks(level, subLevel);
        if (blocks.isEmpty()) {
            return new DisassemblyResult(0, 0, List.of(), false, plotBounds);
        }

        final SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(subLevelAnchor, disassemblyGoal,
                        rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal(), rotation, level);
        final List<BlockPos> occupied = findOccupiedRestoreSpace(level, blocks, transform);
        if (!occupied.isEmpty()) {
            throw SimAssemblyException.occupied(occupied);
        }

        final int blockEntityCount = countBlockEntities(level, blocks);
        ((ServerLevelPlot) subLevel.getPlot()).kickAllEntities();
        SubLevelAssemblyHelper.moveBlocks(level, transform, blocks);
        SubLevelAssemblyHelper.moveTrackingPoints(level, plotBounds, null, transform);

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }

        return new DisassemblyResult(blocks.size(), blockEntityCount, List.of(), true, plotBounds);
    }

    public static List<BlockPos> collectBlocks(final ServerLevel level, final SubLevel subLevel) {
        final ObjectArrayList<BlockPos> blocks = new ObjectArrayList<>();
        final LevelPlot plot = subLevel.getPlot();
        for (final PlotChunkHolder chunk : plot.getLoadedChunks()) {
            final BoundingBox3ic localChunkBounds = chunk.getBoundingBox();
            if (localChunkBounds == null || localChunkBounds == BoundingBox3i.EMPTY) {
                continue;
            }

            for (int x = localChunkBounds.minX(); x <= localChunkBounds.maxX(); x++) {
                for (int y = localChunkBounds.minY(); y <= localChunkBounds.maxY(); y++) {
                    for (int z = localChunkBounds.minZ(); z <= localChunkBounds.maxZ(); z++) {
                        final BlockPos pos = new BlockPos(
                                x + chunk.getPos().getMinBlockX(),
                                y,
                                z + chunk.getPos().getMinBlockZ());
                        final BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    public static BlockPos currentVisibleBlockPos(final SubLevel subLevel, final BlockPos rawPos) {
        final Vector3d visible = subLevel.logicalPose()
                .transformPosition(new Vector3d(rawPos.getX() + 0.5D, rawPos.getY() + 0.5D, rawPos.getZ() + 0.5D));
        return BlockPos.containing(visible.x, visible.y, visible.z);
    }

    private static void throwDisassemblyExceptions(final ServerLevel level, final ServerSubLevel subLevel)
            throws AssemblyException {
        final List<String> activeConstraints = findActiveSpringConstraints(level, subLevel);
        if (!activeConstraints.isEmpty()) {
            throw SimAssemblyException.activeConstraint(activeConstraints);
        }

        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null) {
            return;
        }

        final double linear = handle.getLinearVelocity(new Vector3d()).lengthSquared();
        final double angular = handle.getAngularVelocity(new Vector3d()).lengthSquared();
        if (linear > Mth.square(SimulatedConfig.M22_DISASSEMBLY_MAX_VELOCITY.get().floatValue())
                || angular > Mth.square(SimulatedConfig.M22_DISASSEMBLY_MAX_ANGULAR_VELOCITY.get().floatValue())) {
            throw SimAssemblyException.tooFast();
        }

        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        if (physicsSystem != null && !physicsSystem.getPipeline().isBodyRegistered(subLevel)) {
            Sable.LOGGER.warn("M22 disassembling sub-level without registered physics body: {}", subLevel);
        }
    }

    private static List<String> findActiveSpringConstraints(final ServerLevel level, final ServerSubLevel subLevel) {
        final ObjectArrayList<String> active = new ObjectArrayList<>();
        for (final BlockPos block : collectBlocks(level, subLevel)) {
            if (level.getBlockEntity(block) instanceof final SpringBlockEntity spring
                    && spring.isActiveConstraint()) {
                active.add(spring.logicalConstraintId());
            }
        }
        return active;
    }

    private static List<BlockPos> findOccupiedRestoreSpace(final ServerLevel level, final Iterable<BlockPos> blocks,
                                                           final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final ObjectArrayList<BlockPos> occupied = new ObjectArrayList<>();
        for (final BlockPos raw : blocks) {
            final BlockPos target = transform.apply(raw);
            if (!level.getBlockState(target).isAir()) {
                occupied.add(target);
            }
        }
        return occupied;
    }

    private static int countBlockEntities(final ServerLevel level, final Iterable<BlockPos> blocks) {
        int count = 0;
        for (final BlockPos block : blocks) {
            if (level.getBlockEntity(block) != null) {
                count++;
            }
        }
        return count;
    }

    public static Rotation rotationFrom90DegRots(final int rots) {
        return switch (Math.floorMod(rots, 4)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.COUNTERCLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.CLOCKWISE_90;
            default -> throw new AssertionError();
        };
    }

    public record AssemblyResult(ServerSubLevel subLevel, BlockPos offset, int candidateBlocks,
                                 int acceptedBlocks, int rejectedBlocks, String rejectedReason,
                                 BoundingBox3i bounds, double visibleDeltaAfterAssembly) {
    }

    public record DisassemblyResult(int restoredBlockCount, int restoredBlockEntityCount,
                                    Collection<BlockPos> missingBlocks, boolean oldSableRemoved,
                                    BoundingBox3i oldRawBounds) {
    }
}
