package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * M16 coordinate contract: actor coordinates enter here as Sable logical-local
 * storage coordinates and leave as real Level storage coordinates that Create's
 * BreakingPos/progress/breaker-id machinery can consume unchanged.
 */
public final class SubLevelBlockBreakingUtility {
    private SubLevelBlockBreakingUtility() {
    }

    public static BlockPos findBreakingPos(final BiPredicate<BlockPos, BlockState> canBreak,
                                           @Nullable final SubLevel subLevel,
                                           final Level level,
                                           final Vec3 drillFacingVec,
                                           final Vec3 center,
                                           final BlockPos proposedBreakingPos) {
        if (subLevel == null) {
            return proposedBreakingPos;
        }

        final double scaleDown = 2.0 / 16.0;
        final BoundingBox3d localMiningBox = new BoundingBox3d(new AABB(center.x - 0.5,
                center.y - 0.5,
                center.z - 0.5,
                center.x + 0.5,
                center.y + 0.5,
                center.z + 0.5).inflate(-scaleDown).move(drillFacingVec.scale(12.0 / 16.0 - scaleDown)));

        final BoundingBox3d visibleMiningBox = new BoundingBox3d(localMiningBox);
        visibleMiningBox.transform(subLevel.logicalPose(), visibleMiningBox);

        final BoundingBox3i blockMiningBox = new BoundingBox3i(visibleMiningBox);
        final BoundingBox3d otherLocalMiningBox = new BoundingBox3d();
        final ObjectList<BlockPos> candidates = new ObjectArrayList<>();

        logSearch("candidate_search_begin", level, subLevel, center, drillFacingVec, proposedBreakingPos, null,
                "miningBox=[" + blockMiningBox.minX() + "," + blockMiningBox.minY() + "," + blockMiningBox.minZ()
                        + "]->[" + blockMiningBox.maxX() + "," + blockMiningBox.maxY() + "," + blockMiningBox.maxZ() + "]");

        collectBlocksInBounds(canBreak, level, BlockPos.containing(center), blockMiningBox, candidates);

        for (final SubLevel otherSubLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(visibleMiningBox))) {
            if (subLevel == otherSubLevel) {
                continue;
            }

            visibleMiningBox.transformInverse(otherSubLevel.logicalPose(), otherLocalMiningBox);
            blockMiningBox.set(otherLocalMiningBox);

            collectBlocksInBounds(canBreak, level, BlockPos.containing(center), blockMiningBox, candidates);
        }

        BlockPos closestPosition = proposedBreakingPos;
        double closestDistanceSqr = Double.MAX_VALUE;

        for (final BlockPos candidate : candidates) {
            if (Sable.HELPER.getContaining(level, candidate) == subLevel) {
                logSearch("same_owner_rejected", level, subLevel, center, drillFacingVec, proposedBreakingPos,
                        candidate, "");
                continue;
            }

            final Vec3 candidateCenter = Vec3.atCenterOf(candidate);
            final double distanceSqr = Sable.HELPER.distanceSquaredWithSubLevels(level, center, candidateCenter);

            if (distanceSqr < closestDistanceSqr) {
                closestDistanceSqr = distanceSqr;
                closestPosition = candidate;
            }
        }

        final BlockState closestState = level.getBlockState(closestPosition);
        logSearch("candidate_selected", level, subLevel, center, drillFacingVec, proposedBreakingPos, closestPosition,
                "distanceSqr=" + closestDistanceSqr
                        + " blockId=" + BuiltInRegistries.BLOCK.getKey(closestState.getBlock())
                        + " state=" + closestState);
        return closestPosition;
    }

    private static void collectBlocksInBounds(final BiPredicate<BlockPos, BlockState> canBreak,
                                              final Level level,
                                              final BlockPos drillPos,
                                              final BoundingBox3i blockMiningBox,
                                              final ObjectList<BlockPos> candidates) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = blockMiningBox.minX(); x <= blockMiningBox.maxX(); x++) {
            for (int z = blockMiningBox.minZ(); z <= blockMiningBox.maxZ(); z++) {
                for (int y = blockMiningBox.minY(); y <= blockMiningBox.maxY(); y++) {
                    mutable.set(x, y, z);
                    final BlockState state = level.getBlockState(mutable);

                    if (canBreak.test(mutable, state) && !mutable.equals(drillPos)) {
                        candidates.add(mutable.immutable());
                    }
                }
            }
        }
    }

    private static void logSearch(final String phase,
                                  final Level level,
                                  final SubLevel subLevel,
                                  final Vec3 actorCenter,
                                  final Vec3 drillDirection,
                                  final BlockPos proposed,
                                  @Nullable final BlockPos selected,
                                  final String detail) {
        final BlockPos plotCenter = subLevel.getPlot().getCenterBlock();
        final String selectedLocal = selected == null ? "none"
                : Sable.HELPER.getContaining(level, selected) == subLevel
                ? formatBlockPos(selected.subtract(plotCenter))
                : "external_or_parent";
        Sable.LOGGER.info("SABLE_M16_BREAK phase={} subLevel={} actorCenterSubLevelLocal={} drillDirection={} proposedRaw={} proposedFixtureLocal={} selectedRaw={} selectedFixtureLocal={} {}",
                phase,
                subLevel.getUniqueId(),
                formatVec(actorCenter),
                formatVec(drillDirection),
                formatBlockPos(proposed),
                formatBlockPos(proposed.subtract(plotCenter)),
                formatBlockPos(selected),
                selectedLocal,
                detail);
    }

    private static String formatBlockPos(@Nullable final BlockPos pos) {
        return pos == null ? "null" : "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatVec(final Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)", vec.x, vec.y, vec.z);
    }
}
