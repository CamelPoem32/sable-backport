package dev.ryanhcode.sable.compatibility.create.funnel;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.command.M37FunnelRunAcceptance;
import dev.ryanhcode.sable.command.M37FixtureGeometryObservation;
import dev.ryanhcode.sable.command.M37FunnelFixtureLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Pure physical-cell contract; native Create transfer remains a runtime gate. */
public final class M37FunnelGeometryTest {
    private static final Vec3 RAW = new Vec3(20_000_001.5, 90.5, 20_000_000.5);

    private M37FunnelGeometryTest() {
    }

    public static void run() {
        assertCell(pose(10.5, 90.5, 11.5, new Quaterniond()), RAW, Direction.EAST,
                new BlockPos(11, 90, 11), Direction.EAST, "translation");
        assertCell(pose(-8.5, 90.5, -3.5, new Quaterniond()), RAW, Direction.EAST,
                new BlockPos(-8, 90, -4), Direction.EAST, "negative coordinates");
        assertCell(pose(10.5, 90.5, 11.5, new Quaterniond().rotateY(Math.PI / 2)), RAW, Direction.EAST,
                new BlockPos(10, 90, 10), Direction.NORTH, "yaw90");
        assertCell(pose(10.5, 90.5, 11.5, new Quaterniond()), RAW.add(1, 0, 0), Direction.EAST,
                new BlockPos(12, 90, 11), Direction.EAST, "target boundary");
        assertRejected(new Quaterniond().rotateX(Math.toRadians(5)), "pitch");
        assertRejected(new Quaterniond().rotateZ(Math.toRadians(5)), "roll");
        assertRejected(new Quaterniond().rotateY(Math.PI / 4), "diagonal yaw");
        assertNativeFixtureGeometry();
        assertTimedStationaryOutput();
        assertPerKindAcceptance();
        assertNullableActorObservation();
    }

    private static void assertNullableActorObservation() {
        final Pose3d body = pose(10.5, 90.5, 11.5, new Quaterniond());
        final Vec3 offset = new Vec3(0, 0.65, 0);
        final var firstTick = M37FixtureGeometryObservation.captureActorPose(body, null, offset);
        if (firstTick.isPresent()) {
            throw new AssertionError("new Create actor must remain pending before tickActors assigns position");
        }
        var phase = M37FixtureGeometryObservation.nextPhase(null, false, false, false);
        if (phase != M37FixtureGeometryObservation.Phase.WAIT_FOR_MOVING_CONTRAPTION) {
            throw new AssertionError("fixture must wait for native piston assembly");
        }
        phase = M37FixtureGeometryObservation.nextPhase(phase, true, false, false);
        if (phase != M37FixtureGeometryObservation.Phase.WAIT_FOR_ACTOR_POSITION) {
            throw new AssertionError("captured actor must wait for its first native actor tick");
        }
        final var laterTick = M37FixtureGeometryObservation.captureActorPose(body, RAW, offset);
        if (laterTick.isEmpty() || laterTick.get().visibleActivePoint() == null) {
            throw new AssertionError("actor pose must become observable after Create assigns position");
        }
        phase = M37FixtureGeometryObservation.nextPhase(phase, true, true, false);
        if (phase != M37FixtureGeometryObservation.Phase.WAIT_FOR_RELEVANT_ITEM_ENTITY) {
            throw new AssertionError("missing item must not fabricate a geometry snapshot");
        }
        phase = M37FixtureGeometryObservation.nextPhase(phase, true, true, true);
        if (phase != M37FixtureGeometryObservation.Phase.CAPTURED) {
            throw new AssertionError("later item appearance must permit a snapshot");
        }
        if (M37FixtureGeometryObservation.nextPhase(phase, true, true, false)
                != M37FixtureGeometryObservation.Phase.CAPTURED) {
            throw new AssertionError("item disappearing after capture must not invalidate its snapshot");
        }
        final var vanishedBeforeSampling = M37FixtureGeometryObservation.nextPhase(
                M37FixtureGeometryObservation.Phase.WAIT_FOR_RELEVANT_ITEM_ENTITY,
                true, true, false);
        if (vanishedBeforeSampling != M37FixtureGeometryObservation.Phase.WAIT_FOR_RELEVANT_ITEM_ENTITY) {
            throw new AssertionError("vanished item must remain pending without an exception");
        }
        if (M37FixtureGeometryObservation.actorPositionUnavailable(laterTick.get().visibleActivePoint(), 400)
                || !M37FixtureGeometryObservation.actorPositionUnavailable(null, 400)) {
            throw new AssertionError("normal disassembly must retain the last valid actor pose");
        }
    }

    private static void assertNativeFixtureGeometry() {
        final BlockPos origin = BlockPos.ZERO;
        final BlockPos collector = new BlockPos(12, -2, 0);
        final Vec3 nativeOutput = new Vec3(12.125, 1.65, 0.5);
        if (!M37FunnelFixtureLayout.collisionFree(origin, collector)
                || !M37FunnelFixtureLayout.fallingItemReachesIntake(nativeOutput, collector)
                || M37FunnelFixtureLayout.minimumBlockClearance(origin, collector) < 1) {
            throw new AssertionError("INSERT native drop column must be clear of every swept payload cell");
        }
        if (M37FunnelFixtureLayout.collisionFree(origin, new BlockPos(12, 0, 0))
                || M37FunnelFixtureLayout.fallingItemReachesIntake(nativeOutput,
                        new BlockPos(13, -2, 0))) {
            throw new AssertionError("old colliding/overrun INSERT geometry must be rejected");
        }
        // Native west-facing stationary extract emits at center - 0.25 X,
        // +0.45 Y. Native moving succ queries AABB(target BlockPos).
        final AABB movingQuery = new AABB(new BlockPos(14, 1, 0));
        final AABB alignedOutput = itemBounds(new Vec3(14.25, 1.95, 0.5));
        final AABB oneBlockShort = itemBounds(new Vec3(15.25, 1.95, 0.5));
        if (!movingQuery.intersects(alignedOutput) || movingQuery.intersects(oneBlockShort)) {
            throw new AssertionError("EXTRACT native output misses or old one-block-short layout overlaps");
        }
    }

    private static void assertTimedStationaryOutput() {
        final BlockPos station = new BlockPos(14, 1, 0);
        final AABB nativeQuery = new AABB(station);
        final Vec3 stationaryOutput = new Vec3(14.25, 1.95, 0.5);
        final AABB settledOnSupport = itemBounds(new Vec3(14.25, 1.0, 0.5));
        if (!nativeQuery.intersects(itemBounds(stationaryOutput))
                || !nativeQuery.intersects(settledOnSupport)
                || !M37FixtureGeometryObservation.stationaryItemPrecedesQuery(20, 28, 1)
                || M37FixtureGeometryObservation.stationaryItemPrecedesQuery(28, 28, 1)
                || M37FixtureGeometryObservation.stationaryItemPrecedesQuery(20, 28, 0)) {
            throw new AssertionError("EXTRACT requires native emission, support, then a later succ query");
        }
        if (new AABB(new BlockPos(13, 0, 0)).intersects(new AABB(station.below()))) {
            throw new AssertionError("EXTRACT item support must lie beyond the moving chassis sweep");
        }
    }

    private static AABB itemBounds(final Vec3 feet) {
        return new AABB(feet.x - 0.125, feet.y, feet.z - 0.125,
                feet.x + 0.125, feet.y + 0.25, feet.z + 0.125);
    }

    private static void assertPerKindAcceptance() {
        final M37FunnelRunAcceptance.Result lostInsert = M37FunnelRunAcceptance.evaluate(
                false, true, true, true, false, false, false,
                true, false, 16, 12, 0, 0, false);
        if (!lostInsert.nativeTransfer() || lostInsert.endToEnd()) {
            throw new AssertionError("INSERT native emission cannot imply parent collection");
        }
        final M37FunnelRunAcceptance.Result entity = M37FunnelRunAcceptance.evaluate(
                true, false, true, false, true, false, false,
                false, true, 16, 0, 0, 16, false);
        if (!entity.targetResolved() || !entity.nativeTransfer() || !entity.endToEnd()) {
            throw new AssertionError("ENTITY native pickup must not require a stationary block target");
        }
        final M37FunnelRunAcceptance.Result missingNative = M37FunnelRunAcceptance.evaluate(
                false, false, true, true, false, true, true,
                false, false, 16, 15, 0, 1, false);
        if (missingNative.endToEnd()) {
            throw new AssertionError("count changes alone are not a native moving-Funnel witness");
        }
        final M37FunnelRunAcceptance.Result oneItem = M37FunnelRunAcceptance.evaluate(
                false, false, true, false, false, true, true,
                false, true, 16, 15, 0, 1, false);
        if (!oneItem.targetResolved() || !oneItem.nativeTransfer() || !oneItem.endToEnd()) {
            throw new AssertionError("one native stationary emission and later moving pickup must pass");
        }
        final M37FunnelRunAcceptance.Result extraItem = M37FunnelRunAcceptance.evaluate(
                false, false, true, false, false, true, true,
                false, true, 16, 14, 0, 1, false);
        if (extraItem.endToEnd()) {
            throw new AssertionError("second loose item must not pass deterministic EXTRACT gate");
        }
        final M37FunnelRunAcceptance.Result sameTick = M37FunnelRunAcceptance.evaluate(
                false, false, true, false, false, true, false,
                false, true, 16, 15, 0, 1, false);
        if (sameTick.endToEnd()) {
            throw new AssertionError("stationary item must precede the native moving query");
        }
    }

    private static void assertRejected(final Quaterniond rotation, final String label) {
        final SableMovingFunnelTarget.Result result = SableMovingFunnelTarget.resolveGeometry(
                pose(10.5, 90.5, 11.5, rotation), RAW, Vec3.atLowerCornerOf(Direction.EAST.getNormal()));
        if (result.decision() != SableMovingFunnelTarget.Decision.UNSUPPORTED_ORIENTATION) {
            throw new AssertionError(label + ": " + result);
        }
    }

    private static void assertCell(final Pose3d pose, final Vec3 raw, final Direction localFacing,
                                   final BlockPos expected, final Direction expectedFacing, final String label) {
        final SableMovingFunnelTarget.Result actual = SableMovingFunnelTarget.resolveGeometry(pose, raw,
                Vec3.atLowerCornerOf(localFacing.getNormal()));
        if (!actual.accepted() || !expected.equals(actual.parentBlock()) || actual.parentFacing() != expectedFacing) {
            throw new AssertionError(label + ": expected " + expected + "/" + expectedFacing + ", got " + actual);
        }
    }

    private static Pose3d pose(final double x, final double y, final double z, final Quaterniond rotation) {
        return new Pose3d(new Vector3d(x, y, z), rotation,
                new Vector3d(20_000_000.5, 90.5, 20_000_000.5), new Vector3d(1));
    }
}
