package dev.ryanhcode.sable.compatibility.create.dispenser;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.compatibility.create.portable_interface.SablePortableInterfaceTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Physical source cell and facing for an audited mounted Dropper/Dispenser behavior. */
public final class SableMountedDispenserTarget {
    private SableMountedDispenserTarget() {
    }

    public static Result resolve(final MovementContext context, final SubLevel owner) {
        if (owner.getLevel() != context.world || context.position == null || context.rotation == null
                || !context.state.hasProperty(DispenserBlock.FACING)) {
            return new Result(null, null, Decision.INVALID_CONTEXT);
        }
        final Direction localFacing = context.state.getValue(DispenserBlock.FACING);
        final Vec3 innerFacing = context.rotation.apply(Vec3.atLowerCornerOf(localFacing.getNormal()));
        final SablePortableInterfaceTarget.Result geometry = SablePortableInterfaceTarget.resolveGeometry(
                owner.logicalPose(), context.position, innerFacing);
        if (!geometry.accepted()) {
            return new Result(null, null, geometry.decision()
                    == SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION
                            ? Decision.UNSUPPORTED_ORIENTATION : Decision.INVALID_CONTEXT);
        }
        final BlockPos source = geometry.parentBlock();
        final BlockPos front = source.relative(geometry.parentFacing());
        if (!context.world.hasChunkAt(source) || !context.world.hasChunkAt(front)) {
            return new Result(null, null, Decision.PARENT_CHUNK_UNLOADED);
        }
        if (Sable.HELPER.getContaining(context.world, source) != null
                || Sable.HELPER.getContaining(context.world, front) != null) {
            return new Result(null, null, Decision.HIDDEN_PLOT_TARGET);
        }
        final AABB bounds = new AABB(source).minmax(new AABB(front));
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(context.world,
                new BoundingBox3d(bounds))) {
            if (intersecting != owner) {
                return new Result(null, null, Decision.OTHER_SABLE_BODY);
            }
        }
        return new Result(source, geometry.parentFacing(), Decision.PARENT_SOURCE);
    }

    public enum Decision {
        PARENT_SOURCE, INVALID_CONTEXT, UNSUPPORTED_ORIENTATION,
        PARENT_CHUNK_UNLOADED, HIDDEN_PLOT_TARGET, OTHER_SABLE_BODY
    }

    public record Result(BlockPos source, Direction facing, Decision decision) {
        public boolean accepted() {
            return this.decision == Decision.PARENT_SOURCE;
        }
    }
}
