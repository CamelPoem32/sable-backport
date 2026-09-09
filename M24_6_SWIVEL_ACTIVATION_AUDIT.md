# M24.6 Swivel Activation Audit

Baseline: frozen Simulated `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Frozen Upstream

The Swivel Bearing is the active owner of the rotary backend handle. The paired
`SwivelBearingPlateBlockEntity` stores a parent pointer and delegates its
physics tick back to the bearing through `swivelBearingBlockEntity.updateServoCoefficients()`.

The bearing creates the `RotaryConstraintConfiguration` in
`SwivelBearingBlockEntity.attachConstraints(...)`:

- anchor A: bearing block center one block along bearing facing
- anchor B: plate block center one block along plate facing, offset by `0.001`
  along the plate facing
- axis A: bearing facing normal
- axis B: plate facing normal
- body A: sublevel containing the bearing
- body B: plate sublevel

The backend handle exists for an assembled bearing with a distinct plate
sublevel. Redstone/Create kinetic signal is used for servo/lock motor
coefficients, not as the condition that decides whether the passive rotary
joint exists. When not locking, upstream keeps the rotary joint and applies
passive unlocked damping through the handle.

## Target Before M24.6

The target collapsed M24 physical components into `M24PhysicalBlockEntity` and
elected pair controller ownership by UUID order. That allowed
`SWIVEL_BEARING_LINK_BLOCK` to become controller. The link block is not
constraint-backed, so it never called `ensureBackend(...)`. The actual bearing
became follower and waited for a backend that only the link would have owned in
the generic pairing model.

This exactly matches runtime:

- `SABLE_M24_CONSTRAINT phase=PAIR family=swivel_bearing`
- `constraintMode=SABLE_TO_SABLE`
- both handles valid
- `active=false`
- `runtimeState=WAITING_FOR_BACKEND`

## M24.6 Target Adaptation

Swivel pair ownership is now family-aware:

- `SWIVEL_BEARING` always owns the backend
- `SWIVEL_BEARING_LINK_BLOCK` is always the follower
- the backend creation predicate is `assembled_bearing_with_distinct_plate_sublevel`
- `signal=0` remains a passive/free hinge motor state, not a no-backend state

Diagnostics added:

- `SABLE_M24_SWIVEL phase=PAIR_FOUND`
- `SABLE_M24_SWIVEL phase=ACTIVATION_EVALUATED`
- `SABLE_M24_SWIVEL phase=BACKEND_CREATE_REQUEST`
- `SABLE_M24_SWIVEL phase=BACKEND_CREATED`

Allowed DOF is rotation around the bearing/link facing axis. Translational
separation and forbidden angular axes remain constrained by the Sable Rotary
backend.
