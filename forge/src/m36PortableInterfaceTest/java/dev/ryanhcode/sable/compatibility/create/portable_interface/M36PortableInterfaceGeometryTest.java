package dev.ryanhcode.sable.compatibility.create.portable_interface;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Physical PSI/PFI search-cell and facing contract for a moving nested actor. */
public final class M36PortableInterfaceGeometryTest {
    private static final Vec3 RAW = new Vec3(20_000_001.5, 90.5, 20_000_000.5);

    private M36PortableInterfaceGeometryTest() {
    }

    public static void run() {
        assertTarget(pose(10.5, 90.5, 11.5, new Quaterniond()), Direction.EAST,
                new BlockPos(11, 90, 11), Direction.EAST, "translation");
        assertTarget(pose(-8.5, 90.5, -3.5, new Quaterniond()), Direction.EAST,
                new BlockPos(-8, 90, -4), Direction.EAST, "negative coordinates");
        assertTarget(pose(10.5, 90.5, 11.5, new Quaterniond().rotateY(Math.PI / 2)), Direction.EAST,
                new BlockPos(10, 90, 10), Direction.NORTH, "yaw90");
        assertTarget(pose(10.5, 90.5, 11.5, new Quaterniond().rotateY(Math.toRadians(20))),
                Direction.EAST, new BlockPos(11, 90, 11), Direction.EAST, "near-cardinal yaw");
        final SablePortableInterfaceTarget.Result boundary = SablePortableInterfaceTarget.resolveGeometry(
                pose(10.5, 90.5, 11.5, new Quaterniond()), RAW.add(1, 0, 0),
                Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (!boundary.parentBlock().equals(new BlockPos(12, 90, 11))) {
            throw new AssertionError("moving outer/inner target did not cross to the new parent block");
        }
        final SablePortableInterfaceTarget.Result tilted = SablePortableInterfaceTarget.resolveGeometry(
                pose(10.5, 90.5, 11.5, new Quaterniond().rotateX(Math.toRadians(5))),
                RAW, Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (tilted.decision() != SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION) {
            throw new AssertionError("material pitch must not quantize into a parent-world connection");
        }
        final SablePortableInterfaceTarget.Result rolled = SablePortableInterfaceTarget.resolveGeometry(
                pose(10.5, 90.5, 11.5, new Quaterniond().rotateZ(Math.toRadians(5))),
                RAW, Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (rolled.decision() != SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION) {
            throw new AssertionError("material roll must not create a parent-world connection");
        }
        final SablePortableInterfaceTarget.Result diagonal = SablePortableInterfaceTarget.resolveGeometry(
                pose(10.5, 90.5, 11.5, new Quaterniond().rotateY(Math.PI / 4)),
                RAW, Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (diagonal.decision() != SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION) {
            throw new AssertionError("non-cardinal yaw must not be silently quantized");
        }
    }

    private static Pose3d pose(final double x, final double y, final double z, final Quaterniond rotation) {
        return new Pose3d(new Vector3d(x, y, z), rotation,
                new Vector3d(20_000_000.5, 90.5, 20_000_000.5), new Vector3d(1));
    }

    private static void assertTarget(final Pose3d pose, final Direction innerFacing,
                                     final BlockPos expectedPos, final Direction expectedFacing,
                                     final String label) {
        final SablePortableInterfaceTarget.Result actual = SablePortableInterfaceTarget.resolveGeometry(
                pose, RAW, Vec3.atLowerCornerOf(innerFacing.getNormal()));
        if (!actual.accepted() || !expectedPos.equals(actual.parentBlock())
                || expectedFacing != actual.parentFacing()) {
            throw new AssertionError(label + ": expected " + expectedPos + " " + expectedFacing
                    + ", got " + actual);
        }
    }
}
