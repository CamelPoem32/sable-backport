# M17 Static Audit: Specialized Create Kinetic Visuals

M17 starts after M16 is closed by real runtime acceptance. This audit is an
M17.0 static/rendering boundary gate only: no Minecraft runtime was launched
and no speculative Gearbox rendering patch was enabled.

Target stack:

- Minecraft `1.20.1`
- Forge `47.4.20`
- Java `17`
- Create `1.20.1-6.0.8`
- Flywheel `1.0.5`
- Sable upstream baseline `b7226222caf4eace63a708bdcd73ef36c971137d`

## Gate Result

`M17.0_SPECIALIZED_KINETIC_RENDER_AUDIT=PASS_STATIC_AUDIT`

`M17.1_SPECIALIZED_KINETIC_RENDER=IMPLEMENTED_STATIC_VERIFIED`

`M17.2_SPLIT_SHAFT_LAYER_AND_CONTROL=IMPLEMENTED_STATIC_VERIFIED`

The observed Gearbox defect is classified as a specialized-BER/Flywheel gate
boundary. It is not a proven kinetic-network defect.

## Exact Create 6.0.8 Rendering Inventory

| Block family | Block entity | BER | Flywheel visual | Rotating partials | Render gate |
| --- | --- | --- | --- | --- | --- |
| Gearbox | `com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity` | `com.simibubi.create.content.kinetics.gearbox.GearboxRenderer` | `com.simibubi.create.content.kinetics.gearbox.GearboxVisual` | Four `AllPartialModels.SHAFT_HALF` partials for the axes perpendicular to the Gearbox block `axis` | Specialized `GearboxRenderer.renderSafe(...)` calls `VisualizationManager.supportsVisualization(level)` directly |
| Encased Shaft | `com.simibubi.create.content.kinetics.base.KineticBlockEntity` through `AllBlockEntityTypes.ENCASED_SHAFT` | `com.simibubi.create.content.kinetics.base.ShaftRenderer` | `com.simibubi.create.content.kinetics.base.ShaftVisual` | One full `AllPartialModels.SHAFT` partial | Generic `KineticBlockEntityRenderer.renderSafe(...)` gate |
| Clutch | `com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity` | `com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer` | `com.simibubi.create.content.kinetics.transmission.SplitShaftVisual` | Two `AllPartialModels.SHAFT_HALF` partials on the block axis | Specialized `SplitShaftRenderer.renderSafe(...)` calls `VisualizationManager.supportsVisualization(level)` directly |
| Gearshift | `com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity` | `com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer` | `com.simibubi.create.content.kinetics.transmission.SplitShaftVisual` | Two `AllPartialModels.SHAFT_HALF` partials on the block axis | Specialized `SplitShaftRenderer.renderSafe(...)` calls `VisualizationManager.supportsVisualization(level)` directly |
| Shaft control | `com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity` or `KineticBlockEntity` depending on block | `BracketedKineticBlockEntityRenderer` or `ShaftRenderer` | Bracketed/simple shaft visuals | Full shaft/cogwheel partials | Already covered by M11 Sable fallback mixins |
| Cogwheel control | `BracketedKineticBlockEntity` | `BracketedKineticBlockEntityRenderer` | Cogwheel visuals | Cogwheel and shaft partials | Already covered by M11 Sable fallback mixin |
| Creative Motor control | `CreativeMotorBlockEntity` | Create motor renderer path | Create motor visual | Motor shaft/body components | Already accepted by M11/M14/M16 runtime use |

## Gearbox Path

Exact Create 6.0.8 bytecode for
`GearboxRenderer.renderSafe(GearboxBlockEntity, float, PoseStack,
MultiBufferSource, int, int)`:

1. Calls `blockEntity.getLevel()`.
2. Calls `VisualizationManager.supportsVisualization(LevelAccessor)`.
3. Returns immediately when that call is true.
4. Reads `BlockStateProperties.AXIS` from the Gearbox block state.
5. Iterates `Iterate.directions`.
6. Skips directions whose axis equals the Gearbox casing axis.
7. Builds `CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction)`.
8. Computes angle from `AnimationTickHolder.getRenderTime(level)`,
   `blockEntity.getSpeed()`, source direction, direction sign, and
   `getRotationOffsetForPosition(...)`.
9. Applies `kineticRotationTransform(...)`.
10. Emits to `RenderType.solid()` via `SuperByteBuffer.renderInto(...)`.

For a Gearbox with `axis=y`, the renderer emits four shaft halves on the X/Z
directions. For `axis=x`, it emits four shaft halves on the Y/Z directions. For
`axis=z`, it emits four shaft halves on the X/Y directions.

`GearboxVisual` is the Flywheel alternative. It creates one `RotatingInstance`
per non-casing-axis direction using `AllPartialModels.SHAFT_HALF`, updates the
source facing from `blockEntity.source - blockEntity.pos`, and applies the same
speed sign semantics as the BER.

## Encased Shaft Path

`EncasedShaftBlock` uses `AllBlockEntityTypes.ENCASED_SHAFT`, whose block
entity type is `KineticBlockEntity`. Its renderer path is
`ShaftRenderer -> KineticBlockEntityRenderer.renderSafe`.

`ShaftRenderer.getRenderedBlockState(...)` returns a plain Create shaft state
with the BE rotation axis. The rotating model is rendered by the generic
`KineticBlockEntityRenderer.renderRotatingBuffer(...)` path and its angle comes
from `KineticBlockEntityRenderer.getAngleForBe(...)`.

`ShaftVisual` is the Flywheel alternative and uses `AllPartialModels.SHAFT`.

## Clutch And Gearshift Path

`ClutchBlockEntity` and `GearshiftBlockEntity` both extend
`SplitShaftBlockEntity`.

`SplitShaftRenderer.renderSafe(SplitShaftBlockEntity, float, PoseStack,
MultiBufferSource, int, int)`:

1. Calls `VisualizationManager.supportsVisualization(level)`.
2. Returns immediately when true.
3. Reads the block rotation axis from `IRotate`.
4. Iterates `Iterate.directions` and renders only directions whose axis matches
   the block rotation axis.
5. Computes a base angle from render time and `getSpeed()`.
6. Multiplies by `getRotationSpeedModifier(direction)`.
7. Renders `AllPartialModels.SHAFT_HALF` with `RenderType.solid()`.

`ClutchBlockEntity.getRotationSpeedModifier(direction)` returns `0` for the
non-source half when powered; otherwise it returns `1`.

`GearshiftBlockEntity.getRotationSpeedModifier(direction)` returns `-1` for the
non-source half when powered; otherwise it returns `1`.

`SplitShaftVisual` is the Flywheel alternative and creates two rotating
instances with the same per-direction speed modifier semantics.

## Existing M11 Sable Strategy

M11 currently preserves normal-world Create behavior and forces vanilla/Create
BER fallback only for Sable-contained block entities at these boundaries:

- `m11.create.KineticBlockEntityRendererMixin`
- `m11.create.BracketedKineticBlockEntityRendererMixin`

Both wrap
`VisualizationManager.supportsVisualization(LevelAccessor)` and return false
only when `Sable.HELPER.getContainingClient(blockEntity) != null`.

This intentionally covered generic kinetic and bracketed kinetic renderers used
by ordinary Shaft/Cogwheel-style controls. It does not cover:

- `GearboxRenderer.renderSafe(...)`
- `SplitShaftRenderer.renderSafe(...)`

because both are specialized overrides with their own direct visualization
gate and do not enter the base generic `KineticBlockEntityRenderer.renderSafe`
body before returning.

## Gearbox Failure Classification

Classification: `B. GearboxRenderer is dispatched but exits because
VisualizationManager.supportsVisualization(level) remains true`.

Evidence:

- Runtime observation: the Gearbox casing/block is visible, mechanical rotation
  is transmitted, but the internal rotating shafts are missing.
- Exact Create bytecode: Gearbox casing is baked/static block model; the
  internal shafts are not in that baked model path. They are emitted by
  `GearboxRenderer.renderSafe(...)` or `GearboxVisual`.
- Exact Sable M11 code: the transformed sublevel BE dispatcher calls the normal
  `BlockEntityRenderDispatcher`, and existing M11 mixins force generic kinetic
  BER fallback only for base/bracketed renderer methods.
- Exact Create bytecode: `GearboxRenderer.renderSafe(...)` has its own
  `supportsVisualization(level)` early return. Existing M11 mixins do not wrap
  that specialized method, so in a Sable client world where Flywheel reports
  visualization support, the vanilla Gearbox partials are skipped.

This does not prove a speed/angle problem. The next implementation should first
make the specialized renderer reach its normal Create shaft-partial emission
inside Sable, then runtime can evaluate visible rotation.

## Coordinate And Render-Space Ledger

| Value | Producer | Consumer | Space | Parent Sable transform? | Notes |
| --- | --- | --- | --- | --- | --- |
| `blockEntity.getBlockPos()` | Sable sublevel storage | Create BER and block lookup | Hidden plot/raw storage coordinate | No direct visible comparison | Valid storage identity, not visual acceptance truth |
| Sublevel local block pos | `rawPlotPos - subLevel.plot.centerBlock` | Diagnostics/fixture design | Sable logical-local | Yes when projecting to visible world | Use for fixture identity |
| Visible block position | `subLevel.renderPose().transformPosition(rawPlotPos)` | Sable render bridge | Outer visible/world | Already applied before BER entry | The whole BE renderer is placed into visible frame |
| PoseStack at BER entry | `VanillaSubLevelRenderTransforms.applyBlockTransform(...)` | Create specialized BER | Camera-relative visible frame | Already applied once | Must remain small; no 20M hidden translation cancellation |
| Gearbox partial local transform | `GearboxRenderer` | `SuperByteBuffer.renderInto` | Block-local | No | Create owns shaft direction/sign/angle |
| Shaft angle | `AnimationTickHolder + KineticBlockEntity.getSpeed()` | Create BER/Flywheel visual | Local rotation scalar | No | Actual Create speed remains source of truth |

Invariant: hidden plot coordinates near `20,000,000` remain storage identity
only. M17 must not introduce a PoseStack transform of hidden raw coordinates
followed by a visible-space cancellation.

## BER Versus Flywheel Recommendation

Short term: extend the existing M11 forced-BER strategy to the specialized
Create 6.0.8 renderers whose vanilla BER paths are already correct:

- `GearboxRenderer`
- `SplitShaftRenderer`

This is the narrowest repair because it matches the accepted Sable rendering
architecture: Sable supplies the transformed camera-relative PoseStack, and
Create emits its own local kinetic partials using real speed/angle state.

Direct Flywheel support is likely a larger architecture project because
Flywheel visuals create world/instance storage for block entities in the
ordinary client render world. Sable hidden plot coordinates and moving parent
frames would need a complete visual-manager/light/instance bridge. M17.0 does
not justify reopening that larger boundary.

## Proposed M17 Fixture

Fixture-local coordinates for one compact static Sable kinetic rig:

| Local pos | Block | BlockState | Role |
| --- | --- | --- | --- |
| `(0,0,0)` | `create:creative_motor` | `facing=east`, rpm initially `32` | Kinetic source |
| `(1,0,0)` | `create:shaft` | `axis=x` | Input shaft/reference control |
| `(2,0,0)` | `create:gearbox` | `axis=y` | Primary canary; should show four horizontal shaft halves |
| `(3,0,0)` | `create:shaft` | `axis=x` | Gearbox X output/reference |
| `(2,0,1)` | `create:shaft` | `axis=z` | Gearbox Z output/reference |
| `(4,0,0)` | `create:andesite_encased_shaft` | `axis=x` | Encased shaft canary |
| `(5,0,0)` | `create:clutch` | `axis=x`, `powered=false` | SplitShaftRenderer canary |
| `(6,0,0)` | `create:gearshift` | `axis=x`, `powered=false` | SplitShaftRenderer canary |
| `(7,0,0)` | `create:shaft` | `axis=x` | Downstream reference |
| `(5,1,0)` | `minecraft:air` or `minecraft:redstone_block` | redstone source for clutch | Harness toggle |
| `(6,1,0)` | `minecraft:air` or `minecraft:redstone_block` | redstone source for gearshift | Harness toggle |
| `(2,1,0)` | `create:cogwheel` | axis determined by Create placement rules | Reference visible rotational control |

The fixture should be moderate speed (`32 RPM`) so missing specialized partials
and direction changes are visually inspectable. The Gearbox casing axis `y`
keeps the visible internal shaft halves horizontal.

## Proposed M17 Command Harness

Commands under `/sable m17`:

- `spawn_kinetics <name>`: create the deterministic fixture in a stationary
  Sable body, motor set to a moderate RPM through the existing Create motor
  value behavior.
- `validate <selector>`: structural and kinetic state validation only; visual
  domains stay `UNVERIFIED` until observed.
- `dump_layout <selector>`: fixture-local/raw/storage state table.
- `inspect <selector>`: per-BE class, renderer family, speed, source,
  axis/powered state, expected partial count, and Sable containment.
- `set_speed <selector> <rpm>`: legitimate Creative Motor value change.
- `toggle_clutch <selector>`: place/remove the fixture-local redstone source
  adjacent to the Clutch so Create's normal neighbor update and kinetic
  detach/re-add semantics own the behavior.
- `toggle_gearshift <selector>`: place/remove the fixture-local redstone source
  adjacent to the Gearshift so Create's normal reversal semantics own the
  behavior.
- `snapshot <selector>`: concise state, expected specialized partials, speed,
  source, and client visual status as `UNVERIFIED`.
- `test_translate_parent <selector>`: delegate to existing Sable parent motion
  controls.
- `test_rotate_parent <selector>`: delegate to existing Sable parent rotation
  controls.
- `save_reload_check <selector>`: compare structural/kinetic state before and
  after manual reload.
- `visual_acceptance <selector>`: report machine state plus checklist fields;
  do not mark visual PASS from server state alone.

## First Runtime Acceptance Sequence

```text
/sable m17 spawn_kinetics m17_gearbox
/sable m17 validate @e[name=m17_gearbox,limit=1]
/sable m17 dump_layout @e[name=m17_gearbox,limit=1]
/sable m17 inspect @e[name=m17_gearbox,limit=1]
```

User observes:

- Gearbox casing visible.
- Gearbox internal shaft halves visible and rotating.
- Reference shaft/cog/motor still render normally.

Then:

```text
/sable m17 toggle_clutch @e[name=m17_gearbox,limit=1]
/sable m17 toggle_gearshift @e[name=m17_gearbox,limit=1]
/sable m17 snapshot @e[name=m17_gearbox,limit=1]
/sable m17 test_rotate_parent @e[name=m17_gearbox,limit=1]
/sable m17 visual_acceptance @e[name=m17_gearbox,limit=1]
```

Expected: specialized partials remain in the Sable visible frame, shaft rotation
uses Create speed/angle, and parent Sable rotation composes with the whole
block entity render transform.

## Recommended First Implementation Step

Add a narrow client-only mixin that wraps
`VisualizationManager.supportsVisualization(LevelAccessor)` inside exact
Create 6.0.8 `GearboxRenderer.renderSafe(...)` and returns false only when the
`GearboxBlockEntity` is contained in a Sable client sublevel. Add the same
pattern for `SplitShaftRenderer.renderSafe(...)` only after the Gearbox canary
passes or if the same verifier/runtime confirms the shared specialized boundary.

Do not add fake shaft models, do not globally disable Flywheel, and do not
change normal-world Create visualization behavior.
