package dev.ryanhcode.sable.compatibility.create.funnel;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.compatibility.create.portable_interface.SablePortableInterfaceTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Resolves only the physical active cell of a Sable-hosted moving Create Funnel. */
public final class SableMovingFunnelTarget {
    private SableMovingFunnelTarget() {
    }

    public static Result resolve(final MovementContext context, final SubLevel owner) {
        if (owner.getLevel() != context.world || context.position == null || context.rotation == null) {
            return new Result(null, null, Decision.INVALID_CONTEXT);
        }
        final Direction localFacing = FunnelBlock.getFunnelFacing(context.state);
        final Vec3 rawFacing = context.rotation.apply(Vec3.atLowerCornerOf(localFacing.getNormal()));
        final Result geometry = resolveGeometry(owner.logicalPose(), context.position, rawFacing);
        if (!geometry.accepted()) {
            return geometry;
        }
        final BlockPos parentBlock = geometry.parentBlock();
        if (!context.world.hasChunkAt(parentBlock)) {
            return new Result(null, null, Decision.PARENT_CHUNK_UNLOADED);
        }
        if (Sable.HELPER.getContaining(context.world, parentBlock) != null) {
            return new Result(null, null, Decision.HIDDEN_PLOT_TARGET);
        }
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(context.world,
                new BoundingBox3d(new AABB(parentBlock)))) {
            if (intersecting != owner) {
                return new Result(null, null, Decision.OTHER_SABLE_BODY);
            }
        }
        return new Result(parentBlock, geometry.parentFacing(), Decision.PARENT_CELL);
    }

    public static Result resolveGeometry(final Pose3dc pose, final Vec3 rawActivePoint, final Vec3 rawFacing) {
        final SablePortableInterfaceTarget.Result geometry =
                SablePortableInterfaceTarget.resolveGeometry(pose, rawActivePoint, rawFacing);
        if (!geometry.accepted()) {
            return new Result(null, null, geometry.decision()
                    == SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION
                    ? Decision.UNSUPPORTED_ORIENTATION : Decision.INVALID_CONTEXT);
        }
        return new Result(geometry.parentBlock(), geometry.parentFacing(), Decision.PARENT_CELL);
    }

    public enum Decision {
        PARENT_CELL,
        INVALID_CONTEXT,
        UNSUPPORTED_ORIENTATION,
        PARENT_CHUNK_UNLOADED,
        HIDDEN_PLOT_TARGET,
        OTHER_SABLE_BODY
    }

    public record Result(@Nullable BlockPos parentBlock, @Nullable Direction parentFacing, Decision decision) {
        public boolean accepted() {
            return this.decision == Decision.PARENT_CELL;
        }
    }
}
