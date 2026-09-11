# M28 Golden Aircraft Control Architecture

## Frozen authority

- Repository: `Creators-of-Aeronautics/Simulated-Project`
- Commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Target adaptation: Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8, Flywheel 1.0.5

The frozen tree has no monolithic aircraft controller, pilot seat, throttle
packet, or pitch/yaw/roll packet. Its real player control is the Simulated
Steering Wheel driving ordinary Create kinetics. Pitch, yaw, and roll are names
assigned by the aircraft's mechanical topology, not fields stored by the wheel.

## Exact frozen source graph

| Responsibility | Frozen owner |
| --- | --- |
| Placement, interaction, vertical shaft | `SteeringWheelBlock` |
| Bounded target angle and generated RPM | `SteeringWheelBlockEntity` |
| Mouse-hold interaction and client target | `SteeringWheelHandler` |
| Client-to-server intent | `SteeringWheelPacket` |
| Dynamic wheel geometry | `SteeringWheelRenderer`, `SteeringWheelVisual` |
| Moving control-surface transform | Create `MechanicalBearingBlockEntity` and `ControlledContraptionEntity` |
| Kinematic aerodynamic provider | Sable `KinematicContraption` integration plus Simulated `SymmetricSailBlock` |
| Aerodynamic force and torque | Sable `BlockSubLevelLiftProvider` production path |

## Control contract

The player holds use on the wheel and moves the mouse horizontally. The client
sends the exact wheel `BlockPos`, bounded target angle, and stop state. The
server validates the sender, exact block entity, finite target, owning Sable,
and interaction distance. The server wheel is the authoritative state owner.

The wheel has a configurable travel limit from 1 through 360 degrees, with a
frozen default of 180 degrees. It generates fixed-magnitude 16 RPM with the sign
needed to approach the requested angle. At the target it emits zero RPM. This
is positional control rather than endless spin and does not use a PID.

The output shaft points down for a floor-mounted wheel and up for a
ceiling-mounted wheel. Shafts and gearboxes route that output to a Mechanical
Bearing. The bearing moves an isolated symmetric-sail payload. Its real Create
contraption transform changes the sail normal, and M27 converts that changed
normal and local point velocity into aerodynamic force and torque.

## Axis ownership

| Aircraft role | Player input owner | Server state owner | Consumer | Physical effect |
| --- | --- | --- | --- | --- |
| Pitch | Dedicated onboard Steering Wheel | That wheel BE | Tail Mechanical Bearing around local Z | Tail sail normal changes in local X/Y, producing pitch moment |
| Yaw | Dedicated onboard Steering Wheel | That wheel BE | Fin Mechanical Bearing around local Y | Vertical sail normal changes in local X/Z, producing yaw moment |
| Roll | Dedicated onboard Steering Wheel | That wheel BE | Outboard Mechanical Bearing around local Z | Offset sail force changes roll moment |
| Throttle | Normal Create Creative Motor interaction | Create motor BE | Wooden Propeller kinetic network | M26 propulsion point force |

Input signs depend on wheel facing, floor/ceiling mounting, shaft routing, and
bearing facing exactly as normal Create kinetics dictate. Reverse a channel by
changing gearbox routing or its bearing/wheel facing; there is no hidden axis
sign table in Aeronautics.

## Server authority and cleanup

`SteeringWheelClientControl` reads mouse yaw only while the normal use key is
held. `SteeringWheelControlPacket` is serverbound. It resolves the exact packet
position and never searches for a Sable or fixture. The production wheel BE
clears its held/controller ownership on release, logout, death, dimension or
level departure, leaving the wheel at its last physical target with zero output
once that target is reached. Held ownership is deliberately not restored from
NBT after reload.

The controller association is derived from the wheel block inside a Sable and
`Sable.HELPER.getTrackingSubLevel(player)`. No M25, M26, or M27 fixture session
is imported by the production path.

## Rendering

The static mount and OBJ wheel assets are the exact frozen resources. Target
JSON uses Forge 1.20.1's `forge:obj` loader. The wheel partial is explicitly
registered before model bake and rendered by a BER fallback, so it remains
available inside Sable rendering without depending on Flywheel visual startup.
The renderer uses the local BE pose supplied by the established Sable render
dispatcher; it never introduces hidden plot translations.

## Explicitly absent from M28

There are no aircraft keybindings in the frozen control graph. There is no
direct Sable torque, orientation, velocity, or force packet. No autopilot,
stabilizer, cockpit HUD, fixture-spawn command, command throttle, or command
surface control is added.

