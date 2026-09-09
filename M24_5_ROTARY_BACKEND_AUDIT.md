# M24.5 Rotary/Rope Backend Audit

Closure status: `M24 CLOSED / RUNTIME_PROVEN`. This document preserves the
M24.5 backend audit that led to the accepted implementation.

## Rotary Primitive Boundary

Runtime proved `/sable m24 backend_canary rotary` explodes two ordinary M22/Sable
bodies without any Simulated M24 BlockEntity. The body anchors reconstruct to the
same visible point, the backend handle is created, and both bodies are then moved
to multi-million-coordinate poses before Sable's extreme-coordinate safety removes
them. This isolates the failure below Simulated Swivel.

Target Sable public contract:

- `RotaryConstraintConfiguration.pos1/pos2`: Sable raw plot anchors.
- `RotaryConstraintConfiguration.normal1/normal2`: body-local joint axes.
- Rapier revolute joint insertion requires local anchors and local axes at handle
  creation time.

Target bug:

- `Rapier3D_addRotaryConstraint` inserted a `RevoluteJointBuilder` with
  `local_anchor1(Vec3::ZERO)` and `local_anchor2(Vec3::ZERO)`.
- The later `joints::tick` path already corrected those anchors by subtracting
  each sublevel center of mass, but the solver could observe the wrong zero-anchor
  frame immediately after handle creation.
- This differs from the working post-insert Sable updater and from the target
  Rapier API contract, where the initial local frames must be coherent before the
  first solver pass.

M24.5 fix:

- At rotary handle creation, compute `local_anchor_1 = raw_anchor_a - COM_A` and
  `local_anchor_2 = raw_anchor_b - COM_B` before inserting the revolute joint.
- Initialize both local axes on the joint data before insertion completes.
- Preserve the existing post-create `joints::tick` updater and do not touch Fixed
  constraint handling.

## Fixed Control

Docking Fixed remains the known-good control. M24.5 does not change
`RapierFixedConstraintHandle`, `FixedConstraintConfiguration`, or the docking
fixed-joint production path.

## Rope Primitive Boundary

Runtime proved the M24.4 Rope length mismatch is fixed: visible distance,
configured length, stored configured length, current length, and backend current
length all match at creation. The remaining failure is a first-step impulse from a
zero-error, zero-velocity rope.

Target bug:

- `Rapier3D_setRopeAttachment` inserted the endpoint attachment joint with
  `local_anchor1(Vec3::ZERO)`.
- The later `rope::tick` updater already corrected the body-side anchor with
  `attachment.location - sublevel COM`.
- Therefore the first solver frame could briefly attach the rope endpoint to the
  body center of mass instead of the requested raw attachment point.

M24.5 fix:

- Initialize rope attachment joints with `attachment.location - COM` at insertion
  time.
- Preserve configured rope length, segment construction, RopePhysicsObject state,
  and Winch target semantics.

## Backend Canaries

`/sable m24 backend_canary rotary` remains Simulated-free and now reports the
final body-local anchors and axes expected by Rapier.

`/sable m24 backend_canary rope` creates two ordinary M22/Sable bodies, creates a
`RopePhysicsObject` directly, attaches both ends to real Sable bodies, and reports
configured length, backend current length, and first-step inspection data without
using M24 Rope Connector, Winch, or Simulated rope manager logic.

## Auto-Pair And Body Identity

Frozen Simulated physical-family behavior is auto-owned by block/block-entity
lifecycle once both endpoints are valid. M24 target endpoints also auto-pair from
their `BlockEntitySubLevelActor` tick after the second body assembles.

M24.5 captures fixture body UUIDs at the successful M22 assembly completion point:

`PhysicsAssemblerBlockEntity.assemble -> M24SimulatedSystemsCommands.onM22AssemblyCreated`

This happens before endpoint auto-pair/constraint creation can move or remove the
bodies. `/sable m24 bodies` now reports the last known UUID even if the body is
already removed after constraint creation, rather than degrading to `unresolved`.
