package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Numeric contract for the bridge's outer pose composed with Create's inner piston offset. */
public final class M35RollerMovingRenderTransformTest {
    private static final Vec3 RAW_ANCHOR = new Vec3(20_000_000.5, 90.5, 20_000_000.5);
    private static final Vec3 CAMERA = new Vec3(7.5, 90.5, 11.5);

    private M35RollerMovingRenderTransformTest() {
    }

    public static void run() {
        final Pose3d translated = pose(new Vec3(10.5, 90.5, 11.5), new Quaterniond());
        assertVisible(translated, 0, new Vec3(11.5, 90.5, 11.5), "retracted");
        assertVisible(translated, 6, new Vec3(17.5, 90.5, 11.5), "mid extension");
        assertVisible(translated, 12, new Vec3(23.5, 90.5, 11.5), "full extension");
        assertVisible(translated, 6, new Vec3(17.5, 90.5, 11.5), "mid retraction");
        assertVisible(translated, 0, new Vec3(11.5, 90.5, 11.5), "restored");

        final Pose3d yaw90 = pose(new Vec3(10.5, 90.5, 11.5), new Quaterniond().rotateY(Math.PI / 2));
        assertVisible(yaw90, 0, new Vec3(10.5, 90.5, 10.5), "yaw90 retracted");
        assertVisible(yaw90, 6, new Vec3(10.5, 90.5, 4.5), "yaw90 mid extension");
        assertVisible(yaw90, 12, new Vec3(10.5, 90.5, -1.5), "yaw90 full extension");
    }

    private static Pose3d pose(final Vec3 visibleAnchor, final Quaterniond rotation) {
        return new Pose3d(new Vector3d(visibleAnchor.x, visibleAnchor.y, visibleAnchor.z), rotation,
                new Vector3d(RAW_ANCHOR.x, RAW_ANCHOR.y, RAW_ANCHOR.z), new Vector3d(1));
    }

    private static void assertVisible(final Pose3d pose, final int pistonOffset,
                                      final Vec3 expected, final String stage) {
        final Vec3 actual = pose.transformPosition(RAW_ANCHOR.add(1 + pistonOffset, 0, 0));
        assertNear(expected.x, actual.x, stage + " x");
        assertNear(expected.y, actual.y, stage + " y");
        assertNear(expected.z, actual.z, stage + " z");
        final Vec3 cameraRelative = actual.subtract(CAMERA);
        if (Math.max(Math.abs(cameraRelative.x), Math.abs(cameraRelative.z)) >= 100) {
            throw new AssertionError(stage + ": hidden plot coordinate reached camera-relative draw space");
        }
    }

    private static void assertNear(final double expected, final double actual, final String label) {
        if (Math.abs(expected - actual) > 1e-5) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
