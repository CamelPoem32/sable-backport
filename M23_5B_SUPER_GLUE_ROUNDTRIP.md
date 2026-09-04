# M23.5b Super Glue Roundtrip

Status: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

## Runtime Root Cause

Fresh M23 Spring fixtures proved M23.4 terrain isolation and initial selection:
Create Super Glue selected exactly the six intended blocks for each body, with
zero platform blocks selected. Initial assembly created two distinct six-block
Sables with the Physics Assembler present.

After disassembly, the next assembly selected only one smooth stone block and
the Physics Assembler was absent. That means the authoritative Super Glue graph
was gone before the second `SimAssemblyContraption` search.

## Upstream Semantics

Frozen Simulated `9e60263fb5cb00033f14af655a7e72cf7aebb3e2` relies on Create
Super Glue entities as one of the canonical ownership edges during assembly.
Glue is not a block state; it is an entity with an attachment AABB. Preserving a
glued vehicle across:

parent world -> Sable raw storage -> parent world

therefore requires moving the glue entity relationship through the same
coordinate transform as the selected blocks.

## Target Failure

The target already moved selected glue into the Sable on assembly:

```java
moveAssemblyGluesToSubLevel(serverLevel, contraption.getGlues(), rawAnchor.subtract(anchor));
```

The disassembly side attempted to discover raw glue after
`ServerLevelPlot.kickAllEntities()`. In the target Sable tags,
`#sable:super_glue` is included in `sable:destroy_with_sub_level`, so plot
cleanup can remove Super Glue before the restore pass sees it.

First glue loss point: raw Sable glue existed before disassembly, then was
removed by entity cleanup before `moveSubLevelGluesToParent` queried the plot.

## Fix

`SimAssemblyHelper.disassembleSubLevel` now captures the raw Super Glue AABBs
before `kickAllEntities()`. If block movement fails, missing raw glue is
restored so the M23.1 source-preservation guarantee includes glue. If block
movement succeeds, the captured raw glue boxes are transformed back through the
current disassembly transform and recreated in parent-world coordinates before
the old raw glue boxes are removed.

Assembly-side glue movement is also made source-safe: the destination glue is
created first, and the source glue is removed only after the destination exists.

The fix preserves M23.4 traversal semantics, M23.3 Spring teardown, Spring force
law, Rapier collision, and M22 block movement transaction safety.

## Diagnostics

`SABLE_M22_GLUE_ROUNDTRIP` now logs bounded, transition-only evidence:

- `BEFORE_ASSEMBLY`
- `AFTER_ASSEMBLY`
- `BEFORE_DISASSEMBLY`
- `AFTER_DISASSEMBLY`
- `REASSEMBLY_SELECTION`

The Spring fixture body inspector now refuses malformed bodies before Spring
acceptance by requiring two distinct six-block Sables with the Physics
Assembler and Spring support blocks present.
