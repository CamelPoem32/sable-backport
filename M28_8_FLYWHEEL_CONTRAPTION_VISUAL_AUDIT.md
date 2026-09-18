# M28.8 Flywheel Contraption Visual Audit

## Exact target

- Minecraft 1.20.1 / Forge 47.4.20
- Create 6.0.8 mapped development artifact
- Flywheel Forge 1.0.5
- `ContraptionVisual` and Flywheel bytecode from the artifacts used by the Forge compile classpath

This pass is diagnostic. M28 mechanics remain proven; assembled-sail visual ownership remains runtime-required.

## Create visual registration

Create 6.0.8 registers `CONTROLLED_CONTRAPTION` through
`CreateEntityBuilder.visual(factory)`. The one-argument overload selects the default
`renderNormally=true` policy. During client setup `registerVisualizer()` builds a
`SimpleEntityVisualizer`, installs the supplied factory, converts `renderNormally` to
the inverse `skipVanillaRender` predicate, and applies it through
`VisualizerRegistry.setVisualizer(entityType, visualizer)`.

Flywheel `EntityStorage.willAccept(Entity)` first checks that the entity is alive,
then calls `VisualizationHelper.canVisualize(entity)`, and requires a non-null level.
`EntityStorage.createRaw()` resolves that registered visualizer and calls
`EntityVisualizer.createVisual(context, entity, partialTick)`. Create's factory creates
`ContraptionVisual`.

## Exact ContraptionVisual lifecycle

The constructor:

1. initializes child/actor collections and a reusable `PoseStack`;
2. creates a `VisualEmbedding` at `Vec3i.ZERO`;
3. immediately calls `setEmbeddingMatrices(partialTick)`;
4. obtains the lazy `ClientContraption`;
5. calls `setupStructure()` and `setupChildren()`.

`setupStructure()` builds a `SimpleModel` from
`ClientContraption.RenderedBlocks.positions()` using `ForgeBlockModelBuilder`. The model
is submitted to the embedding's instancer provider as a transformed instance. Structure
version changes rebuild the model and steal the existing instance into the new instancer.

`planFrame()` runs `beginFrame()` before child dynamic visuals. `beginFrame()` obtains
the frame partial tick, updates the embedding matrices, refreshes light sections, and
rebuilds structure/children when their client versions change. `planTick()` only drives
the actor/tickable children. `_delete()` deletes child visuals, actor visuals, the
structure instance, and the embedding.

## Exact transform

`setEmbeddingMatrices(partialTick)` uses Flywheel's `renderOrigin()` and computes an
interpolated entity translation minus that origin. It resets the reusable matrix,
applies that translation, then invokes Create's authoritative
`AbstractContraptionEntity.applyLocalTransforms(matrix, partialTick)`. Finally it writes
the resulting pose and normal matrices into `VisualEmbedding.transforms()`.

This means Create bearing interpolation is present in the Flywheel visual. The exact
matrix nevertheless belongs to Flywheel's parent-world render-origin frame. It does not
call `ClientSubLevel.renderPose(partialTick)` and therefore does not explicitly compose
the Sable outer visible pose. M28.8 logs both matrices/poses so runtime can prove which
component is absent or stale instead of assuming it.

## Structure ownership diagnostics

`SABLE_M28_FLYWHEEL_STRUCTURE` records the captured block positions and IDs, model
builder/model identities, mesh count, bounding sphere, and whether the exact symmetric
sail is present. `SABLE_M28_FLYWHEEL_VISUAL_LIFECYCLE` records visual creation/deletion
and entity/sublevel ownership. `SABLE_M28_FLYWHEEL_TRANSFORM` compares bearing-angle
changes with changes in the exact matrix passed to the embedding.

All render-system fields are guarded by `RenderSystem.isOnRenderThread()`. Worker-thread
model construction reports `UNAVAILABLE_OFF_RENDER_THREAD`; diagnostics never enqueue
or execute OpenGL work.

## Diagnostic suppression boundary

`sable.m28.suppressFlywheelTargetContraption=true` intercepts exact Flywheel 1.0.5
`EntityStorage.willAccept(Entity)`. It returns false only when the candidate is a
`ControlledContraptionEntity` owned by a `ClientSubLevel`. It does not alter the Create
visualizer registry, normal-world contraptions, other entity visuals, block-entity
visuals, or Flywheel globally.

This is an ownership A/B only. With the CPU Sable bridge and vanilla target render also
suppressed, disappearance proves that `ContraptionVisual` owned the stationary image.
Persistence of the image means a lower, still-unidentified visual owner remains.

## Production recommendation gate

No production renderer change is made in M28.8. If runtime suppression proves Flywheel
ownership, two architectures remain:

1. Exclude only Sable-contained contraption entities from Flywheel visualization and use
   the already verified CPU bridge.
2. Add a generic small-coordinate Sable outer-pose integration to the Flywheel embedding.

The first is lower risk and preserves ordinary Create visualization. The second may be
preferable only if runtime matrices prove a clean integration point without hidden-plot
translations. Neither is selected before the suppression A/B.
