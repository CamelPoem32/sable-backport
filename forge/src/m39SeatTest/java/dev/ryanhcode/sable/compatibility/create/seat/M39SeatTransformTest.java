package dev.ryanhcode.sable.compatibility.create.seat;

import dev.ryanhcode.sable.command.M39SeatFixtureLayout;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class M39SeatTransformTest {
    private M39SeatTransformTest() {
    }

    public static void run() {
        final double height = 1.8;
        for (final double yaw : new double[] {0, Math.PI / 2, Math.PI}) {
            for (final double offset : new double[] {0, .125, 5.5, 11, 12, 5.5, 0}) {
                final Pose3d pose = new Pose3d(new Vector3d(10, 80, -30), new Quaterniond().rotationY(yaw),
                        new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(1));
                // Exact Create getPassengerPosition: local .5,myOffset+ySize-.15,.5,
                // then toGlobalVector, + center(ZERO), -(.5,ySize,.5).
                final Vec3 nativePosition = new Vec3(20_000_001.5 + offset, 101.35, 20_000_000.5);
                final Vec3 actual = SableSeatPassengers.physicalPosition(pose, nativePosition, height);
                final Vec3 expected = new Vec3(10 + (1.5 + offset) * Math.cos(yaw) + .5 * Math.sin(yaw),
                        81.35, -30 - (1.5 + offset) * Math.sin(yaw) + .5 * Math.cos(yaw));
                if (actual.distanceTo(expected) > 1e-7 || Math.abs(actual.x) > 1000 || Math.abs(actual.z) > 1000) {
                    throw new AssertionError("Seat transform order, yaw, extension or retraction failed " + actual + " " + expected);
                }
                final Vec3 inverse = pose.transformPositionInverse(actual.add(0, height, 0)).subtract(0, height, 0);
                if (inverse.distanceTo(nativePosition) > 1e-7) throw new AssertionError("Seat transform applied twice");
            }
        }
        final var cells = M39SeatFixtureLayout.mechanism();
        if (cells.size() != 22 || !cells.get(new BlockPos(1, 1, 0)).startsWith("create:red_seat")
                || !cells.get(new BlockPos(0, 0, -2)).equals("minecraft:iron_block")
                || !cells.get(new BlockPos(1, 0, 0)).contains("sticky_north=true,sticky_east=true")) {
            throw new AssertionError("Seat must retain proven native chassis and assembler starting cell");
        }
        for (final BlockPos cell : M39SeatFixtureLayout.platform().keySet()) {
            if (cells.containsKey(cell) || cell.getY() != -4 && cell.getZ() < 3) {
                throw new AssertionError("parent platform overlaps outer hull/native piston payload " + cell);
            }
        }
        for (int offset = 0; offset <= 12; offset++) {
            for (final BlockPos cell : new BlockPos[] {new BlockPos(1, 0, 0), new BlockPos(1, 0, 1), new BlockPos(1, 1, 0)}) {
                final BlockPos travel = cell.east(offset);
                if (M39SeatFixtureLayout.platform().containsKey(travel)) throw new AssertionError("extension hits platform");
                final BlockPos yawed = new BlockPos(cell.getZ(), cell.getY(), -cell.getX() - offset);
                if (M39SeatFixtureLayout.platform().containsKey(yawed)) throw new AssertionError("yawed extension hits platform");
            }
        }
    }
}
