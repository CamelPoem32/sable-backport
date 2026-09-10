# M26 Propulsion Force Architecture

## Production call ownership

The frozen and target path is:

1. Create Creative Motor supplies signed RPM through the ordinary kinetic network.
2. `WoodenPropellerBlockEntity.tick` reads `getSpeed()` and smooths
   `rotationSpeed` toward `KineticBlockEntity.convertToAngular(RPM)` by 0.15.
3. `getDirectionIndependentSpeed` multiplies by `10/3`, the facing-axis sign,
   and the wrenchable `reversed` sign. At equilibrium this equals signed RPM.
4. Wooden thrust is `1.0 * directionIndependentSpeed`; airflow is
   `0.1 * directionIndependentSpeed`.
5. `BlockEntityPropeller.getScaledThrust` computes
   `-thrust * airflowScaling * localAirPressure`. Airflow scaling is
   `clamp((airflow + velocity dot worldAxis) / airflow, 0, 1)`, or 1 near zero
   airflow.
6. `BlockEntitySubLevelPropellerActor.sable$physicsTick` multiplies the body-local
   force by the physics substep and records it at the propeller block center in
   `ForceGroups.PROPULSION`.
7. `ForceTotal.applyImpulseAtPoint` accumulates local linear impulse and
   body-local torque `(applicationPoint - COM) x impulse`.
8. `RigidBodyHandle.applyLinearAndAngularImpulse` updates the existing Rapier body.

No M26 command applies force, changes velocity, or edits logical pose. The RPM
command changes only the real Creative Motor property. The optional orientation
command uses the same proven physical `RigidBodyHandle.teleport` pose API as
M24 and never applies propulsion.

## Sign and scaling

For an east-facing, non-reversed propeller at rest relative to the air:

- positive RPM produces negative-X body-local thrust;
- negative RPM produces positive-X body-local thrust;
- RPM zero converges the smoothed rotation speed and propulsion to zero;
- magnitude is linear in smoothed RPM, config thrust 1.0, airflow scaling, and
  dimension air pressure;
- reverse is supported both by RPM sign and the exact upstream wrench state.

There is no saturation beyond the upstream airflow-relative clamp. M26 retains
the exact 0.15 smoothing and 0.01 active threshold.

## Coordinate frames and torque

| Quantity | Frame/ownership |
| --- | --- |
| Propeller block position | raw Sable plot position |
| Application point | raw plot block center; converted to a small lever arm by subtracting the same raw COM |
| Local thrust axis | block-local facing represented in Sable body-local axes |
| Body-local force | local axis times scaled thrust |
| World thrust | body orientation transforms the local force each frame |
| Visible propeller/COM | each raw point transformed independently through `logicalPose` |
| Torque | body-local `r x F`, applied with the linear impulse |

Hidden plot coordinates never become world lever arms. Rotating the Sable
rotates directional propulsion, while M25 Levitite remains world-up because its
gravity-derived lift path performs the inverse world-to-body conversion.

The centered fixture places the propeller at the structural center with
symmetric motor/ballast and vertical mass. The offset fixture places the same
propeller two blocks along +Z; with positive-RPM force along -X it predicts
negative-Y torque. The combined fixture adds a second legitimate Levitite block
for reduced-fall or ascent behavior without changing production constants.

## Persistence and rendering

The Sable plot serializes the Wooden Propeller, its `reversed` state, kinetic BE
speed/rotation state, Creative Motor, and Levitite blocks. On load, normal block
entity actor registration rebuilds exactly one propulsion provider; M26 owns no
parallel persistent provider list.

M26.1 gives the test harness a separate, non-gameplay fixture session. Before
assembly it identifies the one newly created body by an exact assembler-relative
block fingerprint while excluding every Sable UUID that existed when the fixture
was placed. It then captures that Sable UUID and the assembler, propeller, and
Creative Motor positions relative to the Sable plot center. RPM, inspect, and
release retain that same active session; after capture they resolve only the
authoritative UUID and stored local positions. A missing body or invalidated
session fails explicitly and never falls back to another loaded propeller.

The exact frozen base, dynamic propeller, reversed propeller, item models and
textures are packaged. Forge 1.20.1 registers dynamic partials before bake and
uses one BER fallback; no Flywheel visual is registered concurrently. Sable's
existing renderer supplies camera-relative visible-space transforms.
