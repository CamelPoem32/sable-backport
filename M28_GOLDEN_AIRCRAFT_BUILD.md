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
| 6 | Create gearbox | Vertical wheel output and shaft routing |
| 10 | Create shaft | Control transmission |
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
| `(-1,1,0)` through `(3,1,0)` | Copper fuselage blocks |
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
| `(-3,2,-3)` | Steering Wheel on floor, facing east |
| `(-3,1,-3)` | Gearbox |
| `(-4,1,-3)` | Horizontal shaft |
| `(-5,1,-3)` | Gearbox below bearing |
| `(-5,2,-3)` | Mechanical Bearing facing up; rotation axis Y |
| `(-5,3,-3)` | Symmetric Sail, axis Z; this is the only payload |

The +Y hinge is orthogonal to the sail's +Z normal. Keep the payload unglued
and separated from the tail structure by air.

## Roll channel

| Position | Block and facing |
| --- | --- |
| `(0,2,3)` | Steering Wheel on floor, facing west |
| `(0,1,3)` | Gearbox |
| `(0,1,4)` | Horizontal shaft |
| `(0,1,5)` | Mechanical Bearing facing south; rotation axis Z |
| `(0,1,6)` | Symmetric Sail, axis Y; this is the only payload |

This surface is offset to starboard so its aerodynamic force produces a roll
moment. The payload is one unglued sail and must not touch the main wing sail at
`(0,2,6)`; the one-block Y separation is intentional.

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
bearing with another touching block. Before assembly, activate each bearing and
confirm it captures exactly its one intended symmetric sail.

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

