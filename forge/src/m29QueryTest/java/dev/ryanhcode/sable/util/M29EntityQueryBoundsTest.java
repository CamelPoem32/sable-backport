package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Standalone math regression for the production eight-corner entity-query conversion. */
public final class M29EntityQueryBoundsTest {
    private static final double EPSILON = 1.0e-8;

    private M29EntityQueryBoundsTest() {
    }

    public static void run() {
        final double plotX = 20_481_040.0;
        final double plotZ = 20_665_360.0;
        final AABB raw = new AABB(plotX - 2.0, 127.0, plotZ - 1.0,
                plotX + 3.0, 131.0, plotZ + 4.0);
        final Pose3d first = pose(plotX, 129.0, plotZ, 8.0, 100.0, 61.0, 37.0);
        final BoundingBox3d visible = new BoundingBox3d(raw).transform(first, new BoundingBox3d());
        requireSane(visible, "rotated visible query");
        verifyIndependentCorners(raw, first, visible);

        final BoundingBox3d rawRoundTrip = new BoundingBox3d(visible)
                .transformInverse(first, new BoundingBox3d());
        requireSane(rawRoundTrip, "round-trip raw query");
        require(contains(rawRoundTrip, raw), "round-trip AABB must contain the original raw query");

        final Pose3d second = pose(plotX + 2_048.0, 129.0, plotZ + 2_048.0,
                -12.0, 96.0, 48.0, -71.0);
        final BoundingBox3d secondRaw = new BoundingBox3d(visible)
                .transformInverse(second, new BoundingBox3d());
        requireSane(secondRaw, "second-body raw query");
        require(Math.abs(secondRaw.minX() - visible.minX()) > 1_000_000.0,
                "second-body query must be converted into its own plot space");

        final AABB edge = new AABB(visible.minX() - 0.01, visible.minY(), visible.minZ(),
                visible.minX() + 0.01, visible.maxY(), visible.maxZ());
        final BoundingBox3d edgeRaw = new BoundingBox3d(edge)
                .transformInverse(first, new BoundingBox3d());
        requireSane(edgeRaw, "body-edge raw query");
    }

    private static Pose3d pose(final double rawX, final double rawY, final double rawZ,
                               final double visibleX, final double visibleY, final double visibleZ,
                               final double yawDegrees) {
        return new Pose3d(new Vector3d(visibleX, visibleY, visibleZ),
                new Quaterniond().rotationY(Math.toRadians(yawDegrees)),
                new Vector3d(rawX, rawY, rawZ), new Vector3d(1.0));
    }

    private static void verifyIndependentCorners(final AABB source, final Pose3d pose,
                                                  final BoundingBox3d actual) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            final Vector3d transformed = pose.transformPosition(new Vector3d(
                    (corner & 1) == 0 ? source.minX : source.maxX,
                    (corner & 2) == 0 ? source.minY : source.maxY,
                    (corner & 4) == 0 ? source.minZ : source.maxZ));
            minX = Math.min(minX, transformed.x);
            minY = Math.min(minY, transformed.y);
            minZ = Math.min(minZ, transformed.z);
            maxX = Math.max(maxX, transformed.x);
            maxY = Math.max(maxY, transformed.y);
            maxZ = Math.max(maxZ, transformed.z);
        }
        require(close(actual.minX(), minX) && close(actual.minY(), minY) && close(actual.minZ(), minZ)
                        && close(actual.maxX(), maxX) && close(actual.maxY(), maxY) && close(actual.maxZ(), maxZ),
                "production transform must equal an independent eight-corner reference");
    }

    private static boolean contains(final BoundingBox3d outer, final AABB inner) {
        return outer.minX() <= inner.minX + EPSILON && outer.minY() <= inner.minY + EPSILON
                && outer.minZ() <= inner.minZ + EPSILON && outer.maxX() >= inner.maxX - EPSILON
                && outer.maxY() >= inner.maxY - EPSILON && outer.maxZ() >= inner.maxZ - EPSILON;
    }

    private static void requireSane(final BoundingBox3d bounds, final String description) {
        require(Double.isFinite(bounds.minX()) && Double.isFinite(bounds.maxX())
                        && Double.isFinite(bounds.minY()) && Double.isFinite(bounds.maxY())
                        && Double.isFinite(bounds.minZ()) && Double.isFinite(bounds.maxZ()),
                description + " must be finite");
        require(bounds.maxX() - bounds.minX() < 32.0
                        && bounds.maxY() - bounds.minY() < 32.0
                        && bounds.maxZ() - bounds.minZ() < 32.0,
                description + " must remain local and sane");
    }

    private static boolean close(final double first, final double second) {
        return Math.abs(first - second) < EPSILON;
    }

    private static void require(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
