# M23 Constraint Port Matrix

Frozen upstream commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

M23 reuses `M21_SIMULATED_PORT_MATRIX.md` and `M22_ASSEMBLY_PORT_MATRIX.md`.
Only the exact transitive graph required for the Spring force canary is adapted
now. Other constraint families are inventoried and explicitly deferred.

## Counts

| State | Count |
| --- | ---: |
| ADAPT_NOW | 7 |
| STRUCTURAL_ONLY | 1 |
| DEFER_M24 | 16 |
| NOT_APPLICABLE_1_20_1 | 4 |

## Selected Sources

| Upstream path | Target path | Family | Purpose | Dependency owner | Java 21 issue | NeoForge issue | MC API issue | Create issue | Sable issue | Target action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `content/blocks/spring/SpringBlock.java` | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/spring/SpringBlock.java` | Spring | endpoint block, facing/size state, pair cleanup, assembly listener | Simulated Spring | no | no | `getCloneItemStack` signature adapted | wrench cycling deferred | same listener API | ADAPT_NOW |
| `content/blocks/spring/SpringBlockEntity.java` | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/spring/SpringBlockEntity.java` | Spring | force actor, pair persistence, diagnostics | Simulated Spring | `Math.clamp` replaced | no | HolderLookup SmartBE read/write adapted to 1.20 BE NBT | SmartBE render/data helpers replaced | same Sable force APIs | ADAPT_NOW |
| `content/items/spring/SpringItem.java` | `forge/src/main/java/dev/simulated_team/simulated/content/items/spring/SpringItem.java` | Spring | normal item-driven endpoint creation | Simulated Spring | no | no | client packet path removed | no | same distance/containing helpers | ADAPT_NOW |
| `network/packets/PlaceSpringPacket.java` | folded into `SpringItem` server path | Spring | server-authoritative pair placement | Simulated Spring | `Math.clamp` replaced | Veil payload removed | 1.21 payload codecs removed | no | same pair data | ADAPT_NOW |
| `config/server/blocks/SimBlockConfigs.java` | `SimulatedConfig` | Spring/Rope/Swivel/Docking | M23 config flag and frozen defaults record | Simulated config | no | no | Forge config spec | Create config tree not ported | no | ADAPT_NOW |
| `util/SimAssemblyHelper.java` | existing target helper | Spring/M22 | active Spring disassembly guard | Sable assembly | no | no | no | no | blocks constrained sublevel deletion | ADAPT_NOW |
| `command` diagnostics | `M23SimulatedConstraintCommands.java` | Spring/all | status, fixture, inspect | Sable test commands | no | no | Forge Brigadier | no | reads Sable bodies | ADAPT_NOW |
| `content/blocks/rope/rope_connector/RopeConnectorBlock.java` | existing M21 simple block | Rope | ID/model/resource remains bootstrap-safe | M21 registration | no | no | no | no | rope physics deferred | STRUCTURAL_ONLY |
| `content/blocks/rope/rope_connector/RopeConnectorBlockEntity.java` | none | Rope | endpoint BE, rope attachment | rope graph | no | no | no | no | Sable rope graph | DEFER_M24 |
| `content/blocks/rope/rope_winch/RopeWinchBlock.java` | none | Rope Winch | kinetic winch block | rope graph | no | no | no | Create kinetic speed | Sable rope graph | DEFER_M24 |
| `content/blocks/rope/rope_winch/RopeWinchBlockEntity.java` | none | Rope Winch | rope length control | rope graph | no | no | no | Create kinetic speed | Sable rope graph | DEFER_M24 |
| `content/blocks/rope/strand/server/ServerLevelRopeManager.java` | none | Rope | server strand manager | rope graph | no | no | save data lifecycle | no | Sable rope object | DEFER_M24 |
| `content/blocks/rope/strand/server/ServerRopeStrand.java` | none | Rope | rope strand physics ownership | rope graph | no | no | no | no | Sable rope object | DEFER_M24 |
| `content/blocks/swivel_bearing/SwivelBearingBlock.java` | none | Swivel | bearing interaction and assembly | swivel graph | no | no | `ItemInteractionResult` 1.21-only | Create kinetic placement | Sable assembly | DEFER_M24 |
| `content/blocks/swivel_bearing/SwivelBearingBlockEntity.java` | none | Swivel | rotary constraint owner | swivel graph | no | no | HolderLookup NBT | Create kinetic/extra kinetics | `RotaryConstraintHandle` | DEFER_M24 |
| `content/blocks/swivel_bearing/link_block/SwivelBearingPlateBlock.java` | none | Swivel | attached plate | swivel graph | no | no | no | no | Sable plate owner | DEFER_M24 |
| `content/blocks/swivel_bearing/link_block/SwivelBearingPlateBlockEntity.java` | none | Swivel | plate parent pointer | swivel graph | no | no | no | no | Sable parent lookup | DEFER_M24 |
| `content/blocks/torsion_spring/TorsionSpringBlock.java` | none | Torsion | kinetic spring block | torsion graph | no | no | no | Create kinetic extra-output | no direct joint | DEFER_M24 |
| `content/blocks/torsion_spring/TorsionSpringBlockEntity.java` | none | Torsion | angle/kinetic output state | torsion graph | no | no | HolderLookup NBT | sequencer/value box | no direct joint | DEFER_M24 |
| `content/blocks/docking_connector/DockingConnectorBlock.java` | none | Docking | connector interaction/extension | docking graph | no | no | 1.21 state methods | display/redstone | Sable body lookup | DEFER_M24 |
| `content/blocks/docking_connector/DockingConnectorBlockEntity.java` | none | Docking | fixed constraint owner, inventory/tank/battery | docking graph | no | no | HolderLookup NBT | display/capability integration | `FixedConstraintHandle` | DEFER_M24 |
| `content/blocks/docking_connector/DockingConnectorPair.java` | none | Docking | pair state/alignment | docking graph | no | no | no | no | fixed constraint setup | DEFER_M24 |
| `compat/computercraft/*` | none | Docking/Swivel/Torsion | optional peripheral integration | optional compat | no | no | no | no | no | NOT_APPLICABLE_1_20_1 |
| `mixin/torsion_spring/*` | none | Torsion | comparator/bearing integration | mixin graph | no | no | descriptor verification needed | Create internals | no | NOT_APPLICABLE_1_20_1 |
| `mixin/rope/*` | none | Rope | rope client render hooks | mixin graph | no | no | descriptor verification needed | no | client sublevel render | NOT_APPLICABLE_1_20_1 |
| `client renderer/visual classes` | none | all | advanced visual renderers | client graph | no | no | Flywheel/Pinwheel drift | Flywheel 1.0.6->1.0.5 | visible-space render adaptation | NOT_APPLICABLE_1_20_1 |
