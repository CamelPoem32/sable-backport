package dev.ryanhcode.sable.compatibility.create.block_breakers;

import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves the native Roller's terrain cells without changing its breaking or paving algorithm. */
public final class SableRollerTerrain {
    public static final String EXTERNAL_TARGET = "SableExternalRollerTarget";
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    private SableRollerTerrain() {
    }

    public static SubLevelBlockBreakingUtility.TargetResolution resolve(final MovementContext context,
                                                                          final SubLevel owner,
                                                                          final RollerMovementBehaviour roller) {
        final Vec3 rawActive = context.contraption.entity.toGlobalVector(
                context.localPos.getCenter().add(roller.getActiveAreaOffset(context)), 1.0F);
        final Vec3 rawFacing = context.rotation.apply(Vec3.atLowerCornerOf(
                context.state.getValue(com.simibubi.create.content.contraptions.actors.roller.RollerBlock.FACING)
                        .getNormal()));
        final CreateActorTargetGeometry.ActorSpace space = CreateActorTargetGeometry.resolvePoint(
                owner.logicalPose(), rawActive, rawFacing);
        if (!supportsWorldDown(owner) || context.contraption.entity instanceof CarriageContraptionEntity) {
            return new SubLevelBlockBreakingUtility.TargetResolution(null,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), space,
                    SubLevelBlockBreakingUtility.Decision.UNSUPPORTED_ROLLER_ORIENTATION);
        }
        return SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, space);
    }

    public static boolean supportsWorldDown(final SubLevel owner) {
        return supportsWorldDown(owner.logicalPose());
    }

    public static boolean supportsWorldDown(final Pose3dc pose) {
        return pose.transformNormal(WORLD_UP).normalize().dot(WORLD_UP) > 0.999;
    }

    /** Every native column/layer cell must be valid before it reaches a world query or mutation. */
    public static boolean isSafeCell(final MovementContext context, final SubLevel owner,
                                     final BlockPos parentCell) {
        if (owner.getLevel() != context.world || !context.world.hasChunkAt(parentCell)
                || Sable.HELPER.getContaining(context.world, parentCell) != null) {
            return false;
        }
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(
                context.world, new BoundingBox3d(new AABB(parentCell)))) {
            if (intersecting != owner) {
                return false;
            }
        }
        return true;
    }

    /** Cancel stale progress when outer Sable movement changes the physical terrain cell. */
    public static void retarget(final MovementContext context, final SubLevel owner,
                                final RollerMovementBehaviour roller) {
        if (context.world.isClientSide) {
            return;
        }
        final SubLevelBlockBreakingUtility.TargetResolution resolution = resolve(context, owner, roller);
        final BlockPos previous = readTarget(context);
        final BlockPos next = resolution.target();
        if (ExternalBlockBreakingTargetState.transition(previous, next)
                == ExternalBlockBreakingTargetState.Transition.UNCHANGED
                && (next != null || !hasNativeTerrainState(context))) {
            return;
        }
        if (context.data.contains("BreakingPos")) {
            if (previous != null) {
                roller.cancelStall(context);
            } else {
                context.data.remove("BreakingPos");
                context.data.remove("Progress");
                context.data.remove("TicksUntilNextProgress");
            }
        }
        context.data.remove("ReferencePos");
        context.data.remove("WaitingTicks");
        context.data.remove("LastPos");
        context.stall = false;
        if (next == null) {
            context.data.remove(EXTERNAL_TARGET);
            return;
        }
        roller.visitNewPosition(context, next);
    }

    public static @Nullable BlockPos readTarget(final MovementContext context) {
        return context.data.contains(EXTERNAL_TARGET)
                ? NbtUtils.readBlockPos(context.data.getCompound(EXTERNAL_TARGET)) : null;
    }

    private static boolean hasNativeTerrainState(final MovementContext context) {
        return context.data.contains("BreakingPos") || context.data.contains("ReferencePos")
                || context.data.contains("WaitingTicks") || context.data.contains("LastPos");
    }
}
