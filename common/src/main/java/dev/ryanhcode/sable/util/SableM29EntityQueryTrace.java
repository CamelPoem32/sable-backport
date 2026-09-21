package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded counters and anomaly-only logging for M29 coordinate-space entity queries. */
public final class SableM29EntityQueryTrace {
    public static final String TRACE_PROPERTY = "sable.m29.traceEntityQueries";
    private static final boolean ENABLED = Boolean.getBoolean(TRACE_PROPERTY);
    private static final int MAX_EXAMPLES_PER_CATEGORY = 3;
    private static final BoundedDiagnosticCounters ROUTES =
            new BoundedDiagnosticCounters(Route.values().length, MAX_EXAMPLES_PER_CATEGORY);
    private static final BoundedDiagnosticCounters ANOMALIES =
            new BoundedDiagnosticCounters(Anomaly.values().length, MAX_EXAMPLES_PER_CATEGORY);
    private static final AtomicLong TOTAL_QUERIES = new AtomicLong();
    private static final AtomicLong JADE_NORMALIZED = new AtomicLong();
    private static final AtomicLong DEDUPLICATED_ENTITIES = new AtomicLong();

    private SableM29EntityQueryTrace() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void beginQuery() {
        if (enabled()) {
            TOTAL_QUERIES.incrementAndGet();
        }
    }

    public static void recordRoute(final String routeName, final AABB inputBounds,
                                   final AABB backendBounds, final String targetSubLevel,
                                   final int newResults) {
        if (!enabled()) {
            return;
        }
        final Route route = Route.valueOf(routeName);
        if (!ROUTES.record(route.ordinal())) {
            return;
        }
        Sable.LOGGER.info("SABLE_M29_ENTITY_QUERY event=ROUTE_EXAMPLE route={} inputAabb={} "
                        + "backendAabb={} targetSubLevel={} resultCount={} exampleCap={}",
                route, inputBounds, backendBounds, targetSubLevel, newResults,
                MAX_EXAMPLES_PER_CATEGORY);
    }

    public static void recordJadeNormalization(final AABB ignoredMixedBounds,
                                                final AABB normalizedBounds) {
        if (!enabled()) {
            return;
        }
        final long count = JADE_NORMALIZED.incrementAndGet();
        if (count <= MAX_EXAMPLES_PER_CATEGORY) {
            Sable.LOGGER.info("SABLE_M29_ENTITY_QUERY event=JADE_NORMALIZATION_EXAMPLE "
                            + "ignoredMixedAabb={} normalizedParentVisibleAabb={} exampleCap={}",
                    ignoredMixedBounds, normalizedBounds, MAX_EXAMPLES_PER_CATEGORY);
        }
    }

    public static void recordDuplicateEntity() {
        if (enabled()) {
            DEDUPLICATED_ENTITIES.incrementAndGet();
        }
    }

    public static void anomaly(final Anomaly anomaly, final String decision,
                               final AABB bounds, @Nullable final String details) {
        if (!enabled() || !ANOMALIES.record(anomaly.ordinal())) {
            return;
        }
        final Caller caller = caller();
        Sable.LOGGER.info("SABLE_M29_ENTITY_QUERY event=ANOMALY category={} decision={} "
                        + "callerClass={} callerMethod={} bounds={} details={} exampleCap={}",
                anomaly, decision, caller.className(), caller.methodName(), bounds,
                details == null ? "none" : details, MAX_EXAMPLES_PER_CATEGORY);
    }

    public static void summaryAndReset(final String reason, final String levelDescription) {
        if (!enabled()) {
            return;
        }
        final long total = TOTAL_QUERIES.getAndSet(0L);
        final long jade = JADE_NORMALIZED.getAndSet(0L);
        final long deduplicated = DEDUPLICATED_ENTITIES.getAndSet(0L);
        final long[] routeCounts = resetCounts(ROUTES, Route.values().length);
        final long[] anomalyCounts = resetCounts(ANOMALIES, Anomaly.values().length);
        ROUTES.resetExamples();
        ANOMALIES.resetExamples();
        if (total == 0L && jade == 0L && sum(anomalyCounts) == 0L) {
            return;
        }
        Sable.LOGGER.info("SABLE_M29_QUERY_SUMMARY reason={} level={} totalQueries={} "
                        + "parentVisibleDirectCount={} sublevelRawDirectCount={} "
                        + "sublevelRawToParentVisibleCount={} parentVisibleToSublevelRawCount={} "
                        + "jadeNormalizedCount={} mixedSpaceDetectedCount={} oversizedInputCount={} "
                        + "oversizedConvertedCount={} nonFiniteBoundsCount={} conversionFailureCount={} "
                        + "backendSpaceMismatchCount={} queryRejectedCount={} deduplicatedEntityCount={} "
                        + "maxExamplesPerCategory={}",
                reason, levelDescription, total,
                routeCounts[Route.PARENT_VISIBLE_DIRECT.ordinal()],
                routeCounts[Route.SUBLEVEL_RAW_DIRECT.ordinal()],
                routeCounts[Route.SUBLEVEL_RAW_TO_PARENT_VISIBLE.ordinal()],
                routeCounts[Route.PARENT_VISIBLE_TO_SUBLEVEL_RAW.ordinal()],
                jade,
                anomalyCounts[Anomaly.MIXED_COORDINATE_SPACE.ordinal()],
                anomalyCounts[Anomaly.OVERSIZED_INPUT.ordinal()],
                anomalyCounts[Anomaly.OVERSIZED_CONVERTED.ordinal()],
                anomalyCounts[Anomaly.NON_FINITE_BOUNDS.ordinal()],
                anomalyCounts[Anomaly.CONVERSION_FAILURE.ordinal()],
                anomalyCounts[Anomaly.BACKEND_SPACE_MISMATCH.ordinal()],
                anomalyCounts[Anomaly.QUERY_REJECTED.ordinal()],
                deduplicated, MAX_EXAMPLES_PER_CATEGORY);
    }

    private static long[] resetCounts(final BoundedDiagnosticCounters counters, final int size) {
        final long[] result = new long[size];
        for (int index = 0; index < size; index++) {
            result[index] = counters.getAndResetCount(index);
        }
        return result;
    }

    private static long sum(final long[] values) {
        long total = 0L;
        for (final long value : values) {
            total += value;
        }
        return total;
    }

    private static Caller caller() {
        return StackWalker.getInstance().walk(stream -> stream
                .filter(frame -> !frame.getClassName().startsWith(SableM29EntityQueryTrace.class.getName()))
                .filter(frame -> !frame.getClassName().equals(SubLevelInclusiveLevelEntityGetter.class.getName()))
                .filter(frame -> !frame.getClassName().startsWith("net.minecraft.world.level.entity."))
                .findFirst()
                .map(frame -> new Caller(frame.getClassName(), frame.getMethodName()))
                .orElse(new Caller("UNKNOWN", "UNKNOWN")));
    }

    public enum Anomaly {
        MIXED_COORDINATE_SPACE,
        OVERSIZED_INPUT,
        OVERSIZED_CONVERTED,
        NON_FINITE_BOUNDS,
        CONVERSION_FAILURE,
        BACKEND_SPACE_MISMATCH,
        QUERY_REJECTED
    }

    private enum Route {
        PARENT_VISIBLE_DIRECT,
        SUBLEVEL_RAW_DIRECT,
        SUBLEVEL_RAW_TO_PARENT_VISIBLE,
        PARENT_VISIBLE_TO_SUBLEVEL_RAW
    }

    private record Caller(String className, String methodName) {
    }
}
