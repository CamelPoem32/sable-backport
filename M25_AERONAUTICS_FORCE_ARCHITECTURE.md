# M25 Aeronautics Force Architecture

## Selected mechanism

M25 selects frozen-upstream `aeronautics:levitite`. It is the smallest genuine lift mechanism that requires neither propulsion nor an Aeronautics block entity. Its block tag and `physics_block_properties` entry resolve `sable:floating_material` to `aeronautics:levitite`; Sable's existing production `FloatingClusterContainer` discovers it when the assembled plot changes.

The exact call ownership is:

1. `aeronautics:levitite` block state
2. `PhysicsBlockPropertiesDefinitionLoader`
3. `PhysicsBlockPropertyHelper.getFloatingMaterial`
4. `FloatingClusterContainer` cluster discovery
5. `ServerSubLevel.prePhysicsTick`
6. `FloatingBlockController.physicsTick` and `applyLift`
7. existing local linear/angular impulse accumulation
8. `RigidBodyHandle.applyLinearAndAngularImpulse`
9. existing Rapier rigid body

No Aeronautics parallel physics engine, velocity setter, teleport, or logical-pose mutation exists.

## Exact frozen formula

Levitite is `prevent_self_lift=true`, `lift_strength=10`, `transition_speed=3`, vertical friction `2 -> 0.1`, horizontal friction `1.5 -> 0.05`, and friction scales with gravity. It does not scale lift with pressure.

For each cluster, `weightedForce = liftStrength * totalScale`. Prevent-self-lift clusters are combined at their weighted average local application point. Initial local lift is `-localGravity * totalForce`; torque is `applicationPointFromCOM x liftingForce`. Sable derives translational plus rotational acceleration and caps the scale factor at 1 so Levitite alone cannot produce upward self-acceleration. Final linear and angular impulses are multiplied by the physics substep duration before reaching the rigid body.

One Levitite block can nominally support 10 kpg, but cannot accelerate its own Sable upward. The six-block M25 fixture is therefore expected to approach neutral hover when released; its otherwise identical control is expected to fall.

## Coordinate frames

| Quantity | Frame |
| --- | --- |
| Datapack lift direction | Opposite gravity |
| Gravity read | Visible/world frame from `DimensionPhysicsData` |
| Solver force/impulse | Converted to Sable body-local before accumulation |
| Stored cluster point | Raw plot frame |
| Physical lever arm | Small body-local point minus the same plot-space COM |
| Torque | Body-local `r x F` |
| Diagnostic application point | Transformed through that Sable's `logicalPose` into visible/world space |

Raw hidden plot coordinates are never treated as visible positions or direct lever arms. Lift remains world-up when the body rotates because world gravity is transformed into body local before the local impulse is applied through the body's orientation.

## Ownership and persistence

Mass, COM, inertia, pose, and velocity remain owned by `ServerSubLevel` and `RigidBodyHandle`. The M25 harness enables Sable's existing individual queued-force recording and converts the recorded substep impulse back to force for diagnostics; it does not apply that force.

Persistence requires no M25 custom body state. Levitite block state is serialized inside the Sable plot. On load, Sable rebuilds mass and floating clusters from block-property data, yielding one provider per stored Levitite block without a second Aeronautics force registry that could duplicate lift.

## Runtime closure

Manual qualification measured mass 12, gravity near -132 Y, Levitite lift near
+110 Y, and net force near -22 Y. The lift fixture fell substantially slower
than its zero-provider control. Reload restored the same body and exactly one
finite provider. M25 is `CLOSED / RUNTIME_PROVEN`; this path remains the M26
lift regression canary.
