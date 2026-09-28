# M35 Mechanical Roller parent-world terrain

Source baseline: Create 6.0.8, commit `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
`AbstractContraptionEntity.tickActors` computes the moving actor's active point, raw grid position,
`motion` and `relativeMotion`, calls `RollerMovementBehaviour.visitNewPosition` on grid changes, and
calls inherited `BlockBreakingMovementBehaviour.tick` each active tick.

## Native operations

| Operation | Exact Create method | Persistent state | World/storage effect |
| --- | --- | --- | --- |
| Active point and motion | `getActiveAreaOffset`, `isActive`, `tickActors` | previous actor point | Facing and relative motion determine activation. |
| Clearance profile | `getPositionsToBreak`, `testBreakerTarget`, `canBreak` | none | Non-train `visitedPos.above(i)` for `i=startingY..2`; world/block queries. |
| Breaking | `visitNewPosition`, inherited `tickBreaker`, `onBlockBroken`, `destroyBlock` | `ReferencePos`, `BreakingPos`, `Progress`, `BreakerId` | Native hardness, stall, damage, drops and `BlockHelper.destroyBlock`. |
| Terrain profile | `createHeightProfileForTracks` | train scout | Train graph profile only; unsupported for Sable-hosted train CCEs in M35. |
| Paving footprint | `triggerPaver`, `tryFill` | `WaitingTicks`, `LastPos` | Native straight/tunnel column or wide-fill diamond; global `below`, `above`, X/Z. |
| Material | `getStateToPaveWith`, `tryFill` | BE `Filter`, `ScrollValue` | Native filter and `ItemHelper.extract(context.contraption.getStorage().getAllItems(), ..., 1, false)`. |
| Entities | inherited `damageEntities` | none | Native parent-world AABB and roller damage when the visited cell is not a conductor. |

The Roller is not a point actor. Create derives clearance cells above the visited anchor and paving
cells below/around it. M35 converts the active anchor from Sable raw to parent-visible coordinates
once, then lets Create construct the **entire** native footprint in that parent world. Every native
clearance, break and paving cell is checked for a loaded parent chunk, hidden-plot ownership and
other Sable bodies before it can query or mutate terrain. No raw-coordinate fallback exists.

## Hook and coordinates

`RollerMovementBehaviourMixin.visitNewPosition` is the narrow actor boundary. It resolves the
current raw active point through the owner's logical pose, publishes a parent `BlockPos`, and
temporarily scopes Create's drop/entity pose through `SableCreateActorWorldContext` with automatic
restoration. The inherited breaker tick retargets when outer-body translation/rotation changes
the physical anchor; it clears old progress/wait state before native Create visits the new target.
The native `testBreakerTarget`, `destroyBlock` and `tryFill` calls receive per-cell safety checks.
No `Level` method is globally redirected, and ordinary non-Sable Rollers call Create unchanged.

The raw active point already includes the Roller facing offset and Create's `-2 Y` offset. The
outer logical pose rotates actor position and facing. Create's vertical terrain columns, paving
depth and wide-fill X/Z diamond remain aligned to Minecraft world axes. The outer body may
translate and yaw; pitched, rolled or inverted Sable bodies are rejected when their up vector is
not close to world up. Upside-down paving is not approximated. Carriage/train profiles hosted in
Sable are also rejected instead of guessed. Native Roller relative motion still controls
activation/break speed; physical outer-body movement additionally retargets the footprint when
the visible block boundary changes. This remains a runtime acceptance point.

Create owns unbreakable restrictions, portal/track checks, slab selection, replaceability, fluid
state, native drops, material matching, one-item extraction and native placement. M35 adds no
inventory pump or custom paving algorithm. The material chest must be captured by the **inner**
Create piston contraption; a chest merely attached to the Sable hull is not mounted storage.

Trace with `-Dsable.m31.traceCreateActors=true` only when needed. `ROLLER_*` diagnostic events are
bounded to 64 distinct action/cell keys per actor context and default off. The one-command fixture
functions and test procedure are in `M35_RUNTIME_TEST_COMMANDS.md`. The repaired fixture uses a
negative-speed motor for eastward piston extension and starts its terrain lane beyond the static
chassis/actor. Build verification is not a substitute for the normal-world,
Sable, translation, yaw, depletion and retarget runtime gates there.

## M35.5 level runtime fixture

The 22-block outer hull is a free dynamic body. `SubLevelPhysicsSystem.initialize` supplies
dimension gravity and drag, and its physics tick updates the body's merged mass from the moving
inner contraption. The fixture has no roll/pitch constraint and is asymmetric around the piston
and chest. Runtime showed up vectors tilted outside the Roller's exact
`SableRollerTerrain.supportsWorldDown` predicate (`up dot worldUp > 0.999`); it did not isolate
one particular collision impulse or torque. A passive hull adjustment therefore cannot prove a
level run.

`M35FixtureRunState` is a command-only test fixture. It requires the unique tagged M35 Sable
marker and its assembled body, then uses that body's `RigidBodyHandle.teleport` and
`setLinearAndAngularVelocity` to hold a level yaw pose at a known visible anchor. The hold runs
after each existing Sable physics substep and at the server-tick boundary, updating the same
logical pose that production actors read. The run command checks
`SableRollerTerrain.supportsWorldDown(body)` before releasing the fixture clutch. No global
physics setting, Roller hook, actor context, or normal Sable body is changed. Cleanup ends the
hold before removing the marker.

The terrain generator transforms each fixture raw lane-cell center through the current logical
pose and writes only loaded, unoccupied parent-world cells. It snapshots the displaced lane
states for cleanup. It does not create or tick Roller actors. Diagnostic counters begin at the
run boundary and count real native `ROLLER_FOOTPRINT_RESOLVED`, `ROLLER_CLEAR_SUCCESS` and
`ROLLER_PAVE_SUCCESS` events when actor tracing is enabled. The fixture separately observes
parent terrain differences, native mounted cobblestone count, and unchanged raw plot terrain
below the carriage. A PASS line is withheld when any observation is missing; Minecraft runtime
must still confirm the new fixture behavior.

## M35.6 moving Roller rendering

Create 6.0.8's `RollerMovementBehaviour` disables ordinary block-entity rendering. It creates
`RollerActorVisual` for Flywheel, while `renderInContraption` calls native
`RollerRenderer.renderInContraption` only when visualization is unsupported. Sable's nested
PistonContraption is drawn by `SableForgeCreateContraptionRenderBridge` through Create's CPU
`ContraptionEntityRenderer`; the Roller actor previously still saw visualization support and
skipped its CPU wheel/frame. The client-only Roller render mixin selects Create's animated CPU
renderer only when the actor's Create entity belongs to a Sable SubLevel. Static and ordinary
normal-world Rollers keep Create's original branch.

The bridge transforms the raw nested entity anchor into parent-visible space before subtracting
the camera; Create's `ContraptionMatrices` then applies inner piston interpolation and actor-local
offset. The CPU Roller renderer uses these matrices for its wheel and frame partial models and
native animation speed. No plot-scale translation is applied to PoseStack. The actor diagnostics
are transition-only under `-Dsable.m31.traceCreateActors=true`. Runtime visual acceptance remains
pending until extension, retraction, and yaw90 are observed in Minecraft.
