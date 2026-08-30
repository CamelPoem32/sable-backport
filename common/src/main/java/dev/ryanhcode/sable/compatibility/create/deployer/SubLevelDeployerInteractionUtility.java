package dev.ryanhcode.sable.compatibility.create.deployer;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * M18 coordinate contract: moving Deployer actors keep Create's fake-player
 * semantics, while this helper chooses the storage BlockPos whose visible
 * Sable-local location corresponds to the actor's transformed active area.
 */
public final class SubLevelDeployerInteractionUtility {
    private SubLevelDeployerInteractionUtility() {
    }

    public static BlockPos findInteractionPos(@Nullable final SubLevel subLevel,
                                              final Level level,
                                              final Vec3 activeDirection,
                                              final Vec3 actorStorageCenter,
                                              final BlockPos proposedInteractionPos,
                                              final boolean useMode) {
        if (subLevel == null) {
            return proposedInteractionPos;
        }

        final BlockPos plotCenter = subLevel.getPlot().getCenterBlock();
        final Vec3 actorSubLevelLocalCenter = actorStorageCenter.subtract(Vec3.atLowerCornerOf(plotCenter));
        final Vec3 interactionStorageCenter = proposedInteractionPos.getCenter();
        final Vec3 interactionSubLevelLocalCenter = interactionStorageCenter.subtract(Vec3.atLowerCornerOf(plotCenter));
        final BoundingBox3d ownerLocalInteractionBox = interactionBox(interactionSubLevelLocalCenter);
        final BoundingBox3d ownerStorageInteractionBox = interactionBox(interactionStorageCenter);
        final Vec3 visibleInteractionCenter = subLevel.logicalPose().transformPosition(interactionSubLevelLocalCenter);
        final Candidate best = findBestCandidate(subLevel, level, activeDirection, visibleInteractionCenter,
                proposedInteractionPos, ownerLocalInteractionBox, ownerStorageInteractionBox, useMode);
        if (best.blockPos() != null) {
            log("candidate_selected", subLevel, activeDirection, actorStorageCenter, proposedInteractionPos,
                    best.blockPos(), best.state(), best.distanceSqr(), useMode);
            return best.blockPos();
        }

        final BlockState fallbackState = safeBlockState(level, proposedInteractionPos);
        log("candidate_fallback_original", subLevel, activeDirection, actorStorageCenter, proposedInteractionPos,
                proposedInteractionPos, fallbackState, Double.NaN, useMode);
        return proposedInteractionPos;
    }

    private static BoundingBox3d interactionBox(final Vec3 center) {
        return new BoundingBox3d(new AABB(
                center.x - 0.75,
                center.y - 0.75,
                center.z - 0.75,
                center.x + 0.75,
                center.y + 0.75,
                center.z + 0.75));
    }

    private static Candidate findBestCandidate(final SubLevel owner,
                                               final Level level,
                                               final Vec3 activeDirection,
                                               final Vec3 visibleInteractionCenter,
                                               final BlockPos proposed,
                                               final BoundingBox3d ownerLocalInteractionBox,
                                               final BoundingBox3d ownerStorageInteractionBox,
                                               final boolean useMode) {
        final BoundingBox3d visibleInteractionBox = new BoundingBox3d(ownerLocalInteractionBox);
        visibleInteractionBox.transform(owner.logicalPose(), visibleInteractionBox);

        Candidate best = collectStorage(owner, level, activeDirection, visibleInteractionCenter, proposed,
                new BoundingBox3i(ownerStorageInteractionBox), useMode, Candidate.NONE);

        final BoundingBox3d otherLocalBox = new BoundingBox3d();
        for (final SubLevel other : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(visibleInteractionBox))) {
            if (owner == other) {
                continue;
            }
            visibleInteractionBox.transformInverse(other.logicalPose(), otherLocalBox);
            best = collectSubLevelLocal(other, level, activeDirection, visibleInteractionCenter, proposed,
                    new BoundingBox3i(otherLocalBox), useMode, best);
        }
        return best;
    }

    private static Candidate collectStorage(final SubLevel storage,
                                            final Level level,
                                            final Vec3 activeDirection,
                                            final Vec3 visibleInteractionCenter,
                                            final BlockPos proposed,
                                            final BoundingBox3i storageBounds,
                                            final boolean useMode,
                                            final Candidate startingBest) {
        Candidate best = startingBest;
        final BlockPos.MutableBlockPos mutableStorage = new BlockPos.MutableBlockPos();
        for (int x = storageBounds.minX(); x <= storageBounds.maxX(); x++) {
            for (int y = storageBounds.minY(); y <= storageBounds.maxY(); y++) {
                for (int z = storageBounds.minZ(); z <= storageBounds.maxZ(); z++) {
                    mutableStorage.set(x, y, z);
                    final BlockPos raw = mutableStorage.immutable();
                    final BlockState state = safeBlockState(level, raw);
                    if (!validInteractionTarget(level, raw, state, useMode, activeDirection)) {
                        continue;
                    }
                    final double distanceSqr = visibleCenter(storage, raw).distanceToSqr(visibleInteractionCenter);
                    if (distanceSqr < best.distanceSqr()) {
                        best = new Candidate(raw, state, distanceSqr);
                    }
                }
            }
        }

        final BlockState proposedState = safeBlockState(level, proposed);
        if (best.blockPos() == null && validInteractionTarget(level, proposed, proposedState, useMode, activeDirection)) {
            return new Candidate(proposed, proposedState, visibleCenter(storage, proposed).distanceToSqr(visibleInteractionCenter));
        }
        return best;
    }

    private static Candidate collectSubLevelLocal(final SubLevel storage,
                                                  final Level level,
                                                  final Vec3 activeDirection,
                                                  final Vec3 visibleInteractionCenter,
                                                  final BlockPos proposed,
                                                  final BoundingBox3i localBounds,
                                                  final boolean useMode,
                                                  final Candidate startingBest) {
        final BoundingBox3i storageBounds = new BoundingBox3i(localBounds);
        storageBounds.move(storage.getPlot().getCenterBlock().getX(), storage.getPlot().getCenterBlock().getY(),
                storage.getPlot().getCenterBlock().getZ(), storageBounds);
        return collectStorage(storage, level, activeDirection, visibleInteractionCenter, proposed,
                storageBounds, useMode, startingBest);
    }

    private static Vec3 visibleCenter(final SubLevel storage, final BlockPos raw) {
        return storage.logicalPose().transformPosition(raw.subtract(storage.getPlot().getCenterBlock()).getCenter());
    }

    private static BlockState safeBlockState(final Level level, final BlockPos pos) {
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    private static boolean loaded(final Level level, final BlockPos pos) {
        return level.hasChunkAt(pos);
    }

    private static boolean safeNonAir(final Level level, final BlockPos pos) {
        return loaded(level, pos) && !level.getBlockState(pos).isAir();
    }

    private static boolean safeShapePresent(final Level level, final BlockPos pos, final BlockState state) {
        return loaded(level, pos) && !state.getShape(level, pos).isEmpty();
    }

    private static boolean sameLoadedChunk(final Level level, final BlockPos pos) {
        return loaded(level, pos);
    }

    private static boolean storageQueryCanGenerate(final Level level, final BlockPos pos) {
        return !sameLoadedChunk(level, pos);
    }

    private static boolean canInspect(final Level level, final BlockPos pos) {
        return !storageQueryCanGenerate(level, pos);
    }

    private static boolean safeUseSupport(final Level level, final BlockPos pos, final Vec3 activeDirection) {
        if (!canInspect(level, pos)) {
            return false;
        }
        final Direction face = Direction.getNearest(activeDirection.x, activeDirection.y, activeDirection.z).getOpposite();
        return safeNonAir(level, pos.relative(face)) || safeNonAir(level, pos.below());
    }

    private static boolean validInteractionTarget(final Level level,
                                                  final BlockPos pos,
                                                  final BlockState state,
                                                  final boolean useMode,
                                                  final Vec3 activeDirection) {
        if (!useMode) {
            return safeShapePresent(level, pos, state);
        }
        if (!state.isAir()) {
            return true;
        }
        return safeUseSupport(level, pos, activeDirection);
    }

    private static void log(final String phase,
                            final SubLevel subLevel,
                            final Vec3 activeDirection,
                            final Vec3 actorCenter,
                            final BlockPos proposed,
                            @Nullable final BlockPos selected,
                            final BlockState selectedState,
                            final double distanceSqr,
                            final boolean useMode) {
        final BlockPos plotCenter = subLevel.getPlot().getCenterBlock();
        Sable.LOGGER.info("SABLE_M18_DEPLOYER phase={} subLevel={} mode={} actorCenterStorage={} actorCenterFixtureLocal={} activeDirection={} proposedRaw={} proposedFixtureLocal={} proposedChunkLoaded={} selectedRaw={} selectedFixtureLocal={} selectedChunkLoaded={} blockId={} distanceSqr={} wouldGenerateChunk=false",
                phase,
                subLevel.getUniqueId(),
                useMode ? "USE" : "PUNCH",
                formatVec(actorCenter),
                formatVec(actorCenter.subtract(Vec3.atLowerCornerOf(plotCenter))),
                formatVec(activeDirection),
                formatBlockPos(proposed),
                formatBlockPos(proposed.subtract(plotCenter)),
                loaded(subLevel.getLevel(), proposed),
                formatBlockPos(selected),
                selected == null ? "null" : formatBlockPos(selected.subtract(plotCenter)),
                selected != null && loaded(subLevel.getLevel(), selected),
                BuiltInRegistries.BLOCK.getKey(selectedState.getBlock()),
                Double.isFinite(distanceSqr) ? String.format(java.util.Locale.ROOT, "%.6f", distanceSqr) : "nan");
    }

    private static String formatBlockPos(@Nullable final BlockPos pos) {
        return pos == null ? "null" : "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatVec(final Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)", vec.x, vec.y, vec.z);
    }

    private record Candidate(@Nullable BlockPos blockPos, BlockState state, double distanceSqr) {
        private static final Candidate NONE = new Candidate(null, Blocks.AIR.defaultBlockState(), Double.MAX_VALUE);
    }
}
