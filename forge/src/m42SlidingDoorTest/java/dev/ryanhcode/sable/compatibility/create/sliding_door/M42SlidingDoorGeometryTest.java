package dev.ryanhcode.sable.compatibility.create.sliding_door;

import dev.ryanhcode.sable.command.M42SlidingDoorFixtureLayout;
import dev.ryanhcode.sable.command.M38DispenserFixturePlacement;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class M42SlidingDoorGeometryTest {
    private M42SlidingDoorGeometryTest() { }

    public static void run() {
        final Vec3 up = new Vec3(0, 1, 0);
        final Vec3 north = new Vec3(0, 0, -1);
        for (final double yaw : new double[] {0, Math.PI / 2, Math.PI, -Math.PI / 2}) {
            final Pose3d pose = new Pose3d(new Vector3d(10, 80, -30), new Quaterniond().rotationY(yaw),
                    new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(1));
            for (final double offset : new double[] {0, .125, .5, 1, 2, .5, 0}) {
                final Vec3 raw = new Vec3(20_000_001.5 + offset, 101.5, 20_000_000.5);
                final var result = SableSlidingDoorTarget.geometry(pose, raw, up, north);
                final Vec3 expected = new Vec3(10 + (1.5 + offset) * Math.cos(yaw) + .5 * Math.sin(yaw),
                        81.5, -30 - (1.5 + offset) * Math.sin(yaw) + .5 * Math.cos(yaw));
                if (!result.accepted() || result.visible().distanceTo(expected) > 1e-7) throw new AssertionError("local-inner-outer composition");
                final Direction facing = yaw == 0 ? Direction.NORTH : yaw == Math.PI / 2 ? Direction.WEST
                        : yaw == Math.PI ? Direction.SOUTH : Direction.EAST;
                if (result.facing() != facing) throw new AssertionError("outward facing yaw convention");
                final BlockPos target = BlockPos.containing(result.visible()).relative(facing);
                if (Math.abs(target.getX()) > 1000 || Math.abs(target.getZ()) > 1000) throw new AssertionError("hidden target escaped");
                if (target.equals(BlockPos.containing(raw).relative(facing))) throw new AssertionError("raw fallback");
            }
        }
        final Vec3 raw = new Vec3(20_000_001.5, 101.5, 20_000_000.5);
        final Pose3d near = new Pose3d(new Vector3d(), new Quaterniond().rotationY(Math.toRadians(1)),
                new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(1));
        if (!SableSlidingDoorTarget.geometry(near, raw, up, north).accepted()) throw new AssertionError("near-cardinal yaw rejected");
        for (final Quaterniond rotation : new Quaterniond[] {new Quaterniond().rotationX(Math.toRadians(3)),
                new Quaterniond().rotationZ(Math.toRadians(3)), new Quaterniond().rotationY(Math.toRadians(3))}) {
            final Pose3d unsupported = new Pose3d(new Vector3d(), rotation, new Vector3d(), new Vector3d(1));
            final var result = SableSlidingDoorTarget.geometry(unsupported, raw, up, north);
            if (result.decision() != SableSlidingDoorTarget.Decision.UNSUPPORTED_ORIENTATION || result.visible() != null) throw new AssertionError("unsupported pose has mutation target");
        }
        if (SableSlidingDoorTarget.geometry(near, null, up, north).accepted()
                || SableSlidingDoorTarget.geometry(near, raw, up, Vec3.ZERO).accepted()) throw new AssertionError("invalid context fallback");
        if (M38DispenserFixturePlacement.candidateOffsets().size() != 27) throw new AssertionError("unbounded origin search");
        final var layout = M42SlidingDoorFixtureLayout.mechanism();
        if (!layout.get(M42SlidingDoorFixtureLayout.LOWER).contains("half=lower")
                || !layout.get(M42SlidingDoorFixtureLayout.LOWER.above()).contains("half=upper")
                || !layout.get(M42SlidingDoorFixtureLayout.LOWER.below()).contains("radial_chassis")) throw new AssertionError("unsupported native two-half door payload");
        if (!M42SlidingDoorFixtureLayout.reservation().containsAll(layout.keySet())
                || M42SlidingDoorFixtureLayout.reservation().contains(new BlockPos(0, -4, 0))) throw new AssertionError("reservation/floor regression");
        for (final BlockPos p : M42SlidingDoorFixtureLayout.station(M42SlidingDoorFixtureLayout.PARKED, Direction.NORTH).keySet()) {
            if (layout.containsKey(p)) throw new AssertionError("station captured before inner assembly");
        }
        if (M42SlidingDoorFixtureLayout.glue(BlockPos.ZERO).contains(Vec3.atCenterOf(new BlockPos(1, 0, 0)))) throw new AssertionError("glue captures controller/payload");
        attachmentRegression();
        preservationRegression();
    }

    private static void preservationRegression() {
        final var statics = M42SlidingDoorFixtureLayout.staticOuter();
        if (statics.size() != 7 || !statics.get(BlockPos.ZERO).contains("sticky_mechanical_piston")) throw new AssertionError("live controller omitted or moving blocks counted as static");
        if (statics.containsKey(M42SlidingDoorFixtureLayout.LOWER) || statics.containsKey(M42SlidingDoorFixtureLayout.CHASSIS)
                || statics.containsKey(new BlockPos(1, 0, 1))) throw new AssertionError("double payload ownership");
        final BlockPos source = new BlockPos(-43, 101, 67);
        final BlockPos destination = new BlockPos(20_000_000, 80, 20_000_000);
        final AssemblyTransform transform = new AssemblyTransform(source, destination, 0, Rotation.NONE, null);
        final BlockPos oldAnchor = source.east();
        final BlockPos newAnchor = transform.apply(oldAnchor);
        for (final BlockPos relative : M42SlidingDoorFixtureLayout.capturedBlockIds().keySet()) {
            final BlockPos local = source.offset(relative).subtract(oldAnchor);
            if (!transform.apply(oldAnchor.offset(local)).equals(newAnchor.offset(local))) throw new AssertionError("piston block local coordinates changed across translation");
        }
        final Vec3 oldPosition = Vec3.atLowerCornerOf(oldAnchor).add(.125, 0, 0);
        if (transform.apply(oldPosition).distanceTo(Vec3.atLowerCornerOf(newAnchor).add(.125, 0, 0)) > 1e-7) throw new AssertionError("native piston offset lost");
        // Re-anchoring the old fallen body's restore goal is not permission to delete occupied ground.
        if (statics.containsKey(new BlockPos(-1, -5, 0)) || statics.containsKey(new BlockPos(-1, -5, 1))) throw new AssertionError("unrelated floor classified as fixture");
    }

    /** Pinned native attachment contracts; live setup also calls the actual PistonContraption assembler. */
    private static void attachmentRegression() {
        final var build = M42SlidingDoorFixtureLayout.mechanism();
        final var payload = M42SlidingDoorFixtureLayout.movingPayload();
        final BlockPos chest = new BlockPos(1, 0, 1);
        if (!"minecraft:chest[facing=north,type=single,waterlogged=false]".equals(build.get(chest))) throw new AssertionError("reported position is not the chest");
        if (payload.size() != 4 || !build.entrySet().containsAll(payload.entrySet())) throw new AssertionError("BUILD and VALIDATE payload drift");
        final String chassis = build.get(M42SlidingDoorFixtureLayout.CHASSIS);
        if (!chassis.contains("axis=x,sticky_north=true,sticky_east=true")) throw new AssertionError("native UP/SOUTH sticky faces changed");
        // Doors are brittle (#minecraft:doors). Range traversal skips them, even on a sticky face.
        final Set<BlockPos> range = new LinkedHashSet<>();
        payload.forEach((pos, state) -> {
            if (!pos.equals(M42SlidingDoorFixtureLayout.CHASSIS) && !state.startsWith("create:andesite_door[")) range.add(pos);
        });
        if (!range.equals(Set.of(chest)) || !range.equals(M42SlidingDoorFixtureLayout.chassisRangeExpected())) throw new AssertionError("native radial range expectation");
        if (range.contains(M42SlidingDoorFixtureLayout.LOWER)) throw new AssertionError("old erroneous lower-door range requirement returned");
        final Map<BlockPos, String> world = new LinkedHashMap<>(build);
        world.putAll(M42SlidingDoorFixtureLayout.station(M42SlidingDoorFixtureLayout.PARKED, Direction.NORTH));
        final Set<BlockPos> captured = attachedPayload(world, range);
        if (!captured.equals(payload.keySet())) throw new AssertionError("native door attachment traversal must recover the complete payload " + captured);
        if (!M42SlidingDoorFixtureLayout.station(M42SlidingDoorFixtureLayout.PARKED, Direction.NORTH).keySet().stream().noneMatch(captured::contains)) throw new AssertionError("stationary target captured");
        final Map<BlockPos, String> full = new LinkedHashMap<>();
        build.forEach((pos, state) -> {
            if (state.startsWith("create:piston_extension_pole[")) full.put(pos, "create:piston_extension_pole");
        });
        captured.forEach(pos -> full.put(pos, world.get(pos).split("\\[")[0]));
        full.put(BlockPos.ZERO, "create:mechanical_piston_head");
        if (full.size() != 17 || !full.equals(M42SlidingDoorFixtureLayout.capturedBlockIds())) throw new AssertionError("exact retracted native capture expectation");
        world.remove(M42SlidingDoorFixtureLayout.LOWER);
        if (attachedPayload(world, range).equals(payload.keySet())) throw new AssertionError("damaged lower door wrongly accepted");
        world.put(M42SlidingDoorFixtureLayout.LOWER, build.get(M42SlidingDoorFixtureLayout.LOWER));
        world.remove(M42SlidingDoorFixtureLayout.LOWER.above());
        if (attachedPayload(world, range).equals(payload.keySet())) throw new AssertionError("missing upper half wrongly accepted");
    }

    private static Set<BlockPos> attachedPayload(final Map<BlockPos, String> world, final Set<BlockPos> range) {
        final Set<BlockPos> result = new LinkedHashSet<>();
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>(range);
        frontier.add(M42SlidingDoorFixtureLayout.CHASSIS);
        while (!frontier.isEmpty()) {
            final BlockPos current = frontier.remove();
            if (!result.add(current)) continue;
            for (final Direction offset : Direction.values()) {
                final BlockPos next = current.relative(offset);
                final String state = world.get(next);
                if (state == null || !state.startsWith("create:andesite_door[")) continue;
                // BlockMovementChecksImpl: all door halves attach DOWN; LOWER also attaches UP.
                final Direction towards = offset.getOpposite();
                if (towards == Direction.DOWN || state.contains("half=lower") && towards == Direction.UP) frontier.add(next);
            }
        }
        return result;
    }
}
