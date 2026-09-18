# M28.7 visual render-owner audit

## Proven boundary

M28.6 proved that suppressing Sable's known `ControlledContraptionEntity` draw
does not remove the stationary sail. It also proved that the captured source
position is air, is absent from `VanillaSingleSubLevelRenderData`, and emits no
static geometry. The rotating Create CPU geometry, buffer finalization, and GPU
draw remain closed. M28.7 therefore adds diagnostics only; it does not change
steering, bearing, transform, culling, ownership, or batching behavior.

## Entity ownership architecture

Client contraption entities are ordinary entities in the parent `ClientLevel`.
`ClientSubLevel` is a pose and plot view over that level, not a second entity
manager. Both the existing Forge contraption bridge and vanilla
`ClientLevel.entitiesForRendering()` enumerate the parent collection.

`LevelsMixin` wraps the protected `Level#getEntities()` result in
`SubLevelInclusiveLevelEntityGetter`. Its non-spatial `getAll()` delegates
unchanged. Its spatial overloads query the same delegate in direct parent space,
the containing Sable's transformed visible space, and inverse-transformed spaces
for intersecting Sables. They do not deduplicate callbacks. M28.7 records every
route and identity without changing this behavior. A repeated collection view is
not automatically a second entity instance or a second renderer invocation.

## Diagnostic render contexts

The following scoped owners are now recorded:

- `SABLE_STATIC`: `VanillaSingleSubLevelRenderData.renderSingleBlock`.
- `SABLE_FORGE_STAGE_BRIDGE`: the Forge `AFTER_ENTITIES` Create dispatch.
- `SABLE_ENTITY_PHASE_BRIDGE`: the existing property-gated entity-phase A/B.
- `VANILLA_LEVEL_ENTITY_PASS`: vanilla `LevelRenderer.renderEntity`.
- `OTHER` / `UNATTRIBUTED`: call stacks outside known owners.

The scopes are diagnostic `ThreadLocal` state only. They do not modify matrices,
positions, render types, entity lifecycle, or geometry.

## Global probes

With `-Dsable.m28.visualOwnershipTrace=true`, M28.7 emits:

- `SABLE_M28_ENTITY_DISPATCH` at the common
  `EntityRenderDispatcher.render(...)` boundary;
- `SABLE_M28_CONTRAPTION_RENDER_GLOBAL` at exact Create 6.0.8 renderer HEAD and
  RETURN;
- `SABLE_M28_ENTITY_RENDER_COUNT` for per-frame owner counts;
- `SABLE_M28_ENTITY_REGISTRY_OWNERSHIP` for parent, inclusive, Sable-filtered,
  and bridge collection views;
- `SABLE_M28_INCLUSIVE_ENTITY_QUERY` for transformed spatial query routes and
  duplicate callback identities;
- `SABLE_M28_SAIL_MODEL_DRAW` at exact Forge 1.20.1 baked-model entry points for
  `simulated:white_symmetric_sail`.

The model filter is diagnostic and is not used by production rendering.

## Suppression experiment

`-Dsable.m28.suppressVanillaTargetContraption=true` suppresses only a
Sable-contained `ControlledContraptionEntity` encountered through the actual
`VANILLA_LEVEL_ENTITY_PASS` dispatcher context. It does not suppress Sable's
known Forge-stage bridge, static blocks, normal-world Create contraptions, or any
simulation state.

Run A:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.suppressDynamicContraption=true
```

Run B:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.suppressDynamicContraption=true
-Dsable.m28.suppressVanillaTargetContraption=true
```

Rotate one assembled control sail by at least 90 degrees in each run. Run A
identifies every remaining dispatcher, renderer, and baked-model owner. Run B
answers whether the remaining visible object belongs to vanilla's parent-world
entity pass. Minecraft must be restarted between flag changes.

## Status

M28 mechanics remain PASS. M28 visual remains FAIL / RUNTIME_REQUIRED until the
logs identify the remaining owner and the assembled sail visibly rotates without
diagnostic suppression flags.
