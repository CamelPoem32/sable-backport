# M28.13 Nested Mechanical Bearing Payload Audit

## Scope

This pass is diagnostic only. It does not change Steering Wheel control, Create kinetic propagation,
Mechanical Bearing assembly, Sable block transfer, entity ownership, rendering transforms, or glue behavior.

Target baselines:

- Sable upstream `mc1.21.1-2.0.0-neoforge` at `b7226222caf4eace63a708bdcd73ef36c971137d`.
- Create 6.0.8 source at `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

## Exact Create 6.0.8 Ownership

`MechanicalBearingBlockEntity.tick()` consumes `assembleNextTick` on the server. When the bearing is not
already running and has nonzero speed, it invokes `assemble()`.

`MechanicalBearingBlockEntity.assemble()`:

1. reads `BearingBlock.FACING`;
2. creates `BearingContraption`;
3. calls `BearingContraption.assemble(level, bearingPos)`;
4. removes captured blocks from the level with `removeBlocksFromWorld(level, BlockPos.ZERO)`;
5. creates `ControlledContraptionEntity` with the bearing as controller;
6. positions it at `bearingPos.relative(facing)`;
7. sets its rotation axis;
8. calls `Level.addFreshEntity`;
9. stores the entity in `movedContraption` and marks the bearing running.

`BearingContraption.assemble()` starts structure discovery at `bearingPos.relative(facing)`. Consequently,
an already-assembled payload is intentionally `air` in the source level and exists only in the nested
`ControlledContraptionEntity` block map.

## Exact Outer Sable Assembly

`PhysicsAssemblerBlockEntity.assemble()` calls
`SimAssemblyHelper.assembleFromSingleBlock()`. `SimAssemblyContraption.searchMovedStructure()` discovers
only non-air `Level` blocks connected through Create movement/stickiness/glue rules. The selected positions
are passed to `SubLevelAssemblyHelper.assembleBlocks()`.

The target and upstream `SubLevelAssemblyHelper` implementations have the same ownership behavior:

1. allocate a new sublevel;
2. call `moveOtherStuff()`;
3. call `moveBlocks()`;
4. move tracking points.

Despite the retained-entity tag containing Create contraption entity types, `moveOtherStuff()` only changes
the position of qualifying `HangingEntity` instances. It does not migrate an
`AbstractContraptionEntity`/`ControlledContraptionEntity`, copy its contraption block map, remap its
controller, or reconstruct it in the new sublevel. `moveBlocks()` serializes and recreates selected block
entities, but only for positions present in the selected live block set.

This establishes the source-level risk boundary: a sail already owned by a nested CCE is neither a live
block available to outer block traversal nor an entity migrated by this assembly helper. Runtime tracing is
still required before assigning the final A-G root-cause classification.

## M13 Control

The historical M13 fixture does not preserve a pre-existing nested contraption across parent assembly.
`M13TestCommands.spawnBearing()` first allocates an empty `ServerSubLevel`, writes the motor, shaft,
bearing, chassis, and payload blocks directly into that sublevel, and then relies on normal Create bearing
assembly there. M13 proves that a CCE can be created after its parent Sable body exists; it does not prove
that outer assembly migrates an already-created CCE.

## M28.13 Runtime Trace

Enable:

```text
-Dsable.m28.traceNestedBearingPayload=true
-Dsable.m28.traceBearingHeadRender=true
```

`SABLE_M33_NESTED_PAYLOAD` follows selected Steering Wheel/Mechanical Bearing chains through normal-world
ownership, M22 discovery, transfer, target storage, and post-assembly topology. Association uses the same
Create kinetic network when available; nearest selected wheel is a diagnostic fallback only.

`SABLE_M33_OUTER_TRANSFER` records the existing M22 block transfer without changing it. In particular, it
will show whether the expected payload position is absent from the selected block set because its live state
is air while its state remains in the nested CCE.

## Bearing Top Rendering

Exact Create 6.0.8 uses `AllPartialModels.BEARING_TOP` (or `BEARING_TOP_WOODEN`) for the reported attachment
head.

- CPU fallback: `BearingRenderer.renderSafe()` returns immediately when
  `VisualizationManager.supportsVisualization(level)` is true. Otherwise it renders the shaft via the
  superclass and emits the selected bearing-top partial through `SuperByteBuffer.renderInto()`.
- Flywheel: `BearingVisual` creates an `OrientedInstance topInstance` from the same bearing-top partial.
  `beginFrame()` updates its rotation from `IBearingBlockEntity.getInterpolatedAngle(partialTick - 1)` and
  calls `setChanged()`.

`SABLE_M33_BEARING_RENDER` records the exact visualization decision, selected partial, and whether the BER
actually reaches top-partial emission. It does not alter the decision. The missing head remains independent
from server-side nested payload preservation until runtime evidence proves otherwise.

## Runtime Decision Gate

No production correction is selected in M28.13. The runtime comparison must establish one of classifications
A-G from the milestone request before materializing, migrating, or reconstructing nested payload ownership.

