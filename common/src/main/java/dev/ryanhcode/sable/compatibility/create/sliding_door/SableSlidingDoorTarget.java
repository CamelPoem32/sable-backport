package dev.ryanhcode.sable.compatibility.create.sliding_door;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Spatial adapter and read-only evidence. Native Create/DoorBlock own all state changes. */
public final class SableSlidingDoorTarget {
    public static final double MIN_ALIGNMENT = .999;
    private static final Map<MovementContext, Evidence> EVIDENCE = Collections.synchronizedMap(new WeakHashMap<>());

    private SableSlidingDoorTarget() { }

    public static Geometry geometry(final Pose3dc pose, final Vec3 raw, final Vec3 innerUp,
                                    final Vec3 innerFacing) {
        if (pose == null || !finite(raw) || !finite(innerUp) || !finite(innerFacing)
                || innerFacing.lengthSqr() == 0 || innerUp.lengthSqr() == 0) {
            return new Geometry(null, null, Decision.INVALID_CONTEXT);
        }
        final Vec3 up = pose.transformNormal(innerUp).normalize();
        final Vec3 facing = pose.transformNormal(innerFacing).normalize();
        if (!finite(up) || !finite(facing) || up.lengthSqr() == 0 || facing.lengthSqr() == 0) {
            return new Geometry(null, null, Decision.INVALID_CONTEXT);
        }
        final Direction cardinal = Direction.getNearest(facing.x, facing.y, facing.z);
        if (pose.transformNormal(new Vec3(0, 1, 0)).normalize().y <= MIN_ALIGNMENT
                || up.y <= MIN_ALIGNMENT || !cardinal.getAxis().isHorizontal()
                || facing.dot(Vec3.atLowerCornerOf(cardinal.getNormal())) <= MIN_ALIGNMENT) {
            return new Geometry(null, null, Decision.UNSUPPORTED_ORIENTATION);
        }
        final Vec3 visible = pose.transformPosition(raw);
        return finite(visible) ? new Geometry(visible, cardinal, Decision.PARENT_TARGET)
                : new Geometry(null, null, Decision.INVALID_CONTEXT);
    }

    public static Geometry resolve(final MovementContext context, final SubLevel owner, final Direction innerOutward) {
        final Geometry result = context.rotation == null || owner.getLevel() != context.world
                ? new Geometry(null, null, Decision.INVALID_CONTEXT)
                : geometry(owner.logicalPose(), context.position, context.rotation.apply(new Vec3(0, 1, 0)),
                        Vec3.atLowerCornerOf(innerOutward.getNormal()));
        // Validate the unquantized inner facing too; native getDoorFacing has already rounded it.
        final Geometry unrounded = context.rotation == null || !context.state.hasProperty(DoorBlock.FACING)
                ? new Geometry(null, null, Decision.INVALID_CONTEXT)
                : geometry(owner.logicalPose(), context.position, context.rotation.apply(new Vec3(0, 1, 0)),
                        context.rotation.apply(Vec3.atLowerCornerOf(context.state.getValue(DoorBlock.FACING).getNormal())));
        final Geometry checked = unrounded.accepted() ? result : unrounded;
        final BlockPos target = checked.accepted() ? BlockPos.containing(checked.visible()).relative(checked.facing()) : null;
        final Evidence previous = EVIDENCE.put(context, new Evidence(owner.getUniqueId(), context.localPos,
                context.position, checked.visible(), innerOutward, checked.facing(), target, null, null,
                checked.decision(), context.world.getGameTime()));
        if (previous == null || previous.decision() != checked.decision()
                || !java.util.Objects.equals(previous.target(), target)) {
            trace(context, checked.accepted() ? "TARGET_RESOLVED" : checked.decision().name());
        }
        return checked;
    }

    public static Decision validate(final MovementContext context, final SubLevel owner, final BlockPos target) {
        final Evidence evidence = EVIDENCE.get(context);
        if (owner.getLevel() != context.world || evidence == null || evidence.gameTime() != context.world.getGameTime()
                || evidence.decision() != Decision.PARENT_TARGET || !target.equals(evidence.target())) {
            return evidence == null ? Decision.INVALID_CONTEXT : evidence.decision() == Decision.PARENT_TARGET
                    ? Decision.INVALID_CONTEXT : evidence.decision();
        }
        // DoorBlock updates the paired half. Reserve both possible halves before reading/mutating.
        for (final BlockPos cell : new BlockPos[] {target.below(), target, target.above()}) {
            if (!context.world.hasChunkAt(cell)) return Decision.PARENT_CHUNK_UNLOADED;
            if (Sable.HELPER.getContaining(context.world, cell) != null) return Decision.HIDDEN_PLOT_TARGET;
            for (final SubLevel body : Sable.HELPER.getAllIntersecting(context.world, new BoundingBox3d(new AABB(cell)))) {
                if (body != owner) return Decision.REJECT_CROSS_SABLE;
            }
        }
        return Decision.PARENT_TARGET;
    }

    public static void rejected(final MovementContext context, final Decision decision) {
        final Evidence old = EVIDENCE.get(context);
        if (old != null) EVIDENCE.put(context, new Evidence(old.owner(), old.local(), old.raw(), old.visible(),
                old.innerFacing(), old.parentFacing(), old.target(), null, null, decision, old.gameTime()));
        trace(context, decision.name());
    }

    public static void committed(final MovementContext context, final BlockPos target,
                                 final BlockState before, final BlockState after) {
        final Evidence old = EVIDENCE.get(context);
        if (old == null || !target.equals(old.target())) return;
        EVIDENCE.put(context, new Evidence(old.owner(), old.local(), old.raw(), old.visible(), old.innerFacing(),
                old.parentFacing(), target, before, after, Decision.PARENT_TARGET, context.world.getGameTime()));
        trace(context, before.equals(after) ? "MUTATION_UNCHANGED" : "MUTATION_COMMITTED");
    }

    public static Evidence evidence(final MovementContext context) { return EVIDENCE.get(context); }

    public static void trace(final MovementContext context, final String event) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) return;
        final Evidence e = EVIDENCE.get(context);
        if (e != null) Sable.LOGGER.info("SABLE_M42_SLIDING_DOOR event={} subLevel={} actorLocal={} rawActorPos={} "
                        + "visibleActorPos={} localFacing={} innerFacing={} parentFacing={} expectedTargetBlock={} actualTargetBlock={} "
                        + "beforeState={} afterState={} hiddenPlotTarget={} decision={}", event, e.owner(), e.local(), e.raw(),
                e.visible(), context.state.getOptionalValue(DoorBlock.FACING).orElse(null), e.innerFacing(), e.parentFacing(), e.target(), e.target(), e.before(), e.after(),
                e.decision() == Decision.HIDDEN_PLOT_TARGET, e.decision());
    }

    private static boolean finite(final Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public enum Decision { PARENT_TARGET, INVALID_CONTEXT, UNSUPPORTED_ORIENTATION, PARENT_CHUNK_UNLOADED,
        HIDDEN_PLOT_TARGET, REJECT_CROSS_SABLE }
    public record Geometry(Vec3 visible, Direction facing, Decision decision) {
        public boolean accepted() { return decision == Decision.PARENT_TARGET; }
    }
    public record Evidence(UUID owner, BlockPos local, Vec3 raw, Vec3 visible, Direction innerFacing,
                           Direction parentFacing, BlockPos target, BlockState before, BlockState after,
                           Decision decision, long gameTime) { }
}
