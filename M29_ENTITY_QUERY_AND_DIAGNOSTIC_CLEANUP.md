# M29 Entity Query and Diagnostic Cleanup

## Root cause

Sable block ray casts retain a sublevel block hit in raw plot coordinates so block position, face, and local hit data remain authoritative for interaction. Jade 11.13.3 constructs its entity-search `AABB` from the visible camera position and that hit location. When the hit belongs to a Sable body, this creates one box containing a visible endpoint and a raw plot endpoint near 20 million blocks.

`SubLevelInclusiveLevelEntityGetter` received that already-mixed box. Its old size guard prevented the entity manager from traversing millions of sections, but emitted a full stack trace for every query and could not repair the lost endpoint semantics.

## Coordinate-space model

Every entity-storage query now has one explicit coordinate space:

- `PARENT_VISIBLE`: normal parent-level entities and visible Sable-body intersection tests.
- `SUBLEVEL_RAW_PLOT`: entities stored inside one Sable plot.

The Jade compatibility boundary rebuilds its ray box from the original start and end points after projecting each endpoint to parent-visible space. It does not alter the raw `BlockHitResult`, so Sable block interaction remains unchanged.

The inclusive getter then follows one query plan:

1. Query the input owner in its native space.
2. Obtain one parent-visible AABB.
3. Query parent storage with that visible AABB.
4. Find only Sable bodies intersecting the visible AABB.
5. Convert the visible AABB independently into each body's raw plot space and query that storage.
6. Merge results by Java identity so an entity is emitted exactly once.

No backend receives a visible/raw union.

## Rotated bounds

The production conversion uses `BoundingBox3d.transform` and `transformInverse`. Those methods transform all eight corners and rebuild min/max bounds, so arbitrary Sable orientation is supported. The existing M14/M28 eight-corner regression remains the mathematical control.

## Query diagnostics

`-Dsable.m29.traceEntityQueries=true` enables bounded `SABLE_M29_ENTITY_QUERY` route logs. Normal queries are logged only when a Sable body participates. Oversized inputs emit at most one production warning and at most eight diagnostic trace records; repeated Java stack traces were removed.

Magnitude is only a diagnostic safety check. Production coordinate ownership is determined from the owning level/sublevel and the explicit conversion route.

## Diagnostic policy

- Production-safe: one deduplicated warning for an unexpected oversized query and genuine ownership/lifecycle errors.
- Debug-gated: static render lifecycle (`sable.m10.traceRendering` or `sable.m14.traceRendering`), block-edit packet traces (`sable.m11.traceBlockEdits`), M13 contraption/target probes (`sable.m13.traceRuntime`), M20 Create renderer probes (`sable.m20.traceCreateRendering`), outer transfer diagnostics (`sable.m22.traceAssembly`), steering traces (`sable.m28.traceSteering`), retained M28 visual/ownership probes, M29 query traces, and M36 restored-CCE traces.
- Obsolete behavior removed: unconditional M10/M11/M13/M20/M22 render, block-copy, and steering informational logging plus repeated oversized-query stack dumps.

M36 client restore targets now expire on `EntityLeaveLevelEvent`. A restored CCE legitimately removed by the next outer assembly is no longer retained and reported as a missing historical UUID after a later sublevel removal.

## Regression invariants

- Parent and Sable-contained entities remain queryable.
- Nested Create CCE results are emitted once.
- Raw plot coordinates never enter visible PoseStack/GPU transforms.
- M28 nested-bearing ownership, reconstruction, rollback, and client visual refresh are unchanged.
- Jade needs no block/entity special case; only its generic ray-query coordinate boundary is adapted.
