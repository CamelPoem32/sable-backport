# M20 Create-on-Sable Parity Matrix

Target: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8, Flywheel 1.0.5, Ponder 1.0.91.

Authoritative upstream Sable baseline: `b7226222caf4eace63a708bdcd73ef36c971137d`.

This matrix groups the exact upstream Create/Flywheel compatibility surface into compatibility concerns. Each row names representative upstream files; many concerns are implemented by multiple mixins in the baseline.

## Status Counts

| Status | Count |
| --- | ---: |
| `PORTED_RUNTIME_PROVEN` | 6 |
| `PORTED_STATIC_PROVEN` | 2 |
| `COVERED_BY_GENERALIZED_BACKPORT` | 7 |
| `NOT_APPLICABLE_1_20_1` | 1 |
| `UPSTREAM_FEATURE_ABSENT_IN_TARGET_CREATE` | 0 |
| `DEFERRED_EXPLICITLY` | 26 |
| `MISSING_APPLICABLE` | 0 |

## Matrix

| # | Upstream file/class | Upstream target | Purpose | Side | 1.20.1/Create 6.0.8 target | Current backport | Milestone | Status | Required action | Runtime family |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `compatibility.create.contraptions.AbstractContraptionEntityMixin` | `AbstractContraptionEntity` | Containing SubLevel ownership, hidden plot lifecycle | common | Exists | `AbstractContraptionEntityMixin` | M13/M14/M15 | `PORTED_RUNTIME_PROVEN` | None | bearing/piston/gantry |
| 2 | `compatibility.create.contraptions.ContraptionColliderMixin` | `ContraptionCollider` | Visible-space collision AABBs | common | Exists | `ContraptionColliderMixin` | M13/M14/M15 | `PORTED_RUNTIME_PROVEN` | None | bearing/piston/gantry/drill/deployer |
| 3 | `compatibility.create.contraptions.Matrix3dAccessor` | Create contraption transforms | Transform access | common | Exists | `Matrix3dAccessor` | M13-M15 | `PORTED_STATIC_PROVEN` | None | contraptions |
| 4 | `ContraptionHandlerClientMixin`, `ContraptionControlsRendererMixin`, `ContraptionVisualMixin`, `VisualizationEventHandlerMixin` | Create client contraption render/visual paths | Prevent hidden plot render leakage | client | 6.0.8 has different Flywheel path | Generalized visible-space renderer bridge plus forced BER policy | M13/M14/M15/M16/M17/M18/M19 | `COVERED_BY_GENERALIZED_BACKPORT` | Runtime cover with M20 overlay/controller fixtures | client render |
| 5 | upstream contraption control patches | Create contraption controls | Controller lookup from Sable storage | common/client | Exists | `SableCreateContraptionControllerLookup`, `ControlledContraptionEntityMixin` | M13/M14/M15 | `PORTED_RUNTIME_PROVEN` | None | controllers |
| 6 | target-specific Gantry precision | `GantryContraptionEntity`, `GantryContraptionUpdatePacket` | Fix 6.0.8 hidden-coordinate precision | common/client | Exists and vulnerable | `GantryContraptionEntityMixin`, `GantryContraptionUpdatePacketMixin` | M15 | `PORTED_RUNTIME_PROVEN` | None | gantry |
| 7 | `block_breaking_behaviour.BlockBreakingMovementBehaviourMixin` and helper | `BlockBreakingMovementBehaviour` | Moving drill/saw target selection | common | Exists | `BlockBreakingMovementBehaviourMixin`, `SubLevelBlockBreakingUtility` | M16 | `PORTED_RUNTIME_PROVEN` | None for drill; saw deferred | movement actors |
| 8 | `block_breaking_behaviour.SawMovementBehaviourMixin`, `saw.SawBlockEntityMixin` | Saw actor/static saw | Saw trees/static damage | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer saw/tree/static actor to M21+; not ordinary foundation parity | saw |
| 9 | `harvester_behaviour.*`, `harvester_block_entity.*`, `HarvesterTicker`, `HarvesterLerpedSpeed` | Harvester actor/static harvester | Crop harvesting and renderer | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer crop/block-entity-specific mutation; Drill already proves generic breaker boundary | harvester |
| 10 | `deployer.DeployerBlockEntityMixin` | `DeployerBlockEntity` | Static deployer adjustments | common | Exists | Not needed for tested path | M18 | `COVERED_BY_GENERALIZED_BACKPORT` | Runtime M20 observes no production change | deployer |
| 11 | `DeployerMovementBehaviour` baseline concern | Moving Deployer/fake player | Sable target world/coordinate bridge | common/client | Exists | `DeployerMovementBehaviourMixin`, `SubLevelDeployerInteractionUtility`, renderer mixins | M18 | `PORTED_RUNTIME_PROVEN` | None | deployer |
| 12 | `render_fixes.SafeBlockEntityRendererMixin`, `ValueBoxMixin` | Create SafeBER/value boxes | Render value/config boxes in visible space | client | Exists with changed packages | M11 value settings and M17 BER architecture | M11/M17 | `COVERED_BY_GENERALIZED_BACKPORT` | Use M20 redstone/value runtime fixture | value boxes |
| 13 | `render_fixes.GhostBlockValueBoxMixin`, `PlacementClientMixin` | Catnip placement ghost/value overlays | Placement-helper ghost preview | client | Exists in nested Ponder 1.0.91 | `GhostBlockRendererMixin`, M19 harness | M19 | `PORTED_STATIC_PROVEN` | Await M19 runtime acceptance; isolated from M20 | placement preview |
| 14 | `render_fixes.OutlineMixin`, `LineOutlineMixin`, `AABBOutlineMixin`, `BlockClusterOutlineMixin`, `ChasingAABB*`, `FilteringRendererMixin` | Catnip Outliner | Sable-space Create outlines | client | Exists in Ponder/Catnip | Not broadly ported | M20 | `DEFERRED_EXPLICITLY` | Defer broad overlay family; M19 covers placement ghost canary | overlays |
| 15 | `render_fixes.LinkRendererMixin` | Create link renderer | Link/selection render space | client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer specialized client overlay | overlays |
| 16 | `belt.BeltBlockMixin`, `BeltBlockEntityMixin`, `BeltMovementHandlerMixin`, `BeltRendererMixin` | Belt blocks, BE, movement handler, renderer | Stationary belt logistics and visuals | common/client | Exists | Not production-patched | M20 | `DEFERRED_EXPLICITLY` | M20 fixture/harness only; port on concrete runtime failure | logistics |
| 17 | `render_fixes.BeltRendererMixin` | Belt render path | Belt visible-space custom renderer | client | Exists | General block/BE render may cover; unproven | M20 | `DEFERRED_EXPLICITLY` | Runtime fixture before patch | logistics render |
| 18 | `depot.DepotRendererMixin` | `DepotRenderer` | Depot held item visible transform | client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Runtime fixture before patch | logistics render |
| 19 | `funnels.FunnelBlockMixin` | Funnel placement/use | Funnel targeting/orientation | common | Exists | M13/M19 targeting likely covers | M20 | `COVERED_BY_GENERALIZED_BACKPORT` | Verify with logistics fixture | logistics |
| 20 | `inventory_manipulation.CapManipulationBehaviourBaseMixin`, `ChuteBlockEntityMixin` | Inventory capability lookups | Same-sublevel capability routing | common | Exists | General Sable BE level/chunk storage likely covers | M20 | `COVERED_BY_GENERALIZED_BACKPORT` | Runtime logistics fixture | logistics/capability |
| 21 | `mechanical_arm.MechanicalArmBlockEntity`, `MechanicalArmSublevelFailure`, helpers | Mechanical arm point discovery | Same-Sable target discovery and arm state | common | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Dedicated future arm milestone; M20 fixture identifies behavior without fake transfer | arm |
| 22 | `airflow.AirCurrentMixin`, `FanProcessingTypeMixin`, `fans_provide_force.EncasedFanBlockEntityMixin` | Encased fan/air current | Airflow processing and force | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer fan processing/force; outside ordinary foundation closure | fan |
| 23 | `fluid_handling.PipeConnectionMixin`, `OpenEndedPipeMixin` | Fluid pipes/open pipes | Fluid network direction and world queries | common/client | Exists | General BE storage likely covers closed pipe network; open pipe unproven | M20 | `DEFERRED_EXPLICITLY` | M20 fluid fixture; port on runtime failure | fluids |
| 24 | `hose_pulley.HosePulleyBlockEntityMixin`, `HosePulleyFluidHandlerMixin` | Hose pulley | Large-area fluid intake/output | common | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer specialized fluid-world scanning | fluids |
| 25 | `fluid_tank_heating.BoilerDataMixin` | Boiler/tank heat scan | Fluid tank heating adjacency | common | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer steam/boiler specialization | fluids |
| 26 | `basin_interactions.*`, `blaze_burner.*`, `crushing_wheel*` | Processing machines | Recipe/processing interactions | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer processing machines; not ordinary foundation parity | processing |
| 27 | `redstone_contacts.*` | Redstone contact | Contact BE type and neighbor effects | common | Exists | M13/M14/M15 contraption/entity and M17 redstone harness partially cover | M20 | `DEFERRED_EXPLICITLY` | Defer redstone contact-specific behavior | redstone |
| 28 | `redstone_links.RedstoneLinkNetworkHandlerMixin` | Redstone link network | Wireless network coordinates | common | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer global wireless network semantics | redstone |
| 29 | `display_link.*`, `lectern_controller.*`, `stock_ticker.*` | Display/stock/link interactions | UI/link targets and global data | common/client | Exists | M11 value settings covers generic UI targeting only | M20 | `DEFERRED_EXPLICITLY` | Defer display network/global UI family | display |
| 30 | `ejector.*`, `mixinhelper.ejector.SubLevelScanResult` | Weighted ejector | Entity/item trajectory scan | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer trajectory-specific logistics | logistics |
| 31 | `toolbox.*` | Toolbox range/inventory | Player toolbox lookup | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer toolbox radius/global player inventory integration | player tools |
| 32 | `zapper.ZapperItemMixin`, `wand_of_symmetry.SymmetryHandlerMixin`, `blueprint.BlueprintEntityMixin` | Player tools | Client ray/world selection | common/client | Exists | M13 targeting covers normal block use only | M20 | `DEFERRED_EXPLICITLY` | Defer specialized tools | player tools |
| 33 | `vertical_gearbox.VerticalGearboxItemMixin` | Vertical gearbox item placement | Placement helper/orientation | common/client | Create 6.0.8 has gearbox, not exact upstream item path | M19/M17 cover generic placement/render | M20 | `NOT_APPLICABLE_1_20_1` | None | placement |
| 34 | `schematics.*` | Schematic tools/printer/levels | Schematic preview/place/export | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Explicitly out of ordinary foundation scope | schematics |
| 35 | `tracks.*` / `TrackPlacement` / `TrackGraphVisualizer` | Track placement, targeting, graph visualizer | Train track previews/graph overlays | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer train/track milestone; full train gameplay not M20 | trains/tracks |
| 36 | `trains.CarriageContraptionVisualMixin` | Train carriage visual | Train visual embedding | client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer full trains | trains |
| 37 | `frogports.*`, package port helpers | Package ports/frogports | Package logistics/chain conveyor integration | common/client | Exists in 6.0.8 package logistics | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer package logistics/additional chain conveyor family | package logistics |
| 38 | `chain_conveyor.*` | Chain conveyor | Conveyor shape/riding/entity package interaction | common/client | Exists | Not ported | M20 | `DEFERRED_EXPLICITLY` | Defer specialized package/conveyor subsystem | chain conveyor |
| 39 | `super_glue.*` | Super glue selection/removal/entity | Glue overlays and removal packet | common/client | Exists | Create sticky piston payload runtime covers attachment; UI removal unported | M20 | `DEFERRED_EXPLICITLY` | Defer glue UI packet/overlay | glue |
| 40 | `sticker.*` and `StickerBlockEntityExtension` | Sticker block/BE | Sticky contraption attachment | common | Exists | Generic contraption assembly likely covers static blocks | M20 | `COVERED_BY_GENERALIZED_BACKPORT` | Add controller fixture as future canary if needed | controller |
| 41 | `elevator_controls.*` | Elevator controls | Elevator floor UI/control | common/client | Exists | Generic contraption not enough; not ported | M20 | `DEFERRED_EXPLICITLY` | Defer elevator-specific UI/controller | controller |
| 42 | Flywheel compatibility package | Flywheel engine/storage/matrix/light | Embedded Flywheel render pipeline | client | Flywheel 1.0.5 architecture differs from NeoForge baseline | M11/M17 use forced-BER visible rendering instead | M20 | `COVERED_BY_GENERALIZED_BACKPORT` | No global Flywheel bridge in M20 | Flywheel/render |

## Coordinate Contract

- Fixture/local positions are the acceptance truth for M20 harness output.
- Raw hidden plot positions are storage coordinates only.
- Visible parent-world positions are derived from the Sable pose.
- Camera-relative render coordinates are the only coordinates allowed in PoseStack/model rendering.
- Contraption-local positions remain owned by Create contraption classes.

No M20 row leaves `MISSING_APPLICABLE`. Rows outside the ordinary foundation boundary are explicitly deferred with a proposed future milestone rather than silently ignored.
