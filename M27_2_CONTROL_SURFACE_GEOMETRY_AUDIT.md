# M27.2 Control-Surface Geometry Audit

## Frozen Authority

- Repository: `Creators-of-Aeronautics/Simulated-Project`
- Commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Target Create: 6.0.8 on Minecraft 1.20.1

Frozen `SymmetricSailBlock#sable$getNormal` returns the positive direction of
its `RotatedPillarBlock.AXIS`. The frozen Ponder scene rotates its rudder about
an axis orthogonal to that normal. Sable obtains the real interpolated Create
contraption pose and applies it to both the provider normal and application
point before accumulating drag and torque.

## Previous Failure

The M27.1 control fixture placed a south-facing Mechanical Bearing and a
runtime +Z-normal symmetric sail. Create therefore rotated about Z while the
provider normal was also +Z. Rotation about a vector leaves that vector
unchanged, so the bearing moved while the aerodynamic normal stayed constant.

The old `aero_vehicle` also placed another symmetric sail between the main
Create sail and the control payload. Because the harness glued every adjacent
fixture block, that sail formed a movement path into the main vehicle and let
the bearing capture eight blocks. This was fixture topology, not a Create
traversal defect.

## Corrected Geometry

| Component | Position relative to propeller | State |
| --- | --- | --- |
| control bridge | `(0,0,+1)` | static copper |
| control motor | `(0,0,+2)` | stopped, facing east |
| Mechanical Bearing | `(+1,0,+2)` | facing east, hinge axis +X |
| symmetric sail payload | `(+2,0,+2)` | axis Z, initial normal +Z |

The dot product is `(+X) dot (+Z) = 0`. Positive and negative real bearing
rotation transform the normal away from +Z in opposite directions. Runtime
diagnostics derive the current normal from
`KinematicContraption#sable$getLocalPose`, the same transform consumed by
`ServerSubLevel#prePhysicsTick`.

The payload is exactly one symmetric sail immediately in front of the bearing.
An air gap separates it from the main sail and structure. The vehicle's former
bridging sail is replaced by a main-body Levitite block away from the payload.

## Ownership And Lifecycle

- main Creative Motor, Wooden Propeller, regular Create sail, Mechanical
  Bearing, and control motor: `STATIC_SABLE`
- symmetric sail while assembled: `CREATE_CONTRAPTION`

Commands retain the authoritative fixture-session Sable UUID. Static
components resolve by saved Sable-local positions; the moving provider resolves
from the real bearing contraption. No fingerprint, nearest, or global lookup is
used after assembly.

`/sable m27 prepare_disassembly` stops both real Creative Motors and invokes
Create 6.0.8 `MechanicalBearingBlockEntity#disassemble`. It previews the same
anchor, visible goal, `Rotation.NONE`, block transform, and air occupancy used
by normal M22 disassembly. It never moves the Sable, clears blocks, or bypasses
the occupied-space guard.

Manual control, persistence, and disassembly acceptance completed successfully.
The bearing changes the control normal in opposite directions, captures only
its intended sail, preserves static main components, reloads under the same
Sable identity, and disassembles without loss or duplication. Final status:
`M27 CLOSED / RUNTIME_PROVEN`.
