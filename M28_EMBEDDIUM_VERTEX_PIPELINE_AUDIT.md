# M28 Embeddium Vertex Pipeline Audit

## Boundary

This audit targets the installed `embeddium-0.3.31+mc1.20.1.jar` exactly. Its
SHA-256 is
`EED3D1325F2ACC2FD4E69BB495E5CCB91D962126AC5330F0582EBC2A3DAF47FB`.
ImmediatelyFast is absent from the current runtime and is not part of this
boundary.

## Exact Create to Embeddium path

Create 6.0.8 obtains the cached contraption structure from
`ContraptionRenderInfo`. Catnip's `ShadeSeparatingSuperByteBuffer.renderInto`
iterates the `TemplateMesh` and calls the 14-argument
`VertexConsumer.vertex(FFFFFFFFFIIFFF)` method for each vertex. It does not use
Embeddium's pointer-based `VertexBufferWriter.push` overload on this path.

When Embeddium wraps the destination, the consumer is
`me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder`.
Exact bytecode shows that its 14-argument vertex method:

1. reads the backing `ExtendedBufferBuilder` byte buffer and element offset;
2. writes the three position floats with `PositionAttribute.put(JFFF)`;
3. writes the remaining attributes in the destination format; and
4. calls its mapped `endVertex` method, which in turn calls
   `ExtendedBufferBuilder.sodium$moveToNextVertex()`.

`SodiumBufferBuilder#getOriginalBufferBuilder()` returns that same backing
object cast to vanilla `BufferBuilder`. Embeddium's target `BufferBuilderMixin`
makes the vanilla builder implement `ExtendedBufferBuilder`; it advances the
vanilla vertex count and byte offset. Sodium is therefore a delegate, not an
independent vertex store.

## Instrumentation

`SableM28ContraptionBufferProbe` unwraps the Sodium delegate through its public
method without adding a compile-time Embeddium dependency. It snapshots the
backing builder count and offset immediately before Catnip calls the real
consumer, then reads the position bytes after that call returns. The bounded
markers are:

- `SABLE_M28_SODIUM_VERTEX_WRITE`
- `SABLE_M28_SODIUM_VERTEX_COMPARE`
- `SABLE_M28_SODIUM_BUFFER_LIFECYCLE`
- `SABLE_M28_PRE_DRAW_VERTEX`
- `SABLE_M28_DRAW_PROVENANCE`

Unsupported consumers now report `destinationInspectionSupported=false`.
They are not mislabeled as a failed geometry write.

## Oculus finalization

Oculus 1.8.0's `SegmentedBufferBuilder` returns its underlying vanilla
`BufferBuilder`. On render-type changes and `getSegments()`, it finalizes that
builder into `BufferSegment` objects. `BufferSegmentRenderer.drawInner` passes
the resulting `RenderedBuffer` directly to `BufferUploader.drawWithShader`.
That route bypasses vanilla `BufferSource.endBatch(RenderType)` and
`RenderType.end`, so the M28 trace identifies a segment at the actual draw
boundary by matching its stored vertex signature. This produces a continuous
backing-builder to rendered-buffer to GPU-draw provenance chain without
altering Oculus.

## Entity-phase A/B

The default remains the existing Sable-owned scoped `AFTER_ENTITIES` path.
Setting `-Dsable.m28.entityPhaseAB=true` disables that dispatch and performs the
same contained-contraption dispatch once from the head of the target
`LevelRenderer.renderEntity` method. It receives the real entity-phase camera,
partial tick, pose stack, and `MultiBufferSource`; it does not allocate or flush
a private source. This is a controlled comparison, not a claimed production
fix.

`-Dsable.m28.normalCreateAB=true` enables identical bounded vertex diagnostics
for an ordinary-world `ControlledContraptionEntity`.

## Status

Static inspection proves the exact storage and draw-provenance instrumentation.
It does not prove which render mode is player-visible. M28 mechanics remain
PASS. The assembled-sail visual remains FAIL/PARTIAL until a manual A/B run
provides actual Sodium storage, finalization/draw provenance, and a visible
result.
