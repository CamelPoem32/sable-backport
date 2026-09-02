# M23 Constraint Architecture

Frozen upstream commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Upstream Ownership

Simulated 1.3.0 does not use one single constraint base class for every family.
The families use three different mechanisms:

- Spring: `SpringBlockEntity` implements `BlockEntitySubLevelActor` and applies
  sustained forces and torques every Sable physics tick.
- Swivel Bearing: `SwivelBearingBlockEntity` creates a
  `RotaryConstraintConfiguration`, stores a transient `RotaryConstraintHandle`,
  and recreates it after reload through the saved attached sublevel UUID and
  plate position.
- Docking Connector: `DockingConnectorBlockEntity` creates a
  `FixedConstraintConfiguration` during magnetic docking, stores a transient
  `FixedConstraintHandle`, and persists logical partner position/sublevel data.
- Rope Connector / Rope Winch: rope endpoints are managed by the rope strand
  graph (`ServerLevelRopeManager`, `ServerRopeStrand`, `RopeAttachment`) and use
  Sable rope objects rather than fixed/rotary constraints.
- Torsion Spring: the frozen source is Create kinetic extra-output behavior; it
  does not create a Sable backend joint.

## M23 Target Boundary

Spring is the implemented canary. It keeps the upstream server data model:

`SpringItem` -> two `SpringBlock` endpoints -> two `SpringBlockEntity` records
with `Controller`, `DesiredLength`, `Goal`, and optional `GoalSubLevel`.

When an endpoint is inside a `ServerSubLevel`, Sable calls:

`SpringBlockEntity.sable$physicsTick(ServerSubLevel, RigidBodyHandle, timeStep)`.

That method:

1. Resolves the paired Spring BE.
2. Resolves the partner `ServerSubLevel` through `Sable.HELPER` /
   `SubLevelContainer`.
3. Projects endpoint positions through each sublevel `logicalPose`.
4. Reads point velocities through `Sable.HELPER.getVelocity`.
5. Applies upstream point damping, Hooke-like alignment force, alignment torque,
   and axial angular damping through `ForceTotal`.
6. Calls `RigidBodyHandle.applyForcesAndReset`.

## Force Semantics

The Spring canary preserves the frozen upstream constants:

- max length: `9.0`
- point damping: `4.5`
- Hooke multiplier: `145.0`
- alignment torque multiplier: `20.0`
- axial angular damping: `2.0`
- snap dwell: `0.75` seconds
- size scale: small `0.5`, medium `1.0`, large `8.0`

The force is not a backend Spring joint. It is a Sable physics actor applying
real impulses each physics tick.

## Coordinates

Persistent endpoint identity is stored as the Spring BE block position plus
optional partner sublevel UUID. Runtime force calculations explicitly separate:

- block-local endpoint: face-biased point from the Spring block state
- hidden/raw sublevel position: the Spring BE position while inside a Sable plot
- visible world position: `subLevel.logicalPose().transformPosition(endpoint)`
- body-local force/torque: converted with `logicalPose().transformNormalInverse`

No hidden plot coordinate is used as a visible PoseStack or world-space
cancellation.

## Lifecycle

Creation is server-authoritative. The Forge target replaces the upstream
client-only Veil packet path with a Spring item that stores the first endpoint in
item NBT and creates both endpoint blocks on the second use.

Identity is logical, not Java-object based:

`logicalConstraintId = min(endpointA.asLong, endpointB.asLong) + ":" + max(...)`.

Removal destroys the paired endpoint and logs `SABLE_M23_CONSTRAINT
phase=REMOVE_SUCCESS family=spring`.

M22 disassembly now blocks when an active Spring endpoint is present in the
sublevel, returning `DISASSEMBLY_BLOCKED activeConstraints=[...]`. This avoids
removing a Sable body while the Spring actor still owns a relationship.

## Deferred Families

Swivel, Torsion, Rope/Winch, and Docking are exact frozen-upstream families but
are not physically runtime-enabled in this Spring-gate pass. Their owners and
Sable/Create/backend dependencies are inventoried in
`M23_CONSTRAINT_FAMILY_MATRIX.md`; no fake handles or test-only sustained
physics are introduced for them.
