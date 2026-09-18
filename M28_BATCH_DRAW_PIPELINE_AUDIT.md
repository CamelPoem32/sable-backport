# M28 Oculus entity-batch ownership audit

Authority: the exact `oculus-mc1.20.1-1.8.0.jar` release artifact (SHA-256
`0945DF0CBA0F62B3901DD80C3268E5311B770ECE78C78037A45DB12AC0425FEF`),
the mapped Minecraft 1.20.1 / Forge 47.4.20 classes used by this build, and
Create 6.0.8's CPU `ShadeSeparatingSuperByteBuffer` path.

## Proven runtime failure

Sable manually dispatched contained Create contraptions from Forge
`RenderLevelStageEvent.Stage.AFTER_ENTITIES` into
`Minecraft.renderBuffers().bufferSource()`. With Oculus active that accessor
returns `FullyBufferedMultiBufferSource`, not vanilla `BufferSource`.
Runtime frame 9345 accepted two ranges, `0..24` and `24..48`, but both still
appeared as `UNFLUSHED_RANGE` at frame 9348. Later frames repeated the same
pattern. The vertices were valid and moving; their foreign batch had already
passed the matching Oculus collection/draw boundary.

## Exact Oculus 1.8.0 lifecycle

`MixinRenderBuffers` replaces the normal entity source with one
`FullyBufferedMultiBufferSource` while level rendering is active.
`FullyBufferedMultiBufferSource#getBuffer` calls `removeReady`, assigns the
RenderType to one of 32 `SegmentedBufferBuilder` instances, and writes there.
Its `endBatch(RenderType)` override is deliberately empty.

Oculus owns finalization from `MixinLevelRenderer`:

1. `beginLevelRendering()` runs at `LevelRenderer#renderLevel` HEAD.
2. `startGroup()` / `endGroup()` bracket each ordinary `renderEntity` call.
3. At the exact `"translucent"` profiler transition, `readyUp()` collects the
   segmented builders. Opaque and opaque-decal segments are drawn there when
   separate entity draws are enabled; otherwise `endBatch()` draws all groups.
4. The post-translucent hook draws the remaining segments.
5. `endLevelRendering()` runs at method return.

Consequently the wrapper can accept a late `getBuffer` call: it clears the
ready view and resumes writing to a segmented builder. It does not promise a
second matching collection/draw pass after Sable's late Forge-stage dispatch.
This also explains why the old hooks on vanilla `BufferSource#endBatch(type)`,
`RenderType#end`, and `BufferUploader` never observed those ranges: Oculus's
segmented renderer owns that route and its per-type end method is a no-op.

## Production ownership fix

The Forge-stage bridge now owns one reusable vanilla
`MultiBufferSource.BufferSource` for the complete manual Sable contraption
pass. Every eligible contained contraption in the frame writes into that same
source. A `finally` block calls `endBatch()` exactly once after the entity loop.
The source is never Oculus's global `FullyBufferedMultiBufferSource`, no global
batch is forced closed, and there is no per-entity flush.

This is architecture B from the M28 acceptance request:

`Sable begins scoped source -> all manual Create entities write -> Sable ends
scoped source once -> RenderType selects the active shader and draws -> frame
continues`.

The source and builder are reused only after `endBatch()` resets their state,
so no unflushed builder survives into the next frame. RenderType state setup
still uses the target's normal shader supplier at draw time. Shader/Oculus
visual compatibility remains a manual runtime gate because Minecraft was not
launched during this pass.

## Bounded diagnostics

`SABLE_M28_ENTITY_BATCH` samples startup frames and then one frame in twenty.
It reports `BEGIN_SCOPED_BATCH`, each relevant `WRITE`,
`END_SCOPED_BATCH`, and `FLUSH_COMPLETE`, including source/builder identity,
range, flush owner, flush frame, and `flushed=true/false`. Any range surviving
two frames still emits `UNFLUSHED_RANGE` as a failure signal.

The earlier matrix, vertex, RenderedBuffer, and GPU dump remains available only
with `-Dsable.m28.verboseBufferTrace=true`; it is no longer emitted normally.
No steering, bearing, pose, pivot, culling, model, static ownership, or vertex
transformation behavior changed.

M28 remains mechanics PASS / visual runtime required until both axis-Y and
axis-Z assembled sails visibly rotate with Oculus enabled and scoped traces
show same-frame `flushed=true` with no `UNFLUSHED_RANGE`.
