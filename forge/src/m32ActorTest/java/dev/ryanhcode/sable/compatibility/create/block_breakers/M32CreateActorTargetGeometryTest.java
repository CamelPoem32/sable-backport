package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class M32CreateActorTargetGeometryTest {
    private static final double EPSILON = 1.0e-8;

    private M32CreateActorTargetGeometryTest() {
    }

    public static void run() {
        translatedPoint();
        yawRotatedPointAndFacing();
        pitchRollPointAndFacing();
        combinedTransform();
        negativeCoordinates();
        targetBoundaryCrossing();
        targetTransitions();
    }

    private static void translatedPoint() {
        final Vec3 raw = new Vec3(20_481_040.95, 129.5, 20_665_362.5);
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(4.95, 99.5, 65.5), raw, new Quaterniond()), raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleCenter(), 4.95, 99.5, 65.5, "translated active point");
        assertEquals(new BlockPos(4, 99, 65), BlockPos.containing(result.visibleCenter()), "translated block");
    }

    private static void yawRotatedPointAndFacing() {
        final Vec3 rawPivot = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Vec3 rawPoint = rawPivot.add(0.45, 0.0, 0.0);
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(10.5, 80.5, 10.5), rawPivot, new Quaterniond().rotateY(Math.PI / 2.0)),
                rawPoint, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleCenter(), 10.5, 80.5, 10.05, "yaw active point");
        assertVec(result.visibleDirection(), 0.0, 0.0, -1.0, "yaw facing");
    }

    private static void pitchRollPointAndFacing() {
        final Vec3 rawPivot = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Vec3 rawPoint = rawPivot.add(0.45, 0.0, 0.0);
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(10.5, 80.5, 10.5), rawPivot,
                        new Quaterniond().rotateXYZ(0.0, 0.0, Math.PI / 2.0)),
                rawPoint, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.visibleCenter(), 10.5, 80.95, 10.5, "pitch active point");
        assertVec(result.visibleDirection(), 0.0, 1.0, 0.0, "pitch facing");
    }

    private static void combinedTransform() {
        final Vec3 rawPivot = new Vec3(20_123_456.5, 70.5, 20_654_321.5);
        final Vec3 rawPoint = rawPivot.add(0.25, -0.35, 0.4);
        final Quaterniond rotation = new Quaterniond().rotateXYZ(0.31, -0.72, 0.44);
        final Pose3d pose = pose(new Vec3(-12.25, 73.75, 8.5), rawPivot, rotation);
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolvePoint(
                pose, rawPoint, new Vec3(0.0, 0.0, 1.0));
        final Vector3d expected = pose.transformPosition(new Vector3d(rawPoint.x, rawPoint.y, rawPoint.z));
        assertVec(result.visibleCenter(), expected.x, expected.y, expected.z, "combined point");
    }

    private static void negativeCoordinates() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final CreateActorTargetGeometry.ActorSpace result = CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(-10.25, 64.5, -7.75), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0));
        assertEquals(new BlockPos(-11, 64, -8), BlockPos.containing(result.visibleCenter()), "negative block");
    }

    private static void targetBoundaryCrossing() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final BlockPos before = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(10.99, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).visibleCenter());
        final BlockPos after = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(11.01, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).visibleCenter());
        assertEquals(10, before.getX(), "boundary before");
        assertEquals(11, after.getX(), "boundary after");
    }

    private static void targetTransitions() {
        final BlockPos first = new BlockPos(1, 2, 3);
        final BlockPos second = new BlockPos(2, 2, 3);
        assertEquals(ExternalBlockBreakingTargetState.Transition.START,
                ExternalBlockBreakingTargetState.transition(null, first), "start");
        assertEquals(ExternalBlockBreakingTargetState.Transition.UNCHANGED,
                ExternalBlockBreakingTargetState.transition(first, first), "unchanged");
        assertEquals(ExternalBlockBreakingTargetState.Transition.RETARGET,
                ExternalBlockBreakingTargetState.transition(first, second), "retarget");
        assertEquals(ExternalBlockBreakingTargetState.Transition.STOP,
                ExternalBlockBreakingTargetState.transition(first, null), "stop");
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
