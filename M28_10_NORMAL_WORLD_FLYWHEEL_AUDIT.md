# M28.10 Normal-World Controlled Sail Visual Gate

Status: mechanics PASS / visual runtime required. This artifact adds observations,
not a production transform or rendering-policy fix. Minecraft is not launched by this pass.

## Corrected target

M28.9's tinted CPU ranges belonged to older Sable-contained contraptions. The supplied
runtime instead identifies a normal-world bearing contraption when the airplane wheel
is operated. Its Create angle advances while CPU structure emission is skipped and
Flywheel builds its captured symmetric sail. This is strong ownership evidence, not
yet a suppression or player-visible proof for the new diagnostic artifact.

No ID, UUID or coordinate from that run is hard-coded. Candidates must be live
ControlledContraptionEntities outside Sable, have an exact Mechanical Bearing controller
at Create's stored controller position, and contain the symmetric-sail block state.
A candidate becomes an active target only after observed shortest-angle change exceeds
0.01 degrees. That epsilon selects diagnostics; it does not affect control tolerance.
Other bearing-mounted symmetric sails that actually rotate can also qualify; no claim
is made that geometry alone identifies a particular airplane or Steering Wheel.

## Exact dependency audit

The authority is the mapped Create 6.0.8 development artifact and Flywheel 1.0.5 on the
Forge compile classpath, not newer sources. The added bytecode verifier reads those jars.

ContraptionVisual's constructor creates an embedding, immediately sets its matrices,
builds structure with ForgeBlockModelBuilder, then creates child visuals. planFrame runs
beginFrame, which calls setEmbeddingMatrices with frame partial tick before updating
children. Structure-version changes rebuild the model. planTick updates actor/tickable
children. _delete deletes the structure, children, actors and embedding.

setEmbeddingMatrices resets its reusable PoseStack and translates interpolated entity
position minus renderOrigin (current position instead if isPrevPosInvalid). It then
calls entity.applyLocalTransforms(stack, partialTick) and writes pose and normal through
VisualEmbedding.transforms. The diagnostic reference repeats that documented sequence
on a separate PoseStack; actual values come from EmbeddedEnvironment.pose after the setter.
No Steering Wheel target or RPM-derived angle is used.

EmbeddedEnvironment.transforms copies the supplied matrices into pose and normal.
EnvironmentStorage.flush visits embeddings and calls flush at their CPU-arena slot.
EmbeddedEnvironment.flush composes any parent environment, writes the composed Matrix4f
at pointer+0 and padded Matrix3f at pointer+64. There is no embedding dirty flag in that
path. The new RETURN probe reads the actual arena floats, not reconstructed coordinates.
CPU staging is explicitly labeled separately from GPU upload/draw, which remain unknown
until the Java embedding comparison and runtime evidence require that deeper trace.

EngineImpl.render flushes EnvironmentStorage before DrawManager.render. In the instancing
backend, EmbeddedEnvironment.setupDraw submits poseComposed to the program's
_flw_modelMatrixUniform; the new bounded RETURN hook records that exact argument and
program identity. In the indirect backend, MatrixBuffer.flush enqueues a copy of the
CPU arena through StagingBuffer into the matrix storage array. That copy and GPU draw
are not misrepresented as observed by the arena probe. Runtime logs distinguish
CPU_ARENA_WRITE from INSTANCING_EMBEDDING_UNIFORM_SUBMITTED. The instance local pose
and embedding pose are separate; changing an embedding does not need to mutate the
structure instance's local pose. No GPU readback or new dirty-flag policy is added.

Create registers a SimpleEntityVisualizer for controlled contraptions. EntityStorage
admission uses VisualizationHelper.canVisualize. Separately, ContraptionEntityRenderer
skips CPU structure when VisualizationManager.supportsVisualization(level) is true.
Rejecting EntityStorage admission does NOT change that level-level early-out and does
NOT guarantee CPU fallback. No fallback or production visualizer policy is changed here.

## Diagnostics and suppression

- SABLE_M30_CONTROLLED_SAIL_TARGET: semantic reasons, identities, controller, states and angles.
- SABLE_M30_FLYWHEEL_TRANSFORM: creation/deletion/model, actual stored before/after matrices,
  prior sample, render origin, axis, partial tick, per-frame invocation count and thread.
- SABLE_M30_FLYWHEEL_EXPECTED: independent documented sequence versus actual stored pose.
- SABLE_M30_FLYWHEEL_UPLOAD: actual composed CPU-arena bytes and matrix delta; not a GPU claim.
- Optional M30 Flywheel overlay uses the actual embedding, render-origin-to-camera
  translation, Forge view/projection and GUI scale. It does not reuse M28.9 CPU ranges.

All diagnostics are opt-in with visualOwnershipTrace and traceControlledSailFlywheel
or suppressControlledSailFlywheel. Matrix logs are bounded to 80 samples per visual,
at five-degree changes or a 60-frame heartbeat; up to 32 embeddings are retained and
deleted visuals are removed. Worker hooks do not query GL state.

The suppression flag vetoes future admission only after motion proves the target.
Since the first visual may be admitted before motion, a render-thread observation also
uses Flywheel's legitimate entities().queueRemove(entity) once for that target. This
deletes only its visual, not the entity or payload. Subsequent admission remains vetoed.
The first trace run does not enable suppression.

## First Runtime

Use shaders OFF for simplicity, with only:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.traceControlledSailFlywheel=true
-Dsable.m28.showControlledSailFlywheelOverlay=true
```

Operate the actual airplane wheel and rotate its assembled sail at least 90 degrees.
Match UUID/entity identity across CONTROLLED_SAIL_TARGET, STRUCTURE_MODEL, stored embedding
updates and EXPECTED records. Compare angle and matrix progression. Inspect the actual
M30 overlay, not old CPU boxes. Do not enable the old Sable-only Flywheel suppression.

Only if ownership needs a binary check, repeat with suppressControlledSailFlywheel=true.
The removal/veto log must precede the visual observation; absence of a sail would prove
that visual owner, not prove CPU fallback. No runtime result is invented in this audit.
