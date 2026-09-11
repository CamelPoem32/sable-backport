# M27 Aerodynamic Force Architecture

## Production Call Graph

`SailBlock`/`SymmetricSailBlock`
-> `ServerLevelPlot.getLiftProviders()`
-> `ServerSubLevel.prePhysicsTick(...)`
-> `BlockSubLevelLiftProvider.sable$contributeLiftAndDrag(...)`
-> local linear/angular impulse accumulation
-> `RigidBodyHandle.applyLinearAndAngularImpulse(...)`
-> existing Rapier body.

Create bearing contraptions use the same provider interface through
`KinematicContraption.sable$liftProviders()` and its interpolated local pose.

## Frames And Point Velocity

- provider position: raw plot block center
- lever arm: provider raw center minus raw Sable COM; both are in the same
  body-local plot frame, so the hidden plot origin cancels
- visible application point: `logicalPose.transformPosition(rawPoint)`
- surface normal: block local, then optional contraption-local rotation
- point velocity world: `vCOM + omega x rWorld`
- point velocity body local: inverse Sable orientation of point velocity world
- force/impulse: accumulated in the Sable body-local frame
- torque: `(applicationPointLocal - COMLocal) x forceLocal`

Hidden plot coordinates never become visible-world lever arms or force axes.

The frozen model has no world wind field and no surface propwash coupling.
Wooden Propeller airflow affects its upstream entity/particle airflow behavior;
sail lift/drag consumes body and angular point velocity only.

## Exact Frozen Formula

For local point velocity `v`, local normal `n`, pressure `p`, and physics
substep `dt`:

```text
parallelDragImpulse = n * dot(n, v) * parallelDragScalar * p * dt
directionlessDragImpulse = v * directionlessDragScalar * p * dt
liftImpulse = n * length(v - parallelDragImpulse) * liftScalar * p * dt
bodyLinearImpulse -= parallelDragImpulse + directionlessDragImpulse + liftImpulse
bodyAngularImpulse -= (pointLocal - COMLocal) x
                      (parallelDragImpulse + directionlessDragImpulse + liftImpulse)
```

The implementation is intentionally preserved even though the lift expression
subtracts an impulse-scaled drag vector from velocity. M27 does not replace it
with a textbook coefficient model.

## Angle Convention

Frozen Sable does not calculate or persist a signed angle-of-attack scalar,
chord, or span. Its operational convention is the normal component
`dot(n, v)` plus the tangential speed used by the lift expression. M27 reports
`angleOfAttack=UNDEFINED_BY_FROZEN_MODEL`, `normalVelocity`, and
`tangentialSpeed` rather than inventing an angle contract.

## Control Ownership

The selected control surface is the exact frozen composition demonstrated by
Simulated: a symmetric sail on a Create Mechanical Bearing. The test command
assembles the bearing through its production API and changes a real Creative
Motor speed. Create owns surface motion; Sable discovers the moved provider and
computes drag/torque. The command never writes force or body state.

Persistence and disassembly remain production-owned: sail blockstates,
bearing contraption state, kinetic blocks, and Sable actor discovery serialize
through their existing M13/M22 paths. The M27 fixture session stores only the
authoritative Sable UUID and local component identities for diagnostics.

M27.2 keeps main propulsion and the regular sail in `STATIC_SABLE` ownership.
Only the symmetric sail moves into `CREATE_CONTRAPTION`. Its current normal is
read from the same `sable$getLocalPose` transform used by the production
pre-physics contribution path. The harness does not synthesize or write that
normal.
