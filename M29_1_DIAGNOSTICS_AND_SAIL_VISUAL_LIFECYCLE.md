# M29.1 Bounded Diagnostics and Sail Visual Lifecycle

## M29.5 premature client removal boundary

M29.5 leaves the proven M28 ownership transfer and M29.4 visual-admission state machine unchanged.
For reverse-restored normal-world contraptions, the existing sail lifecycle flag now correlates the
vanilla server tracker with the exact client removal path:

- `SERVER_STOP_TRACKING` and `SERVER_REMOVE_PACKET_SENT` identify `ServerEntity.removePairing`;
- `CLIENT_REMOVE_PACKET_RECEIVED` identifies a vanilla remove packet before `ClientLevel` mutation;
- `CLIENT_LEVEL_REMOVE_ENTER`, `CLIENT_ENTITY_SET_REMOVED`, and `CLIENT_TRACKING_END` identify the
  authoritative client removal chain;
- `CLIENT_ENTITY_ID_COLLISION`, `CLIENT_ENTITY_UUID_COLLISION`, and `CLIENT_ENTITY_REPLACED` expose
  replacement semantics;
- a scoped Sable stop-tracking context distinguishes old-sublevel teardown from unrelated local code.

All probes require `-Dsable.m29.traceSailVisualLifecycle=true`, only inspect reverse-restored CCEs,
and emit each transition once. They do not suppress remove packets, retain dead entities, or retry
Flywheel admission. Until a runtime shows one of R1-R6, no removal behavior is changed.

## Scope

M29.1 preserves the M28 nested-bearing transfer and the M29 entity-query coordinate routes. It
changes diagnostic policy and adds a narrow classifier for the intermittent disappearance of a
rotated symmetric-sail contraption. It does not alter Create angles, renderer ownership, culling,
contraption payloads, or Flywheel admission.

## Why the M29 query trace became huge

`SubLevelInclusiveLevelEntityGetter.QueryDispatch.route()` logged one
`SABLE_M29_ENTITY_QUERY decision=QUERY_BACKEND` line for every backend route, and `finish()` logged
another line for every completed query. Jade and normal collision/targeting code invoke this hot
path continuously. A healthy query could therefore produce several lines, including a stack walk,
even though no coordinate error occurred. The observed 1.3 million lines were successful routing
evidence, not 1.3 million failures.

## Bounded query diagnostics

`SableM29EntityQueryTrace` now owns fixed-size atomic counters for the four production routes:

- `PARENT_VISIBLE_DIRECT`
- `SUBLEVEL_RAW_DIRECT`
- `SUBLEVEL_RAW_TO_PARENT_VISIBLE`
- `PARENT_VISIBLE_TO_SUBLEVEL_RAW`

It also counts Jade endpoint normalization, identity-deduplicated results, non-finite bounds,
oversized input or converted bounds, conversion failures, backend-space mismatches, mixed-space
detections, and rejected queries. Only the first three normal examples per route and the first
three examples per anomaly category are emitted. Stack walking occurs only for those capped
anomaly examples. The implementation stores counters and example budgets only; it never retains
AABBs or an unbounded set of query identities.

`SABLE_M29_QUERY_SUMMARY` is emitted and reset on level unload. Normal successful queries no longer
emit completion records. `SABLE_M29_ENTITY_QUERY` is reserved for capped route examples and
anomalies.

The M29 coordinate model is unchanged: one backend receives one AABB entirely in its own space,
and rotated conversions continue to use the existing eight-corner `BoundingBox3d` transform.

## Default logging policy

Production warnings and errors remain for malformed packets, invalid restored payloads, and the
first rejected non-finite/oversized entity query. Historical INFO diagnostics are opt-in:

- M10/M14 rendering: `sable.m10.traceRendering` or `sable.m14.traceRendering`
- M11 interaction/rendering: `sable.m11.traceRuntime`
- M13 runtime: `sable.m13.traceRuntime`
- M20 Create rendering: `sable.m20.traceCreateRendering`
- M22 outer transfer: `sable.m22.traceAssembly`
- M24 mechanics diagnostics: `sable.m24.traceRuntime`
- M28 buffer/vertex probe: `sable.m28.traceBufferProbe` or the corresponding explicit M28
  visual/tint/overlay experiment flag
- M28 steering and ownership traces: their existing explicit M28 flags
- M36 reverse-restore sync: `sable.m28.traceNormalWorldCceSync`

In particular, `SABLE_M28_ACTUAL_VERTEX`, `SABLE_M28_BUFFER_WRITE`, and
`SABLE_M28_BUFFER_TRANSFORM` are unreachable by default, and their reflective byte-buffer
inspection is skipped. `SABLE_M24_TORSION_TICK` and `SABLE_M24_WINCH_TICK` are also disabled by
default. None of these gates changes the associated gameplay code.

## Sail visual lifecycle classifier

Enable only:

```text
-Dsable.m29.traceSailVisualLifecycle=true
```

The semantic target is a client `ControlledContraptionEntity` whose controller resolves to a
Create `MechanicalBearingBlockEntity` and whose captured block map contains
`simulated:white_symmetric_sail`. Coordinates, entity IDs, UUIDs, Sable IDs, and payload sizes are
not identity criteria.

Existing lifecycle boundaries feed `SableM29SailVisualLifecycle`:

- Forge entity join/leave: entity and payload ownership;
- Create `ContraptionVisual` constructor, `setEmbeddingMatrices`, and `_delete`: Flywheel visual
  creation, update heartbeat, and removal;
- the real `SuperByteBuffer.renderInto` wrapper: CPU owner eligibility and geometry submission;
- the Sable render bridge: transformed visible bounds and cull-state transitions.

The tracker emits only transitions: discovery, payload presence/change, owner selection, visual
creation/removal, first render/geometry, movement-state changes, a capped set of significant angle
changes, cull changes, suspected heartbeat gaps, and heartbeat recovery. Each target retains at
most 32 compact in-memory transition strings. The ring is written only with a suspected gap.

A geometry gap requires an alive nearby entity, a present payload, a present visual, a prior
heartbeat, and more than 30 render frames without a heartbeat. Flywheel's heartbeat is the exact
per-frame `ContraptionVisual.setEmbeddingMatrices()` update boundary; the CPU heartbeat is the
actual Create `SuperByteBuffer.renderInto()` submission. The trace does not claim a Flywheel GPU
draw from the Java update alone.

If a target leaves after a confirmed gap, the tracker retains only its level identity, controller
position, three booleans, and removal frame for at most 300 frames. A replacement semantic target
at the same bearing emits `RECOVERY_AFTER_REASSEMBLY` on its first geometry heartbeat. This state
is bounded, expires automatically, and is never created for healthy entity retirement.

## Exact Create 6.0.8 angle audit

The mapped Create 6.0.8 artifact at commit `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`
shows:

- `ControlledContraptionEntity` stores `prevAngle`, `angle`, and `angleDelta`;
- `tickContraption()` computes `angleDelta = angle - prevAngle`, then assigns `prevAngle = angle`;
- `getAngle(1.0F)` returns `angle` directly;
- other partial ticks use `AngleHelper.angleLerp(partialTick, prevAngle, angle)`;
- `ContraptionVisual.beginFrame()` passes the frame partial tick to `setEmbeddingMatrices()`;
- `setEmbeddingMatrices()` resets its PoseStack, interpolates entity position relative to the
  Flywheel render origin, invokes `AbstractContraptionEntity.applyLocalTransforms(stack,
  partialTick)`, and writes the resulting pose/normal matrices to `VisualEmbedding.transforms()`.

This source audit does not expose an unconditional angle-wrap or stopped-bearing bug. M29.1 does
not modify angle state. The tracker deliberately treats `179 -> -179` as a two-degree transition,
not 358 degrees, and records running/stopped transitions for runtime classification.

## Runtime acceptance

Sail reproduction uses only `-Dsable.m29.traceSailVisualLifecycle=true`. Stop the test before
repairing/reassembling after a disappearance so the gap state remains in the log. A clean run uses
no Sable JVM flags. The intermittent issue remains runtime-classification pending until one of the
entity, payload, visual, geometry, culling, or movement-state boundaries diverges.

## M29.2 reverse-restore correlation

The M29.1 reproduction ended at an outer-disassembly ownership boundary: both source
Sable-contained CCEs retired normally, but the old trace could not correlate them with the
replacement normal-world entities. M29.2 keeps the ownership implementation unchanged and adds a
diagnostic `transferId` plus `snapshotIndex` to each reverse snapshot. For reverse restores only,
that marker is copied to the destination entity persistent data and through Create's spawn NBT.

With `sable.m29.traceSailVisualLifecycle` enabled, the same marker now connects the bounded events
from `REVERSE_SNAPSHOT_CAPTURED` through server pre-add, authoritative registration, next-tick
survival, player tracking, spawn serialization, client payload decode, semantic target discovery,
visual creation, and first geometry. A missing server event classifies B1; a complete server chain
without its matching client chain classifies B2. No event is emitted per frame.

The source audit found no one-slot restore state. Server snapshots are stored per bearing in a
`LinkedHashMap`, source cleanup first verifies that every snapshot has a destination, spawn markers
are stored on each destination entity, and client tracking is UUID-keyed. Source-sublevel cleanup
therefore cannot commit through the M28 transaction until all server destinations validate. This
pass intentionally makes no speculative production lifecycle change before the correlated runtime
identifies the first missing event.

The visual lifecycle tracker also rejects dead or removed entities before discovery and records
owner selection only when the owner actually changes. Asynchronous Flywheel callbacks can no
longer recreate a retired source target, and stable `CPU_BRIDGE` observations do not repeat
`VISUAL_OWNER_SELECTED`.

## M29.3 post-decode visual admission

The correlated M29.2 runtime classified the intermittent reverse-disassembly failure as B2: both
destination entities were alive and tracked on the server, and both client spawn payloads decoded
with the expected sail maps, but no destination `ContraptionVisual` followed.

The apparent `controllerPos=null` at `CLIENT_PAYLOAD_DECODED` is a diagnostic-order artifact.
Create 6.0.8 calls `ControlledContraptionEntity.readAdditional()`, whose first operation invokes
the superclass implementation. The Sable spawn marker is observed at that superclass return;
Create then reads `ControllerRelative`, the rotation axis, and angle before `readSpawnData()`
returns. `ContraptionVisual` construction itself consumes the entity and decoded contraption and
does not require the controller block entity.

Flywheel 1.0.5 uses a `ConcurrentLinkedQueue` of distinct add/remove transactions. Its frame and
tick plans drain that queue before updating visuals, so remove/add requests are FIFO and are not
coalesced. The defect in the old compatibility path was nevertheless lifecycle-sensitive: it
issued one remove/add pair synchronously from `readSpawnData()` and never verified that the visual
entered Flywheel storage. A missed or unavailable manager therefore became a permanent invisible
entity with no completion signal.

M29.3 keeps the original scoped request but adds a UUID-keyed, bounded admission record. At the
next client tick it checks Flywheel's actual entity-visual storage by entity identity. If the
visual is absent it permits exactly one safe-boundary retry; visual construction and the first
embedding update complete and remove that entity's record. Missing managers and incomplete queue
processing can live for at most four client ticks before one
`CLIENT_POST_DECODE_VISUAL_TIMEOUT` event. Entity removal and level unload clear records
immediately. Two restored CCEs therefore retain independent request, visual, and geometry state.

With `sable.m29.traceSailVisualLifecycle`, M29.3 adds bounded `CLIENT_REFRESH_*`,
`CLIENT_VISUAL_*`, and timeout events. The actual visual constructor/embedding hooks are tracked
independently of the semantic bearing matcher; the matcher may use the transaction's destination
bearing while Create is still completing controller deserialization.

## M29.4 client visual admission race

The same-process A/B runtime showed that a post-decode remove/add request could produce geometry
when the restored bearing block entity was already present, but could stop after the request was
submitted when block synchronization lagged behind the CCE spawn. Exact Flywheel 1.0.5 bytecode
shows that the bearing block entity is not an admission dependency: `EntityStorage.willAccept()`
checks entity liveness, visualization support, and level presence; `EntityStorage.createRaw()`
looks up an entity visualizer; and `SimpleEntityVisualizer.createVisual()` invokes Create's visual
factory. `ContraptionVisual` consumes the decoded contraption and entity transform without looking
up the bearing. The absent bearing block entity is therefore an ordering signal, not a reason to
delay visual admission.

M29.3 registered its next-tick driver from the shared Simulated bootstrap and silently removed
records on several cleanup paths. Its logs could therefore end at `CLIENT_VISUAL_QUEUE_COMPLETE`,
which meant only that Sable returned from `queueAdd`, not that Flywheel accepted or processed the
request. M29.4 installs the driver from `SableForgeClient`, runs it at Forge client-tick END, and
gives every UUID-keyed admission record an explicit terminal reason. A record receives at most an
initial request plus one safe client-boundary retry, then ends as success, entity removal, level
unload, payload rejection, replacement, or timeout.

The bounded trace now observes the actual Flywheel chain for correlated restored entities only:
`VisualManagerImpl.queueAdd()` acceptance, `processQueue()` dequeue, `EntityStorage.createRaw()`
visualizer lookup, `SimpleEntityVisualizer.createVisual()`, `ContraptionVisual` construction,
storage insertion, and the first embedding update. A request submitted event is never treated as
admission success. Prerequisite records include bearing/controller synchronization for A/B
diagnosis, but only Flywheel's exact `willAccept` result gates a refresh.

## M29.6 reverse-restored controller synchronization

Create 6.0.8 `ControlledContraptionEntity.tickContraption()` returns while its controller
position is unloaded, but discards the entity when the position is loaded and
`getController()` returns null. Reverse disassembly reconstructs and starts tracking the
destination CCE from `afterBlockTransfer()` during the block-copy loop; destination block
notifications are emitted only after that loop. The client can therefore receive and tick the
CCE while the destination Mechanical Bearing is still air.

M29.6 keeps the actual server-spawned CCE alive across only that synchronization gap. The guard
runs immediately before Create's `getController()` call, after Create has updated angle state and
actors. It is eligible only for a normal-world CCE carrying the M28 reverse-restore marker, an
exact valid payload, and a serialized controller position equal to the restored bearing position.
The guard expires after four client entity ticks, permanently disables itself when the controller
appears or the window expires, and never intercepts remove packets or generic entity removal.
Ordinary Create contraptions retain the exact Create 6.0.8 lifetime behavior.
