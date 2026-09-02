# M21 Simulated Registry Manifest

Frozen upstream commit: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

M21 registers only the bootstrap-safe static subset needed to prove the
Simulated mod id, Forge registration lifecycle, block/item packaging,
BlockEntity creation, config bootstrap, network channel creation, creative tab
visibility, resources, and `/sable m21` diagnostics. Physics and behavior-heavy
registrations are explicitly deferred in `M21_SIMULATED_PORT_MATRIX.md`.

## Counts

| Registry type | Count | M21 status |
| --- | ---: | --- |
| `minecraft:block` | 6 | `ADAPT_NOW` |
| `minecraft:item` | 11 | `ADAPT_NOW` |
| `minecraft:block_entity_type` | 2 | `ADAPT_NOW` |
| `minecraft:creative_mode_tab` | 1 | `ADAPT_NOW` |
| `minecraft:entity_type` | 0 | `DEFER_M22` |
| `minecraft:menu` | 0 | `DEFER_M23` |
| `minecraft:recipe_type` | 0 | `DEFER_M23` |
| `minecraft:recipe_serializer` | 0 | `DEFER_M23` |
| `simulated:network_channel` | 1 | `ADAPT_NOW` |
| `forge:common_config` | 1 | `ADAPT_NOW` |
| `forge:client_config` | 1 | `ADAPT_NOW` |

## Registered Entries

| Registry type | Registry id | Upstream owner | Target owner | M21 status |
| --- | --- | --- | --- | --- |
| `minecraft:block` | `simulated:physics_assembler` | `SimBlocks.PHYSICS_ASSEMBLER` | `SimulatedBlocks.PHYSICS_ASSEMBLER` | `ADAPT_NOW` |
| `minecraft:block` | `simulated:spring` | `SimBlocks.SPRING` | `SimulatedBlocks.SPRING` | `ADAPT_NOW` |
| `minecraft:block` | `simulated:rope_connector` | `SimBlocks.ROPE_CONNECTOR` | `SimulatedBlocks.ROPE_CONNECTOR` | `ADAPT_NOW` |
| `minecraft:block` | `simulated:iron_handle` | `SimBlocks.IRON_HANDLE` | `SimulatedBlocks.IRON_HANDLE` | `ADAPT_NOW` |
| `minecraft:block` | `simulated:redstone_magnet` | `SimBlocks.REDSTONE_MAGNET` | `SimulatedBlocks.REDSTONE_MAGNET` | `ADAPT_NOW` |
| `minecraft:block` | `simulated:white_symmetric_sail` | `SimBlocks.WHITE_SYMMETRIC_SAIL` | `SimulatedBlocks.WHITE_SYMMETRIC_SAIL` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:physics_assembler` | block item from `SimBlocks.PHYSICS_ASSEMBLER` | `SimulatedItems.PHYSICS_ASSEMBLER` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:spring` | `SimItems.SPRING` placement item | `SimulatedItems.SPRING` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:rope_connector` | block item from `SimBlocks.ROPE_CONNECTOR` | `SimulatedItems.ROPE_CONNECTOR` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:iron_handle` | block item from `SimBlocks.IRON_HANDLE` | `SimulatedItems.IRON_HANDLE` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:redstone_magnet` | block item from `SimBlocks.REDSTONE_MAGNET` | `SimulatedItems.REDSTONE_MAGNET` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:white_symmetric_sail` | block item from `SimBlocks.WHITE_SYMMETRIC_SAIL` | `SimulatedItems.WHITE_SYMMETRIC_SAIL` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:contraption_diagram` | `SimItems.CONTRAPTION_DIAGRAM` | `SimulatedItems.CONTRAPTION_DIAGRAM` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:rope_coupling` | `SimItems.ROPE_COUPLING` | `SimulatedItems.ROPE_COUPLING` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:gyroscopic_mechanism` | `SimItems.GYRO_MECHANISM` | `SimulatedItems.GYROSCOPIC_MECHANISM` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:engine_assembly` | `SimItems.ENGINE_ASSEMBLY` | `SimulatedItems.ENGINE_ASSEMBLY` | `ADAPT_NOW` |
| `minecraft:item` | `simulated:honey_glue` | `SimItems.HONEY_GLUE` | `SimulatedItems.HONEY_GLUE` | `ADAPT_NOW` |
| `minecraft:block_entity_type` | `simulated:physics_assembler` | `SimBlockEntityTypes.PHYSICS_ASSEMBLER` | `SimulatedBlockEntityTypes.PHYSICS_ASSEMBLER` | `ADAPT_NOW` |
| `minecraft:block_entity_type` | `simulated:spring` | `SimBlockEntityTypes.SPRING` | `SimulatedBlockEntityTypes.SPRING` | `ADAPT_NOW` |
| `minecraft:creative_mode_tab` | `simulated:main_tab` | `SimulatedNeoForge.TAB` | `SimulatedCreativeTabs.MAIN_TAB` | `ADAPT_NOW` |
| `simulated:network_channel` | `simulated:main` | `SimPacketManager` / Veil payload registration | `SimulatedNetwork` / Forge `SimpleChannel` | `ADAPT_NOW` |
| `forge:common_config` | `simulated-common.toml` | `NeoForgeSimConfigService` / `SimServer` | `SimulatedConfig.COMMON_SPEC` | `ADAPT_NOW` |
| `forge:client_config` | `simulated-client.toml` | `NeoForgeSimConfigService` / `SimClient` | `SimulatedConfig.CLIENT_SPEC` | `ADAPT_NOW` |

## Deferred Registration Families

- Full upstream block graph: deferred to M22/M23 because constructors and static
  setup pull in Create movement behaviors, Sable assembly hooks, sensors,
  docking, rope, physics staff, advanced renderers, or NeoForge services.
- Entities: deferred to M22/M24 because upstream entities use 1.21
  `CustomPacketPayload`, complex spawn hooks, visualizers, and real gameplay
  behavior.
- Menus/screens: deferred to M23 because the linked typewriter menu is tied to
  keyboard/network behavior.
- Recipe serializer/type `portable_engine_dyeing`: deferred to M23 because it
  belongs to the portable-engine dyeing graph, which is not bootstrap-safe in
  M21.
- Particles, stats, ponder, optional integrations, capabilities, and all
  upstream mixins: deferred or not applicable for M21.
