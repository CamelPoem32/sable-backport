# M26 Aeronautics Propulsion Port Matrix

M25 classified all 200 frozen Aeronautics Java and 534 resource paths. M26
refines the 46 Java and 83 resources previously deferred as propulsion.

| Kind | `ADAPT_M26` | `DEFER_M27_AERODYNAMICS` | `DEFER_M28_GOLDEN_AIRCRAFT` | `NOT_APPLICABLE_1_20_1` | Total M26 graph |
| --- | ---: | ---: | ---: | ---: | ---: |
| Java | 11 | 13 | 14 | 8 | 46 |
| Resources | 8 | 17 | 41 | 17 | 83 |
| Total | 19 | 30 | 55 | 25 | 129 |

## ADAPT_M26

The 11 upstream semantic owners are listed in `M26_PROPULSION_BASELINE.md`.
They become eight focused target owners: the propulsion registry, Wooden
Propeller block/entity/renderer, client partial-model registration, common
config additions, Aeronautics bootstrap extension, and the M26 command harness.

The eight selected resources are:

1. generated target blockstate `assets/aeronautics/blockstates/wooden_propeller.json`
2. frozen `models/block/wooden_propeller/block.json`
3. frozen `models/block/wooden_propeller/propeller.json`
4. frozen `models/block/wooden_propeller/propeller_reversed.json`
5. frozen item model adapted to `models/item/wooden_propeller.json`
6. frozen `textures/block/propeller_wood.png`
7. frozen `textures/block/custom_break_particles/propeller.png`
8. frozen block loot table adapted from the 1.21.1 singular
   `loot_table` directory to the 1.20.1 `loot_tables` directory and with the
   newer `random_sequence` field omitted

The English block-name entry is selected from the shared language catalog and
does not add a separate upstream resource path. The Wooden conversion recipe
depends on the deferred Andesite Propeller and is therefore not emitted as a
broken partial recipe.

## Deferred boundaries

`DEFER_M27_AERODYNAMICS` owns Smart/Gyroscopic control behavior and broader
airflow/aerodynamic control integration. `DEFER_M28_GOLDEN_AIRCRAFT` owns the
large bearing contraption graph, remaining propeller variants, recipes, sound,
Ponder and full-vehicle content. NeoForge datagen, duplicate generated output,
optional visuals, and mixins outside the selected mechanism are
`NOT_APPLICABLE_1_20_1` for M26.
