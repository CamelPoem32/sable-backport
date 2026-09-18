# M28 contraption buffer-submission boundary

Target authority is mapped Create 6.0.8 and Catnip as packaged for Minecraft
1.20.1. Steering, `ROTATE_NEVER_PLACE`, contraption lifetime, client angle
synchronization, visible bounds, and static-versus-contraption ownership remain
unchanged.

## Mandatory classification

The current live evidence proves that the controlled entity angle, Create model
matrix, synthetic transformed probe, and dispatch stage all advance. It also
proves the captured source is air and the static Sable renderer emits no stale
copy. It does not yet prove which vertices reach the real render consumer.

`simulated:white_symmetric_sail` is a plain `RotatedPillarBlock` with
`RenderShape.MODEL`. It has no block entity, block-entity renderer, Flywheel
visual, or custom movement renderer. Its JSON is baked into the same Create
contraption structure buffer as an ordinary model block. This makes an ordinary
asymmetric captured block a valid A/B control.

The required live classification is:

1. Compare a symmetric sail and an offset asymmetric model block on one bearing
   inside a nearly stationary Sable.
2. Compare the same symmetric sail on a normal-world Create bearing.
3. Compare `SABLE_M28_ACTUAL_VERTEX` and `SABLE_M28_VERTEX_COMPARE` samples
   separated by at least 30 degrees.

No final renderer behavior changes are justified until those observations
identify block-specific model behavior, CPU buffer submission, or a downstream
render backend boundary.

## Exact Create 6.0.8 CPU path

`ContraptionEntityRenderer.render` obtains the cached `SuperByteBuffer` for each
chunk render layer. For a nonempty buffer it calls, in order:

1. `SuperByteBuffer.transform(ContraptionMatrices.getModel())`
2. `SuperByteBuffer.useLevelLight(level, ContraptionMatrices.getWorld())`
3. `SuperByteBuffer.renderInto(rendererPoseStack, layerVertexConsumer)`

The actual runtime buffer is `ShadeSeparatingSuperByteBuffer`, not
`DefaultSuperByteBuffer`. It owns one `TemplateMesh` and an array of shade-swap
vertex indices; it does not contain child `SuperByteBuffer` objects. Its
`renderInto` computes `modelMat = rendererPose.last().pose() *
transforms.last().pose()`, reads each source vertex from `TemplateMesh`,
transforms it using `modelMat`, and calls the combined 14-argument
`VertexConsumer.vertex(float,float,float,float,float,float,float,float,float,int,int,float,float,float)`.
It calls `reset()` after submission. This is a per-frame CPU transform, not a
cached transform or a bulk byte copy.

The first diagnostic targeted `DefaultSuperByteBuffer` and the three-double
`vertex` overload. Neither was executed for this contraption, explaining its
`submittedVertexProbeCount=0`. That probe has been replaced with one at the
actual shaded-buffer call. The destination `BufferBuilder.vertex` is also
observed; its private byte buffer is read after the write, before any draw or
flush, so `SABLE_M28_ACTUAL_VERTEX` reports stored destination positions rather
than argument values or a synthetic point. The shaded buffer's own `modelMat`
field is sampled at its vertex call, so comparisons use the production matrix
rather than only a reconstructed product.

Sable's compatibility mixin intentionally reports visualization support as
false for contraptions contained in a Sable. Their captured model blocks use
this CPU `SuperByteBuffer` fallback even when Flywheel supports the containing
client level. The normal-world path remains unchanged.

## Diagnostic ownership

`SABLE_M28_BUFFER_TRANSFORM` is emitted only for controlled contraptions in a
client Sable, at most six times per entity and only after an initial sample or a
30-degree angle change. It records the actual cached buffer, consumer, renderer
PoseStack, buffer transform PoseStack, `ContraptionMatrices`, and the exact
matrix product expected at `ShadeSeparatingSuperByteBuffer` entry.

`SABLE_M28_ACTUAL_VERTEX` records up to twelve real cached local vertices and
their actual destination-buffer positions. `SABLE_M28_VERTEX_COMPARE` compares
the same source mesh/index at two angle-separated samples. `SABLE_M28_BUFFER_WRITE`
reports actual destination vertex and byte-write growth. `SABLE_M28_SHADE_BUFFER`
reports the one mesh, shade switches, and destination consumer. None of these
diagnostics changes a production matrix, vertex, render layer, cache, or
consumer.

The optional `-Dsable.m28.referenceGeometry=true` diagnostic adds one bright
asymmetric quad-shaped triangle to the same solid-layer consumer using the same
production final transform. It does not change the world or contraption, and
is disabled by default. Its player-visible rotation must be observed manually;
it is not part of a final behavioral fix.

Hidden plot coordinates remain forbidden. Each matrix and vertex sample reports
whether a coordinate larger than one million survives into the final render
frame.

## Runtime interpretation

- Ordinary block moves but sail does not, and the sail works outside Sable:
  inspect the symmetric-sail baked model interaction with Sable's buffer path.
- Neither block moves in Sable, while both work outside: inspect the generic
  Sable/Create CPU buffer boundary.
- Expected vertices move but submitted vertices do not: fix the first recorded
  matrix/buffer divergence.
- Submitted vertices move with near-zero expected error but the screen does
  not: the CPU transform is correct; continue downstream from the real consumer
  and active shader/buffer backend.

M28 remains `IMPLEMENTED / RUNTIME_REQUIRED`. Player-visible rotation and one
render owner are both required before closure.
