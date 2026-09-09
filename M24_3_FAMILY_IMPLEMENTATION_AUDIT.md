# M24.3 Family Implementation Audit

Closure status: `M24 CLOSED / RUNTIME_PROVEN`. This document preserves the
M24.3 pre-runtime family audit that led to the accepted implementation.

Frozen upstream: `Creators-of-Aeronautics/Simulated-Project` commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

This audit corrects the M24.2 assumption that a single generic `M24PhysicalBlockEntity`
could stand in as semantic authority for every remaining Simulated family. It cannot:
the frozen upstream families have distinct BlockEntity, manager, and lifecycle owners.

## Family Classes

| family | frozen upstream block | frozen upstream BlockEntity / owner | upstream backend owner | target representation before M24.3 | classification |
| --- | --- | --- | --- | --- | --- |
| Swivel Bearing | `content/blocks/swivel_bearing/SwivelBearingBlock.java` | `SwivelBearingBlockEntity.java`, plus `link_block/SwivelBearingPlateBlockEntity.java` | `RotaryConstraintConfiguration` owned by Swivel BE | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` | LOSSY_GENERIC_PLACEHOLDER |
| Rope Connector | `content/blocks/rope/rope_connector/RopeConnectorBlock.java` | `RopeConnectorBlockEntity.java` with `RopeStrandHolderBehavior` | `ServerRopeStrand` / `ServerLevelRopeManager` | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` | LOSSY_GENERIC_PLACEHOLDER |
| Rope Winch | `content/blocks/rope/rope_winch/RopeWinchBlock.java` | `RopeWinchBlockEntity.java` with Create kinetic speed and `RopeStrandHolderBehavior` | `ServerRopeStrand.updateFirstSegmentExtension` | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` | LOSSY_GENERIC_PLACEHOLDER |
| Docking Connector | `content/blocks/docking_connector/DockingConnectorBlock.java` | `DockingConnectorBlockEntity.java` plus `DockingConnectorPair.java` | `FixedConstraintConfiguration` with symmetric connector state | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` | LOSSY_GENERIC_PLACEHOLDER |
| Paired Docking Connector | `content/blocks/docking_connector/PairedDockingConnectorBlock.java` | no independent backend owner; paired front block participates in docking geometry | owned by Docking Connector BE | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` | MECHANICAL_ADAPTER |
| Torsion Spring | `content/blocks/torsion_spring/TorsionSpringBlock.java` | `TorsionSpringBlockEntity.java` and nested `Output` Create kinetic BE | Create kinetic output, not backend Sable joint | `M24PhysicalComponentBlock` + `M24PhysicalBlockEntity` structural/signal placeholder | STRUCTURAL_ONLY |

## Swivel

Frozen upstream creates a rotary constraint between the bearing face and the
assembled plate face. A Swivel is passive/unlocked by default: `updateServoCoefficients`
sets a motor with zero stiffness and friction damping until the block is in a locking
state or is driven by Create/sequenced kinetic input.

Target before M24.3 always applied a stiff generic rotary motor:

`setMotor(DEFAULT_AXIS, target, 24.0, 5.0, false, 0.0)`

even for the basic fixture with no valid kinetic command. Runtime evidence proves M24.2
anchors are aligned, so M24.3 preserves the anchor fix and changes only the generic
Swivel backend update to passive friction unless a target angle is explicitly present.

The previous `FIRST_STEP_VALIDATED` diagnostic fired from `sable$physicsTick`, before
`RapierPhysicsPipeline.physicsTick()` and before `SubLevelPhysicsSystem.updateAllPoses`.
M24.3 replaces it with delayed post-solver samples observed from later actor ticks:
`AFTER_PHYSICS_STEP_1`, `2`, `5`, and for ropes `20`.

## Rope

Frozen upstream `RopeStrandHolderBehavior.createRope` builds rope points from the
visible projected endpoint distance. It computes:

- `distance = ropeTarget.distanceTo(ropeStart)`
- `oneLongSegments = floor(distance)`
- `shortSegmentLength = distance - oneLongSegments`
- first segment extension = `shortSegmentLength`

Target before M24.3 created only start/mid/end points and then forced first-segment
length to `4.0` in `updateBackend`. Runtime reported visible endpoint distance around
`5.375`, so a fixed `4.0` first segment was not upstream-equivalent and could produce
delayed tension impulses.

M24.3 creates upstream-shaped rope points and initializes first-segment length from
the current visible geometry. Rope remains a rope; no Spring force law or Rapier terrain
code was changed.

## Winch

Frozen upstream Winch modifies the owned `ServerRopeStrand` extension from actual
Create kinetic speed. With no meaningful kinetic command, it must not command an
arbitrary target length. Target before M24.3 shared the Rope fixed `4.0` overwrite.
M24.3 inherits the corrected rope initialization and only applies a target length when
the generic target value is explicitly positive.

## Docking

Frozen upstream stores connector identity on both docking participants. One backend
handle may be owned by the connector that created it, but both bodies are logically
connected and lifecycle/disassembly state is symmetric while docked.

Target before M24.3 reported `simulated$hasActiveConstraint()` only on the BE that
owned the backend handle, so the M22 disassembly guard could block the controller body
while allowing the partner body. M24.3 makes a paired non-controller endpoint report
the controller-owned active relationship without clearing or weakening the guard.

## Torsion

Frozen upstream Torsion Spring is Create kinetic machinery. It stores an angle, drives
a nested generated-output kinetic BE, and unwinds only after kinetic input/preload and
redstone state allow it. The old fixture standing still is an equilibrium/inconclusive
case, not evidence that a backend Sable joint is missing.

M24.3 does not add fake torque.

## Frozen Boundaries

Unchanged:

- M21-M23 proven behavior
- Spring force, targeting, and teardown
- Rapier terrain collision and `PhysicsColliderBlockGetter`
- M22 glue/terrain selection and disassembly transaction safety
- Sable extreme-coordinate safety removal
