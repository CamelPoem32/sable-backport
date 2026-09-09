# M24.2 Constraint Frame Audit

Closure status: `M24 CLOSED / RUNTIME_PROVEN`. This document preserves the
M24.2 pre-runtime frame audit that led to the accepted implementation.

## Shared Sable / Rapier Contract

Sable `RotaryConstraintConfiguration` and `FixedConstraintConfiguration` validate `pos1` and `pos2` as SABLE_RAW_PLOT anchors: each anchor must fall inside the plot of its owning `ServerSubLevel`. The Rapier native joint set stores those raw anchors and, during `joints::tick`, converts each anchor to RIGID_BODY_LOCAL by subtracting the owning body's center of mass before writing Rapier local anchors. PARENT_VISIBLE_WORLD / SABLE_VISIBLE_WORLD coordinates are used only for projected diagnostics, initial-error validation, and free rope body points.

The Java native entrypoint names these parameters `localAnchor*`, but the actual Sable wrapper contract is raw plot-space at the public API and COM-relative local-space inside the Rapier tick updater.

## Swivel Bearing

Frozen upstream derives a rotary anchor from the bearing face and the attached plate face:

- owner anchor: `bearingPos.relative(facing).getCenter()`
- partner anchor: `platePos.relative(plateFacing).getCenter()` with the small upstream plate-facing epsilon
- axes: block-facing normals in body/block-local orientation

M24 target before this pass passed each generic endpoint block center directly. In the swivel fixture, the two endpoints were also seven blocks apart, so the initial backend anchor error was several visible blocks even though the raw plots were unrelated hidden coordinates. This could create an unsatisfiable rotary joint immediately after backend registration.

Target adaptation:

- `backendAnchorRaw` derives swivel anchors from the block one space in front of each endpoint, matching upstream bearing/plate frame semantics.
- The swivel fixture places the second body four blocks away so the two face-derived raw anchors project to one visible hinge point.
- `SABLE_M24_CONSTRAINT_FRAME` logs raw anchors, visible anchors, body-local magnitudes, raw endpoint distance, and computed visible initial error before creation.
- Backend creation rejects non-finite frames or rigid-joint visible error over the M24 bootstrap tolerance before Rapier receives the handle.

## Fixed / Docking Connector

Frozen upstream docking derives fixed anchors from connector tip positions:

- owner anchor: `getTipPosition()`
- partner anchor: `other.getTipPosition()`
- orientation: relative body/block orientation, smoothed upstream while locking

Target adaptation:

- docking anchors are raw plot-space connector tips: block center plus facing * 1.5.
- the docking fixture spaces endpoints five blocks apart so their visible tips coincide.
- fixed orientation is derived as `inverse(ownerPose.orientation) * partnerPose.orientation`, normalized, rather than an unconditional identity frame.
- the same pre-create finite/error diagnostics gate fixed constraints.

## Rope Connector / Winch

Frozen upstream rope separates spaces:

- rope simulation points are projected visible scene-space positions from `Sable.HELPER.projectOutOfSubLevel`.
- rope attachments preserve block attachment identity and sublevel UUID.
- `ServerRopeStrand.applyAttachment` gives Sable/Rapier a raw plot-space attachment point; Rapier subtracts that sublevel COM during `rope::tick`.

M24 target before this pass initialized rope points from raw hidden plot positions and also used those raw points as attachments. That conflated visible rope-object coordinates with raw sublevel anchors.

Target adaptation:

- rope strand points are created from visible projected endpoints.
- rope attachments remain raw plot-space endpoint anchors so the native rope tick can convert them to body-local anchors.
- winch inherits the corrected rope endpoint and length-control path rather than rebuilding endpoints in parent/raw fixture coordinates.

## Numerical Canary

For two raw plots separated by thousands of blocks but visible bodies separated by approximately five blocks:

- raw endpoint distance may be thousands or millions and is only a storage identity diagnostic.
- visible endpoint distance is the physically meaningful initial error.
- rigid Swivel/Fixed fixtures must have visible endpoint error near zero before backend creation.
- rope visible segment length must be derived from visible endpoints, not raw plot separation.

No backend family may feed a raw plot distance as the physical visible distance between two different sublevels.
