# M32 Create World-Interaction Actors

## Scope

M32 extends the runtime-proven M31 parent-world actor boundary from the Mechanical Drill to the
Mechanical Saw, Mechanical Harvester, and Mechanical Plough. The Deployer is deliberately deferred
to M33.

## Exact Create 6.0.8 paths

All paths begin in `AbstractContraptionEntity.tickActors()`. Create computes the active point with
`toGlobalVector(actorLocalCenter + behaviour.getActiveAreaOffset(context), 1)`, updates
`MovementContext.position`, and calls `visitNewPosition()` when the Create-space block position
changes. It then calls `MovementBehaviour.tick()`.

### Mechanical Saw: classification A

`SawMovementBehaviour` extends `BlockBreakingMovementBehaviour`.

1. `SawMovementBehaviour.visitNewPosition()` calls
   `BlockBreakingMovementBehaviour.visitNewPosition()`.
2. `BlockBreakingMovementBehaviour.tick()` calls `tickBreaker()`.
3. `tickBreaker()` retains Create's `BreakingPos`, hardness, progress, stall, sound, falling-block,
   and `BlockHelper.destroyBlock()` behavior.
4. `SawMovementBehaviour.canBreak()` adds `SawBlockEntity.isSawable()`.
5. `SawMovementBehaviour.onBlockBroken()` delegates tree discovery to
   `TreeCutter.findDynamicTree()` or `TreeCutter.findTree()` and destroys the resulting native
   Create break queue.
6. `dropItemFromCutTree()` inserts into mounted storage or creates an `ItemEntity`.

M32 reuses M31's breaker-volume target resolver. It only presents parent-visible position and
motion while native Create drop code executes.

### Mechanical Harvester: classification C

`HarvesterMovementBehaviour` implements `MovementBehaviour` directly.

1. `visitNewPosition()` reads the target state.
2. `isValidCrop()` and `isValidOther()` apply Create's crop/config/tag checks.
3. `BlockHelper.destroyBlockAs()` produces native drops.
4. `cutCrop()` applies Create's replant, age, sugar-cane, growing-plant, berry, and fluid rules.
5. `Level.setBlockAndUpdate()` writes the resulting crop state or air.

There is no breaker progress state. M32 resolves the current physical active point and calls the
unchanged method with the parent `BlockPos`. A small per-context target key makes an outer-body
translation or rotation trigger the same actor action when the physical block cell changes.

### Mechanical Plough: classification B

`PloughMovementBehaviour` extends `BlockBreakingMovementBehaviour`, then performs an additional
terrain action.

1. `visitNewPosition()` calls the base breaker path, including native entity throwing.
2. It addresses `activePos.below()` and requires the parent chunk to be loaded.
3. It clips one block downward with the native `PloughFakePlayer`.
4. It applies a vanilla diamond hoe through `ItemStack.useOn(new UseOnContext(...))`.
5. `canBreak()` preserves Create's air, farmland, fluid, bubble-column, portal, track, and collision
   shape rules; `onBlockBroken()` preserves snow loot behavior.

M32 supplies one parent-visible active cell to both the base breaker and the hoe-use tail. Entity
interaction remains Create-owned and operates in the parent level; it is not routed through the
block resolver.

## Coordinate ownership

The implemented route is:

```text
ACTOR_LOCAL
  -> Create inner-contraption transform
SABLE_RAW active point/direction
  -> current SubLevel.logicalPose()
PARENT_VISIBLE point/direction
  -> BlockPos.containing(...)
PARENT_BLOCK
```

M31's volume target remains in use for Drill and Saw. Harvester and Plough use the new point target.
Both routes require the parent chunk to be loaded, reject raw plot ownership, reject another Sable
body, and have no raw-position fallback. `BoundingBox3d.transform()` retains the existing
eight-corner rule for rotated breaker volumes.

The implementation names storage and visible values explicitly. It never patches `Level.getBlockState`,
`Level.setBlock`, or `Level.destroyBlock` globally.

## Current-pose and drop semantics

Every evaluation uses `SubLevel.logicalPose()`. Persistent breaker state is retargeted when the
physical parent block changes. Harvester and Plough keep a separate physical-cell key because their
native action may validly target air while operating on a crop or the block below.

Native drop logic temporarily sees a parent-visible `MovementContext.position`. Physical motion is
computed from the current visible point and the previous raw point transformed by `lastPose()`.
Relative motion is rotated by the current body pose. The original raw context fields are restored in
a `finally`-equivalent scope immediately after Create returns.

## Authority and safety

- All mutation remains server-authoritative.
- Normal-world actors take the original Create path without context mutation or target conversion.
- Own hidden plot storage is never used as an external target.
- Contact with another visible Sable body is unsupported and is a safe no-op in M32.
- Unloaded parent chunks are a safe no-op.
- No crop list, loot path, tree algorithm, hoe behavior, or breaker timing was reimplemented.
- Deployer fake-player/item/inventory semantics remain M33 scope.

## Diagnostics

`-Dsable.m31.traceCreateActors=true` remains the single actor trace flag. It now identifies DRILL,
SAW, HARVESTER, and PLOUGH and emits only target/action transitions. The historical
`SABLE_M16_DRILL_RENDER` event is also behind this flag; default runtime performs no diagnostic set
insertion or logging for that event.

## Runtime acceptance

Use `M32_RUNTIME_TEST_COMMANDS.md`. Test each actor in the normal-world inner carriage first, then
assemble the outer Sable body and repeat while stationary, translated, rotated, and moving. A target
change must never mutate the old target later. After outer disassembly, the normal Create behavior
must remain unchanged.

