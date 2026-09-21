package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Queries parent-visible and raw Sable entity storage without mixing their coordinate spaces. */
public class SubLevelInclusiveLevelEntityGetter<T extends EntityAccess> implements LevelEntityGetter<T> {
    public static final int MAX_GET_SIDE_LENGTH = 100_000;
    private static final AtomicBoolean LARGE_QUERY_WARNING_EMITTED = new AtomicBoolean();

    private final Level level;
    private final LevelEntityGetter<T> delegate;

    public SubLevelInclusiveLevelEntityGetter(final Level level, final LevelEntityGetter<T> delegate) {
        this.level = level;
        this.delegate = delegate;
    }

    @Override
    public @Nullable T get(final int id) {
        return this.delegate.get(id);
    }

    @Override
    public @Nullable T get(final UUID uuid) {
        return this.delegate.get(uuid);
    }

    @Override
    public @NotNull Iterable<T> getAll() {
        return this.delegate.getAll();
    }

    @Override
    public <U extends T> void get(final EntityTypeTest<T, U> type,
                                  final AbortableIterationConsumer<U> consumer) {
        this.delegate.get(type, consumer);
    }

    @Override
    public void get(final AABB inputBounds, final Consumer<T> consumer) {
        final QueryDispatch<T> dispatch = new QueryDispatch<>(inputBounds, "UNTYPED_SPATIAL");
        if (!dispatch.valid()) {
            return;
        }
        final Consumer<T> unique = dispatch.uniqueConsumer(consumer);
        this.querySpaces(inputBounds, (route, subLevel, bounds) -> {
            if (!dispatch.validBackend(route, bounds)) {
                return;
            }
            final int before = dispatch.resultCount();
            this.delegate.get(bounds, unique);
            dispatch.route(route, subLevel, bounds, dispatch.resultCount() - before);
        });
    }

    @Override
    public <U extends T> void get(final @NotNull EntityTypeTest<T, U> type,
                                  final AABB inputBounds,
                                  final AbortableIterationConsumer<U> consumer) {
        final QueryDispatch<U> dispatch = new QueryDispatch<>(inputBounds, "TYPED_SPATIAL");
        if (!dispatch.valid()) {
            return;
        }
        final AbortableIterationConsumer<U> unique = dispatch.uniqueAbortable(consumer);
        this.querySpaces(inputBounds, (route, subLevel, bounds) -> {
            if (dispatch.aborted() || !dispatch.validBackend(route, bounds)) {
                return;
            }
            final int before = dispatch.resultCount();
            this.delegate.get(type, bounds, unique);
            dispatch.route(route, subLevel, bounds, dispatch.resultCount() - before);
        });
    }

    public void getIgnoringSubLevels(final AABB bounds, final Consumer<T> consumer) {
        this.delegate.get(bounds, consumer);
    }

    public <U extends T> void getIgnoringSubLevels(final EntityTypeTest<T, U> type,
                                                    final AABB bounds,
                                                    final AbortableIterationConsumer<U> consumer) {
        this.delegate.get(type, bounds, consumer);
    }

    private void querySpaces(final AABB inputBounds, final SpaceQuery query) {
        final SubLevel sourceSubLevel = Sable.HELPER.getContaining(this.level, inputBounds.getCenter());
        final AABB parentVisibleBounds = sourceSubLevel == null
                ? inputBounds
                : new BoundingBox3d(inputBounds)
                        .transform(SubLevelEntityQueryBounds.pose(this.level, sourceSubLevel), new BoundingBox3d())
                        .toMojang();

        if (sourceSubLevel == null) {
            query.accept("PARENT_VISIBLE_DIRECT", null, parentVisibleBounds);
        } else {
            query.accept("SUBLEVEL_RAW_DIRECT", sourceSubLevel, inputBounds);
            query.accept("SUBLEVEL_RAW_TO_PARENT_VISIBLE", null, parentVisibleBounds);
        }

        final BoundingBox3d parentVisible = new BoundingBox3d(parentVisibleBounds);
        final BoundingBox3d rawScratch = new BoundingBox3d();
        for (final SubLevel target : Sable.HELPER.getAllIntersecting(this.level, parentVisible)) {
            if (target == sourceSubLevel) {
                continue;
            }
            parentVisible.transformInverse(
                    SubLevelEntityQueryBounds.pose(this.level, target), rawScratch);
            query.accept("PARENT_VISIBLE_TO_SUBLEVEL_RAW", target, rawScratch.toMojang());
        }
    }

    @FunctionalInterface
    private interface SpaceQuery {
        void accept(String route, @Nullable SubLevel subLevel, AABB bounds);
    }

    private final class QueryDispatch<U extends EntityAccess> {
        private final AABB inputBounds;
        private final Map<U, Boolean> emitted = new IdentityHashMap<>();
        private final SubLevel inputOwner;
        private final boolean valid;
        private boolean aborted;

        private QueryDispatch(final AABB inputBounds, final String kind) {
            this.inputBounds = inputBounds;
            SableM29EntityQueryTrace.beginQuery();
            final boolean finite = finite(inputBounds);
            this.inputOwner = finite ? Sable.HELPER.getContaining(
                    SubLevelInclusiveLevelEntityGetter.this.level, inputBounds.getCenter()) : null;
            this.valid = finite && inputBounds.getXsize() <= MAX_GET_SIDE_LENGTH
                    && inputBounds.getYsize() <= MAX_GET_SIDE_LENGTH
                    && inputBounds.getZsize() <= MAX_GET_SIDE_LENGTH;
            if (!this.valid) {
                this.logRejectedInput(finite, kind);
            }
        }

        private boolean valid() {
            return this.valid;
        }

        private int resultCount() {
            return this.emitted.size();
        }

        private boolean aborted() {
            return this.aborted;
        }

        private Consumer<U> uniqueConsumer(final Consumer<U> target) {
            return value -> {
                if (this.emitted.put(value, Boolean.TRUE) == null) {
                    target.accept(value);
                } else {
                    SableM29EntityQueryTrace.recordDuplicateEntity();
                }
            };
        }

        private AbortableIterationConsumer<U> uniqueAbortable(final AbortableIterationConsumer<U> target) {
            return value -> {
                if (this.emitted.put(value, Boolean.TRUE) != null) {
                    SableM29EntityQueryTrace.recordDuplicateEntity();
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                }
                final AbortableIterationConsumer.Continuation result = target.accept(value);
                this.aborted |= result.shouldAbort();
                return result;
            };
        }

        private void route(final String route, @Nullable final SubLevel target,
                           final AABB delegatedBounds, final int newResults) {
            SableM29EntityQueryTrace.recordRoute(route, this.inputBounds, delegatedBounds,
                    target == null ? "none" : target.getUniqueId().toString(), newResults);
        }

        private boolean validBackend(final String route, final AABB bounds) {
            if (!finite(bounds)) {
                SableM29EntityQueryTrace.anomaly(SableM29EntityQueryTrace.Anomaly.NON_FINITE_BOUNDS,
                        "REJECT_BACKEND", bounds, "route=" + route);
                SableM29EntityQueryTrace.anomaly(SableM29EntityQueryTrace.Anomaly.QUERY_REJECTED,
                        "REJECT_BACKEND", bounds, "route=" + route);
                return false;
            }
            if (bounds.getXsize() > MAX_GET_SIDE_LENGTH || bounds.getYsize() > MAX_GET_SIDE_LENGTH
                    || bounds.getZsize() > MAX_GET_SIDE_LENGTH) {
                SableM29EntityQueryTrace.anomaly(SableM29EntityQueryTrace.Anomaly.OVERSIZED_CONVERTED,
                        "REJECT_BACKEND", bounds, "route=" + route);
                SableM29EntityQueryTrace.anomaly(SableM29EntityQueryTrace.Anomaly.QUERY_REJECTED,
                        "REJECT_BACKEND", bounds, "route=" + route);
                return false;
            }
            return true;
        }

        private void logRejectedInput(final boolean finite, final String kind) {
            final boolean emitWarning = LARGE_QUERY_WARNING_EMITTED.compareAndSet(false, true);
            if (emitWarning) {
                Sable.LOGGER.warn("Rejected non-finite or abnormally large entity query; bounds={}",
                        this.inputBounds);
            }
            final SableM29EntityQueryTrace.Anomaly anomaly = finite
                    ? SableM29EntityQueryTrace.Anomaly.OVERSIZED_INPUT
                    : SableM29EntityQueryTrace.Anomaly.NON_FINITE_BOUNDS;
            SableM29EntityQueryTrace.anomaly(anomaly, "REJECT_INPUT", this.inputBounds,
                    "kind=" + kind + ",inputSpace="
                            + (this.inputOwner == null ? "PARENT_VISIBLE" : "SUBLEVEL_RAW_PLOT"));
            SableM29EntityQueryTrace.anomaly(SableM29EntityQueryTrace.Anomaly.QUERY_REJECTED,
                    "REJECT_INPUT", this.inputBounds, "kind=" + kind);
        }
    }

    private static boolean finite(final AABB bounds) {
        return Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY)
                && Double.isFinite(bounds.maxZ);
    }
}
