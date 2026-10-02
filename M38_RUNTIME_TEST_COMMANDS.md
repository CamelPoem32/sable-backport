# M38 one-command fixtures

Stand on an ordinary flat platform with open air around you. The setup searches 27 deterministic nearby origins (nine horizontal positions at each of three heights), checks only the mechanism and travel cells, and never clears unrelated blocks or generates unloaded chunks. It prints the selected origin. A rejected candidate prints its cell, loaded state, BlockState, fluid, block entity, replaceability, and reason. These functions require operator permission. No glue or block placement is manual.

Setup also prints one `LEGACY_CELL` read-only snapshot at relative `(-12,-3,-16)`. This identifies the block that the former broad reservation rejected; its `MAY_BE_SUPPORT_GROUND` category means it no longer prevents setup.

Test the Dropper first. After replacing the jar, the FIRST command is `/function sable:m38/cleanup`. It recognizes the old untyped `sable_m38_origin` marker, or the unique known previous layout at `(-31,101,69)` if the marker is absent. It reports ownership evidence, retracts native Create if needed, returns only the identified fixture body to its original origin, and disassembles through the native Physics Assembler. Current-pose restore blockers are inspected and preserved. Only exact deterministic fixture residue at the original restore footprint can be removed transactionally; an unrelated occupant causes an explicit refusal. No global disassembly guard is changed.

Wait for `CLEANUP COMPLETE`, then setup. Expected order: `SETUP origin=...`, `MARKER status=VALID`, `phase=BUILD`, `phase=VALIDATE`, `ATTACHMENT innerNativeAttachmentValid=true actorCaptured=true mountedChestCaptured=true`, `ATTACHMENT outerGlueValid=true innerGlue=NOT_REQUIRED`, `phase=READY`. Only after `READY`, click the generated Physics Assembler once and run `run`. Expect the original Create piston to assemble and move. Keep `-Dsable.m31.traceCreateActors=true` for bounded actor diagnostics.

The v2 marker persists origin, kind and a READY tag; the resolved outer body UUID is also retained. Marker creation and block placement are synchronous Java operations, not nested function calls. Failed marker verification cannot place blocks. Failed BUILD/VALIDATE reports rollback complete only after a block/glue footprint scan and marker removal; otherwise it reports `ROLLBACK INCOMPLETE` and retains identity. A manually assembled pre-READY structure is cleanup-only, not a valid runtime test.

## Dropper

```mcfunction
/function sable:m38/cleanup
/function sable:m38/dropper
/function sable:m38/run
/function sable:m38/cleanup
```

The mounted Dropper starts with four cobblestone. PASS requires a native parent-world ItemEntity near the visible mouth, source count 4 to 3, physical eastward velocity, and `hiddenPlotSpawn=false`.

## Reserved footprint (relative to selected origin)

| Category | Cells |
|---|---|
| Inner Create mechanism | Piston poles `x=-12..-1,y=0,z=0`; piston `(0,0,0)`; chassis `(1,0,0)`; mounted chest `(1,0,1)`; actor `(1,1,0)`; motor `(0,-2,0)`; clutch `(0,-1,0)`; lever `(0,-1,1)` |
| Assembler/hull | Iron `(0,0,-2)`, `(0,0,-1)`; assembler `(0,1,-2)` |
| Must be air | Moving payload sweep `x=2..13` at `(y,z)=(0,0),(0,1),(1,0)` |
| Emission corridor | `x=14..18,y=0..2,z=0` |
| Fixture replacement | Ignition fixture only: stone support `(14,0,0)` |
| Optional yaw sweep | Projectile fixture only: `z=-3..-13` at `(x,y)=(0,0),(1,0),(0,1)` and emission `x=0,y=0..2,z=-14..-18` |
| May be support ground | All cells at `y<=-3`, including the formerly rejected `(-12,-3,-16)`, are not reserved and need not be air |

All other cells are unreserved. Structural and travel cells must be air before setup; replaceable plants and unrelated block entities are not overwritten. The parent floor may remain intact below the mechanism.

## Dispenser default item

```mcfunction
/function sable:m38/dispenser_item
/function sable:m38/run
/function sable:m38/cleanup
```

The Dispenser starts with four cobblestone. The same parent item/count checks must pass through Create's Dispenser behavior selection, not the Dropper behavior.

## Arrow and yaw

```mcfunction
/function sable:m38/projectile
/function sable:m38/run
/function sable:m38/yaw90
/function sable:m38/run
/function sable:m38/cleanup
```

The first run must spawn a native arrow in the parent world with eastward velocity and decrease the mounted arrow count. `yaw90` retracts the native piston, restores four arrows, and levels/yaws only the tagged outer fixture. The second run must spawn a northward arrow. `yaw90` may take several ticks; wait for `YAW90 COMPLETE` before the second run.

## Native block mutation

```mcfunction
/function sable:m38/block
/function sable:m38/run
/function sable:m38/cleanup
```

The actor contains one flint and steel. The fixture creates the stone ignition support at the east end of travel. PASS requires parent-world fire, native flint-and-steel damage increase, and no hidden-plot mutation. Fire is removed by cleanup only if it occupies the fixture's known target cell.

Only the assembler click is manual. Cleanup may take several seconds while the native piston retracts; wait for `CLEANUP COMPLETE` before creating the next fixture. Running cleanup again after completion is harmless. A run summary of `INCOMPLETE` is evidence to inspect, not a pass.
