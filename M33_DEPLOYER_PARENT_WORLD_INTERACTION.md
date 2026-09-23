# M33 Deployer Parent-World Interaction

## Scope

M33 adapts only Create 6.0.8's moving `DeployerMovementBehaviour` when its contraption is hosted by
a Sable `SubLevel`. Ordinary normal-world Deployers take the original Create path unchanged. No
global fake-player, `Level`, item-use, or entity-interaction API is redirected.

## Exact Create 6.0.8 call chain

`AbstractContraptionEntity.tickActors()` computes the moving actor's active point with
`toGlobalVector(...)`, stores it in `MovementContext.position`, and invokes
`DeployerMovementBehaviour.visitNewPosition(context, pos)` on a newly visited cell. The method is
server-only and performs this native sequence:

1. `tryGrabbingItem(context)` obtains the held stack from mounted contraption storage.
2. `getPlayer(context)` creates a `DeployerFakePlayer` backed by `(ServerLevel) context.world`, loads
   the serialized `Inventory`, and restores `HeldItem` to its main hand.
3. `getMode(context)` selects `USE` or `PUNCH`.
4. `DeployerHandler.shouldActivate(...)` applies the native activation predicate in `USE` mode.
5. `activate(context, pos, player, mode)` computes facing and fake-player pose, then calls
   `DeployerHandler.activate(...)`.
6. `tryDisposeOfExcess(...)` returns surplus or replacement items to mounted storage, or drops them.
7. Native block-breaking progress can stall the contraption; `tick(context)` continues the PUNCH
   operation every 20 ticks using `blockBreakingProgress`.

`DeployerHandler.activate()` and `activateInner()` own the interaction semantics. They construct the
ray and `BlockHitResult`, query entities, process Forge interaction hooks, construct `UseOnContext`,
call block and item interaction methods, and run native attack/harvest behavior.

## Interaction classification

- **D1 item use on block: supported.** `UseOnContext`, `onItemUseFirst`, `ItemStack.useOn`, and
  `Item.use` execute through the native `USE` path.
- **D2 block placement: supported.** A held `BlockItem` is handled by the same native item-use path.
- **D3 block activation: supported.** Forge right-click hooks and block `safeOnUse`/state use remain
  authoritative.
- **D4 block punch/break: supported.** `PUNCH` mode uses native left-click hooks, destroy progress,
  `tryHarvestBlock`, drops, durability, and stall behavior.
- **D5 entity interaction: supported by the same path.** The handler queries the fake player's
  parent `ServerLevel`, then calls `Entity.interact` and `ItemStack.interactLivingEntity`.
- **D6 entity attack: supported by the same path.** `PUNCH` mode calls the native fake-player attack
  path. M33 does not add a separate entity redirect.

## Root coordinate problem

The fake player already belongs to the parent `ServerLevel`, but a Sable-contained actor previously
supplied it with a target cell, position, and facing derived from hidden `SABLE_RAW` coordinates.
That made native parent-world queries point near the plot rather than at the physically visible
Deployer.

M33 keeps the explicit conversion:

```text
ACTOR_LOCAL
    -> SABLE_RAW active point and facing
    -> current Sable logical pose
    -> PARENT_VISIBLE fake-player pose and ray
    -> PARENT_BLOCK interaction target
```

`CreateActorTargetGeometry.resolveDeployer(...)` independently describes the native ray endpoints,
target block, and fallback clicked face. The clicked face is the opposite of the physically
transformed facing. Arbitrary yaw, pitch, roll, translation, and combinations are supported by the
same full pose transform.

## Compatibility boundary

`DeployerMovementBehaviourMixin` wraps two exact methods:

- `visitNewPosition` resolves the physical parent target through
  `SubLevelBlockBreakingUtility.findExternalPointTarget`. An unloaded parent chunk or another Sable
  body is a safe no-op. There is no hidden raw fallback.
- `activate` uses `SableCreateActorWorldContext.enterParentInteractionSpace` to present transformed
  `position`, `motion`, `relativeMotion`, and `rotation` to native Create. The scope restores every
  raw field in `close()`, and try-with-resources makes restoration exception-safe.

The context's backing world is deliberately unchanged. Because `getPlayer` already builds the fake
player from the parent `ServerLevel`, native `DeployerHandler` now receives one coherent parent-world
pose and target without replacing fake-player ownership globally.

## Held-item lifecycle

M33 does not copy, consume, damage, replace, or return held stacks itself. Native Create remains the
sole owner of:

- mounted-storage extraction in `tryGrabbingItem`;
- fake-player main-hand state loaded from `HeldItem`;
- item placement/use, replacement items, and tool damage in `DeployerHandler`;
- serialization in `writeExtraData`;
- excess insertion/drop behavior in `tryDisposeOfExcess`.

This preserves buckets, block-item consumption, tools, filters, and mounted inventories.

## Authority and safety

`visitNewPosition` returns immediately on the client, so mutation remains server-authoritative.
The adapter activates only when `SableCreateActorWorldContext.owner(context)` finds a Sable owner.
Normal-world Create is unchanged. Own hidden storage is never used as an external fallback, and a
target owned by another Sable body is rejected. The current physical pose is resolved for each
actor evaluation, so body motion cannot retain a stale raw target.

## Diagnostics

`-Dsable.m31.traceCreateActors=true` enables bounded transition/action events under
`SABLE_M31_CREATE_ACTOR`, now including `DEPLOYER`. It records target resolution, prepared
fake-player pose, interaction attempts, held-item changes, and successful state changes. The flag is
off by default and does not control production behavior.

## Runtime acceptance

Use `M33_RUNTIME_TEST_COMMANDS.md`. Validate block placement, lever activation, and native PUNCH
block breaking in normal world, stationary assembled Sable, translated Sable, and rotated Sable.
Then disassemble and repeat the normal-world control. M33 remains runtime pending until those gates
pass.

