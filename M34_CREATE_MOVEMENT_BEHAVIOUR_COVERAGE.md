# M34 Create 6.0.8 Movement-Behaviour Coverage

Baseline: Create `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b` (mapped 6.0.8 jar). The registry is populated by `AllMovementBehaviours.registerDefaults`, `AllBlocks` Registrate `onRegister(MovementBehaviour.movementBehaviour(...))`, and `BuilderTransformers.slidingDoor`. Those three owners contain 5, 21, and 1 registration call sites respectively. The seat helper registers all 16 dye colors; the door transformer is applied to five doors. There are 22 distinct behaviour classes and 46 block registrations. `SlidingDoorMovementBehaviour` is registered in the transformer, not directly in `AllBlocks`.

Legend: BQ/BM = external block query/mutation; EQ/EI = entity query/interaction; MS = mounted storage; R = special actor rendering. `-` means the inspected behaviour does not use that category; `Y` means it does; `I` means internal contraption state only. These are source/bytecode findings, not runtime claims for untested actors.

| Block / actor | Behaviour class | BQ | BM | EQ | EI | MS | R | Current Sable status | M34 action | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| Vanilla bell | BellMovementBehaviour | - | - | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | `tick`, `onSpeedChanged`, `playSound` |
| Vanilla campfire | CampfireMovementBehaviour | - | - | - | - | - | Y | NO_EXTERNAL_WORLD_INTERACTION | None | `tick` emits particles from actor position; no block mutation |
| Vanilla dispenser | DispenserMovementBehaviour | Y | Y | Y | Y | Y | - | NEW_ARCHITECTURE_REQUIRED | Defer mounted dispensing | Inherits `DropperMovementBehaviour.visitNewPosition`; delegates item-specific effects to `MountedDispenseBehavior` |
| Vanilla dropper | DropperMovementBehaviour | - | - | - | Y | Y | - | ENTITY_TARGET_GAP | Defer mounted item emission | `visitNewPosition`, `getDispenseBehavior`, mounted slot/top-off |
| White, orange, magenta, light blue, yellow, lime, pink, gray, light gray, cyan, purple, blue, brown, green, red, black seats | SeatMovementBehaviour | Y | - | Y | Y | - | - | NEW_ARCHITECTURE_REQUIRED | Defer riding/dismount mapping | `registerSeat` helper; `visitNewPosition` uses seat mapping, passengers and world block state |
| Basin | BasinMovementBehaviour | - | - | - | Y | I | - | ENTITY_TARGET_GAP | Defer dropped-output positioning | `tick`, `dump`, internal inventory map |
| Blaze burner | BlazeBurnerMovementBehaviour | - | - | - | - | - | Y | NO_EXTERNAL_WORLD_INTERACTION | None | `tick`, `determineIfConducting`, `renderInContraption` |
| Fluid tank | FluidTankMovementBehavior | - | - | - | - | I | - | NO_EXTERNAL_WORLD_INTERACTION | None | `tick` maintains contraption tank state |
| Portable fluid interface | PortableStorageInterfaceMovement | Y | - | - | - | Y | Y | NEW_ARCHITECTURE_REQUIRED | Defer connection handshake | `findInterface`, `getStationaryInterfaceAt`, `getBlockEntity`, stall/reset |
| Mechanical bearing | StabilizedBearingMovementBehaviour | - | - | - | - | - | Y | ALREADY_COORDINATE_SAFE | None; M28 bearing work retained | `createVisual`, `renderInContraption`; no external mutation |
| Contraption controls | ContraptionControlsMovement | - | - | - | - | - | Y | NO_EXTERNAL_WORLD_INTERACTION | None | `startMoving`, `tick`, `tickFloorSelection` operate on contraption/train state |
| Mechanical drill | DrillMovementBehaviour | Y | Y | - | - | Y | Y | COVERED_M31 | Retain | Inherits `BlockBreakingMovementBehaviour`; M31 target/drop scope |
| Mechanical saw | SawMovementBehaviour | Y | Y | - | - | Y | Y | COVERED_M32 | Retain | M32 breaker/tree handling |
| Deployer | DeployerMovementBehaviour | Y | Y | Y | Y | Y | Y | COVERED_M33 | Retain | M33 parent-world fake-player scope; M33.1 native mounted storage |
| Portable storage interface | PortableStorageInterfaceMovement | Y | - | - | - | Y | Y | NEW_ARCHITECTURE_REQUIRED | Defer connection handshake | Same behaviour class as fluid interface |
| Redstone contact | ContactMovementBehaviour | Y | Y | - | - | - | - | ALREADY_COORDINATE_SAFE | Parent-visible point/facing hook | `visitNewPosition` reads and powers visited block; `deactivateLastVisitedContact` stores target |
| Mechanical harvester | HarvesterMovementBehaviour | Y | Y | - | - | Y | Y | COVERED_M32 | Retain | M32 crop world-context hook |
| Mechanical plough | PloughMovementBehaviour | Y | Y | - | - | Y | Y | COVERED_M32 | Retain | M32 target/hoe-use hook |
| Mechanical roller | RollerMovementBehaviour | Y | Y | - | - | Y | Y | NEW_ARCHITECTURE_REQUIRED | Defer multi-cell paving | Inherits breaker but overrides `visitNewPosition`, `getPositionsToBreak`, `destroyBlock`; adds `PaveTask`, `triggerPaver`, `tryFill` |
| Train controls | ControlsMovementBehaviour | - | - | - | - | - | Y | NOT_APPLICABLE | None for generic Sable actors | `tick` is train-control-specific |
| Andesite funnel | FunnelMovementBehaviour | Y | - | Y | Y | Y | - | NEW_ARCHITECTURE_REQUIRED | Defer world/inventory transfer | Factory `andesite`; `visitNewPosition`, `extract`, `succ` |
| Brass funnel | FunnelMovementBehaviour | Y | - | Y | Y | Y | - | NEW_ARCHITECTURE_REQUIRED | Defer world/inventory transfer | Factory `brass`; filter variant of same behaviour |
| Peculiar bell | BellMovementBehaviour | - | - | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | Same behaviour as vanilla bell |
| Haunted bell | HauntedBellMovementBehaviour | - | - | Y | - | - | - | ALREADY_COORDINATE_SAFE | Parent-visible pulse point hook | `visitNewPosition` calls `HauntedBellPulser.sendPulse(world, visited, ...)` |
| Desk bell | BellMovementBehaviour | - | - | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | Same behaviour as vanilla bell |
| Andesite door | SlidingDoorMovementBehaviour | I | I | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | `BuilderTransformers.slidingDoor`; `toggleDoor` updates contraption blocks |
| Brass door | SlidingDoorMovementBehaviour | I | I | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | Same transformer |
| Copper door | SlidingDoorMovementBehaviour | I | I | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | Same transformer |
| Train door | SlidingDoorMovementBehaviour | I | I | - | - | - | - | NOT_APPLICABLE | None for generic Sable actors | Same transformer; train station/elevator door control |
| Framed glass door | SlidingDoorMovementBehaviour | I | I | - | - | - | - | NO_EXTERNAL_WORLD_INTERACTION | None | Same transformer |

## Coordinate boundary

`AbstractContraptionEntity.tickActors` supplies `MovementContext.position` and the visited `BlockPos` in the nested Create contraption's raw sublevel space. `context.world` is the authoritative parent `Level`, so Create's raw `getBlockState`, `setBlockAndUpdate`, and pulse chunk lookup address the hidden plot unless the actor boundary converts the position. M31-M33 already cover Drill, Saw, Harvester, Plough, and Deployer. M34 converts only Contact and Haunted Bell visits using `SableCreateActorWorldContext.resolveActivePoint` and `SubLevelBlockBreakingUtility.findExternalPointTarget`. Contact additionally uses the scoped transformed `context.rotation` for native facing comparison. The scope restores raw context in `finally` via try-with-resources. No raw fallback, global level redirect, or storage emulation was added.

## Remaining frontier

The highest-priority unimplemented branch is **Mechanical Roller**: its multi-cell terrain profile, destruction, paving, and mounted material extraction are coupled and cannot be made correct by forwarding one point target through the Drill hook. Portable Storage Interface connection/handshake and mounted Dispenser/Dropper item effects are separate future architecture candidates. Funnel transfer and seat riding likewise need dedicated world/entity ownership work. These are source-proven coordinate gaps, not assertions of runtime failure in a tested fixture.

`SableDeployerMountedStorageTrace` remains `KEEP_DEBUG_GATED`. It checks the true inner-contraption mounted storage only under `-Dsable.m31.traceCreateActors=true`, uses weak per-context history, and never extracts or inserts items. The M33.1 observed chest-outside-inner-contraption case is a fixture topology issue, not a production storage defect.

## Verification boundary

`verifyM34MovementBehaviourCoverage` checks the three exact Create registration owners, registration-site counts and classified class set, the two scoped mixins and their Forge registration, raw-fallback prohibition, debug gating, and fixture IDs/blockstates. It is a static contract. Contact power transitions and Haunted Bell pulse visibility remain runtime pending.
