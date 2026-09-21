# M30 Production Baseline

This document records the permanent Forge 1.20.1 compatibility boundaries established by M28 and M29. Historical milestone documents remain useful as investigation records, but they are not the production architecture.

## Nested Create Contraption Ownership

A running Mechanical Bearing owns its captured payload through a nested `ControlledContraptionEntity` (CCE). The outer Sable body owns the hull, drivetrain, and bearing controller, but never the captured payload blocks.

`SableNestedBearingOwnershipTransfer` snapshots every nested CCE before an outer assembly or disassembly, copies the exact captured block set, reconstructs one destination CCE per snapshot, verifies registration and controller ownership, and commits only after every restore succeeds. Failure rolls the entire outer transfer back. Reconstruction never reruns unrestricted Create structure traversal, so a saved payload cannot expand into the hull.

## Visible Rendering Coordinates

Sable plot coordinates are storage coordinates. They never enter a visible `PoseStack` as a large translation followed by cancellation. Sable-contained Create contraptions are rendered through the scoped CPU bridge using the sublevel rigid pose and small camera-relative coordinates. Rotated bounds use all eight corners.

Sable-contained Mechanical Bearings use Create's CPU bearing-top renderer because a Flywheel instance positioned in raw plot space is not a valid visible-space owner. Normal-world bearings retain Create's normal Flywheel behavior.

## Entity Query Coordinates

Every entity-storage backend receives an AABB expressed entirely in that backend's coordinate space. The supported routes are:

- `PARENT_VISIBLE_DIRECT`
- `SUBLEVEL_RAW_DIRECT`
- `SUBLEVEL_RAW_TO_PARENT_VISIBLE`
- `PARENT_VISIBLE_TO_SUBLEVEL_RAW`

Conversions transform all eight corners and recompute the axis-aligned bounds. Parent and sublevel results are merged with identity-based deduplication. Jade normalization occurs at its entity-query boundary; there is no caller-specific exclusion or oversized-AABB allowance.

## Reverse Restore Client Lifecycle

Vanilla may deliver a restored CCE spawn before the destination bearing block and block entity are visible on the client. The spawn marker carries the exact expected payload count and destination bearing position. After Create finishes `readSpawnData`, `SableM28RestoredContraptionClientSync` independently admits each UUID to Flywheel and verifies an actual `ContraptionVisual` embedding update before completing.

The admission state has a four-client-tick bound. It is removed on success, payload mismatch, entity removal, timeout, or level unload. Production admission and cleanup never depend on a diagnostic flag.

## Controller Grace Rule

Create 6.0.8 normally discards a `ControlledContraptionEntity` when its serialized controller position is loaded but no `IControlContraption` block entity exists. For a verified reverse-restored normal-world CCE only, `SableM29RestoredControllerSyncGuard` defers that exact discard tail for at most four entity ticks while the destination bearing packet catches up.

Eligibility requires the restore marker, valid exact payload count, known controller position equal to the restored bearing position, a live client entity, and no containing Sable sublevel. The guard ends immediately when the controller appears, the payload becomes invalid, the server removes the entity, the level unloads, or the bound expires. Ordinary Create contraptions retain unmodified Create behavior.

## Flywheel Admission Rule

`EntityJoinLevelEvent` can precede Create's spawn-payload decode. The post-`readSpawnData` hook therefore queues one scoped visual refresh and one safe tick-boundary retry if needed. Constructor and first embedding-update callbacks are the authoritative success signals. No global Flywheel fallback, visual disable, or client-side replacement entity is used.

## Diagnostic Policy

Production logic is always active. Diagnostics are optional and are checked before expensive state collection.

- `-Dsable.m29.traceSailVisualLifecycle=true` enables bounded reverse-transfer, visual-admission, controller-sync, and sail lifecycle events.
- `-Dsable.m29.traceEntityQueries=true` enables route counters, capped examples, anomaly events, and unload summaries.
- `-Dsable.m28.traceNestedBearingPayload=true` enables bounded ownership-transfer transaction diagnostics.
- `-Dsable.m13.traceRuntime=true`, `-Dsable.m14.traceRendering=true`, `-Dsable.m22.traceAssembly=true`, `-Dsable.m24.traceRuntime=true`, and `-Dsable.m28.traceSteering=true` retain focused subsystem diagnostics.

Removed M28 GPU, framebuffer, per-vertex, presentation, forced-suppression, global entity-dispatch, bearing-assembly, and removal-provenance probes are intentionally unsupported. Their questions were closed by runtime evidence.

## Retained State Lifetimes

| State | Key | Created | Removed | Bound |
| --- | --- | --- | --- | --- |
| Nested transfer session | thread | outer transfer begin | commit/rollback `finish` | one transaction |
| Nested next-tick verification | bearing/entity | successful restore | next server verification tick | one tick |
| Restore marker | entity persistent data | destination CCE construction | entity retirement | entity lifetime |
| Visual admission | destination UUID | client spawn payload decoded | success/failure/removal/unload | four client ticks |
| Controller grace | destination UUID | first missing-controller tick | controller/failure/removal/unload | four entity ticks |
| Sail lifecycle diagnostics | destination UUID | debug discovery | entity leave/level unload | debug only |
| Query diagnostics | route/category counters | debug query | summary/reset on unload | fixed counters and capped examples |

## Regression Contract

`./gradlew.bat :forge:verifySableProductionBaseline` covers the retained M13, M14, M22, M24, M27, M28, and M29 boundaries plus the M29.6 controller race. It also verifies that obsolete probe mixins are absent, production state machines do not depend on debug flags, historical hot logging is disabled by default, and the packaged mixin list contains only the retained compatibility hooks.
