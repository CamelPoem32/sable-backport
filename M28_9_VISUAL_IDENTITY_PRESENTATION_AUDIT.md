# M28.9 Visual Identity and Final Presentation Gate

Status: **IMPLEMENTED / VISUAL RUNTIME REQUIRED**

M28.8 proved that both Sable-contained target contraptions can be excluded from Flywheel while the
reported stationary sail remains visible. M28.9 does not alter mechanics or transforms. It makes the
already-proven Forge-stage CPU draw visually identifiable and traces its final presentation.

## Diagnostic ownership gates

- `sable.m28.tintCpuTargetContraption` changes only the `COLOR` element bytes in the exact final
  `BufferBuilder` range emitted for a Sable-contained `ControlledContraptionEntity`. The owning
  sublevel UUID selects opaque magenta or cyan deterministically. Positions, normals, UVs, light,
  matrices, depth, culling, and render order are unchanged. CPU and finalized-buffer readback verify
  the color bytes.
- `sable.m28.showCpuTargetOverlay` projects the finalized target vertices with the actual draw
  model-view, projection, and physical viewport. A GUI-stage rectangle labels the resulting screen
  box without using hidden plot coordinates.
- `sable.m28.suppressStaticSymmetricSails` suppresses only
  `simulated:white_symmetric_sail` while `VanillaSingleSubLevelRenderData` is emitting static Sable
  models. It cannot suppress captured contraption geometry or normal-world blocks.
- `sable.m28.traceFinalPresentation` starts at the tracked GPU draw, observes subsequent
  `RenderTarget` bind/unbind operations, records `AFTER_LEVEL`, and traces the final
  `RenderTarget._blitToScreen(int,int,boolean)` composite execution and `Window.updateDisplay()`
  presentation. The public `blitToScreen` wrapper queues the real composite; its return is not proof
  of presentation. These diagnostics never force a flush, framebuffer switch, or GL operation.

## Runtime decision

Run all four gates with `sable.m28.visualOwnershipTrace=true` and the proven scoped Flywheel target
suppression. A moving tinted sail proves the prior white observation was another owner. A moving box
without tinted geometry moves the frontier to presentation/occlusion. No production rendering fix is
selected until that visual result is recorded.
