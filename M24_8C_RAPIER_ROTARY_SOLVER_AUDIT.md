# M24.8c Rapier Rotary Solver Audit

## Versions And Ownership

- Frozen Sable: `b7226222caf4eace63a708bdcd73ef36c971137d`.
- Frozen Sable Rapier dependency: fork `ryanhcode/rapier`, crate version `0.32.0`, revision `38e92f117590862481a53df6fc69a5d893e29186`.
- Target backport Rapier dependency: the same crate version and exact revision.
- Frozen Simulated: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

There is no Rapier version skew in this failure. The target retained the frozen
Sable adapter contract, including its treatment of the two endpoint normals.

## Exact Contract Mismatch

Rapier 0.32 `RevoluteJointBuilder::new(axis)` creates a generic joint with
`JointAxesMask::LOCKED_REVOLUTE_AXES` and initializes both local joint frames
from the same directed local X axis. The mask is correct: linear X/Y/Z and
angular Y/Z are locked, while angular X remains free.

`GenericJoint::set_local_axis1/2` does more than store an unoriented line. Each
call constructs a full local rotation frame with the supplied vector as its
directed X basis. Opposite vectors therefore produce joint frames separated by
180 degrees.

Frozen Simulated Swivel supplies the bearing facing and plate facing as the two
Sable Rotary normals. For facing components these are normally opposing vectors,
for example `(+1, 0, 0)` and `(-1, 0, 0)`. Frozen Sable forwarded both vectors
unchanged to Rapier and refreshed them unchanged before every solver step.
This treats an unoriented hinge line as two independently directed Rapier frames.

The real-body canary proved that the resulting 180-degree frame error is solved
on locked angular axes during the first physics step. With realistic inertia and
anchors near `(+2, ...)` and `(-2, ...)`, the corrective impulse sends both bodies
to extreme coordinates. Construction, insertion, raw-anchor conversion, and
pre-solver state remain finite.

## Generalized Adaptation

At the Sable-to-Rapier Rotary boundary, both endpoint axes are normalized and
projected through their owning rigid-body rotations. If their world-space dot
product is negative, body B's local axis is negated before Rapier constructs its
local frame. This preserves a valid local axis for already-rotated bodies while
giving Rapier the same directed physical hinge axis on both sides.

The conversion is applied both at insertion and in `joints::tick`, because the
latter refreshes Sable-owned joint frames immediately before every real solver
step. The public Sable anchors, endpoint normals, Simulated Swivel behavior, and
body transforms are unchanged.

## Other Candidates

- Joint mask: correct (`LIN_X|LIN_Y|LIN_Z|ANG_Y|ANG_Z` locked, `ANG_X` free).
- Anchor conversion: correct after M24.5 (`raw plot anchor - owning sublevel COM`).
- Frame finiteness: both generated quaternions were finite and normalized; the
  defect was their 180-degree relative orientation on locked axes.
- Connected-body contacts: not causal. The reproducing real-body canary calls
  `setContactsEnabled(false)` before the first step and still exploded.
- Rapier Fixed: unchanged and remains the stable control.
- Mass/inertia: realistic inertia plus offset anchors made the latent frame
  mismatch reproduce catastrophically; neither is modified by the fix.

## Native Regression Matrix

The target-native tests exercise the exact Rapier 0.32 revolute builder for:

1. simple inertia with centered anchors;
2. real anisotropic inertia with centered anchors;
3. simple inertia with offset anchors;
4. real anisotropic inertia with offset anchors.

All use opposing public endpoint normals, the generalized world-space sign
adaptation, the exact revolute locked-axis mask, and 100 solver steps. A separate
case proves that a body already rotated 180 degrees keeps its correct local axis
instead of being flipped from a local-space-only comparison.
