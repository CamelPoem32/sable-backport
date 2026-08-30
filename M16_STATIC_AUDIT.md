# M16 Static Audit: Create Block-Breaking Actors

M16 starts from the closed M13-M15 Create contraption baseline. This audit is
only for Create block-breaking movement actors, with Mechanical Drill as the
first canary. No Minecraft runtime was launched, and no M16 behavior patch is
enabled by this document.

Coordinate vocabulary is explicit throughout this milestone:
`contraption-local`, `Sable sublevel logical-local`, `hidden plot/raw world`,
`outer visible/world`, `target block coordinate`, and
`Create contraption anchor coordinate`.

## Gate Result

M16.0 is an architecture and fixture audit gate. Result: `PASS_STATIC_AUDIT`.

The Forge backport currently does not enable the upstream block-breaking
compatibility mixins. The correct first implementation step is a narrow Forge
port of the upstream `BlockBreakingMovementBehaviour` actor boundary, guarded
by direct Create 6.0.8 bytecode verification, followed by an M16 drill fixture
command harness.

## Upstream Sable 2.0.0 Inventory

Exact upstream baseline:

```text
branch/ref: mc1.21.1-2.0.0-neoforge
commit: b7226222caf4eace63a708bdcd73ef36c971137d
```

The exact-baseline block-breaking compatibility inventory is:

| Upstream source path | Target | Responsibility | M16 status |
| --- | --- | --- | --- |
| `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/behaviour_compatibility/block_breaking_behaviour/BlockBreakingMovementBehaviourMixin.java` | `BlockBreakingMovementBehaviour` | Replaces the block-breaking movement actor's target selection after normal Create fails to stall, and clears stale breaking state using Sable-aware transformed distance. | Primary M16 candidate |
| `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixinhelper/compatibility/create/block_breakers/SubLevelBlockBreakingUtility.java` | Helper | Builds a drill mining box, transforms it through the containing Sable pose, searches parent-world and intersecting sublevels, and returns the closest breakable target. | Primary M16 candidate |
| `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/block_breakers/BlockBreakingKineticBlockEntityMixin.java` | `BlockBreakingKineticBlockEntity#tick` | Sable-aware target replacement for static kinetic block breakers outside moving contraptions. | Out of initial M16 canary unless a static drill boundary is explicitly added |
| `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/block_breakers/BlockBreakingKineticBlockEntityDamageMixin.java` | `DrillBlock#entityInside`, `SawBlock#entityInside` | Sable-aware entity damage intersection for static drill/saw blocks. | Not part of block mutation canary |
| `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/behaviour_compatibility/block_breaking_behaviour/SawMovementBehaviourMixin.java` | `SawMovementBehaviour` | Saw tree-cut/drop compatibility. | Explicitly out of M16 |

There is no drill-specific upstream movement-behavior mixin beyond the generic
`BlockBreakingMovementBehaviourMixin`; Mechanical Drill inherits the generic
block-breaking actor path through `DrillMovementBehaviour`.

## Create 6.0.8 Mechanical Drill Call Path

Direct bytecode inspection of the exact mapped Create 6.0.8 jar shows the
actor flow:

```text
Contraption assembly
  -> Contraption stores actors as StructureBlockInfo + MovementContext
  -> MovementContext(world, blockInfo, contraption)
     localPos = blockInfo.pos
     state = blockInfo.state
     data = new CompoundTag

AbstractContraptionEntity.tickActors()
  -> for each actor block
  -> MovementBehaviour.REGISTRY.get(blockState)
  -> active center = toGlobalVector(center(localPos) + activeAreaOffset, 1)
  -> target BlockPos = BlockPos.containing(active center)
  -> context.rotation = this::applyRotation
  -> context.position = active center
  -> isActorActive(...)
  -> if shouldActorTrigger(...) and not stalled:
       MovementBehaviour.visitNewPosition(context, target)
  -> if context.motion changed:
       MovementBehaviour.onSpeedChanged(context, oldMotion, newMotion)
  -> MovementBehaviour.tick(context)

DrillMovementBehaviour
  -> extends BlockBreakingMovementBehaviour
  -> getActiveAreaOffset uses DrillBlock.FACING normal scaled by 0.6499999761581421
  -> canBreak requires generic breakable state, non-empty collision shape, and not Create tracks

BlockBreakingMovementBehaviour.startMoving()
  -> assigns negative BreakerId

BlockBreakingMovementBehaviour.visitNewPosition(context, pos)
  -> state = context.world.getBlockState(pos)
  -> damageEntities when state is not redstone conductor
  -> server side only:
       canBreak(world, pos, state)
       data["BreakingPos"] = pos
       context.stall = true

BlockBreakingMovementBehaviour.tick(context)
  -> tickBreaker(context)
  -> handles WaitingTicks and retry through visitNewPosition(context, LastPos)

BlockBreakingMovementBehaviour.tickBreaker(context)
  -> reads BreakingPos, Progress, BreakerId, TicksUntilNextProgress
  -> if no target or no relative motion: unstalls
  -> state = world.getBlockState(BreakingPos)
  -> if target no longer breakable: clear progress and destroyBlockProgress(-1)
  -> progress += block-breaking speed / hardness
  -> on complete:
       BlockHelper.destroyBlock(context.world, BreakingPos, 1, drops -> dropItem(...))
       onBlockBroken(...)
       clear Progress, TicksUntilNextProgress, BreakingPos
```

The server mutation point is therefore still Create-owned:
`BlockHelper.destroyBlock(context.world, breakingPos, ...)`.

## Coordinate-Space Ledger

| Value | Producer | Consumer | Expected space | Current raw meaning in Sable-contained contraption | Transform concern |
| --- | --- | --- | --- | --- | --- |
| `MovementContext.localPos` | `MovementContext` constructor from actor `StructureBlockInfo.pos` | Drill active area and helper | Contraption-local | Contraption-local | No outer Sable transform |
| `context.localPos.getCenter()` | Upstream mixin/helper | `toGlobalVector` input | Contraption-local vector | Contraption-local vector | Safe local value |
| `context.contraption.entity.toGlobalVector(localCenter, 1)` | Create entity | Upstream helper as drill center | Create global vector in contraption frame | Hidden plot/sublevel logical coordinate for contained contraptions | Must not be treated as visible world until Sable pose is applied |
| `context.rotation.apply(getActiveAreaOffset(context))` | `AbstractContraptionEntity.tickActors` rotation function | Mining-box offset | Contraption/Sable-local direction | Direction after Create inner transform | Parent Sable rotation matters only when the mining box is transformed by Sable pose |
| `pos` passed to `visitNewPosition` | `BlockPos.containing(active center)` in `tickActors` | `BlockBreakingMovementBehaviour.visitNewPosition` | Level block coordinate | Hidden plot coordinate for same-sublevel targets | Ordinary Create can miss visible parent-world targets |
| `context.contraption.anchor` | Create contraption assembly/entity | `Sable.HELPER.getContaining(world, anchor)` | Create anchor block coordinate | Hidden plot coordinate of containing sublevel | Used to find parent Sable |
| `localMiningBox` | `SubLevelBlockBreakingUtility` | transformed to global | Drill mining box near actor | Box in sublevel logical/raw coordinate before Sable pose | Parent Sable rotation and translation matter |
| `globalMiningBox` | Utility after `subLevel.logicalPose()` | parent-world and intersecting-sublevel search | Visible/world box | Visible world target query region | Must be derived by transform, not hidden plot names |
| `otherLocalMiningBox` | Utility inverse-transform of other sublevel pose | other sublevel block scan | Target sublevel local/raw coordinate | Candidate target raw coordinate inside another hidden plot | Uses inverse Sable transform |
| `BreakingPos` NBT | `visitNewPosition` | `tickBreaker`, cleanup, destroy progress | Mutatable `Level` block coordinate | Parent world or target sublevel raw coordinate | Cleanup must compare visible positions, not raw distance |
| `context.world.getBlockState(BreakingPos)` | Create block breaking | CanBreak/progress | Owning level coordinate | Raw coordinate appropriate for actual storage | Correct only if selected coordinate maps to real storage |
| `BlockHelper.destroyBlock(context.world, BreakingPos, ...)` | Create completion | World mutation | Owning level coordinate | Raw coordinate appropriate for actual storage | Final mutation must hit the intended target, not hidden controller blocks |

## Upstream To Create 6.0.8 Classification

| Upstream item | Upstream target | Create 6.0.8 target evidence | Classification | Notes |
| --- | --- | --- | --- | --- |
| `BlockBreakingMovementBehaviourMixin.sable$checkPosition` | `@WrapMethod(method = "visitNewPosition")` | `visitNewPosition(MovementContext, BlockPos)V` exists; it calls `Level.getBlockState`, `canBreak`, writes `BreakingPos`, and stalls. | `DIRECT_PORT` with package/adaptation review | MixinExtras `@WrapMethod` is already used elsewhere in the Forge backport. |
| `BlockBreakingMovementBehaviourMixin.sable$testBreakingPosDist` | `@Inject(method = "tick", at = @At("HEAD"), cancellable = true)` | `tick(MovementContext)V` exists and calls `tickBreaker` before WaitingTicks retry. | `DIRECT_PORT` with NBT API adaptation already present in local NeoForge source | Must verify 1.20.1 `NbtUtils.readBlockPos` return type during implementation. |
| `SubLevelBlockBreakingUtility.findBreakingPos` | Helper only | Uses Sable helper, AABB, `BoundingBox3d/3i`, and Create-agnostic `canBreak` predicate. | `SEMANTIC_ADAPTATION` | Package moves from NeoForge helper namespace to common/Forge; verify `logicalPose` APIs. |
| `BlockBreakingKineticBlockEntityMixin` | `@Redirect(method="tick", target=getBreakingPos())` | Static kinetic block-entity path exists but is not the moving contraption actor. | `NOT_REQUIRED_ON_CREATE_6_0_8` for first M16 canary | Keep deferred unless M16 expands to static drill blocks. |
| `BlockBreakingKineticBlockEntityDamageMixin` | `entityInside` AABB intersection | Damage path is separate from block mutation. | `NOT_REQUIRED_ON_CREATE_6_0_8` for first M16 canary | Out of scope for Mechanical Drill block breaking. |
| `SawMovementBehaviourMixin` | Saw tree/drop behavior | Saw actor is not M16. | `NOT_REQUIRED_ON_CREATE_6_0_8` | Remains deferred. |

## Existing Backport Activation State

The Forge mixin config currently does not register any M16 block-breaking
compatibility mixins. The deferred upstream NeoForge entries remain documented
as `DEFER` in `MIXIN_BACKPORT_MATRIX.md`.

M16 must not enable Harvester, Saw tree cutting, static block-entity drill/saw
damage, Deployer, fan processing, trains, or inventory mutation as part of the
Mechanical Drill actor canary.

## Proposed M16 Fixture

The first deterministic fixture should reuse the accepted M14 sticky piston as
the carrier so the new variable is block-breaking actor target selection, not a
new motion controller.

Local coordinates for the initial stationary Sable fixture:

| Local pos | Block | Blockstate | Role |
| --- | --- | --- | --- |
| `(0,-2,0)` | `create:creative_motor` | `facing=up`, speed `0` at spawn | Legitimate kinetic source |
| `(0,-1,0)` | `create:shaft` | `axis=y` | Motor-to-piston shaft |
| `(0,0,0)` | `create:sticky_mechanical_piston` | `facing=east,axis_along_first=true,state=retracted` | Proven M14 carrier |
| `(-1,0,0)..(-4,0,0)` | `create:piston_extension_pole` | `facing=east` | Extension chain behind piston |
| `(1,0,0)` | `create:radial_chassis` | `axis=x,sticky_north=true` | Proven sticky payload base |
| `(1,1,0)` | `create:mechanical_drill` | `facing=east` | M16 block-breaking actor |
| `(1,1,1)` | `minecraft:stone` | default | Off-axis marker retained from M14-style visual diagnosis |
| `(6,1,0)` | `minecraft:stone` | default | First deterministic drill target at full travel |
| `(7,1,0)` | `minecraft:stone` | default | Second target for repeated cycle |
| `(8,1,0)` | `minecraft:stone` | default | Third target/persistence canary |

The target coordinates are fixture-local acceptance truth. Hidden plot
coordinates are never the acceptance source; they are derived storage details.
Hidden plot coordinates are never the acceptance source.

## Proposed Runtime Acceptance Sequence

One future launch should begin with a stationary Sable:

```text
/sable m16 spawn_drill m16_drill
/sable m16 validate @e[name=m16_drill,limit=1]
/sable m16 dump_layout @e[name=m16_drill,limit=1]
/sable m16 inspect @e[name=m16_drill,limit=1]
/sable m16 extend @e[name=m16_drill,limit=1]
/sable m16 snapshot @e[name=m16_drill,limit=1]
```

Acceptance gates:

| Gate | Expected evidence |
| --- | --- |
| A: static Sable first break | Drill actor stalls/progresses on fixture-local `(6,1,0)` and destroys that stone only. |
| B: repeated cycle | Retract/extend reaches the next target without losing drill/payload. |
| C: target identity | Command diagnostics report fixture-local target, selected storage coordinate, block id, and owner world/sublevel. |
| D: no wrong mutation | No hidden plot controller/pole/payload block and no unrelated parent-world block is destroyed. |
| E: parent translation | With M10 body translation, drill target selection follows transformed Sable pose. |
| F: parent rotation | With M10 body rotation, drill mining direction follows Sable-local drill facing. |
| G: render/collision regression | M14 piston render/collision remains visually accepted; M13-M15 contraption infrastructure untouched. |
| H: save/reload | Remaining target blocks and actor progress are diagnosable after reload; no persistence PASS is claimed before runtime reload evidence. |
| I: airborne acceptance | A later one-shot airborne sequence uses existing M10 body controls and normal M14 piston motor control only. |

## First Implementation Recommendation

Port only `BlockBreakingMovementBehaviourMixin` plus the shared
`SubLevelBlockBreakingUtility` into the Forge/common source set, with a direct
Create 6.0.8 bytecode verifier for:

```text
BlockBreakingMovementBehaviour.visitNewPosition(MovementContext, BlockPos)V
BlockBreakingMovementBehaviour.tick(MovementContext)V
BlockBreakingMovementBehaviour.tickBreaker(MovementContext)V
DrillMovementBehaviour.getActiveAreaOffset(MovementContext)
DrillMovementBehaviour.canBreak(Level, BlockPos, BlockState)
AbstractContraptionEntity.tickActors()
```

Do not port static block-entity drill/saw damage, Harvester, Saw tree behavior,
Deployer, fan processing, inventories, or trains in the first implementation.

## Implementation Follow-Up

M16.1 follows this recommendation: the Forge config now enables only the moving
`BlockBreakingMovementBehaviourMixin`, backed by the common
`SubLevelBlockBreakingUtility`. Static drill/saw damage, Saw tree behavior,
Harvester, Deployer, fan processing, inventory handling, and trains remain
disabled for this milestone.
