# M26 Propulsion Baseline

## Authority

M26 uses the `aeronautics` module in
`https://github.com/Creators-of-Aeronautics/Simulated-Project.git` at frozen
commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`, Aeronautics 1.3.0. This is the
same release graph used by M25: Minecraft 1.21.1, NeoForge 21.1.228, Java 21,
Create 6.0.10, Flywheel 1.0.6, Sable 2.0.0, and Simulated 1.3.0. The target is
Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8, and Flywheel 1.0.5.

## Frozen propulsion inventory

The 46 Java and 83 resource paths previously classified as M26 propulsion
contain these intentional translational-force candidates:

| Candidate | Kinetic/force owner | Reverse | M26 decision |
| --- | --- | --- | --- |
| Wooden Propeller | `BasePropellerBlockEntity` plus `BlockEntitySubLevelPropellerActor` | Signed RPM and wrenchable `reversed` state | Selected |
| Andesite Propeller | Same small-propeller base, different radius/model | Yes | Deferred variant |
| Smart Propeller | Small propeller plus orientation/hinge control | Yes | Deferred to controls |
| Propeller Bearing | Mounted bearing contraption and sail graph | Signed speed | Deferred; requires entity, collision mixins, sound and mounted ownership |
| Gyroscopic Propeller Bearing | Bearing plus gyro behavior | Controlled | Deferred to M27 controls |
| Mounted Potato Cannon | Discrete recoil on firing | Not continuous RPM propulsion | Deferred |

The Wooden Propeller is the smallest source graph that reads real Create RPM
and contributes continuous body force without mounted contraptions or M27
aerodynamic/control systems.

## Selected frozen graph

The selected upstream graph comprises 11 semantic owners: `BasePropellerBlock`,
`BasePropellerBlockEntity`, `WoodenPropellerBlock`,
`WoodenPropellerBlockEntity`, `SimplePropellerRenderer`,
`WoodenPropellerRenderer`, `WoodenPropellerVisual`, `AeroBlocks`,
`AeroBlockEntityTypes`, `AeroPartialModels`, and `AeroPhysics`. Target Forge
registration and BER ownership replace Registrate/NeoForge/Flywheel bootstrap;
Sable's already-ported `BlockEntitySubLevelPropellerActor` remains the force
owner. Entity airflow particles/pushing, Ponder, advancements, and alternate
propellers are not needed by the body-thrust canary and remain deferred.
