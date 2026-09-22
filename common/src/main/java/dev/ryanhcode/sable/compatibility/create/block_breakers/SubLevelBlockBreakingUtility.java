package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/** Resolves a Sable-contained block-breaking actor against parent-world storage. */
public final class SubLevelBlockBreakingUtility {
    private SubLevelBlockBreakingUtility() {
    }

    public static TargetResolution findExternalTarget(final BiPredicate<BlockPos, BlockState> canBreak,
                                                      final SubLevel owner,
                                                      final Level parentLevel,
                                                      final CreateActorTargetGeometry.ActorSpace actorSpace) {
        if (owner.getLevel() != parentLevel) {
            return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace,
                    Decision.SOURCE_LEVEL_MISMATCH);
        }
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(
                parentLevel, new BoundingBox3d(actorSpace.visibleMiningBounds()))) {
            if (intersecting != owner) {
                return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace,
                        Decision.OTHER_SABLE_BODY_UNSUPPORTED);
            }
        }
        final BoundingBox3i candidateBounds = new BoundingBox3i(actorSpace.visibleMiningBounds());
        final BlockPos visibleActorBlock = BlockPos.containing(actorSpace.visibleCenter());
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        BlockPos closest = null;
        BlockState closestState = Blocks.AIR.defaultBlockState();
        double closestDistanceSqr = Double.MAX_VALUE;
        boolean unloadedCandidate = false;
        boolean rawPlotCandidate = false;

        for (int x = candidateBounds.minX(); x <= candidateBounds.maxX(); x++) {
            for (int z = candidateBounds.minZ(); z <= candidateBounds.maxZ(); z++) {
                for (int y = candidateBounds.minY(); y <= candidateBounds.maxY(); y++) {
                    mutable.set(x, y, z);
                    if (!parentLevel.hasChunkAt(mutable)) {
                        unloadedCandidate = true;
                        continue;
                    }
                    if (mutable.equals(visibleActorBlock)) {
                        continue;
                    }
                    if (Sable.HELPER.getContaining(parentLevel, mutable) != null) {
                        rawPlotCandidate = true;
                        continue;
                    }

                    final BlockState state = parentLevel.getBlockState(mutable);
                    if (!canBreak.test(mutable, state)) {
                        continue;
                    }

                    final double distanceSqr = Vec3.atCenterOf(mutable).distanceToSqr(actorSpace.visibleCenter());
                    if (distanceSqr < closestDistanceSqr) {
                        closest = mutable.immutable();
                        closestState = state;
                        closestDistanceSqr = distanceSqr;
                    }
                }
            }
        }

        if (closest != null) {
            return new TargetResolution(closest, closestState, actorSpace, Decision.PARENT_TARGET_RESOLVED);
        }
        if (rawPlotCandidate) {
            return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace,
                    Decision.OTHER_SABLE_BODY_UNSUPPORTED);
        }
        if (unloadedCandidate) {
            return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace,
                    Decision.PARENT_CHUNK_NOT_LOADED);
        }
        return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace,
                Decision.NO_BREAKABLE_PARENT_TARGET);
    }

    /** Resolves an actor's current physical active point without requiring a breakable block. */
    public static TargetResolution findExternalPointTarget(final SubLevel owner,
                                                           final Level parentLevel,
                                                           final CreateActorTargetGeometry.ActorSpace actorSpace) {
        if (owner.getLevel() != parentLevel) {
            return rejected(actorSpace, Decision.SOURCE_LEVEL_MISMATCH);
        }

        final BlockPos parentTarget = BlockPos.containing(actorSpace.visibleCenter());
        if (!parentLevel.hasChunkAt(parentTarget)) {
            return rejected(actorSpace, Decision.PARENT_CHUNK_NOT_LOADED);
        }
        if (Sable.HELPER.getContaining(parentLevel, parentTarget) != null) {
            return rejected(actorSpace, Decision.OTHER_SABLE_BODY_UNSUPPORTED);
        }

        final BoundingBox3d targetBounds = new BoundingBox3d(new AABB(parentTarget));
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(parentLevel, targetBounds)) {
            if (intersecting != owner) {
                return rejected(actorSpace, Decision.OTHER_SABLE_BODY_UNSUPPORTED);
            }
        }

        return new TargetResolution(parentTarget, parentLevel.getBlockState(parentTarget), actorSpace,
                Decision.PARENT_TARGET_RESOLVED);
    }

    private static TargetResolution rejected(final CreateActorTargetGeometry.ActorSpace actorSpace,
                                             final Decision decision) {
        return new TargetResolution(null, Blocks.AIR.defaultBlockState(), actorSpace, decision);
    }

    public enum Decision {
        PARENT_TARGET_RESOLVED,
        NO_BREAKABLE_PARENT_TARGET,
        PARENT_CHUNK_NOT_LOADED,
        OTHER_SABLE_BODY_UNSUPPORTED,
        SOURCE_LEVEL_MISMATCH
    }

    public record TargetResolution(@Nullable BlockPos target,
                                   BlockState targetState,
                                   CreateActorTargetGeometry.ActorSpace actorSpace,
                                   Decision decision) {
    }
}
