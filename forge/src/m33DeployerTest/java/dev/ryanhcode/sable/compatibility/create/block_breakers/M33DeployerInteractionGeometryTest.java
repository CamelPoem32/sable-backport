package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class M33DeployerInteractionGeometryTest {
    private static final double EPSILON = 1.0e-8;

    private M33DeployerInteractionGeometryTest() {
    }

    public static void run() {
        translatedPoseAndRay();
        yawTransformsFacingAndClickedFace();
        pitchTransformsFacingAndClickedFace();
        rollTransformsFacingAndClickedFace();
        combinedTransform();
        negativeCoordinates();
        targetBoundaryCrossing();
    }

    private static void translatedPoseAndRay() {
        final Vec3 raw = new Vec3(20_481_040.5, 129.5, 20_665_362.5);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(6.5, 100.5, 60.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0));
        assertVec(result.actorSpace().visibleCenter(), 6.5, 100.5, 60.5, "translated target");
        assertVec(result.fakePlayerPosition(), 4.5, 100.5, 60.5, "translated fake player");
        assertVec(result.rayStart(), 6.015625, 100.5, 60.5, "translated ray start");
        assertVec(result.rayEnd(), 6.984375, 100.5, 60.5, "translated ray end");
        assertEquals(new BlockPos(6, 100, 60), result.target(), "translated target block");
        assertEquals(Direction.WEST, result.fallbackClickedFace(), "translated clicked face");
    }

    private static void yawTransformsFacingAndClickedFace() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(10.5, 80.5, 10.5), raw,
                        new Quaterniond().rotateY(Math.PI / 2.0)),
                raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.actorSpace().visibleDirection(), 0.0, 0.0, -1.0, "yaw facing");
        assertVec(result.fakePlayerPosition(), 10.5, 80.5, 12.5, "yaw fake player");
        assertEquals(Direction.SOUTH, result.fallbackClickedFace(), "yaw clicked face");
    }

    private static void pitchTransformsFacingAndClickedFace() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(10.5, 80.5, 10.5), raw,
                        new Quaterniond().rotateX(-Math.PI / 2.0)),
                raw, new Vec3(0.0, 0.0, 1.0));
        assertVec(result.actorSpace().visibleDirection(), 0.0, 1.0, 0.0, "pitch facing");
        assertEquals(Direction.DOWN, result.fallbackClickedFace(), "pitch clicked face");
    }

    private static void rollTransformsFacingAndClickedFace() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(10.5, 80.5, 10.5), raw,
                        new Quaterniond().rotateZ(Math.PI / 2.0)),
                raw, new Vec3(1.0, 0.0, 0.0));
        assertVec(result.actorSpace().visibleDirection(), 0.0, 1.0, 0.0, "roll facing");
        assertEquals(Direction.DOWN, result.fallbackClickedFace(), "roll clicked face");
    }

    private static void combinedTransform() {
        final Vec3 rawPivot = new Vec3(20_123_456.5, 70.5, 20_654_321.5);
        final Vec3 rawTarget = rawPivot.add(0.25, -0.35, 0.4);
        final Quaterniond rotation = new Quaterniond().rotateXYZ(0.31, -0.72, 0.44);
        final Pose3d pose = pose(new Vec3(-12.25, 73.75, 8.5), rawPivot, rotation);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose, rawTarget, new Vec3(0.0, 0.0, 1.0));
        final Vector3d expectedPoint = pose.transformPosition(
                new Vector3d(rawTarget.x, rawTarget.y, rawTarget.z));
        final Vector3d expectedDirection = pose.transformNormal(new Vector3d(0.0, 0.0, 1.0)).normalize();
        assertVec(result.actorSpace().visibleCenter(), expectedPoint.x, expectedPoint.y, expectedPoint.z,
                "combined target");
        assertVec(result.actorSpace().visibleDirection(), expectedDirection.x, expectedDirection.y,
                expectedDirection.z, "combined facing");
        assertVec(result.fakePlayerPosition(),
                expectedPoint.x - expectedDirection.x * 2.0,
                expectedPoint.y - expectedDirection.y * 2.0,
                expectedPoint.z - expectedDirection.z * 2.0,
                "combined fake player");
    }

    private static void negativeCoordinates() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final CreateActorTargetGeometry.DeployerSpace result = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(-10.25, 64.5, -7.75), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0));
        assertEquals(new BlockPos(-11, 64, -8), result.target(), "negative target block");
    }

    private static void targetBoundaryCrossing() {
        final Vec3 raw = new Vec3(20_000_000.5, 64.5, 20_000_000.5);
        final BlockPos before = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(10.99, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).target();
        final BlockPos after = CreateActorTargetGeometry.resolveDeployer(
                pose(new Vec3(11.01, 64.5, 10.5), raw, new Quaterniond()), raw,
                new Vec3(1.0, 0.0, 0.0)).target();
        assertEquals(10, before.getX(), "boundary before");
        assertEquals(11, after.getX(), "boundary after");
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
