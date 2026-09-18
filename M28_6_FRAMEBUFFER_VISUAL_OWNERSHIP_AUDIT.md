# M28.6 framebuffer visual-ownership audit

## Closed boundary

Runtime has proven that the controlled-contraption bearing angle, Create inner
matrix, Sable outer matrix, Catnip buffer transform, destination vertices,
finalized vertices, and same-frame GPU draw all change together. M28.6 does not
alter any of those paths. It asks which draw produces the stationary image seen
by the player.

## Static Sable rendering architecture

Forge 1.20.1 selects `VanillaSubLevelRenderDispatcher`; the Sodium reach-around
renderer is deliberately unavailable. `VanillaSingleSubLevelRenderData` keeps
only a bounded `RenderBlock` list containing position, state, and model seed.
It owns no retained CPU mesh, uploaded section, or GPU buffer.

Each static layer draw starts the global immediate `Tesselator` builder,
tessellates every current `RenderBlock`, and calls `RenderType.end`. A client
block mutation reaches `LevelChunkMixin`, `ClientLevelPlot.onBlockChange`, and
`ClientSubLevel.updateRenderData`. That closes the old render-data object and
constructs a new snapshot from current `LevelChunkSection` states. Thus a stale
uploaded static sail mesh is not supported by this renderer's architecture.
The runtime diagnostics still verify the mutation and actual emitted ranges.

## Diagnostic A/B controls

- `-Dsable.m28.visualOwnershipTrace=true` enables static snapshot lifecycle,
  static emitted-range, and draw-owner markers.
- `-Dsable.m28.suppressDynamicContraption=true` skips only Sable-contained
  `ControlledContraptionEntity` dispatch. It does not alter mechanics, block
  ownership, normal-world Create entities, or disassembly.
- `-Dsable.m28.forceCapturedStaticInvalidate=true` forces one additional normal
  static snapshot rebuild per captured position/entity pair. This is a binary
  test of the stale-snapshot theory, not the production fix.
- `-Dsable.m28.framebufferProbe=true` samples three pixels inside the projected
  bounds after the proven dynamic draw and again at Forge `AFTER_LEVEL`. It is
  rate-limited to one frame in twenty.
- `-Dsable.m28.entityPhaseAB=true` retains the existing real entity-phase A/B.

The dynamic draw projects every actual finalized vertex, reports full NDC and
pixel bounds, and records the active framebuffer, shader, draw order, and
rendered-buffer identity. Framebuffer readback is tightly bounded and disabled
by default.

## Runtime decision

1. If dynamic suppression leaves the stationary sail visible, a second draw is
   the visible owner. The static lifecycle and emitted ranges identify whether
   Sable supplied it.
2. If suppression removes the sail, the proven Create draw is the visible
   owner. Compare `AFTER_DYNAMIC_DRAW` and `END_WORLD_RENDER` probes.
3. If forced static invalidation changes the visual, attach the final generic
   fix to the existing block-mutation invalidation lifecycle.
4. If entity-phase mode alone changes the visual, replace the Forge-stage
   bridge with the real entity-phase integration.

## Status

M28 mechanics remain PASS. The assembled-sail visual remains FAIL/PARTIAL
until the property-gated runtime A/B identifies the framebuffer owner and the
player confirms visible rotation. Minecraft is not launched by this build
pass.
