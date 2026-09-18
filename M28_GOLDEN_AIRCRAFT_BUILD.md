# M28 Golden Aircraft Build

This is a compact integration aircraft, not a balanced survival design. Build
it manually in Creative mode. No `/sable m25`, `m26`, or `m27` fixture command
is part of this build.

## Coordinates

Use the Physics Assembler as local `(0, 1, 0)`. The nose points east (`+X`), up
is `+Y`, and starboard is south (`+Z`). Coordinates below are relative to the
assembler.

## Block list

| Count | Block | Purpose |
| ---: | --- | --- |
| 1 | `simulated:physics_assembler` | Normal Sable assembly/disassembly |
| 1 | `aeronautics:wooden_propeller` | M26 propulsion |
| 2 | Create Creative Motor | Propulsion and optional drill drive |
| 3 | `simulated:steering_wheel` | Pitch, yaw, and roll target input |
| 3 | Create Mechanical Bearing | Control-surface actuators |
| 3 | `simulated:white_symmetric_sail` | Isolated moving control payloads |
| 12 | Create white sail | Main aerodynamic lifting surfaces |
| 4 | Create gearbox | Vertical wheel output and shaft routing |
| 3 | Create shaft | Control transmission |
| 1 | Mechanical Drill | In-flight Create actor regression |
| 1 | Chest | Onboard inventory regression |
| 1 | Lever | Onboard redstone interaction |
| 1 | Redstone Lamp | Visible redstone result |
| about 30 | Copper block or other solid structure | Fuselage, deck, skids, spars |
| as needed | Super Glue | Attach only the static aircraft structure |

## Core and propulsion

| Position | Block and facing |
| --- | --- |
| `(0,1,0)` | Physics Assembler |
| `(-1,1,0)` and `(1,1,0)` through `(3,1,0)` | Copper fuselage blocks; leave `(0,1,0)` for the assembler |
| `(3,2,0)` | Creative Motor facing east, set to 0 before assembly |
| `(4,2,0)` | Wooden Propeller facing east |
| `(-1,2,0)` | Chest |
| `(0,2,-1)` | Lever |
| `(0,1,-1)` | Redstone Lamp |

The propeller and motor must remain part of the static Sable, not any bearing
payload. Configure motor speed through the normal Create motor value box after
assembly.

## Main wing

Build copper spars at `x=0`, `y=1`, from `z=-6` through `z=6`. Place Create
white sails at `y=2`, `x=0`, `z=-6..-1` and `z=1..6`, facing down. Glue each
sail to its spar and glue the spar back to the fuselage. These are the static
M27 lift/drag providers.

## Pitch channel

| Position | Block and facing |
| --- | --- |
| `(-5,2,-2)` | Steering Wheel on floor, facing north |
| `(-5,1,-2)` | Gearbox receiving the wheel's downward shaft |
| `(-5,1,-1)` | Horizontal shaft |
| `(-5,1,0)` | Mechanical Bearing facing south; rotation axis Z |
| `(-5,1,1)` | Symmetric Sail, axis Y; this is the only payload |

The sail touches only the bearing face. Leave air around its other five faces
and do not glue it. The bearing axis Z is orthogonal to its initial +Y normal.

## Yaw channel

| Position | Block and facing |
| --- | --- |
| `(-3,2,-4)` | Steering Wheel on floor, facing east |
| `(-3,1,-4)` | Gearbox |
| `(-4,1,-4)` | Horizontal shaft |
| `(-5,1,-4)` | Gearbox below bearing |
| `(-5,2,-4)` | Mechanical Bearing facing up; rotation axis Y |
| `(-5,3,-4)` | Symmetric Sail, axis Z; this is the only payload |

The +Y hinge is orthogonal to the sail's +Z normal. Keep the payload unglued
and separated from the tail structure by air.

## Roll channel

| Position | Block and facing |
| --- | --- |
| `(1,2,3)` | Steering Wheel on floor, facing west |
| `(1,1,3)` | Gearbox |
| `(1,1,4)` | Horizontal shaft |
| `(1,1,5)` | Mechanical Bearing facing south; rotation axis Z |
| `(1,1,6)` | Symmetric Sail, axis Y; this is the only payload |

This surface is offset to starboard so its aerodynamic force produces a roll
moment. The one-block east offset keeps the Steering Wheel out of the main-wing
sail row. The payload is one unglued sail and must not touch the main wing.

## Canonical control topology

All positions are relative to the Physics Assembler at `(0,1,0)`. Each listed
channel is a separate Create kinetic network. A control payload is exactly one
`simulated:white_symmetric_sail`, never `create:white_sail`.

| Channel | Wheel local position / facing | Gearbox local position(s) | Shaft local position | Bearing local position / facing | Sail local position / axis |
| --- | --- | --- | --- | --- | --- |
| Pitch | `(-5,2,-2)` / north | `(-5,1,-2)` | `(-5,1,-1)` | `(-5,1,0)` / south | `(-5,1,1)` / Y |
| Yaw | `(-3,2,-4)` / east | `(-3,1,-4)`, `(-5,1,-4)` | `(-4,1,-4)` | `(-5,2,-4)` / up | `(-5,3,-4)` / Z |
| Roll | `(1,2,3)` / west | `(1,1,3)` | `(1,1,4)` | `(1,1,5)` / south | `(1,1,6)` / Y |

Before moving a control wheel, run `/sable m28 validate_controls` while on or
targeting the assembled aircraft. It inventories the actual wheels first and
then compares their exact positions, the Create connectivity graph, the
assembled bearing movement mode, and the captured payload. `overallStatus=FAIL`
means repair only the reported blocks or settings before flight-control testing.
Power reaching a bearing does not prove the payload or movement mode is correct.

For an isolated comparison, build the pitch route from the table in an ordinary
static world: wheel, gearbox, shaft, Mechanical Bearing in `Only Place when
Anchor Destroyed` mode, and one unglued `simulated:white_symmetric_sail` on its
front. Operate the wheel and note wheel/shaft/bearing speeds, captured payload,
and contraption create/remove counts. Then build exactly that route on a tiny
manually assembled Sable with a Physics Assembler and static support, and repeat.
If the ordinary-world rig passes but the identical Sable rig fails, investigate
Sable/Create network reconstruction; if both fail, investigate the isolated
Steering Wheel/Create adaptation. Neither rig needs an M28 fixture command.

## Drill pod and skids

Place a second Creative Motor at `(2,1,-2)` facing east and a Mechanical Drill
at `(3,1,-2)` facing east. Set the motor to 0 before assembly. Build two simple
copper skids at `y=0`, `z=-2` and `z=2`, spanning `x=-2..2`, with one-block
struts to the fuselage.

## Glue map

Glue the fuselage, static wing spars/sails, deck, chest, lamp, drivetrain
controllers, drill pod, and skids into one connected assembly. Glue each wheel,
shaft, gearbox, and bearing casing to the static structure.

Never glue a symmetric sail payload to the aircraft. Never bridge around the
bearing with another touching block. Configure every control Mechanical Bearing
to Create's `ROTATE_NEVER_PLACE` (`Only Place when Anchor Destroyed`) movement
mode with its normal
wrench/scroll-value interaction. This is the real Create mode that keeps a
stopped contraption assembled at its commanded angle. Before Sable assembly,
activate each bearing and confirm it captures exactly its one intended symmetric
sail and remains present when its shaft stops.

Keep the three control drivetrains kinetically isolated. In particular, leave
`(-5,1,-3)` empty between the pitch gearbox at `(-5,1,-2)` and yaw gearbox at
`(-5,1,-4)`. Adjacent gearboxes join networks and turn independent Steering
Wheels into competing Create generators.

## Views

Top view, nose right:

```text
                 yaw sail
                    S
                    B
 pitch W-G--B-S  === fuselage ===  motor-propeller >
                    |      chest
       wing S S S S S S + S S S S S S
                              W-G--B-S roll
                    drill motor-D >
```

Side view:

```text
       steering wheels / chest / static sails
          W       C       S S S
       ===G=======A================M=P=>
          skid=================skid
```

Front view looking toward the nose:

```text
             yaw S
                 B
     S S S S S S | S S S S S S
     =============+=============
          skid         skid
```
