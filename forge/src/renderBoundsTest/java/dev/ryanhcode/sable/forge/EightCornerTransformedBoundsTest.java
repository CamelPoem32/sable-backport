package dev.ryanhcode.sable.forge;

public final class EightCornerTransformedBoundsTest {
    private static final double EPSILON = 1.0e-8;
    private static final EightCornerTransformedBounds.Bounds LOCAL =
            new EightCornerTransformedBounds.Bounds(-1.0, -0.25, -0.5, 2.0, 0.75, 1.0);

    private EightCornerTransformedBoundsTest() {
    }

    public static void run() {
        verify(0, 0, 0, 0, 0, 0, false);
        verify(45, 0, 0, 0, 0, 0, false);
        verify(0, 90, 0, 0, 0, 0, false);
        verify(45, 90, 0, 0, 0, 0, false);
        verify(0, 0, 90, 0, 0, 0, false);
        verify(-32, -71, 18, 4, -3, 2, false);
        verify(27, 43, -19, 1.5, -2.25, 3.75, false);
        verify(35, 79, 53, -2, 3, 1, true);
    }

    private static void verify(final double bearingDegrees, final double outerYDegrees,
                               final double outerZDegrees, final double pivotX, final double pivotY,
                               final double pivotZ, final boolean hiddenPlot) {
        final double hidden = hiddenPlot ? 20_000_000.0 : 0.0;
        final EightCornerTransformedBounds.Point pivot = new EightCornerTransformedBounds.Point(
                pivotX, pivotY, pivotZ);
        final EightCornerTransformedBounds.Point createCenter = new EightCornerTransformedBounds.Point(0.5, 0.5, 0.5);
        final EightCornerTransformedBounds.PointTransform create = point -> {
            final var centered = subtract(point, createCenter);
            final var rotated = rotateZ(centered, bearingDegrees);
            return add(add(rotated, createCenter), new EightCornerTransformedBounds.Point(hidden + 12, 98, hidden + 40));
        };
        final EightCornerTransformedBounds.PointTransform sable = point -> {
            final var rawPivot = new EightCornerTransformedBounds.Point(hidden + pivot.x(), pivot.y(), hidden + pivot.z());
            final var centered = subtract(point, rawPivot);
            final var rotated = rotateZ(rotateY(centered, outerYDegrees), outerZDegrees);
            return add(rotated, new EightCornerTransformedBounds.Point(8, 96, 39));
        };
        final var result = EightCornerTransformedBounds.transform(LOCAL, create, sable);
        final var bounds = result.bounds();
        require(bounds.finite(), "finite and ordered bounds");
        require(result.localCorners().length == 8 && result.rawCorners().length == 8
                && result.visibleCorners().length == 8, "eight corners");
        double referenceMinX = Double.POSITIVE_INFINITY;
        double referenceMinY = Double.POSITIVE_INFINITY;
        double referenceMinZ = Double.POSITIVE_INFINITY;
        double referenceMaxX = Double.NEGATIVE_INFINITY;
        double referenceMaxY = Double.NEGATIVE_INFINITY;
        double referenceMaxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{LOCAL.minX(), LOCAL.maxX()}) {
            for (double y : new double[]{LOCAL.minY(), LOCAL.maxY()}) {
                for (double z : new double[]{LOCAL.minZ(), LOCAL.maxZ()}) {
                    final var visible = sable.transform(create.transform(new EightCornerTransformedBounds.Point(x, y, z)));
                    referenceMinX = Math.min(referenceMinX, visible.x());
                    referenceMinY = Math.min(referenceMinY, visible.y());
                    referenceMinZ = Math.min(referenceMinZ, visible.z());
                    referenceMaxX = Math.max(referenceMaxX, visible.x());
                    referenceMaxY = Math.max(referenceMaxY, visible.y());
                    referenceMaxZ = Math.max(referenceMaxZ, visible.z());
                }
            }
        }
        require(Math.abs(bounds.minX() - referenceMinX) < EPSILON
                && Math.abs(bounds.minY() - referenceMinY) < EPSILON
                && Math.abs(bounds.minZ() - referenceMinZ) < EPSILON
                && Math.abs(bounds.maxX() - referenceMaxX) < EPSILON
                && Math.abs(bounds.maxY() - referenceMaxY) < EPSILON
                && Math.abs(bounds.maxZ() - referenceMaxZ) < EPSILON,
                "production bounds differ from reference eight-corner transform");
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    final var sample = new EightCornerTransformedBounds.Point(
                            LOCAL.minX() + (LOCAL.maxX() - LOCAL.minX()) * x / 4.0,
                            LOCAL.minY() + (LOCAL.maxY() - LOCAL.minY()) * y / 4.0,
                            LOCAL.minZ() + (LOCAL.maxZ() - LOCAL.minZ()) * z / 4.0);
                    final var visible = sable.transform(create.transform(sample));
                    require(inside(bounds, visible), "transformed geometry sample outside visible AABB");
                }
            }
        }
        if (hiddenPlot) {
            require(Math.abs(bounds.minX()) < 1000 && Math.abs(bounds.maxZ()) < 1000,
                    "hidden plot leaked into visible bounds");
        }
    }

    private static boolean inside(final EightCornerTransformedBounds.Bounds bounds,
                                  final EightCornerTransformedBounds.Point point) {
        return point.x() >= bounds.minX() - EPSILON && point.x() <= bounds.maxX() + EPSILON
                && point.y() >= bounds.minY() - EPSILON && point.y() <= bounds.maxY() + EPSILON
                && point.z() >= bounds.minZ() - EPSILON && point.z() <= bounds.maxZ() + EPSILON;
    }

    private static EightCornerTransformedBounds.Point rotateY(final EightCornerTransformedBounds.Point point,
                                                               final double degrees) {
        final double angle = Math.toRadians(degrees);
        return new EightCornerTransformedBounds.Point(
                Math.cos(angle) * point.x() + Math.sin(angle) * point.z(), point.y(),
                -Math.sin(angle) * point.x() + Math.cos(angle) * point.z());
    }

    private static EightCornerTransformedBounds.Point rotateZ(final EightCornerTransformedBounds.Point point,
                                                               final double degrees) {
        final double angle = Math.toRadians(degrees);
        return new EightCornerTransformedBounds.Point(
                Math.cos(angle) * point.x() - Math.sin(angle) * point.y(),
                Math.sin(angle) * point.x() + Math.cos(angle) * point.y(), point.z());
    }

    private static EightCornerTransformedBounds.Point add(final EightCornerTransformedBounds.Point a,
                                                           final EightCornerTransformedBounds.Point b) {
        return new EightCornerTransformedBounds.Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static EightCornerTransformedBounds.Point subtract(final EightCornerTransformedBounds.Point a,
                                                                final EightCornerTransformedBounds.Point b) {
        return new EightCornerTransformedBounds.Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z());
    }

    private static void require(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
