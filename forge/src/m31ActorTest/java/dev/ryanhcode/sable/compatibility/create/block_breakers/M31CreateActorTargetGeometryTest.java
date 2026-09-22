package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class M31CreateActorTargetGeometryTest {
    private static final double EPSILON = 1.0e-8;

    private M31CreateActorTargetGeometryTest() {
    }

    public static void run() {
        translation();
        yawRotation();
        pitchRotation();
        negativeCoordinates();
        targetBoundaryCrossing();
        eightCornerBounds();
        targetTransitions();
    }

    private static void translation() {
        final Vec3 raw = new Vec3(20_481_040.5, 129.5, 20_665_362.5);
        final Pose3d pose = pose(new Vec3(4.5, 99.5, 65.5), raw, new Quaterniond());
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolve(
                pose, raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleCenter(), 4.5, 99.5, 65.5, "translated center");
        assertVec(result.visibleDirection(), 1.0, 0.0, 0.0, "translated direction");
        final BoundingBox3i blocks = new BoundingBox3i(result.visibleMiningBounds());
        assertEquals(4, blocks.minX(), "translated min x");
        assertEquals(5, blocks.maxX(), "translated max x");
    }

    private static void yawRotation() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(10.5, 80.5, 10.5), raw,
                new Quaterniond().rotateY(Math.PI / 2.0));
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolve(
                pose, raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleDirection(), 0.0, 0.0, -1.0, "yaw direction");
    }

    private static void pitchRotation() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(10.5, 80.5, 10.5), raw,
                new Quaterniond().rotateZ(Math.PI / 2.0));
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolve(
                pose, raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleDirection(), 0.0, 1.0, 0.0, "pitch direction");
    }

    private static void negativeCoordinates() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(-10.5, 64.5, -7.5), raw, new Quaterniond());
        final BoundingBox3i blocks = new BoundingBox3i(CreateActorTargetGeometry.resolve(
                pose, raw, new Vec3(1.0, 0.0, 0.0)).visibleMiningBounds());
        assertEquals(-11, blocks.minX(), "negative min x");
        assertEquals(-10, blocks.maxX(), "negative max x");
    }

    private static void targetBoundaryCrossing() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final BoundingBox3i before = new BoundingBox3i(CreateActorTargetGeometry.resolve(
                pose(new Vec3(10.74, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).visibleMiningBounds());
        final BoundingBox3i after = new BoundingBox3i(CreateActorTargetGeometry.resolve(
                pose(new Vec3(10.76, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).visibleMiningBounds());
        assertEquals(10, before.minX(), "boundary before");
        assertEquals(11, after.minX(), "boundary after");
    }

    private static void eightCornerBounds() {
        final Vec3 raw = new Vec3(20_000_000.5, 70.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(3.25, 71.75, -8.5), raw,
                new Quaterniond().rotateXYZ(0.41, -0.67, 0.29));
        final BoundingBox3d source = CreateActorTargetGeometry.storageMiningBounds(raw, new Vec3(1.0, 0.0, 0.0));
        final BoundingBox3d expected = new BoundingBox3d(
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        expected.setUnchecked(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < 8; i++) {
            final Vector3d corner = new Vector3d(
                    (i & 1) == 0 ? source.minX : source.maxX,
                    (i & 2) == 0 ? source.minY : source.maxY,
                    (i & 4) == 0 ? source.minZ : source.maxZ);
            expected.expandTo(pose.transformPosition(corner));
        }
        final BoundingBox3d actual = CreateActorTargetGeometry.resolve(
                pose, raw, new Vec3(1.0, 0.0, 0.0)).visibleMiningBounds();
        assertNear(expected.minX, actual.minX, "eight-corner min x");
        assertNear(expected.minY, actual.minY, "eight-corner min y");
        assertNear(expected.minZ, actual.minZ, "eight-corner min z");
        assertNear(expected.maxX, actual.maxX, "eight-corner max x");
        assertNear(expected.maxY, actual.maxY, "eight-corner max y");
        assertNear(expected.maxZ, actual.maxZ, "eight-corner max z");
    }

    private static void targetTransitions() {
        final BlockPos first = new BlockPos(1, 2, 3);
        final BlockPos second = new BlockPos(2, 2, 3);
        assertEquals(ExternalBlockBreakingTargetState.Transition.START,
                ExternalBlockBreakingTargetState.transition(null, first), "start transition");
        assertEquals(ExternalBlockBreakingTargetState.Transition.UNCHANGED,
                ExternalBlockBreakingTargetState.transition(first, first), "unchanged transition");
        assertEquals(ExternalBlockBreakingTargetState.Transition.RETARGET,
                ExternalBlockBreakingTargetState.transition(first, second), "retarget transition");
        assertEquals(ExternalBlockBreakingTargetState.Transition.STOP,
                ExternalBlockBreakingTargetState.transition(first, null), "stop transition");
    }

    private static Pose3d pose(final Vec3 visibleCenter, final Vec3 rawCenter, final Quaterniond orientation) {
        return new Pose3d(new Vector3d(visibleCenter.x, visibleCenter.y, visibleCenter.z),
                orientation, new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z), new Vector3d(1.0));
    }

    private static void assertVec(final Vec3 actual,
                                  final double x,
                                  final double y,
                                  final double z,
                                  final String label) {
        assertNear(x, actual.x, label + " x");
        assertNear(y, actual.y, label + " y");
        assertNear(z, actual.z, label + " z");
    }

    private static void assertNear(final double expected, final double actual, final String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(final Object expected, final Object actual, final String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
