package dev.ryanhcode.sable.compatibility.create.dispenser;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.compatibility.create.portable_interface.SablePortableInterfaceTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class M38DispenserGeometryTest {
    private static final Vec3 RAW = new Vec3(20_000_002.5, 90.5, 20_000_000.5);

    private M38DispenserGeometryTest() {
    }

    public static void run() {
        assertSource(pose(new Quaterniond()), new BlockPos(12, 80, 10), Direction.EAST);
        assertSource(pose(new Quaterniond().rotationY(Math.PI / 2)),
                new BlockPos(10, 80, 8), Direction.NORTH);
        final var tilted = SablePortableInterfaceTarget.resolveGeometry(
                pose(new Quaterniond().rotationX(Math.toRadians(15))), RAW,
                Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (tilted.decision() != SablePortableInterfaceTarget.Decision.UNSUPPORTED_ORIENTATION) {
            throw new AssertionError("pitched dispenser must not quantize to a parent block: " + tilted);
        }
    }

    private static void assertSource(final Pose3d pose, final BlockPos expected, final Direction facing) {
        final var result = SablePortableInterfaceTarget.resolveGeometry(pose, RAW,
                Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (!result.accepted() || !result.parentBlock().equals(expected) || result.parentFacing() != facing
                || Math.abs(result.parentBlock().getX()) > 1_000_000) {
            throw new AssertionError("visible dispenser source/facing mismatch: " + result);
        }
    }

    private static Pose3d pose(final Quaterniond rotation) {
        return new Pose3d(new Vector3d(10, 80, 10), rotation,
                new Vector3d(20_000_000.5, 90.5, 20_000_000.5), new Vector3d(1));
    }
}
