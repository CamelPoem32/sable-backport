# M23.2a: Rapier World Terrain Collision Regression

Status after static implementation: `M23 PARTIAL_RUNTIME_PROVEN / GLOBAL_RAPIER_COLLISION_REGRESSION_BLOCKER`.

Do not close M23 until manual runtime proves ordinary Sable bodies rest on
parent-world terrain again.

## Last Good Diff

The semantic Rapier changes between the last known-good terrain collision state
and the current failing state were:

| File | Last-good behavior | Regressed behavior | M23.2a correction |
| --- | --- | --- | --- |
| `PhysicsColliderBlockGetter.java` | Synthetic one-block getter returned the queried state at `BlockPos.ZERO`; `getBlockEntity` returned absent context | Added optional `sourcePos` and delegated `getBlockEntity(BlockPos.ZERO)` to the backing level | Keep this 1.20.1 BlockGetter contract fix |
| `RapierVoxelColliderBakery.java` | Ordinary physics data was memoized by `BlockState` | Positioned overload bypassed the memoized cache for every world terrain block | Positioned overload falls back to the memoized cache unless the state is `MovingPistonBlock` |
| `RapierPhysicsPipeline.java` | Chunk and block updates requested cached collider data by state | Chunk and block updates passed real world positions into the positioned overload | Calls may pass positions, but ordinary blocks still resolve through the cached path |

The Java 17 `Util.memoize((BlockState state) -> buildPhysicsDataForBlock(state))`
change is compile-only and remains semantically equivalent to the original
one-argument method reference.

## Root Cause

Sable body colliders remained valid: runtime showed recreated bodies with valid
body handles, positive mass, uploaded blocks, and owned collision geometry.

The regression was in parent-world terrain collider identity. M23.2 changed
world section uploads and block updates so that every ordinary terrain block
used `buildPhysicsDataForBlock(state, sourcePos)` directly. That bypassed the
original `BlockState` memoized bakery. A plain `minecraft:stone` section could
therefore mint many transient Rapier voxel collider entries instead of reusing
one stable full-cube collider ID.

Rapier packs voxel collider IDs into the uploaded block data. The state-only
cache is part of the terrain collider contract: ordinary blocks whose collision
geometry is fully determined by `BlockState` must reuse stable collider IDs.
Only context-sensitive blocks may bypass that cache.

## Corrected Architecture

- Plain state-stable blocks, including `minecraft:stone`, use the original
  `BlockState` memoized path.
- `PhysicsColliderBlockGetter.getBlockEntity(BlockPos)` still exists and remains
  remapped in the production Rapier jar, preventing the previous
  `AbstractMethodError`.
- `MovingPistonBlock` is the narrow context-sensitive case. Its collision shape
  can query a `MovingPistonBlockEntity`, so the positioned overload delegates
  `BlockPos.ZERO` to the real parent-world `sourcePos`.
- The general `BlockState` cache is no longer dependent on mutable world,
  BlockEntity, ThreadLocal, or per-position context.
- No Spring, Simulated constraint, rendering, or transaction-safety code changed.

## Runtime Diagnostic Boundary

Manual runtime should use a single stone canary and inspect/log:

```text
SABLE_RAPIER_WORLD_CONTACT
sableId=
bodyHandleValid=
bodyColliderCount=
terrainColliderCount=
stoneBlockPos=
stoneState=minecraft:stone
stoneCollisionShapeEmpty=false
stoneVoxelBounds=0,0,0 -> 1,1,1
bodyAabb=
terrainAabb=
aabbOverlapExpected=
contactPairSeen=
contactManifoldCount=
```

Static verification cannot prove Rapier runtime contact, but it now proves the
source-level boundary that the previous verifier missed: the positioned
BlockEntity-aware path cannot be used for ordinary stone terrain.
