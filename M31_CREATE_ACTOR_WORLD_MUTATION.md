# M31 Create Actor World Mutation

M31 establishes the first production boundary for a Create movement actor that
mutates the environment around a moving Sable body. The supported actor is the
Mechanical Drill. The parent-world breaking gates are runtime validated.

## Existing M16 State

The retained M16 implementation was **B: partially implemented**. It enabled an
upstream-derived `BlockBreakingMovementBehaviour` mixin and transformed a
mining box through the owning Sable pose. It was not a production-safe external
mutation boundary because it:

- allowed Create to inspect and accept the hidden raw proposed position first;
- returned that hidden raw position when no transformed candidate was found;
- did not reset progress as outer-body motion changed the physical parent target;
- covered every `BlockBreakingMovementBehaviour` subtype although only Drill
  was the accepted canary;
- emitted unconditional per-progress M16 INFO diagnostics.

The old M16 command fixture remains useful as a historical actor/render harness,
but its hidden-plot target assumptions are not the M31 acceptance source.

## Exact Create 6.0.8 Path

The audited Create baseline is commit
`1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

```text
AbstractContraptionEntity.tickActors()
  -> MovementBehaviour.REGISTRY.get(actor state)
  -> toGlobalVector(actor center + getActiveAreaOffset(context), 1)
  -> shouldActorTrigger(...)
  -> MovementBehaviour.visitNewPosition(context, proposed BlockPos)
  -> MovementBehaviour.tick(context)

DrillMovementBehaviour
  -> extends BlockBreakingMovementBehaviour
  -> getActiveAreaOffset(context)
     = DrillBlock.FACING normal * 0.6499999761581421
  -> canBreak(...)
     = generic hardness rule + collision shape + non-track rule

BlockBreakingMovementBehaviour.visitNewPosition(context, pos)
  -> context.world.getBlockState(pos)
  -> damageEntities(context, pos, world)
  -> server-side canBreak(world, pos, state)
  -> data[BreakingPos] = pos
  -> context.stall = true

BlockBreakingMovementBehaviour.tick(context)
  -> tickBreaker(context)
  -> WaitingTicks / LastPos retry

BlockBreakingMovementBehaviour.tickBreaker(context)
  -> reads BreakingPos, Progress, BreakerId, TicksUntilNextProgress
  -> reads hardness from context.world at BreakingPos
  -> advances Create's speed/hardness progress
  -> Level.destroyBlockProgress(...)
  -> destroyBlock(context, BreakingPos)

BlockBreakingMovementBehaviour.destroyBlock(context, pos)
  -> BlockHelper.destroyBlock(context.world, pos, 1, drop callback)
```

M31 leaves the Create methods after target selection unchanged. This preserves
hardness, breaker progress, falling-block delay, sounds, loot/drop insertion,
block-entity destruction, fluids, unbreakable-state checks, and server authority.

## Coordinate Spaces

| Value | Space | Owner |
| --- | --- | --- |
| `MovementContext.localPos` | `ACTOR_LOCAL` | Create contraption |
| `toGlobalVector(localPos.center, 1)` | `SABLE_RAW` | hidden plot storage |
| `context.rotation.apply(activeAreaOffset)` | `SABLE_RAW` direction | Create inner rotation |
| transformed actor center/direction | `PARENT_VISIBLE` | current Sable logical pose |
| scanned candidate | `PARENT_BLOCK` | parent `Level` storage |
| `data[BreakingPos]` | `PARENT_BLOCK` | Create breaker state |

`CreateActorTargetGeometry` transforms the small Drill mining box using the
current Sable pose. `BoundingBox3d.transform` transforms all eight corners, so
pitch, yaw, roll, and combined rotations produce a conservative parent-visible
box. The direction is transformed as a normal, never as a position.

## Production Boundary

`BlockBreakingMovementBehaviourMixin` is retained because it is the narrowest
point before Create commits the target into `BreakingPos`. Its M31 behavior is
additionally restricted to `DrillMovementBehaviour` whose contraption anchor is
inside a Sable sublevel.

For a supported actor:

1. The raw proposed BlockPos is never passed to Create.
2. `SubLevelBlockBreakingUtility` scans only loaded parent-world blocks in the
   transformed mining box.
3. Any position owned by a Sable plot is rejected.
4. No candidate means no action; there is no raw-position fallback.
5. The selected parent BlockPos is passed to Create's original
   `visitNewPosition` and becomes its normal `BreakingPos`.
6. Each server actor tick resolves the current physical target. Translation,
   rotation, or a block-boundary crossing clears the old crack/progress state
   before Create starts the new target.

Normal-world Drill actors and all non-Drill block-breaking behaviours call the
original Create method without M31 target conversion.

## Authority And Safety

Create's `tickBreaker` already returns on the client. Final mutation therefore
remains server-authoritative through `BlockHelper.destroyBlock` on the parent
Level. M31 does not redirect `Level` APIs and does not create a Level wrapper.

The parent candidate must be in a loaded chunk. An unresolved or unloaded
target is a no-op. A candidate belonging to another Sable plot is also a no-op
in M31; body-to-body actor mutation is explicitly unsupported rather than
guessing at hidden storage ownership. A Drill cannot select its own hidden
aircraft blocks.

## Diagnostics

Enable only when needed:

```text
-Dsable.m31.traceCreateActors=true
```

`SABLE_M31_CREATE_ACTOR` emits transition/action events:

```text
ACTOR_DISCOVERED
TARGET_RESOLVED
TARGET_CHANGED
BREAK_PROGRESS_STARTED
BREAK_PROGRESS_RESET
BLOCK_MUTATION_ATTEMPT
BLOCK_MUTATION_SUCCESS
BLOCK_MUTATION_REJECTED
```

The trace is disabled before formatting work and uses weak, per-context
transition state. It does not log every actor tick.

## Supported And Deferred Actors

Supported in M31:

- `DrillMovementBehaviour` / Mechanical Drill.

Likely future users of the same coordinate-ownership architecture, not changed
by M31:

- Saw movement behavior and tree-cut traversal;
- Harvester and Plough movement behaviors;
- Deployer mutation details beyond its existing interaction-position bridge;
- Portable Storage Interface and other environment-coupled actors.

Static kinetic Drill block entities are not movement actors and remain outside
this milestone.

## Golden Drill Runtime Acceptance

Use a small assembled Sable body with a normal Create moving contraption carrying
a Mechanical Drill at its edge. Place ordinary stone in the visible parent
world; do not place acceptance blocks in the hidden plot.

1. Stationary assembled body: advance the Create actor into stone. The visible
   parent block breaks and the corresponding hidden-plot coordinate is unchanged.
2. Translate the body and repeat. Only the new visible target breaks.
3. Rotate the body 90 degrees and repeat. The target follows the rotated Drill
   direction.
4. Move the body slowly while the actor is breaking. A changed parent target
   resets progress; no stale block is destroyed.
5. Disassemble Sable and repeat with an ordinary normal-world Create Drill.
   Behavior must remain vanilla/Create-equivalent.

Recommended diagnostic runtime flag:

```text
-Dsable.m31.traceCreateActors=true
```

These runtime gates passed before M32 began; M31 is closed.
