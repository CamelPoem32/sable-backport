# M24 Simulated Port Matrix

Scope: remaining ordinary Simulated physical systems and onboard foundation from
frozen upstream commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`. M21/M22/M23
remain closed and frozen.

| State | Count |
| --- | ---: |
| PORT_NOW | 6 |
| ADAPT_NOW | 15 |
| STRUCTURAL_ONLY | 4 |
| DEFER_AERONAUTICS | 7 |
| NOT_APPLICABLE_1_20_1 | 3 |

| Upstream path | Purpose | Dependencies | Java 21 / NeoForge / MC 1.21 usage | Target action | Target path |
| --- | --- | --- | --- | --- | --- |
| `common/src/main/java/dev/simulated_team/simulated/index/SimBlocks.java` | Registry owner for M24 block IDs. | Registrate, Simulated blocks. | NeoForge/Registrate chain differs from target DeferredRegister. | ADAPT_NOW | `forge/src/main/java/dev/simulated_team/simulated/index/SimulatedBlocks.java` |
| `common/src/main/java/dev/simulated_team/simulated/index/SimBlockEntityTypes.java` | Registry owner for M24 block entity IDs. | Registrate, BE classes. | NeoForge/Registrate chain differs from target DeferredRegister. | ADAPT_NOW | `forge/src/main/java/dev/simulated_team/simulated/index/SimulatedBlockEntityTypes.java` |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/torsion_spring/TorsionSpringBlockEntity.java` | Create kinetic torsion spring output. | Create kinetics, `ExtraKinetics`, value boxes. | Java 21 list APIs and newer Create helper shape. | ADAPT_NOW / RUNTIME_PROVEN | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/m24/M24TorsionSpringBlockEntity.java` |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/swivel_bearing/SwivelBearingBlockEntity.java` | Rotary bearing ownership and motor control. | Sable rotary constraint, Create kinetics. | NeoForge services and Create 6.0.10 helpers. | ADAPT_NOW / RUNTIME_PROVEN | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/m24/M24PhysicalBlockEntity.java` |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/swivel_bearing/link_block/*` | Swivel link block/plate endpoint. | Sable sublevel actor. | Registrate block/BE setup. | ADAPT_NOW | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/rope/rope_connector/*` | Rope endpoint. | Sable rope object/handle. | NeoForge/Registrate setup. | ADAPT_NOW / RUNTIME_PROVEN | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/rope/rope_winch/*` | Rope length control endpoint. | Sable rope object/handle, Create kinetics. | NeoForge/Registrate setup. | ADAPT_NOW / RUNTIME_PROVEN | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/m24/M24WinchBlockEntity.java` |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity.java` | Fixed docking constraint and transfer surfaces. | Sable fixed constraint, inventory/fluid/energy/CC. | NeoForge capabilities and optional CC APIs. | ADAPT_NOW / RUNTIME_PROVEN physics and lifecycle; STRUCTURAL_ONLY transfer | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/altitude_sensor/*` | Onboard altitude signal. | Sable pose, redstone/display. | NeoForge/Registrate setup. | ADAPT_NOW | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/velocity_sensor/*` | Onboard velocity signal. | Sable body velocity, redstone/display. | NeoForge/Registrate setup. | ADAPT_NOW | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/lasers/optical_sensor/*` | Onboard ray sensor. | Level raycast, redstone/display. | NeoForge/Registrate setup. | ADAPT_NOW | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/blocks/steering_wheel/*` | Onboard control input. | Later vehicle/Aeronautics control stack. | NeoForge/Registrate setup. | STRUCTURAL_ONLY | shared M24 block/entity |
| `common/src/main/java/dev/simulated_team/simulated/content/items/RopeCouplingItem.java` | Rope utility item. | Rope graph. | No Java 21-only runtime required for bootstrap. | PORT_NOW from M21 | `SimulatedItems.ROPE_COUPLING` |
| Aeronautics lift/propulsion/control classes | Aircraft gameplay. | Aeronautics, Create Aeronautics, aircraft controllers. | Outside Simulated ordinary systems. | DEFER_AERONAUTICS | none |

The target intentionally uses a compact Forge-only M24 implementation rather
than copying the frozen NeoForge/Registrate multi-loader graph. Registry IDs,
Sable backend primitive choices, persistence keys, and runtime command surfaces
are preserved or documented where the 1.20.1 backport API differs.

Final runtime disposition: Swivel Bearing, Rope Connector, Rope Winch, Docking
Connector, and Torsion Spring are all `RUNTIME_PROVEN`. `M24 CLOSED /
RUNTIME_PROVEN`; M21-M24 are frozen as the completed Simulated/Sable foundation.
