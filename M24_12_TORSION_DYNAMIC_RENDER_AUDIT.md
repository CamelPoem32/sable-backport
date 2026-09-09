# M24.12 Torsion Dynamic Render Audit

Authority: frozen Simulated commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`,
target Minecraft 1.20.1, Create 6.0.8, Flywheel 1.0.5.

## Frozen graph

Frozen `SimPartialModels.TORSION_SPRING` owns the partial model
`simulated:block/torsion_spring/spring`. `SimulatedClient.init()` eagerly loads
`SimPartialModels`, allowing Flywheel's partial-model event handler to add that
location during model registration and populate its `BakedModel` after every
bake. `TorsionSpringRenderer` retrieves it through `CachedBuffers.partial`; the
Flywheel visual retrieves the same partial through `Models.partial`.

The model source is
`assets/simulated/models/block/torsion_spring/spring.json`. Its particle and side
sprite is `simulated:block/torsion_spring`, and its inside sprite is
`simulated:block/torsion_spring_inside`, both on the block atlas. The static
casing is `simulated:block/torsion_spring/block`; the complete item-only model is
`simulated:block/torsion_spring/item`.

## Target defect

M24.11 used the correct model location, but declared its `PartialModel` as a
static field of the nested BER class. That class was not an eagerly initialized
client model owner. The dynamic location could therefore miss Forge's
`ModelEvent.RegisterAdditional` collection and resolve through the model manager
to Minecraft's missing baked model. The black/magenta dynamic geometry is the
result of that missing-model lookup, not ordinary UV distortion.

Two obsolete copper placeholder JSON files also remained in the resources even
though the live blockstate and renderer no longer referenced them. M24.12 removes
them so there is one canonical dynamic spring model path.

## Target 1.20.1 adaptation

`SableForgeM24PartialModels` is initialized from the client bootstrap before
model baking. It owns the one canonical `PartialModel`, explicitly registers the
model location through Forge `ModelEvent.RegisterAdditional`, and observes
`ModelEvent.BakingCompleted` to validate the actual baked model and particle
sprite. Flywheel's `PartialModelEventHandler` retains ownership of refreshing the
`PartialModel` instance after initial bake and resource reload.

The Sable path intentionally uses the BER fallback and does not register a
second Torsion Flywheel visual. `CachedBuffers.partial` consumes the eagerly
baked partial; the existing Sable-local PoseStack remains unchanged and never
receives hidden plot coordinates.

| Role | ResourceLocation | Source/final artifact path | Registration/bake/lookup owner |
| --- | --- | --- | --- |
| dynamic spring | `simulated:block/torsion_spring/spring` | `assets/simulated/models/block/torsion_spring/spring.json` | `SableForgeM24PartialModels` / Forge+Flywheel / Torsion BER |
| static casing | `simulated:block/torsion_spring/block` | `assets/simulated/models/block/torsion_spring/block.json` | blockstate model bake / vanilla Sable block renderer |
| item | `simulated:block/torsion_spring/item` | `assets/simulated/models/block/torsion_spring/item.json` | item model parent / vanilla item renderer |
| casing/spring sprite | `simulated:block/torsion_spring` | `assets/simulated/textures/block/torsion_spring.png` | block atlas stitch / baked models |
| inside sprite | `simulated:block/torsion_spring_inside` | `assets/simulated/textures/block/torsion_spring_inside.png` | block atlas stitch / baked models |

The one-shot `SABLE_M24_TORSION_RENDER` diagnostic records registration, baked
model identity, particle sprite identity, atlas, BER ownership, and render type.

## Runtime acceptance

Manual M24.12 acceptance confirms the static casing and dynamic spring render
with their intended textures, the dynamic geometry follows the proven kinetic
state, and no black/magenta missing-model geometry remains. Torsion Spring is
`RUNTIME_PROVEN` and M24 is eligible for closure.
