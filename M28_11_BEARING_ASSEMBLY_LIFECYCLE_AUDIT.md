# M28.11 Mechanical Bearing Assembly Lifecycle Audit

## Scope

M28.11 is diagnostic-only. It does not change Steering Wheel behavior, Create
kinetics, bearing movement modes, contraption transforms, entity registration,
Sable physics, or rendering. Enable it with:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.traceBearingAssemblyLifecycle=true
```

## Exact Create 6.0.8 Chain

The mapped Create 6.0.8 artifact is retained from commit
`1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

1. `MechanicalBearingBlockEntity.onSpeedChanged(float)` sets
   `assembleNextTick=true`.
2. On the server, `MechanicalBearingBlockEntity.tick()` consumes that flag. If
   the bearing is not running and its speed is nonzero, it calls `assemble()`.
3. `assemble()` verifies that the live block is a `BearingBlock`, constructs
   `BearingContraption(isWindmill(), facing)`, and calls
   `BearingContraption.assemble(Level, BlockPos)`.
4. `BearingContraption.assemble()` starts at `bearingPos.relative(facing)`, calls
   inherited `Contraption.searchMovedStructure(level, start, null)`, then
   `startMoving(level)` and `expandBoundsAroundAxis(facing.getAxis())`. It returns
   false when search fails or the captured block map is empty. Windmills alone
   also enforce the configured minimum sail count.
5. A successful bearing assembly removes captured blocks with
   `removeBlocksFromWorld(level, BlockPos.ZERO)`.
6. `ControlledContraptionEntity.create(level, bearing, contraption)` constructs
   the entity, copies `bearing.getBlockPosition()` into `controllerPos`, and
   assigns the contraption (which also establishes the bearing rotation axis).
7. The bearing positions the entity at `bearingPos.relative(facing)`, sets the
   axis, and calls `Level.addFreshEntity(entity)`. Create 6.0.8 discards this
   boolean return value.
8. Create marks the bearing running only after the insertion call, resets its
   angle, synchronizes data, and refreshes generated rotation.

## Runtime Markers

- `SABLE_M31_BEARING_ASSEMBLY`: scheduling, entry/return, capture completion,
  entity construction, controller assignment, block removal, and disassembly.
- `SABLE_M31_ASSEMBLY_PRECONDITION`: exact server tick and assembly gates.
- `SABLE_M31_STRUCTURE_LOOKUP`: actual level, raw positions, derived local
  positions, search start/direction, capture lookups, and captured states.
- `SABLE_M31_ENTITY_INSERTION`: request/result, return value, immediate ID/UUID
  lookup, registration callback, next-tick availability, and removal reason.

Lookup logging is capped at 64 capture calls per assembly. All probes are in the
common/server-capable mixin list and are inert without the property.

## Paired Runtime Procedure

1. Leave the airplane disassembled and operate the same Steering Wheel once.
2. Capture the normal-world lifecycle through entity registration and one later
   tick.
3. Reset/disassemble the inner bearing as necessary.
4. Assemble the airplane into its Sable body.
5. Operate the same wheel and capture the Sable-contained lifecycle.
6. Compare the first absent or failing event, without inferring later stages.

## Evidence Table

Runtime has not been launched for M28.11, by requirement. The prior runtime only
establishes that speed reaches the Sable-contained bearing and steering lookup
does not find a moved contraption.

| Stage | Normal world | Sable-contained |
| --- | --- | --- |
| speed reaches bearing | Prior runtime: yes | Prior runtime: yes (`-16 RPM`) |
| assemble invoked | Runtime required | Runtime required |
| BearingContraption created | Runtime required | Runtime required |
| structure traversal succeeds | Runtime required | Runtime required |
| captured blocks | Runtime required | Runtime required |
| ControlledContraptionEntity constructed | Prior normal run: yes | Runtime required |
| controller set | Prior normal run: yes | Runtime required |
| entity insertion requested | Prior normal run: yes | Runtime required |
| insertion succeeds | Prior normal run: yes | Runtime required |
| entity survives next tick | Prior normal run: yes | Runtime required |
| steering lookup finds entity | Prior normal run: yes | Prior assembled run: no |

Status: **M28 mechanics partially proven / assembled-airplane inner-bearing
lifecycle unresolved**.
