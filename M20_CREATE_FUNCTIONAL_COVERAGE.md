# M20 Create 6.0.8 Functional Coverage

Target: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8, Flywheel 1.0.5, Ponder 1.0.91.

Authoritative Create source/bytecode: mapped development runtime
`forge/build/runtimePreflight/create-1.20.1-6.0.8-mapped-dev.jar`.

Authoritative upstream Sable baseline: `b7226222caf4eace63a708bdcd73ef36c971137d`.

`AllBlocks` exposes 197 block/list registration fields in the exact target jar.
Decorative/material families are grouped below by implementation class; every
non-decorative functional family is mapped to a runtime canary, a generalized
runtime-proven path, a static-only implementation waiting for runtime, or an
explicit large-subsystem deferral.

## Boundary Keys

`LEVEL_LOOKUP`, `BLOCKPOS_SPACE`, `BLOCK_ENTITY_LOOKUP`, `CAPABILITY_LOOKUP`,
`NEIGHBOR_UPDATE`, `GLOBAL_NETWORK`, `ENTITY_QUERY`, `CONTRAPTION_ASSEMBLY`,
`MOVEMENT_ACTOR`, `RENDER_BER`, `RENDER_VISUAL`, `ITEM_RENDER`, `OUTLINER_UI`,
`RAYCAST`, `PERSISTENCE`.

## Status Counts

| Status | Count |
| --- | ---: |
| `RUNTIME_PROVEN` | 14 |
| `GENERALIZED_RUNTIME_PROVEN` | 7 |
| `STATIC_ONLY` | 0 |
| `RUNTIME_REQUIRED` | 20 |
| `DEFERRED_LARGE_SUBSYSTEM` | 5 |

Unique functional behaviour families: 46.

## Coverage

| Family | Category | Representative Create block ids | BE / renderer / visual | Boundary keys | M milestone / canary | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Basic kinetic source/relay | `KINETIC_RELAY` | `create:creative_motor`, `create:shaft`, `create:cogwheel`, `create:large_cogwheel` | `CreativeMotorBlockEntity`, `BracketedKineticBlockEntity`, generic kinetic BER/visual | `NEIGHBOR_UPDATE`, `BLOCK_ENTITY_LOOKUP`, `RENDER_BER` | M11/M17/M20 `spawn_kinetic` | `RUNTIME_PROVEN` |
| Encased kinetic relays | `KINETIC_RELAY` | `create:andesite_encased_shaft`, `create:brass_encased_shaft`, encased cogwheel variants | `KineticBlockEntity`, `SimpleKineticBlockEntity` | `RENDER_BER`, `RENDER_VISUAL` | M17 specialized kinetic canary | `RUNTIME_PROVEN` |
| Gearbox/split shafts | `KINETIC_RELAY` | `create:gearbox`, `create:clutch`, `create:gearshift` | `GearboxRenderer`, `SplitShaftRenderer`, Flywheel visuals | `RENDER_BER`, `RENDER_VISUAL`, `NEIGHBOR_UPDATE` | M17 | `RUNTIME_PROVEN` |
| Chain drives | `KINETIC_RELAY` | `create:encased_chain_drive`, `create:adjustable_chain_gearshift` | `ChainGearshiftBlockEntity` | `NEIGHBOR_UPDATE`, `RENDER_VISUAL` | M20.7 `/sable m20 gauntlet kinetic <name>` creates `chain_drive` | `RUNTIME_REQUIRED` |
| Water wheels / hand crank / turntable | `KINETIC_MACHINE` | `create:water_wheel`, `create:large_water_wheel`, `create:hand_crank`, `create:turntable` | wheel/crank/turntable BEs | `BLOCK_ENTITY_LOOKUP`, `RENDER_VISUAL` | generalized kinetic + future input canary | `GENERALIZED_RUNTIME_PROVEN` |
| Gauges and speed control | `KINETIC_MACHINE` | `create:speedometer`, `create:stressometer`, `create:rotation_speed_controller` | gauge/controller BEs, value boxes | `OUTLINER_UI`, `BLOCK_ENTITY_LOOKUP`, `NEIGHBOR_UPDATE` | M20.7 `/sable m20 gauntlet controls <name>` creates `gauges` and `rotation_speed_controller` | `RUNTIME_REQUIRED` |
| Bearings | `CONTRAPTION_CONTROLLER` | `create:mechanical_bearing`, `create:windmill_bearing`, `create:clockwork_bearing` | bearing BEs, controlled contraptions | `CONTRAPTION_ASSEMBLY`, `RENDER_BER`, `PERSISTENCE` | M13 | `RUNTIME_PROVEN` |
| Piston controllers | `CONTRAPTION_CONTROLLER` | `create:mechanical_piston`, `create:sticky_mechanical_piston`, extension/head | piston BE, controlled contraption | `CONTRAPTION_ASSEMBLY`, `BLOCKPOS_SPACE`, `PERSISTENCE` | M14 | `RUNTIME_PROVEN` |
| Gantry | `CONTRAPTION_CONTROLLER` | `create:gantry_carriage`, `create:gantry_shaft` | gantry BEs/entity/update packet | `CONTRAPTION_ASSEMBLY`, `PERSISTENCE`, `RENDER_BER` | M15 | `RUNTIME_PROVEN` |
| Rope Pulley actuator | `CONTRAPTION_CONTROLLER` | `create:rope_pulley`, `create:rope`, `create:pulley_magnet` | `PulleyBlockEntity`, `PulleyContraption`, `AbstractPulleyRenderer` | `LEVEL_LOOKUP`, `CONTRAPTION_ASSEMBLY`, `RENDER_BER`, `PERSISTENCE` | M20.5 `spawn_controller`; M20.7 `/sable m20 gauntlet actors <name>` | `RUNTIME_PROVEN` |
| Elevator Pulley | `CONTRAPTION_CONTROLLER` | `create:elevator_pulley`, `create:elevator_contact`, `create:redstone_contact` | `ElevatorPulleyBlockEntity`, `ElevatorContraption`, `ElevatorColumn` | `LEVEL_LOOKUP`, `CONTRAPTION_ASSEMBLY`, `GLOBAL_NETWORK`, `PERSISTENCE` | M20.6 `spawn_elevator`; M20.7 `/sable m20 gauntlet controls <name>` | `RUNTIME_PROVEN` |
| Chassis/sticker/glue controls | `CONTRAPTION_CONTROLLER` | `create:linear_chassis`, `create:secondary_linear_chassis`, `create:radial_chassis`, `create:sticker`, `create:super_glue` | chassis/sticker BE, glue entity | `CONTRAPTION_ASSEMBLY`, `OUTLINER_UI` | M14/M16/M18 generalized payloads | `GENERALIZED_RUNTIME_PROVEN` |
| Mechanical Drill | `CONTRAPTION_ACTOR` | `create:mechanical_drill` | `DrillBlockEntity`, `DrillMovementBehaviour`, renderer/actor render | `MOVEMENT_ACTOR`, `LEVEL_LOOKUP`, `RENDER_BER` | M16 | `RUNTIME_PROVEN` |
| Deployer | `CONTRAPTION_ACTOR` | `create:deployer` | `DeployerBlockEntity`, fake player, renderer | `MOVEMENT_ACTOR`, `RAYCAST`, `LEVEL_LOOKUP`, `ITEM_RENDER` | M18 | `RUNTIME_PROVEN` |
| Saw | `CONTRAPTION_ACTOR` | `create:mechanical_saw` | `SawBlockEntity`, saw movement behaviour | `MOVEMENT_ACTOR`, `LEVEL_LOOKUP`, `ENTITY_QUERY` | M20.7 `/sable m20 gauntlet actors <name>` creates `saw_static` and `saw_tree` | `RUNTIME_REQUIRED` |
| Harvester | `CONTRAPTION_ACTOR` | `create:mechanical_harvester` | harvester BE/movement behaviour | `MOVEMENT_ACTOR`, `LEVEL_LOOKUP`, `BLOCKPOS_SPACE` | M20.7 `/sable m20 gauntlet actors <name>` creates `harvester` | `RUNTIME_REQUIRED` |
| Plough / Roller | `CONTRAPTION_ACTOR` | `create:mechanical_plough`, `create:mechanical_roller` | movement actors | `MOVEMENT_ACTOR`, `LEVEL_LOOKUP`, `ENTITY_QUERY` | M20.7 `/sable m20 gauntlet actors <name>` creates `plough` and `roller` | `RUNTIME_REQUIRED` |
| Belt logistics | `LOGISTICS` | `create:belt` | `BeltBlockEntity`, `BeltInventory`, `BeltRenderer` | `ITEM_RENDER`, `RENDER_BER`, `CAPABILITY_LOOKUP` | M20 `spawn_logistics` | `RUNTIME_PROVEN` |
| Depot and weighted ejector | `LOGISTICS` | `create:depot`, `create:weighted_ejector` | depot/ejector BEs/renderers | `CAPABILITY_LOOKUP`, `ITEM_RENDER`, `ENTITY_QUERY` | depot proven via Arm; M20.7 `/sable m20 gauntlet logistics <name>` creates `weighted_ejector` | `RUNTIME_REQUIRED` |
| Funnels, chutes, tunnels | `LOGISTICS` | `create:andesite_funnel`, `create:brass_funnel`, belt funnel variants, `create:chute`, `create:smart_chute`, `create:andesite_tunnel`, `create:brass_tunnel` | funnel/chute/tunnel BEs | `CAPABILITY_LOOKUP`, `NEIGHBOR_UPDATE`, `ITEM_RENDER` | M20.7 `/sable m20 gauntlet logistics <name>` creates `funnels_tunnels` and `chute_smart_chute` | `RUNTIME_REQUIRED` |
| Item vault/hatch/crate | `LOGISTICS` | `create:item_vault`, `create:item_hatch`, `create:creative_crate` | storage BEs | `CAPABILITY_LOOKUP`, `PERSISTENCE` | M20.7 `/sable m20 gauntlet logistics <name>` creates `vault` | `RUNTIME_REQUIRED` |
| Mechanical Arm | `LOGISTICS` | `create:mechanical_arm` | arm BE, arm renderer, arm selection outline | `CAPABILITY_LOOKUP`, `OUTLINER_UI`, `RENDER_BER` | M20.3 | `RUNTIME_PROVEN` |
| Portable storage interface | `LOGISTICS` | `create:portable_storage_interface` | PSI BE | `CAPABILITY_LOOKUP`, `CONTRAPTION_ASSEMBLY` | M20.7 `/sable m20 gauntlet interfaces <name>` creates `portable_storage_interface` | `RUNTIME_REQUIRED` |
| Belt/package logistics | `PACKAGE_LOGISTICS` | `create:chain_conveyor`, package port/frogport/postbox/packager/repackager/stock link/ticker/requester/factory gauge/table cloth families | package and chain conveyor BEs | `GLOBAL_NETWORK`, `ENTITY_QUERY`, `CAPABILITY_LOOKUP` | future package milestone | `DEFERRED_LARGE_SUBSYSTEM` |
| Closed fluid transfer | `FLUID` | `create:fluid_tank`, `create:fluid_pipe`, `create:mechanical_pump` | tank/pipe/pump BEs/renderers | `CAPABILITY_LOOKUP`, `NEIGHBOR_UPDATE`, `RENDER_BER` | M20 `spawn_fluids` | `RUNTIME_PROVEN` |
| Fluid valves/filtering | `FLUID` | `create:fluid_valve`, `create:smart_fluid_pipe`, glass/encased pipe variants | fluid BEs | `CAPABILITY_LOOKUP`, `NEIGHBOR_UPDATE` | M20.7 `/sable m20 gauntlet fluids <name>` creates `fluid_valve` and `smart_fluid_pipe` | `RUNTIME_REQUIRED` |
| Hose pulley / open-ended pipe | `FLUID` | `create:hose_pulley`, `create:item_drain`, `create:spout` | hose/drain/spout BEs | `LEVEL_LOOKUP`, `CAPABILITY_LOOKUP` | M20.7 `/sable m20 gauntlet fluids <name>` creates `hose_pulley` | `RUNTIME_REQUIRED` |
| Portable fluid interface | `FLUID` | `create:portable_fluid_interface` | PFI BE | `CAPABILITY_LOOKUP`, `CONTRAPTION_ASSEMBLY` | M20.7 `/sable m20 gauntlet interfaces <name>` creates `portable_fluid_interface` | `RUNTIME_REQUIRED` |
| Basin / burner / boiler | `PROCESSING_MACHINE` | `create:basin`, `create:blaze_burner`, `create:lit_blaze_burner`, tank boiler/steam engine/powered shaft | processing and heat BEs | `LEVEL_LOOKUP`, `CAPABILITY_LOOKUP`, `NEIGHBOR_UPDATE` | M20.7 `/sable m20 gauntlet processing <name>` creates `mixer_basin` and `heated_basin` | `RUNTIME_REQUIRED` |
| Press / mixer / millstone | `PROCESSING_MACHINE` | `create:mechanical_press`, `create:mechanical_mixer`, `create:millstone` | processing BEs/renderers | `CAPABILITY_LOOKUP`, `ITEM_RENDER`, `RENDER_BER` | M20.7 `/sable m20 gauntlet processing <name>` creates `press`, `mixer_basin`, and `millstone` | `RUNTIME_REQUIRED` |
| Crushing wheels | `PROCESSING_MACHINE` | `create:crushing_wheel`, `create:crushing_wheel_controller` | wheel/controller BEs | `ENTITY_QUERY`, `LEVEL_LOOKUP`, `ITEM_RENDER` | M20.7 `/sable m20 gauntlet processing <name>` creates `crushing_wheels` | `RUNTIME_REQUIRED` |
| Mechanical crafters | `PROCESSING_MACHINE` | `create:mechanical_crafter` | crafter BE | `NEIGHBOR_UPDATE`, `CAPABILITY_LOOKUP` | M20.7 `/sable m20 gauntlet processing <name>` creates `mechanical_crafters` | `RUNTIME_REQUIRED` |
| Fan and airflow | `PROCESSING_MACHINE` | `create:encased_fan`, `create:nozzle` | fan/nozzle BEs, air current | `LEVEL_LOOKUP`, `ENTITY_QUERY`, `CAPABILITY_LOOKUP` | M20.7 `/sable m20 gauntlet processing <name>` creates `encased_fan` | `RUNTIME_REQUIRED` |
| Ordinary Redstone Link | `GLOBAL_NETWORK` | `create:redstone_link` | link BE/network handler | `GLOBAL_NETWORK`, `BLOCKPOS_SPACE`, `NEIGHBOR_UPDATE` | M20.4 `/sable m20 spawn_link <name>` and `/sable m20 gauntlet redstone <name>` | `RUNTIME_PROVEN` |
| Contacts and observers | `REDSTONE` | `create:redstone_contact`, `create:smart_observer`, `create:threshold_switch` | redstone BEs | `LEVEL_LOOKUP`, `CAPABILITY_LOOKUP`, `NEIGHBOR_UPDATE` | M20.7 `/sable m20 gauntlet redstone <name>` creates `redstone_contact`, `threshold_switch`, and `smart_observer` | `RUNTIME_REQUIRED` |
| Analog lever/diodes | `REDSTONE` | `create:analog_lever`, pulse repeater/extender/timer, powered/toggle latch | redstone BEs | `NEIGHBOR_UPDATE`, `OUTLINER_UI` | generalized redstone canary | `GENERALIZED_RUNTIME_PROVEN` |
| Display links / boards / nixies | `DISPLAY_UI` | `create:display_link`, `create:display_board`, `create:orange_nixie_tube`, dyed nixies, `create:rose_quartz_lamp` | display/nixie BEs | `GLOBAL_NETWORK`, `OUTLINER_UI`, `RENDER_VISUAL` | M20.7 `/sable m20 gauntlet controls <name>` creates `display_link` | `RUNTIME_REQUIRED` |
| Contraption controls | `DISPLAY_UI` | `create:contraption_controls`, `create:controller_rail` | control BEs | `RAYCAST`, `CONTRAPTION_ASSEMBLY` | M13-M15 generic controller canaries | `GENERALIZED_RUNTIME_PROVEN` |
| Cart assembler | `CONTRAPTION_CONTROLLER` | `create:cart_assembler`, `create:minecart_anchor` | cart assembler BE | `CONTRAPTION_ASSEMBLY`, `ENTITY_QUERY`, `PERSISTENCE` | M20.7 `/sable m20 gauntlet controls <name>` creates `cart_assembler` | `RUNTIME_REQUIRED` |
| Trains/tracks | `TRAIN` | `create:track`, fake track, station, signal, observer, bogeys, train controls, railway casing | train graph BEs/renderers | `GLOBAL_NETWORK`, `RAYCAST`, `OUTLINER_UI` | future train milestone | `DEFERRED_LARGE_SUBSYSTEM` |
| Schematics | `SCHEMATIC` | `create:schematicannon`, `create:schematic_table` | schematic BEs/tools | `RAYCAST`, `PERSISTENCE`, `OUTLINER_UI` | future schematic milestone | `DEFERRED_LARGE_SUBSYSTEM` |
| Player equipment/tools | `PLAYER_TOOL` | backtanks, toolbox, clipboard, zapper/wand/blueprint item paths | equipment/tool BEs/items | `RAYCAST`, `CAPABILITY_LOOKUP`, `OUTLINER_UI` | future player-tool milestone | `DEFERRED_LARGE_SUBSYSTEM` |
| Decorative connected/copycat/material blocks | `DECORATIVE` | casings, brackets, doors, ladders, bars, scaffolds, girders, copycats, ores/material blocks, copper sets, sails, seats, whistles, bells, placards, cardboard, rose quartz variants | mostly vanilla/simple BEs | `RENDER_BER`, `PERSISTENCE` | grouped by implementation class | `GENERALIZED_RUNTIME_PROVEN` |

No non-decorative family is intentionally absent from this document. New runtime
failures should update this file with the exact boundary key before adding
production compatibility.
