# M23.4 / M22 Assembly Selection Fix

Status: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

## Frozen Upstream Predicate

Frozen Simulated `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
`SimAssemblyContraption` checks the 18 face/edge neighbor offsets and adds a
neighbor only when all ordinary movement checks pass and at least one exact
attachment predicate is true:

```java
!wasVisited && (canStick || blockAttachedTowardsFace || faceHasGlue)
```

`canStick` is symmetric and requires:

```java
!brittle
&& SimAssemblyService.INSTANCE.canStickTo(state, blockState)
&& SimAssemblyService.INSTANCE.canStickTo(blockState, state)
```

then rejects `PUSH_ONLY` and non-supportive face pairs.

Frozen NeoForge `NeoForgeSimAssemblyService` implements stickiness as:

```java
return stateA.canStickTo(stateB);
```

## Target Bug

The M22 Forge port widened the service to:

```java
stateA.canStickTo(stateB) || (!stateA.isAir() && !stateB.isAir())
```

That made every ordinary non-air block pair sticky, so a small vehicle touching
stone terrain could recursively absorb the support platform until
`maxBlocksMoved` was exceeded.

## Fix

The target Forge 1.20.1 service now delegates directly to the available
`BlockState.canStickTo(BlockState)` hook, matching the frozen NeoForge semantic
surface.

The traversal predicate remains upstream-shaped. It still honors Create Super
Glue, attached-towards-face checks, chassis, pistons, bogeys, double chests,
cart assemblers, and symmetric sticky blocks. It no longer treats ordinary
geometric adjacency as ownership.

## Glue Boundary

Honey Glue is supported by frozen upstream, but its entity/network/render graph
is not part of the current target registration set. The M23.4 runnable fixture
therefore uses Create Super Glue, which is already present in the M22 source
graph and is an exact upstream-supported attachment mechanism.

Create Super Glue selected by the assembler is now moved into the Sable sublevel
on assembly and moved back to parent-world coordinates on disassembly. This
keeps the same glue graph available for reassembly after a moved body is
restored onto ordinary terrain.

## Diagnostics

`SABLE_M22_ASSEMBLY_SELECTION` is emitted by the `assembly_boundary` fixture and
reports selected block count, platform block count, selected platform blocks,
selection bounds, and a deterministic selection digest.

`SABLE_M22_ASSEMBLY_SELECTION_FAIL` replaces bare oversized-structure failures
with a bounded frontier summary:

- start position
- visited count
- frontier count
- limit
- first/last unexpected frontier position and state
- entered-from position and state
- attachment reason

## Runtime Boundary

The fresh M23 Spring fixtures now rest on ordinary unglued stone platforms. The
intended body payload and the simplified target Physics Assembler are connected
with Create Super Glue, while support terrain is left unglued.

No Spring force law, Spring teardown, Rapier collision, rendering, or additional
constraint family was changed for this milestone.
