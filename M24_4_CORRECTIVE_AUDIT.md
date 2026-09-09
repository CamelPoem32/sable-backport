# M24.4 Corrective Audit

Closure status: `M24 CLOSED / RUNTIME_PROVEN`. This document preserves the
M24.4 corrective audit that led to the accepted implementation.

Frozen upstream: `Creators-of-Aeronautics/Simulated-Project` commit
`9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Rotary Isolation

M24.4 adds `/sable m24 backend_canary rotary` and
`/sable m24 backend_canary rotary inspect`. The canary creates two ordinary
Sable sublevels containing only stone, then registers one passive
`RotaryConstraintConfiguration` through the public Sable physics API. It does
not use `M24PhysicalBlockEntity`, Swivel gameplay logic, Rope, Docking, or
Simulated family state.

This isolates the runtime question:

- If the canary explodes, the defect is in the Sable/Rapier rotary backport.
- If the canary survives, the defect is in Swivel family configuration beyond
  the raw Sable rotary primitive.

## Swivel

Frozen upstream Swivel is not two identical bearing blocks. The owner block is
`SwivelBearingBlockEntity`; the partner endpoint is the
`swivel_bearing_link_block` / plate side. The frozen upstream creates the
rotary configuration from:

- bearing anchor: `bearingPos.relative(facing).getCenter()`
- plate/link anchor: `plateAttachPos - plateFacing * 0.001`
- axes: bearing facing and plate facing

M24.4 changes only the target fixture and endpoint adapter to use the
bearing-to-link relationship and the tiny upstream plate offset. It does not
change the Sable/Rapier rotary backend, does not weaken extreme-coordinate
safety removal, and does not alter Spring behavior.

## Rope And Winch

Frozen upstream Rope creates visible scene-space rope points from the projected
endpoint distance. It stores a first/source segment extension equal to the
fractional remainder of that distance and uses full one-block segments for the
rest of the strand.

For a visible endpoint distance of `5.375`, upstream-equivalent state is:

- first segment extension: `0.375`
- fixed one-block segments: `5`
- total configured neutral length: `5.375`
- backend current length at creation: `5.375`

The target diagnostics previously compared backend current total length against
only the first-segment extension, making a correct upstream representation look
like a five-block length error. M24.4 reports first-segment extension,
fixed-segment count, total configured length, backend current length, and the
initial error between the two totals.

Winch inherits the same rope representation. With no explicit positive target,
it keeps the initial first-segment extension and therefore starts neutral; real
Create kinetic length control remains runtime-required.

## Docking

Docking Fixed backend creation is left unchanged. Runtime evidence already
showed stable fixed-constraint physics, symmetric active ownership, and
disassembly blocking while connected. M24.4 only verifies disconnect/removal as
the remaining lifecycle gate.

## Torsion

Frozen upstream Torsion is Create kinetic/onboard machinery, not a Swivel/Rope/
Docking backend joint. M24.4 keeps it out of backend rotary/fixed/rope
corrections. The runtime observation that bodies appear coupled must be
qualified against exact Create kinetic semantics, not treated as proof that a
new Sable joint should be invented.

## Fixture Body Identity

`/sable m24 bodies` previously rediscovered body A/B by spatial lookup near the
fixture's original visible assembler positions. Rope/Winch can move bodies
away from those positions, causing diagnostics to report unresolved bodies even
when the original Sables still exist.

M24.4 records authoritative Sable UUIDs after first successful resolution and
uses those UUIDs for later body inspection. Spatial lookup remains only the
initial discovery fallback.

## Collision Diagnostic Authority

`collisionGeometryPresent` is an upload bookkeeping diagnostic. M24.4 reports
it, but body validation now distinguishes it from authoritative rigid-body
existence. A body with valid stored blocks, endpoint, registered body, and valid
rigid-body handle is not rejected solely because the upload counter is false;
that case is reported as
`UPLOAD_COUNTER_FALSE_BODY_HANDLE_PRESENT` for runtime follow-up.
