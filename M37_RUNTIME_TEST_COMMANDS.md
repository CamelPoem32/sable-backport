# M37 one-command Funnel fixtures

Use cheats/operator permission in a clear area. Each setup reserves a 31-by-8-by-5 block box around a point four blocks above your feet; the optional yaw test also needs clear space about 16 blocks north. Each command builds the native Create piston, radial chassis, Andesite Funnel, mounted chest, outer Sable hull, Physics Assembler, parent-world target and controls. Do not place or glue blocks manually. Wait for `[M37] SETUP phase=READY outerSelectedBlocks=22`; if setup fails it rolls back and prints the reason.

INSERT and EXTRACT setup print component-level `VALIDATE` lines before READY. INSERT requires an unpowered collector below, but outside, the payload sweep. EXTRACT starts with a redstone-held source; `/run` unpowers it while the piston is stopped, then starts the piston only after a native emitted item rests in its future query cell. Create updates a stationary Funnel's `powered` state on its scheduled block tick, so setup checks the immediately present signal and attachment geometry.

Relative to the fixture origin, all modes place piston poles at `x=-12..-1,y=0,z=0`, piston `(0,0,0)`, radial chassis `(1,0,0)`, moving Funnel `(1,1,0)`, mounted chest `(1,0,1)`, motor `(0,-2,0)`, clutch `(0,-1,0)`, lever `(0,-1,1)`, iron hull `(0,0,-2)` and `(0,0,-1)`, and Physics Assembler `(0,1,-2)`. INSERT's station marker is in air at `(12,2,0)`, collector at `(12,-2,0)`, chest at `(12,-3,0)`. The moving payload sweeps `x=1..13,y=0..1,z=0..1`; stationary hardware lies below it with `y=-1` as an air gap. EXTRACT has a WEST stationary extractor `(14,1,0)`, parent chest `(15,1,0)`, item support `(14,0,0)`, and redstone hold `(14,1,1)`. That support is beyond the chassis's final `x=13` cell. No later fill overlaps these blocks.

The moving Funnel in Create 6.0.8 never opens an adjacent inventory directly. INSERT and EXTRACT deliberately include a **stationary parent-world Create Funnel** backed by a chest. This tests the native moving-Funnel ItemEntity leg plus the native stationary-Funnel inventory leg; it does not pretend there is a direct moving-Funnel capability transfer.

## Recover the previous crashed fixture first

After installing the repaired jar and loading the world, run this before creating another fixture:

```mcfunction
/function sable:m37/cleanup
```

The old INSERT origin and station markers identify the assembled Sable even though the in-memory run was lost in the crash. Cleanup stops and natively retracts the inner piston, disassembles the outer Sable, and removes only the tagged fixture. Wait for `[M37] CLEANUP COMPLETE`. Repeat the same command if native retraction reports a recoverable timeout; do not break the payload manually.

## INSERT: moving storage to parent chest

```mcfunction
/function sable:m37/insert
```

The mounted chest starts with 16 cobblestone; the parent chest is empty. The moving Funnel emits at its visible active point minus 0.5 Y. The collector is below the known-good moving lane, without stationary blocks in any swept payload cell. `INSERT_ATTACHMENT` prints Create's actual included positions and must show both Funnel and chest included. Click the generated Physics Assembler once. Then run:

```mcfunction
/function sable:m37/run
```

The command starts the native piston. PASS requires `[M37] INSERT ... funnelActor=true ... movingExtractObserved=true ... endToEndTransfer=true ... hiddenPlotInteraction=false PASS`, with mounted source below 16 and parent destination above 0. The moving Funnel emits an ItemEntity; the stationary parent Funnel inserts it into the chest. `INSERT_GEOMETRY` prints the emitted item and collector AABBs plus the collector's powered state. No inventory is copied by fixture or compatibility code.

Once the piston assembles, `[M37] MOVING_PAYLOAD` should report one captured Funnel, one captured ChestBlockEntity, and at least one mounted storage. The moving chest was visually runtime-validated. With `-Dsable.m31.traceCreateActors=true`, `SABLE_M37_CHEST_RENDER` reports the native ChestRenderer dispatch and small camera-relative coordinates.

The first tick may print `[M37] GEOMETRY pending reason=NO_ACTOR_POSITION_YET`. This is normal: Create has captured the actor but has not yet assigned its active position. A later tick can print `NO_EMITTED_ITEM_YET` before `INSERT_GEOMETRY`; neither pending state is a run failure.

For a transformed-facing test after this run:

```mcfunction
/function sable:m37/yaw90
```

Wait for `[M37] YAW90 COMPLETE`, then:

```mcfunction
/function sable:m37/run
```

The helper natively retracts the piston, turns only the tagged Sable to a level +90-degree yaw, moves the fixture-owned parent station, and replenishes the finite test cobblestone. The old station cell is no longer active. PASS again requires actual counts to change. To move the body away from that target, run:

```mcfunction
/function sable:m37/disconnect
```

Then confirm the old chest count stops changing.

## EXTRACT: parent chest to moving storage

After cleanup, use:

```mcfunction
/function sable:m37/extract
```

The parent chest starts with 16 cobblestone; the mounted chest is empty. Click the Physics Assembler once, then:

```mcfunction
/function sable:m37/run
```

The redstone hold keeps the stationary Funnel inactive through setup. The run unpowers that native source while keeping the piston stopped. At the first native stationary ItemEntity spawn, the fixture powers the source Funnel again, waits for its native powered state and exactly one tagged item resting on the support, then engages the piston. `EXTRACT_TIMING` prints spawn, disable, query, and insert order. PASS requires `[M37] EXTRACT ... itemQueryResolved=true ... itemExistedBeforeNativeQuery=true ... movingInsertObserved=true ... endToEndTransfer=true ... hiddenPlotInteraction=false PASS`, with parent source 16 to 15 and mounted destination 0 to 1. This is not a direct parent capability lookup by the moving actor.

## ENTITY: parent dropped item to moving storage

After cleanup, use:

```mcfunction
/function sable:m37/entity
```

The mounted chest starts empty. The run command creates exactly one tagged, 16-cobblestone parent ItemEntity in the physical target cell and starts the native piston:

```mcfunction
/function sable:m37/run
```

PASS requires `[M37] ENTITY ... entityQueryResolved=true ... movingInsertObserved=true ... endToEndTransfer=true ... hiddenPlotInteraction=false PASS`, a reduced/consumed tagged parent ItemEntity and an increased mounted chest count. ENTITY does not require a stationary block target. No hidden plot item should appear.

## Cleanup

After each mode, including after yaw or disconnect, use:

```mcfunction
/function sable:m37/cleanup
```

Wait for `[M37] CLEANUP COMPLETE` before creating another fixture. The command stops the tagged native piston, reverses its Creative Motor to retract naturally, disassembles its tagged outer Sable only after the inner payload is restored, then removes known fixture blocks and markers. A timeout leaves the fixture recoverable by the same cleanup command. Do not break an active contraption by hand.

For diagnostic detail, launch with `-Dsable.m31.traceCreateActors=true`; `SABLE_M37_FUNNEL` reports target transitions and native mounted-storage count changes only, not every frame.
