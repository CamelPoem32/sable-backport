# M27 Aerodynamics Baseline

## Authority

- Repository: `Creators-of-Aeronautics/Simulated-Project`
- Commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Ecosystem: Aeronautics/Simulated 1.3.0 with Sable 2.0.0
- Frozen source platform: Minecraft 1.21.1, NeoForge, Java 21, Create 6.0.10
- Target: Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8,
  Flywheel 1.0.5

The aerodynamic implementation is split across Sable, Create, and Simulated;
it is not an Aeronautics `wing` package. This split is why a name-only search
initially missed the frozen system.

## Frozen Owners

| Owner | Frozen role | M27 disposition |
| --- | --- | --- |
| `sable/common/.../BlockSubLevelLiftProvider` | Per-physics-step point velocity, air pressure, drag, lift, and off-COM torque | Existing exact Sable owner retained |
| `sable/neoforge/.../SailBlockMixin` | Makes Create `SailBlock` a lift provider; normal is `FACING.opposite`; center of mass is block center | Adapted as a common Forge-visible compatibility mixin |
| `simulated/common/.../SymmetricSailBlock` | Drag-only surface; lift scalar `0`, parallel drag scalar `1.75`, normal is positive pillar axis | Minimal Java 17/Forge block port |
| Create `MechanicalBearingBlockEntity` | Rotates a carried sail contraption through the normal Create kinetic lifecycle | Reused from target Create 6.0.8 |
| `simulated/.../SymmetricSailScenes` | Documents regular-sail lift and bearing-mounted symmetric-sail rudder/stabilizer use | Audit authority; Ponder scene deferred |

There is no dedicated upstream elevator/rudder block entity. Frozen control
surfaces are composition: a real aerodynamic sail is moved by Create
contraption mechanics. M27 uses a Mechanical Bearing carrying a symmetric sail,
powered by a separate Creative Motor.

## Surface Semantics

Regular Create sail:

- normal: opposite its `FACING`
- parallel drag scalar: `0.75`
- directionless drag scalar: `0.06888202261`
- lift scalar: `0.475`

Simulated symmetric sail:

- normal: positive direction of its pillar `AXIS`
- parallel drag scalar: `1.75`
- directionless drag scalar: inherited `0.06888202261`
- lift scalar: `0`

Frozen Ponder text explicitly describes the regular sail as producing lift and
the symmetric sail as producing drag for turning or stabilization.

## Scope

M27 adapts one production mixin, one production block implementation, the exact
white symmetric-sail model graph, and a command-only fixture/diagnostic owner.
M25 Levitite and M26 Wooden Propeller production code are unchanged. Wings,
autopilot, cockpit controls, and Golden Aircraft integration remain M28 scope.

Final status after manual M27.2 qualification: `M27 CLOSED / RUNTIME_PROVEN`.

## M27.2 Control Geometry

The corrected pitch fixture uses a +X Mechanical Bearing axis and a symmetric
sail whose frozen block-state normal is +Z. Their dot product is zero, so the
real interpolated Create contraption transform changes the aerodynamic normal.
The bearing frontier contains only that sail; an air gap prevents movement
traversal into the static main vehicle.
