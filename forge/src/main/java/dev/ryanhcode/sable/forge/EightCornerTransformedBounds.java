package dev.ryanhcode.sable.forge;

final class EightCornerTransformedBounds {
    record Point(double x, double y, double z) {
        boolean finite() {
            return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
        }
    }

    record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boolean finite() {
            return Double.isFinite(this.minX) && Double.isFinite(this.minY) && Double.isFinite(this.minZ)
                    && Double.isFinite(this.maxX) && Double.isFinite(this.maxY) && Double.isFinite(this.maxZ)
                    && this.minX <= this.maxX && this.minY <= this.maxY && this.minZ <= this.maxZ;
        }
    }

    record Result(Bounds bounds, Point[] localCorners, Point[] rawCorners, Point[] visibleCorners) {
    }

    @FunctionalInterface
    interface PointTransform {
        Point transform(Point point);
    }

    private EightCornerTransformedBounds() {
    }

    static Result transform(final Bounds localBounds, final PointTransform createTransform,
                            final PointTransform sableTransform) {
        if (!localBounds.finite()) {
            throw new IllegalArgumentException("Non-finite local contraption bounds");
        }
        final Point[] localCorners = new Point[8];
        final Point[] rawCorners = new Point[8];
        final Point[] visibleCorners = new Point[8];
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            final Point local = new Point(
                    (corner & 1) == 0 ? localBounds.minX : localBounds.maxX,
                    (corner & 2) == 0 ? localBounds.minY : localBounds.maxY,
                    (corner & 4) == 0 ? localBounds.minZ : localBounds.maxZ);
            final Point raw = createTransform.transform(local);
            final Point visible = sableTransform.transform(raw);
            if (!raw.finite() || !visible.finite()) {
                throw new IllegalArgumentException("Non-finite transformed contraption corner");
            }
            localCorners[corner] = local;
            rawCorners[corner] = raw;
            visibleCorners[corner] = visible;
            minX = Math.min(minX, visible.x);
            minY = Math.min(minY, visible.y);
            minZ = Math.min(minZ, visible.z);
            maxX = Math.max(maxX, visible.x);
            maxY = Math.max(maxY, visible.y);
            maxZ = Math.max(maxZ, visible.z);
        }
        return new Result(new Bounds(minX, minY, minZ, maxX, maxY, maxZ),
                localCorners, rawCorners, visibleCorners);
    }
}
