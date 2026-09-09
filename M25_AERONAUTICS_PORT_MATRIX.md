# M25 Aeronautics Port Matrix

## Complete frozen inventory

The inventory is taken from `git ls-tree -r` at commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2` over both Aeronautics Java roots and both common resource roots. All 734 production source/resource paths are covered by the mutually exclusive classifications below; the selected M25 paths are enumerated exactly.

| Kind | `ADAPT_M25` | `DEFER_M26_PROPULSION` | `DEFER_M27_AERODYNAMICS_CONTROLS` | `DEFER_M28_GOLDEN_AIRCRAFT` | `NOT_APPLICABLE_1_20_1` | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Java | 7 | 46 | 36 | 67 | 44 | 200 |
| Resources | 11 | 83 | 253 | 110 | 77 | 534 |
| Total | 18 | 129 | 289 | 177 | 121 | 734 |

## ADAPT_M25

| Frozen Java path | Target ownership |
| --- | --- |
| `common/.../Aeronautics.java` | Mod identity/bootstrap in `Aeronautics` |
| `common/.../config/AeroConfig.java` | Forge common config bootstrap in `AeroConfig` |
| `common/.../index/AeroBlocks.java` | One selected `levitite` block in `AeroBlocks` |
| `common/.../index/AeroItems.java` | Levitite block item in `AeroItems` |
| `common/.../network/AeroPacketManager.java` | Empty M25 SimpleChannel bootstrap; crystallization packet deferred |
| `neoforge/.../AeronauticsNeoForge.java` | Forge `@Mod` entrypoint and event-bus wiring |
| `neoforge/.../service/NeoForgeAeroConfigService.java` | Forge 1.20.1 config registration |

| Frozen resource | M25 result |
| --- | --- |
| `assets/aeronautics/blockstates/levitite.json` | Ported unchanged semantically |
| `assets/aeronautics/models/block/levitite.json` | Ported unchanged semantically |
| `assets/aeronautics/models/item/levitite.json` | Ported unchanged semantically |
| `assets/aeronautics/lang/en_us.json` | Selected Levitite and tab entries only |
| `assets/aeronautics/textures/block/levitite.png` | Exact frozen blob `18bb8a4a9e8968c4bbee3dc4d2fec36b0f992498` |
| `data/aeronautics/tags/block/levitite.json` | Adapted to 1.20.1 `tags/blocks`; pearlescent content deferred |
| `data/aeronautics/tags/item/levitite.json` | Adapted to 1.20.1 `tags/items`; pearlescent content deferred |
| `data/minecraft/tags/block/mineable/pickaxe.json` | Selected Levitite entry adapted to 1.20.1 `tags/blocks` |
| `data/sable/tags/block/always_chunk_rendering.json` | Selected Levitite entry adapted to 1.20.1 `tags/blocks` |
| `data/aeronautics/floating_materials/levitite.json` | Exact lift/friction values |
| `data/aeronautics/physics_block_properties/levitite.json` | Exact Sable floating-material selector |

## Deferred graph

| Classification | Complete ownership rule |
| --- | --- |
| `DEFER_M26_PROPULSION` | All propeller, propeller-bearing, mounted potato cannon, thrust, airflow, and direct models/textures/recipes. |
| `DEFER_M27_AERODYNAMICS_CONTROLS` | Hot-air balloons, envelopes, burners, steam vents, lifting-gas graph, broader aerodynamic/control source, and direct assets/data. |
| `DEFER_M28_GOLDEN_AIRCRAFT` | Remaining full-content registrations, items, components, fluids, integrations, and assets needed for a coherent full aircraft/content pass. |
| `NOT_APPLICABLE_1_20_1` | NeoForge-only service glue, upstream datagen implementation, Ponder scenes, optional shader integrations, deferred mixins, and generated non-selected gameplay data. |

The 534-resource inventory includes 274 generated and 260 main files: 43 generated blockstates, 81 generated models, 45 recipes, 40 loot tables, 35 advancements, 15 Aeronautics tags, 96 main textures, 43 main models, 39 sounds, 20 translations, 17 Ponder structures, 16 Pinwheel shader files, and remaining metadata/data roots. The category totals cover every file without importing deferred runtime code.

M25 adds eight target Java files: seven Aeronautics bootstrap/registry classes plus `M25AeronauticsCommands`. It adds the eleven selected upstream resources plus one implementation-revision marker. No mixin is added.
