package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;

public final class M35RollerTerrainGeometryTest {
    private M35RollerTerrainGeometryTest() {
    }

    public static void run() {
        translatedColumn();
        yawAndWideLayer();
        arbitraryYaw();
        tiltRejection();
        boundaryAndRetarget();
        negativeCoordinates();
    }

    private static void translatedColumn() {
        final Vec3 raw = new Vec3(20_481_040.95, 129.5, 20_665_362.5);
        final Pose3d pose = pose(new Vec3(4.95, 99.5, 65.5), raw, new Quaterniond());
        final BlockPos anchor = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose, raw, new Vec3(1, 0, 0)).visibleCenter());
        equal(new BlockPos(4, 99, 65), anchor, "translated terrain anchor");
        equal(new BlockPos(4, 100, 65), anchor.above(), "native clearance cell");
        equal(new BlockPos(4, 97, 65), anchor.below(2), "native paving depth");
        check(anchor.getX() < 1_000_000 && anchor.getZ() < 1_000_000, "raw plot leaked");
        check(SableRollerTerrain.supportsWorldDown(pose), "level pose rejected");
    }

    private static void yawAndWideLayer() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(10.5, 80.5, 10.5), raw,
                new Quaterniond().rotateY(Math.PI / 2));
        final CreateActorTargetGeometry.ActorSpace space = CreateActorTargetGeometry.resolvePoint(
                pose, raw.add(1, 0, 0), new Vec3(1, 0, 0));
        final BlockPos anchor = BlockPos.containing(space.visibleCenter());
        equal(new BlockPos(10, 80, 9), anchor, "yaw anchor");
        final Set<BlockPos> layer = new HashSet<>();
        final int radius = (2 + 1) / 2;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) + Math.abs(z) <= radius) {
                    layer.add(anchor.offset(x, -2, z));
                }
            }
        }
        equal(5, layer.size(), "native wide-fill diamond at depth two");
        check(layer.contains(anchor.below(2)), "wide-fill center missing");
        check(SableRollerTerrain.supportsWorldDown(pose), "yaw rejected");
    }

    private static void arbitraryYaw() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final Pose3d pose = pose(new Vec3(3.25, 72.5, -7.75), raw,
                new Quaterniond().rotateY(0.37));
        final BlockPos anchor = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose, raw, new Vec3(1, 0, 0)).visibleCenter());
        equal(new BlockPos(3, 72, -8), anchor, "arbitrary yaw anchor");
        check(SableRollerTerrain.supportsWorldDown(pose), "arbitrary yaw rejected");
    }

    private static void tiltRejection() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        check(!SableRollerTerrain.supportsWorldDown(pose(raw, raw,
                new Quaterniond().rotateX(Math.PI / 5))), "pitch accepted");
        check(!SableRollerTerrain.supportsWorldDown(pose(raw, raw,
                new Quaterniond().rotateZ(Math.PI / 5))), "roll accepted");
        check(!SableRollerTerrain.supportsWorldDown(pose(raw, raw,
                new Quaterniond().rotateZ(Math.PI))), "inverted body accepted");
    }

    private static void boundaryAndRetarget() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final BlockPos first = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(4.99, 80.5, 2.5), raw, new Quaterniond()), raw,
                new Vec3(1, 0, 0)).visibleCenter());
        final BlockPos second = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(5.01, 80.5, 2.5), raw, new Quaterniond()), raw,
                new Vec3(1, 0, 0)).visibleCenter());
        equal(ExternalBlockBreakingTargetState.Transition.RETARGET,
                ExternalBlockBreakingTargetState.transition(first, second), "boundary retarget");
    }

    private static void negativeCoordinates() {
        final Vec3 raw = new Vec3(20_000_000.5, 80.5, 20_000_000.5);
        final BlockPos anchor = BlockPos.containing(CreateActorTargetGeometry.resolvePoint(
                pose(new Vec3(-9.5, 64.5, -12.5), raw, new Quaterniond()), raw,
                new Vec3(1, 0, 0)).visibleCenter());
        equal(new BlockPos(-10, 64, -13), anchor, "negative visible anchor");
    }

    private static Pose3d pose(final Vec3 visible, final Vec3 raw, final Quaterniond rotation) {
        return new Pose3d(new Vector3d(visible.x, visible.y, visible.z), rotation,
                new Vector3d(raw.x, raw.y, raw.z), new Vector3d(1));
    }

    private static void equal(final Object expected, final Object actual, final String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(final boolean condition, final String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
