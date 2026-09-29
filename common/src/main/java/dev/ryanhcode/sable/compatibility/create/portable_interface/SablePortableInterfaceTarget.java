package dev.ryanhcode.sable.compatibility.create.portable_interface;

import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves Create's active PSI/PFI search cell in the physical parent world. */
public final class SablePortableInterfaceTarget {
    private static final double MIN_UP_DOT = 0.999;
    private static final double CREATE_FACING_ERROR_SQR = 0.5;

    private SablePortableInterfaceTarget() {
    }

    public static Result resolve(final MovementContext context, final SubLevel owner) {
        final Level parent = context.world;
        if (owner.getLevel() != parent || context.position == null || context.rotation == null
                || !context.state.hasProperty(PortableStorageInterfaceBlock.FACING)) {
            return new Result(null, null, Decision.INVALID_CONTEXT);
        }
        final Direction localFacing = context.state.getValue(PortableStorageInterfaceBlock.FACING);
        final Vec3 innerFacing = context.rotation.apply(Vec3.atLowerCornerOf(localFacing.getNormal()));
        if (innerFacing == null) {
            return new Result(null, null, Decision.INVALID_CONTEXT);
        }
        final Result geometry = resolveGeometry(owner.logicalPose(), context.position, innerFacing);
        if (!geometry.accepted()) {
            return geometry;
        }
        final BlockPos parentBlock = geometry.parentBlock();
        final Direction parentFacing = geometry.parentFacing();
        final BlockPos nextBlock = parentBlock.relative(parentFacing);
        if (!parent.hasChunkAt(parentBlock) || !parent.hasChunkAt(nextBlock)) {
            return new Result(null, null, Decision.PARENT_CHUNK_UNLOADED);
        }
        if (Sable.HELPER.getContaining(parent, parentBlock) != null
                || Sable.HELPER.getContaining(parent, nextBlock) != null) {
            return new Result(null, null, Decision.HIDDEN_PLOT_TARGET);
        }
        final AABB candidateBounds = new AABB(parentBlock).minmax(new AABB(nextBlock));
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(parent,
                new BoundingBox3d(candidateBounds))) {
            if (intersecting != owner) {
                return new Result(null, null, Decision.OTHER_SABLE_BODY);
            }
        }
        return new Result(parentBlock, parentFacing, Decision.PARENT_CANDIDATE);
    }

    public static Result resolveGeometry(final Pose3dc pose, final Vec3 rawActivePoint, final Vec3 innerFacing) {
        if (pose == null || rawActivePoint == null || innerFacing == null || innerFacing.lengthSqr() == 0) {
            return new Result(null, null, Decision.INVALID_CONTEXT);
        }
        final Vec3 visibleUp = pose.transformNormal(new Vec3(0, 1, 0));
        if (visibleUp.y <= MIN_UP_DOT) {
            return new Result(null, null, Decision.UNSUPPORTED_ORIENTATION);
        }
        final Vec3 visibleFacing = pose.transformNormal(innerFacing).normalize();
        final Direction parentFacing = Direction.getNearest(visibleFacing.x, visibleFacing.y, visibleFacing.z);
        if (visibleFacing.distanceToSqr(Vec3.atLowerCornerOf(parentFacing.getNormal())) > CREATE_FACING_ERROR_SQR) {
            return new Result(null, null, Decision.UNSUPPORTED_ORIENTATION);
        }
        return new Result(BlockPos.containing(pose.transformPosition(rawActivePoint)), parentFacing,
                Decision.PARENT_CANDIDATE);
    }

    public enum Decision {
        PARENT_CANDIDATE,
        INVALID_CONTEXT,
        UNSUPPORTED_ORIENTATION,
        PARENT_CHUNK_UNLOADED,
        HIDDEN_PLOT_TARGET,
        OTHER_SABLE_BODY
    }

    public record Result(BlockPos parentBlock, Direction parentFacing, Decision decision) {
        public boolean accepted() {
            return this.decision == Decision.PARENT_CANDIDATE;
        }
    }
}
