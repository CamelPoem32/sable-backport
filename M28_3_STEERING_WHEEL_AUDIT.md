# M28.3 Steering Wheel Audit

## Frozen authority

- Repository: `Creators-of-Aeronautics/Simulated-Project`
- Commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Target: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8

Frozen `SteeringWheelBlock` exposes one vertical shaft: down when mounted on a
floor and up when mounted on a ceiling. Its rotation axis is always Y. It also
implements Create's `QuietUse` contract so the held steering interaction owns
the input instead of repeatedly invoking ordinary block use.
`SteeringWheelHandler` owns a held-use interaction, consumes mouse movement and
attack while active, and sends `SteeringWheelPacket`. The server
`SteeringWheelBlockEntity` is a `GeneratingKineticBlockEntity`. It emits signed
16 RPM, creates a Create `TURN_ANGLE` sequence context, propagates through the
ordinary kinetic network, integrates only while actual network speed is
nonzero, and returns generated speed to zero at the requested angle.

## Target divergence

The first target port retained the vertical `IRotate` contract and called
Create 6.0.8 `updateGeneratedRotation`, but simplified away the frozen timed
sequence context and integrated its displayed angle from logical speed even if
the network had no actual speed. Its client hold also left vanilla repeat-use
and attack paths live after the initial click.

The Golden Aircraft blueprint had two independent topology errors:

1. Pitch gearbox `(-5,1,-2)` touched yaw gearbox `(-5,1,-3)`. Create gearboxes
   have shafts on all faces, so the two Steering Wheels were competing
   generators in one network rather than independent pitch and yaw sources.
2. Roll Steering Wheel `(0,2,3)` occupied the same coordinate as the main-wing
   Create white sail at `(0,2,3)`. Whichever block was placed last replaced the
   other, explaining a stable count of only two wheels without requiring an
   assembly-loss hypothesis.

Create 6.0.8 `GeneratingKineticBlockEntity.applyNewSpeed` intentionally calls
`Level.destroyBlock` when a generator already has a source of equal-or-greater
speed with the opposite sign. The merged pitch/yaw network therefore explains
the interaction-correlated block destruction; it is not a Steering Wheel
right-click block replacement.

## M28.3 adaptation

The target wheel now retains the frozen `TURN_ANGLE` sequence ownership and
derives displayed physical travel only while Create reports actual network
speed. A client-neutral hold guard prevents repeated use and attack from being
routed to blocks that move under the crosshair during steering. It does not
change ordinary interaction when no Steering Wheel hold is active.

The blueprint moves yaw one block north and roll one block east. Every wheel
still outputs vertically into its own gearbox, but no control-network block is
shared or face-adjacent across channels, and no wheel overlaps a sail.
Production Create conflict handling is unchanged.
