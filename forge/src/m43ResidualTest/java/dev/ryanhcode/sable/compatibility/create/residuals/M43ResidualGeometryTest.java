package dev.ryanhcode.sable.compatibility.create.residuals;

import dev.ryanhcode.sable.command.M43ResidualFixtureLayout;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Headless coordinate/policy regressions; native damage/filter/registration semantics are separately checked in ASM. */
public final class M43ResidualGeometryTest {
    private M43ResidualGeometryTest() { }
    public static void run() {
        plough();
        air("DRILL");
        air("SAW");
        blaze();
        fixture();
    }

    private static void plough() {
        final Vec3 inner = new Vec3(.2, .03, -.08);
        for (final Quaterniond rotation : new Quaterniond[] {new Quaterniond(), new Quaterniond().rotationY(Math.PI / 2),
                new Quaterniond().rotationXYZ(.4, 1.2, -.8)}) {
            final Pose3d p = new Pose3d(new Vector3d(100, 80, -20), rotation, new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(3, 2, 1));
            final Vec3 physical = ResidualActorGeometry.rotate(p, inner);
            near(physical.length(), inner.length(), "vector magnitude / no scale");
            near(ResidualActorGeometry.inverseRotate(p, physical).distanceTo(inner), 0, "SO(3) inverse");
            final Pose3d moved = new Pose3d(p);
            moved.position().add(1e6, 20, -1e6);
            near(ResidualActorGeometry.rotate(moved, inner).distanceTo(physical), 0, "no translation on impulse");
            final Vec3 lift = ResidualActorGeometry.nativeBoost(physical).subtract(physical);
            near(lift.x, 0, "world-up lift X"); near(lift.z, 0, "world-up lift Z"); near(lift.y, inner.length() / 4, "native lift magnitude");
        }
        final Pose3d yaw = new Pose3d(new Vector3d(), new Quaterniond().rotationY(Math.PI / 2), new Vector3d(), new Vector3d(1));
        final Vec3 north = ResidualActorGeometry.rotate(yaw, new Vec3(1, 0, 0));
        near(north.distanceTo(new Vec3(0, 0, -1)), 0, "yaw90 east->north");
        if (north.distanceTo(ResidualActorGeometry.rotate(yaw, north)) < .5) throw new AssertionError("double rotation not detected");
        near(ResidualActorGeometry.nativeBoost(new Vec3(100, 1, 20)).length(), 4, "native boost cap");
    }

    private static void air(final String kind) {
        // Exact native independent predicates: !redstoneConductor -> damage; canBreak -> block progress.
        for (final boolean entity : new boolean[] {false, true}) for (final boolean conductor : new boolean[] {false, true})
            for (final boolean breakable : new boolean[] {false, true}) {
                final int queries = conductor ? 0 : 1;
                final int attempts = entity && queries == 1 ? 1 : 0;
                final boolean stall = breakable;
                if (queries > 1 || attempts > 1 || (!breakable && stall)) throw new AssertionError(kind + " duplicate/fake breaking");
                if (!conductor && !breakable && entity && attempts != 1) throw new AssertionError(kind + " AIR entity suppressed");
            }
        // Per physical-cell transition, including tick+visit in one tick, re-entry and target loss.
        BlockPos last = null;
        int queries = 0;
        for (final BlockPos cell : new BlockPos[] {BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.east(), BlockPos.ZERO}) {
            if (!cell.equals(last)) queries++;
            last = cell;
        }
        if (queries != 3) throw new AssertionError(kind + " physical visit de-duplication");
    }

    private static void blaze() {
        final Vec3 raw = new Vec3(20_000_003.5, 103.5, 20_000_006.5);
        final Vec3 camera = new Vec3(12, 83, -25);
        for (final Quaterniond rotation : new Quaterniond[] {new Quaterniond(), new Quaterniond().rotationY(Math.PI / 2), new Quaterniond().rotationXYZ(.2, .7, -.3)}) {
            final Pose3d p = new Pose3d(new Vector3d(10, 80, -30), rotation, new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(1));
            final Vec3 visible = p.transformPosition(raw);
            final Vec3 physical = camera.subtract(visible);
            if (physical.length() > 100 || visible.length() > 1000) throw new AssertionError("hidden client gaze");
            final Vec3 local = ResidualActorGeometry.inverseRotate(p, physical);
            near(ResidualActorGeometry.rotate(p, local).distanceTo(physical), 0, "outer gaze inverse before inner reverseRotation");
            final Quaterniond inner = new Quaterniond().rotationY(.35);
            final Vector3d nativeLocal = inner.transformInverse(new Vector3d(local.x, local.y, local.z));
            final float angle = (float) (-Math.toDegrees(Math.atan2(nativeLocal.z, nativeLocal.x)) - 90);
            final double radians = Math.toRadians(-angle - 90);
            final Vec3 actual = new Vec3(Math.cos(radians), 0, Math.sin(radians));
            if (actual.dot(new Vec3(nativeLocal.x, 0, nativeLocal.z).normalize()) < 1 - 1e-10) throw new AssertionError("native horizontal gaze angle");
        }
    }

    private static void fixture() {
        final var map = M43ResidualFixtureLayout.blocks();
        for (int lane = 0; lane < 4; lane++) {
            final BlockPos base = M43ResidualFixtureLayout.lane(lane);
            if (!map.get(base).contains("sticky_mechanical_piston") || !map.get(base.east()).contains("sticky_north=true")
                    || !map.get(base.east().south()).contains("chest") || !map.containsKey(base.east().above())) throw new AssertionError("known-good independent attachment");
            for (int x = 2; x <= 12; x++) {
                if (map.containsKey(base.offset(x, 1, 0))) throw new AssertionError("stationary hardware in actor travel lane");
            }
        }
        if (!M43ResidualFixtureLayout.reservation().containsAll(map.keySet()) || M43ResidualFixtureLayout.reservation().contains(new BlockPos(0, -3, 0))) throw new AssertionError("floor/footprint");
        if (!M43ResidualFixtureLayout.allPassed(true, true, true, true, true, true)) throw new AssertionError("aggregate acceptance");
        for (int i = 0; i < 6; i++) {
            final boolean[] gates = {true, true, true, true, true, true}; gates[i] = false;
            if (M43ResidualFixtureLayout.allPassed(gates[0], gates[1], gates[2], gates[3], gates[4], gates[5])) throw new AssertionError("incomplete aggregate PASS");
        }
    }

    private static void near(final double actual, final double expected, final String label) {
        if (Math.abs(actual - expected) > 1e-9) throw new AssertionError(label + " actual=" + actual + " expected=" + expected);
    }
}
