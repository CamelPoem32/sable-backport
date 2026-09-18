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
import org.joml.Matrix4d;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A {@link LevelEntityGetter} that delegates all calls to a child, taking into account sub-levels and their plots
 *
 * @param <T>
 */
public class SubLevelInclusiveLevelEntityGetter<T extends EntityAccess> implements LevelEntityGetter<T> {
    public static final int MAX_GET_SIDE_LENGTH = 100_000;
    private static final boolean TRACE_M28_ENTITY_OWNERSHIP =
            Boolean.getBoolean("sable.m28.visualOwnershipTrace");
    private static final AtomicLong QUERY_IDS = new AtomicLong();

    private final Level level;
    private final LevelEntityGetter<T> delegate;

    public SubLevelInclusiveLevelEntityGetter(final Level level, final LevelEntityGetter<T> delegate) {
        this.level = level;
        this.delegate = delegate;
    }

    private static void logError(final AABB aabb) {
        Sable.LOGGER.error("Aborting entity get for abnormally large AABB: {}", aabb, new Throwable("Stack Trace"));
    }

    @Override
    public @Nullable T get(final int i) {
        return this.delegate.get(i);
    }

    @Override
    public @Nullable T get(final UUID uUID) {
        return this.delegate.get(uUID);
    }

    @Override
    public @NotNull Iterable<T> getAll() {
        return this.delegate.getAll();
    }

    @Override
    public <U extends T> void get(final EntityTypeTest<T, U> entityTypeTest, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, abortableIterationConsumer);
    }

    @Override
    public void get(AABB aABB, final Consumer<T> consumer) {
        final QueryTrace trace = new QueryTrace(aABB, "UNTYPED_SPATIAL");
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            trace.aborted();
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());

        this.delegate.get(aABB, trace.wrap("DIRECT_PARENT_SPACE", aABB, consumer));

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        final Matrix4d bakedMatrix = new Matrix4d();
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(aABB, trace.wrap("CONTAINING_SUBLEVEL_TO_VISIBLE", aABB, consumer));
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bakedMatrix, bb).toMojang();

            this.delegate.get(localBounds, trace.wrap("VISIBLE_TO_INTERSECTING_SUBLEVEL", localBounds, consumer));
        }
        trace.finish();
    }

    @Override
    public <U extends T> void get(final @NotNull EntityTypeTest<T, U> entityTypeTest, AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        final QueryTrace trace = new QueryTrace(aABB, "TYPED_SPATIAL");
        if (aABB.getSize() > MAX_GET_SIDE_LENGTH) {
            logError(aABB);
            trace.aborted();
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, aABB.getCenter());
        this.delegate.get(entityTypeTest, aABB,
                trace.wrapAbortable("DIRECT_PARENT_SPACE", aABB, abortableIterationConsumer));

        final BoundingBox3d bb = new BoundingBox3d(aABB);
        if (subLevel != null) {
            aABB = bb.transform(subLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, aABB,
                    trace.wrapAbortable("CONTAINING_SUBLEVEL_TO_VISIBLE", aABB, abortableIterationConsumer));
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bb));

        for (final SubLevel otherSubLevel : intersecting) {
            if (otherSubLevel == subLevel) {
                continue;
            }

            final AABB localBounds = bb.set(aABB).transformInverse(otherSubLevel.logicalPose(), bb).toMojang();

            this.delegate.get(entityTypeTest, localBounds,
                    trace.wrapAbortable("VISIBLE_TO_INTERSECTING_SUBLEVEL", localBounds, abortableIterationConsumer));
        }
        trace.finish();
    }

    public void getIgnoringSubLevels(final AABB aABB, final Consumer<T> consumer) {
        this.delegate.get(aABB, consumer);
    }

    public <U extends T> void getIgnoringSubLevels(final EntityTypeTest<T, U> entityTypeTest, final AABB aABB, final AbortableIterationConsumer<U> abortableIterationConsumer) {
        this.delegate.get(entityTypeTest, aABB, abortableIterationConsumer);
    }

    /** Records query aliases without deduplicating or otherwise changing the production result. */
    private final class QueryTrace {
        private final long id = QUERY_IDS.incrementAndGet();
        private final AABB originalBounds;
        private final String kind;
        private final Map<EntityAccess, Integer> identities = new IdentityHashMap<>();
        private int contraptionEmissions;

        private QueryTrace(final AABB originalBounds, final String kind) {
            this.originalBounds = originalBounds;
            this.kind = kind;
        }

        private Consumer<T> wrap(final String route, final AABB bounds, final Consumer<T> target) {
            if (!TRACE_M28_ENTITY_OWNERSHIP) {
                return target;
            }
            return value -> {
                this.record(route, bounds, value);
                target.accept(value);
            };
        }

        private <U extends T> AbortableIterationConsumer<U> wrapAbortable(
                final String route, final AABB bounds, final AbortableIterationConsumer<U> target) {
            if (!TRACE_M28_ENTITY_OWNERSHIP) {
                return target;
            }
            return value -> {
                this.record(route, bounds, value);
                return target.accept(value);
            };
        }

        private void record(final String route, final AABB bounds, final EntityAccess value) {
            if (!isContraption(value)) {
                return;
            }
            final int occurrence = this.identities.merge(value, 1, Integer::sum);
            this.contraptionEmissions++;
            final String position = value instanceof final net.minecraft.world.entity.Entity entity
                    ? entity.position().toString() : "UNAVAILABLE";
            Sable.LOGGER.info("SABLE_M28_INCLUSIVE_ENTITY_QUERY queryId={} queryKind={} route={} "
                            + "originalBounds={} delegatedBounds={} entityId={} entityUuid={} entityIdentity={} "
                            + "entityClass={} rawPosition={} sameQueryIdentityOccurrence={} duplicateEmission={} "
                            + "delegateIdentity={} callerStackFingerprint={}",
                    this.id, this.kind, this.originalBounds, bounds, value.getId(), value.getUUID(),
                    System.identityHashCode(value), value.getClass().getName(), position, occurrence,
                    occurrence > 1, System.identityHashCode(SubLevelInclusiveLevelEntityGetter.this.delegate),
                    stackFingerprint());
        }

        private void finish() {
            if (TRACE_M28_ENTITY_OWNERSHIP && this.contraptionEmissions > 0) {
                Sable.LOGGER.info("SABLE_M28_INCLUSIVE_ENTITY_QUERY queryId={} event=COMPLETE "
                                + "queryKind={} originalBounds={} contraptionEmissions={} "
                                + "distinctContraptionIdentities={} duplicateEmissionPresent={}",
                        this.id, this.kind, this.originalBounds, this.contraptionEmissions,
                        this.identities.size(), this.identities.values().stream().anyMatch(count -> count > 1));
            }
        }

        private void aborted() {
            if (TRACE_M28_ENTITY_OWNERSHIP) {
                Sable.LOGGER.info("SABLE_M28_INCLUSIVE_ENTITY_QUERY queryId={} event=ABORTED_LARGE_AABB "
                                + "queryKind={} originalBounds={} maxSideLength={} callerStackFingerprint={}",
                        this.id, this.kind, this.originalBounds, MAX_GET_SIDE_LENGTH, stackFingerprint());
            }
        }
    }

    private static boolean isContraption(final EntityAccess value) {
        final String name = value.getClass().getName();
        return name.contains("ContraptionEntity");
    }

    private static String stackFingerprint() {
        return StackWalker.getInstance().walk(stream -> stream
                .filter(frame -> !frame.getClassName().equals(SubLevelInclusiveLevelEntityGetter.class.getName()))
                .limit(12)
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(" <- ")));
    }
}
