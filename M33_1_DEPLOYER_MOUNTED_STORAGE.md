# M33.1 Deployer Mounted Storage

## Exact Create 6.0.8 refill path

`AbstractContraptionEntity.tickActors` invokes `DeployerMovementBehaviour.visitNewPosition` for a newly visited cell. On the server, `visitNewPosition` calls `tryGrabbingItem` **before** activation. `tryGrabbingItem` obtains the cached or newly constructed `DeployerFakePlayer` from `MovementContext.temporaryData`. Only when its main hand is empty and the Deployer filter is not a Schematic does it call `context.contraption.getStorage().getAllItems()` and `ItemHelper.extract(handler, stack -> filter.test(context.world, stack), 1, false)`, then assigns the returned stack to the fake player's main hand. `activate` calls native `DeployerHandler`, and `writeExtraData` persists the hand as `HeldItem` in the actor context.

`Contraption.addBlock` calls `MountedStorageManager.addBlock` for captured blocks. That method looks up a registered `MountedItemStorageType`, mounts the source block/entity, and stores it under the contraption-local position. Assembly calls `MountedStorageManager.initialize`. `Contraption.writeNBT(false)` calls `writeStorage`, which serializes mounted items. `Contraption.readNBT(level, tag, false)` calls `MountedStorageManager.read`, which resets, decodes, and initializes the storage manager. The M28 nested snapshot uses this NBT path, but it selects only `MechanicalBearingBlockEntity` payloads. A moving piston Deployer is not automatically an M28 bearing snapshot.

## Current evidence and boundary

The prior runtime log proves one native placement consumed `minecraft:cobblestone` from the hand, followed by later interactions with an empty hand. The same log shows a chest delivered as an outer Sable block when that Sable body was created. It does **not** record whether that chest was subsequently captured into the inner piston contraption or mounted. Thus the exact I1/I2/I3/I4 distinction cannot be established from the old log alone. Source does not support a claim that `readNBT` inherently drops mounted storage.

M33.1 adds a read-only, server-only probe around native `tryGrabbingItem` under `-Dsable.m31.traceCreateActors=true`. It logs captured chest count and BE presence separately from mounted storage count, combined inventory slots, candidate/matching item counts, filter, and hand result. Repeated identical rejections are deduplicated. Native Create extraction and the M33 parent-world target/pose wrapper are unchanged.

Decisions:

| Trace decision | First boundary |
| --- | --- |
| `CHEST_NOT_CAPTURED_BY_INNER_CONTRAPTION` | I1, fixture ownership/connectivity |
| `CHEST_CAPTURED_BUT_NOT_MOUNTED` | I2, Create mount lifecycle/provider |
| `NO_MATCHING_MOUNTED_ITEM` | I4, empty or filtered storage |
| `NATIVE_EXTRACTION_RETURNED_EMPTY` | I3/I5, requires deeper native handler evidence |
| `NATIVE_EXTRACTION_SUCCEEDED` | Native refill works; inspect later held-item persistence if behavior differs |

No Sable inventory pump or speculative storage rebuild is added. A production correction should follow the first proven failing stage from the M33.1 runtime, rather than crossing ownership layers by reading a stationary Sable chest from an inner Create actor.

## Test

Use `M33_1_RUNTIME_TEST_COMMANDS.md`. Compare normal-world inner piston, Sable-contained inner piston, and a recreated inner piston after outer disassembly/reassembly. An already assembled piston CCE is not covered by the bearing-only M28 transfer, so record whether the inner Create piston is reassembled after each outer transition. Eight placements must consume exactly eight items from the **same mounted chest**, with no manual hand injection.
