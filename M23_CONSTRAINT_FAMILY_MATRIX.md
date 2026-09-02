# M23 Constraint Family Matrix

Frozen upstream commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

M23 is constrained to Simulated physical constraints. The Spring family is the
first shared-architecture canary and is ported now as a real Sable force actor.
The other frozen-upstream families are inventoried here and remain explicit
future work rather than receiving invented or partial physics semantics.

## Counts

| State | Count |
| --- | ---: |
| ADAPT_NOW | 1 |
| STRUCTURAL_ONLY | 1 |
| DEFER_M24 | 4 |
| NOT_APPLICABLE_1_20_1 | 0 |

## Families

| Feature | Registry IDs | Block class | BlockEntity class | Item class | Renderer | Physics owner | Serialization owner | Create deps | Sable deps | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Spring | `simulated:spring` item, `simulated:spring` hidden/placed endpoint block | `content/blocks/spring/SpringBlock.java` | `content/blocks/spring/SpringBlockEntity.java` | `content/items/spring/SpringItem.java` | `SpringRenderer` upstream, target uses static M21 model for M23 | `SpringBlockEntity.sable$physicsTick` applies impulses/torques through `RigidBodyHandle` and `ForceTotal` | Spring BE NBT: `Controller`, `DesiredLength`, `Goal`, `GoalSubLevel` | upstream `SmartBlockEntity`, wrench/value UI; target removes GUI dependency | `BlockEntitySubLevelActor`, `RigidBodyHandle`, `ForceTotal`, `SubLevelContainer`, `Sable.HELPER` | ADAPT_NOW |
| Rope Connector | `simulated:rope_connector`, `simulated:rope_coupling` | `content/blocks/rope/rope_connector/RopeConnectorBlock.java` | `RopeConnectorBlockEntity.java` | `RopeItem` / `ROPE_COUPLING` | rope strand render graph | `ServerLevelRopeManager`, `ServerRopeStrand`, Sable `RopePhysicsObject` | rope manager/saved strand graph | no core Create physics dependency | Sable rope object/body attachment | STRUCTURAL_ONLY |
| Rope Winch | `simulated:rope_winch` | `content/blocks/rope/rope_winch/RopeWinchBlock.java` | `RopeWinchBlockEntity.java` | block item | `RopeWinchRenderer` | rope manager plus winch length control | BE + rope strand graph | kinetic block entity / speed control | Sable rope object | DEFER_M24 |
| Swivel Bearing | `simulated:swivel_bearing`, `simulated:swivel_bearing_link_block` | `SwivelBearingBlock.java`, `SwivelBearingPlateBlock.java` | `SwivelBearingBlockEntity.java`, `SwivelBearingPlateBlockEntity.java` | block item | `SwivelBearingRenderer`, visual | `RotaryConstraintConfiguration` / `RotaryConstraintHandle` | BE NBT: `SubLevelID`, `SwivelPlate`, `TargetAngle`, sequencer state | Create kinetic source, extra kinetic output, sequencer, cog placement | Sable rotary constraint API, sublevel assembly | DEFER_M24 |
| Torsion Spring | `simulated:torsion_spring` | `TorsionSpringBlock.java` | `TorsionSpringBlockEntity.java` | block item | `TorsionSpringRenderer`, visual | Create kinetic extra-output, no Sable backend joint in frozen source | BE NBT: angle/target/generated-speed/sequencer state | Create kinetic network, sequencer, value box | no direct Sable constraint handle | DEFER_M24 |
| Docking Connector | `simulated:docking_connector`, `simulated:paired_docking_connector` | `DockingConnectorBlock.java`, `PairedDockingConnectorBlock.java` | `DockingConnectorBlockEntity.java` | block item | `DockingConnectorRenderer` | `FixedConstraintConfiguration` / `FixedConstraintHandle` through magnetic pair search | BE NBT: other connector position/sublevel, inventory/tank/battery, extension | display source, redstone, optional ComputerCraft, fluid/energy storage | Sable fixed constraint API and body lookup | DEFER_M24 |

## M23 Registry Decision

No new registry entries are added in this pass. The runtime-proven M21 graph
already contains the `simulated:spring` and `simulated:rope_connector` IDs. M23
activates Spring behavior on those existing IDs and keeps all other family IDs
documented here until their full dependency graphs can be ported without
inventing semantics.
