# M35 one-command Roller fixture

Minecraft 1.20.1 / Forge / Create 6.0.8. Cheats enabled. Stand in an empty area with at least
27 blocks east-west, five blocks north-south, and six blocks of vertical clearance. The
function creates an internal marker four blocks above your feet and refuses an occupied
reserved volume. Do not run setup twice without cleanup.

## Normal-world Create control

```mcfunction
/function sable:m35/baseline
```

The function creates the complete east-facing sticky-piston Roller carriage, a mounted chest
with 16 cobblestone, a powered lever/clutch, outer assembler, and a separate terrain lane.
Leave the Physics Assembler alone. Click the visible lever on the clutch **once**, switching
it OFF. The negative-speed motor should extend the piston EAST. The chest and Roller must move
together as a native Create PistonContraption. If they do not, stop: the baseline fixture has
not passed and Roller compatibility cannot yet be judged.

## Inside Sable

After cleaning up the baseline, stand in another empty area:

```mcfunction
/function sable:m35/sable
```

Click the Physics Assembler above the two iron hull blocks **once**. It starts traversal at
the iron block directly below it, not at the Roller. Confirm the outer assembly succeeded.
Do not click the clutch lever for the Sable test. Start the level fixture and native piston
with one command:

```mcfunction
/function sable:m35/run
```

The command finds only the tagged, assembled M35 body, returns its controller to the
marker location, levels its rigid body while retaining yaw, and zeros fixture velocity each
server tick. It checks the production Roller's world-up predicate before releasing the native
Create clutch. It prepares the parent-world terrain lane using the body's current logical
pose; the lane is not fixed to the old world X axis. It never invokes a Roller actor itself.

```text
WEST                                                   EAST motion ->
[12 poles][sticky piston][radial chassis + chest]
                                [Roller above chassis]       [terrain X=4..14]
             [motor][clutch + lever]
             [iron hull -- iron hull + assembler above]
```

The function prints `[M35] READY` only after final build validation confirms the assembler's
starting iron block, connected stationary hull/glue, all extension poles, radial-chassis
attachment of Roller and chest, and chest with 16 cobblestone. Expect:

```text
[M35] SETUP phase=VALIDATE
[M35] SETUP startingBlock=<position> state=Block{minecraft:iron_block}
[M35] SETUP lookupStatus=EXACT_CURRENT
[M35] GLUE candidateCount=1
[M35] GLUE status=VALID movingPayload=CHASSIS_NATIVE_ATTACHMENT
[M35] SETUP outerSelectedBlocks=22
[M35] SETUP phase=READY
```

A failed validation rolls the generated fixture back and cannot print READY. This is a pre-click geometry check, not
proof that Create has assembled a moving entity. With actor tracing enabled using only
`-Dsable.m31.traceCreateActors=true`, expect `[M35] ORIENTATION ... supported=true` before
`[M35] RUN: native Create clutch engaged`. The exact gate is world-up dot product > 0.999,
or combined tilt < 2.563 degrees; the fixture targets roll=0 and pitch=0. A valid moving
fixture must produce a bounded `ROLLER_FOOTPRINT_RESOLVED` event.

PASS: the Roller/chest move together; the Roller clears the raised stone section and uses
native mounted cobblestone paving on the visible terrain; the chest loses material according
to native Create rules; no hidden plot terrain changes. `[M35] RUNTIME RUN PASS` requires
observed native footprint, clear and pave events, a changed parent terrain cell, lower native
mounted storage count, and unchanged raw plot terrain below the carriage. `INCOMPLETE` is not
a pass. Pitch/roll significantly away from world-up is intentionally unsupported in M35.

After each run, retract the native piston and wait for `CLEANUP phase=INNER_DISASSEMBLED`:

```mcfunction
/function sable:m35/retract
```

Then test yaw, translation, or depletion with exactly one of these commands. Each prepares
its own physical lane and starts the native clutch:

```mcfunction
/function sable:m35/yaw90
/function sable:m35/translate
/function sable:m35/depletion
```

Run them one at a time, retracting between runs. `yaw90` adds 90 degrees of world yaw while
keeping roll/pitch level. `translate` relocates the controller 18 blocks south of the marker.
`depletion` loads exactly two cobblestone into the mounted chest and requires native
consumption to zero. A moving piston, missing tagged fixture, or occupied/unloaded physical
lane is refused. The test pose hold is removed by cleanup.

## Reset

The fixture can now retract the inner piston through native Create motion without hand-editing
motor NBT. To return the piston to its static starting position while retaining the fixture:

```mcfunction
/function sable:m35/retract
```

The command powers the fixture lever to stop, reverses the Creative Motor to +64, re-engages
the clutch, waits for the sticky piston to place its chassis/Roller/chest at the retracted
position, then stops the clutch and resets the motor to -64. This may take several seconds.
Only run reset after `[M35] RETRACT: inner contraption disassembled` appears. For an assembled
Sable body, `retract` leaves the outer body assembled; use `cleanup` to disassemble it.

Then, if you wish to repeat the test without removing the fixture:

```mcfunction
/function sable:m35/reset
```

Reset refuses to modify an absent/moving static Roller. On success it restores the lane,
16 cobblestone, Roller mode 0/filter, and powered lever. It never edits a moving entity.

## Cleanup

From either the baseline or Sable fixture, run just:

```mcfunction
/function sable:m35/cleanup
```

Cleanup performs the same native retraction. In Sable mode it then invokes the fixture's own
Physics Assembler to disassemble the outer body, checks that the static fixture was restored,
restores the generated physical lane, stops the fixture-only pose hold, and removes the
reserved fixture blocks, terrain strip, glue and marker. Wait for
`[M35] CLEANUP COMPLETE version=5` before running the next `/function sable:m35/sable` or `baseline`.
After a yaw or translation test, cleanup first returns the tagged, fully retracted outer body
to its original level fixture pose so the native assembler can restore its known static layout.
On an unexpected state or 240-tick retraction timeout it stops without deleting an active
Create or Sable contraption. Stay within 96 blocks of the fixture marker when issuing the
command. Do not put unrelated builds in the reserved fixture volume.

Each public function now contains one command routed through `sable_m35`. Setup, reset,
retract, and cleanup all use the same loaded-dimension marker/controller lookup. A fresh
marker carries `sable_m35_origin`, `sable_m35_v5`, and a baseline/Sable mode tag. Version-4
and earlier M35 markers remain candidates; a moving native PistonContraption may own the
Roller/chest while their static positions are air. For a leftover fixture, run
`/function sable:m35/cleanup` first after installing the new jar. The first chat line must be
`[M35] CLEANUP ENTRY version=5`, followed by `[M35] LOOKUP candidateCount=...`, one bounded
component report per tagged candidate, and the teardown phases. If lookup reports `CORRUPT`
or `AMBIGUOUS`, preserve its exact `rejectReason` and component lines; do not remove blocks
manually. If entry does not appear, the new jar/function is not active.

## Source-backed correction to the previous fixture

The previous version-4 build did issue `setblock ~ ~ ~-2 minecraft:iron_block`.
No later terrain `/fill` intersects x=0. The log establishes that the cell was AIR
when assembly was attempted, but does not identify the later event that removed it.
Create's glue `contains(BlockPos)` tests block centers, but command-summoned glue
does not retain its custom bounding box: `/summon` loads `From`/`To`, then vanilla
`moveTo` calls `setPos`, rebuilding the default entity-sized box. The fixture now
creates one stationary glue entity with Create's AABB constructor. The x=1 radial
chassis needs no glue: with axis X, `sticky_north` means UP (Roller) and
`sticky_east` means SOUTH (chest) in Create 6.0.8. Setup checks Create's own
chassis inclusion list and runs a read-only outer assembly search; failed validation
rolls the fixture back without READY. The static verifier evaluates all setup
`setblock` and `fill` commands in order.

Relative to the elevated marker O, the stationary structure is: extension poles
x=-12..-1 at y=0,z=0; piston (0,0,0); motor (0,-2,0); clutch (0,-1,0);
lever (0,-1,1); iron hull (0,0,-2) and (0,0,-1); Physics Assembler
(0,1,-2). The inner moving payload is chassis (1,0,0), Roller (1,1,0),
and chest (1,0,1). The external terrain starts at x=4, leaving x=2 and x=3
clear. The assembler starts at (0,0,-2), the iron block directly below it.

If version 4 left the hull missing but the fixture controller remains, run
`/function sable:m35/cleanup` first. It identifies only the tagged version-4
fixture. A moving piston is recognized by its native CCE; a fully extended
piston is recognized by the chassis/Roller/chest at the controller's native
integer offset, including the observed offset 12. Cleanup reverses native
Create motion, then removes the partial static fixture. An orphan marker is
removed alone only when no fixture blocks or moving CCE remain.

The old motor used Create's default positive speed. For an east-facing Mechanical Piston,
`MechanicalPistonBlockEntity.getMovementSpeed()` negates positive shaft speed; a retracted
piston cannot extend in that direction. The new function sets the motor's native `ScrollValue`
to `-64` before kinetic startup. The old terrain `/fill` started at X=1 and cleared Y=-1..1,
including the static chassis at (1,0,0) and Roller at (1,1,0) in the normal-world baseline.
The new lane starts at X=4. `STARTING_BLOCK_AIR` means the assembler found AIR directly below
it; the abbreviated runtime report does not identify which subsequent action removed the old
fixture's iron block. The new setup asserts that iron block immediately before user action.

Runtime verification remains required. Static checks do not prove kinetic network formation,
Sable capture, contraption actor ticking, or terrain/material accounting in a particular world.
