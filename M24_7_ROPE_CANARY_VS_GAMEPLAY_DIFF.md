# M24.7 Rope Canary Vs Gameplay Low-Level Diff

Latest runtime evidence proved the high-level Rope length state is neutral at creation and that no later backend length write occurs: `backendLengthWriteCount=0`, `lastBackendLengthWriteOwner=CREATE_CONSTRUCTOR`.

The pure backend Rope canary uses two tiny Sable bodies and raw anchors outside the test bodies. It exercises `RopePhysicsObject`, `RapierRopeHandle`, native `createRope`, and `setRopeAttachment`, but it does not include Simulated Rope Connector or Rope Winch block collision geometry.

The real gameplay Rope path uses M24 component blocks. Before M24.7 those blocks inherited the generic full-cube block collision in Sable physics. Frozen upstream `RopeConnectorBlock` and `RopeWinchBlock` both implement `BlockSubLevelCollisionShape` and return `ROPE_CONNECTOR_COLLIDER`, a very thin `1,0,1 -> 15,0.25,15` collider transformed by block facing/axis. The target generic full cube made the rope endpoint/connector geometry materially different from upstream and from the passing pure canary.

M24.7 ports the upstream-style thin sublevel collider for target Rope Connector and Rope Winch while leaving the low-level Rope primitive, stiffness, damping, gravity, velocities, and length writes unchanged.

Additional diagnostics now record the actual backend constructor and attachment contract:

- `SABLE_ROPE_BACKEND_CONSTRUCT`
- `SABLE_ROPE_BACKEND_CONSTRUCT_JAVA`
- `SABLE_ROPE_BACKEND_CONSTRUCT_NATIVE`
- `SABLE_ROPE_BACKEND_ATTACHMENT`

These logs expose constructor first-joint length, stored backend first-joint length, raw anchors, body-space anchors, rope endpoint positions, solver attachment distance, moment arms, and the one expected force owner for a fresh pair.
