# M43: Final MovementBehaviour Residuals

Target: Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8, Flywheel 1.0.5.
Authoritative Create commit: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
Create production jar SHA-256: `6fbb910c367dbce8e4fc7e5bf64b6edd4de980906ed00af8e47e4af843c0d9b0`.

M28-M42 retain their user-accepted runtime status, including M37's accepted
synthetic INSERT-fixture limitation. M43 is IMPLEMENTED / runtime pending.
No Minecraft client/server was launched for this delivery. Static tests are not
native runtime acceptance, and this report does not close M43 or M41 functional coverage.

## Exact source and bytecode audit

Pinned source was read from `build/m41Audit/source/`. The corresponding classes
were inspected with Java 17 `javap -c -p` against the production Create jar, not
the newer development branch. `build/m43-native-bytecode.txt` retains the compact
native disassembly. The aggregate inventory re-reads all production behavior
classes, overridden/inherited callbacks, registration owners and their hashes.

Registration sites are `AllBlocks.<clinit>`:

| Native behavior | Block | Source registration | Callback inheritance |
|---|---|---|---|
| `actors.plough.PloughMovementBehaviour` | `create:mechanical_plough` | AllBlocks.java:1442 | overrides visit/throw; base damage/throw implementation |
| `kinetics.drill.DrillMovementBehaviour` | `create:mechanical_drill` | AllBlocks.java:1347 | inherits visit/damage/throw; supplies drill damage source |
| `kinetics.saw.SawMovementBehaviour` | `create:mechanical_saw` | AllBlocks.java:1360 | overrides visit, calls super once; supplies saw damage source |
| `processing.burner.BlazeBurnerMovementBehaviour` | `create:blaze_burner` | AllBlocks.java:756 | client tick/getTargetAngle; native render/head animation |

The common native prefix is `AbstractContraptionEntity.tickActors`:

1. `toGlobalVector(local block center + actor active offset, 1)` produces SABLE_RAW.
2. `shouldActorTrigger` computes `context.motion = actorPosition - previousPosition`.
3. `context.relativeMotion = reverseRotation(context.motion, 1)` is INNER_MOTION.
4. `context.position = actorPosition`, `context.rotation = applyRotation(..., 1)`.
5. Native active/visit/stall gates, `visitNewPosition`, speed-change callback, then `tick`.

Thus `context.motion` includes the native inner CCE's translation/rotation in
Sable's raw axes. It does not already contain the outer Sable pose. M32's parent
drop scopes are not active in this entity-throw call chain.

### Plough: M43-A

Exact call graph:

```text
tickActors -> Plough.visitNewPosition -> BlockBreaking.visitNewPosition
 -> if !stateVisited.isRedstoneConductor: damageEntities
 -> world.getEntitiesOfClass(Entity.class, new AABB(pos))
 -> native exclusions and throwsEntities/client-player gate
 -> Plough.throwEntity -> super.throwEntity
 -> context.motion.add(0, context.motion.length()/4, 0)
 -> native length-4 cap
 -> entity.setDeltaMovement(oldVelocity + boost), hurtMarked=true
 -> native Plough anvil advancement path
```

Native base bytecode: visit `damageEntities` at offset 26 precedes `canBreak` at
42. `throwEntity` reads `MovementContext.motion` twice at offsets 1 and 6;
`Vec3.m_82520_(DDD)` at 17 adds the lift; `Entity.m_20256_(Vec3)` at 64 commits it.
Plough's terrain hoe/soil path and its native advancement path are unchanged.

The existing M32 point resolver already makes `pos` a physical parent cell, but
did not rotate this motion. The narrow base `throwEntity` WrapMethod now scopes
only the motion input, restoring it in `finally`. It activates for the three
supported Sable breaker types only. Drill and Saw inherit this same native
throw operation, so their native post-damage impulses receive the same correction.

Let `R` be outer orientation, `m` native raw-axis motion, and `U=(0,1,0)`:

```text
parentMotion = R*m
uncappedBoost = parentMotion + U*|parentMotion|/4
boost = uncappedBoost * min(1, 4/|uncappedBoost|)
velocityAfter = velocityBefore + boost
```

The runtime code calls Create's original operation, not this reference formula.
`ResidualActorGeometry.nativeBoost` is only a numerical acceptance reference.
Only `R` is used: no translation, scale, linear body velocity, or angular-body
velocity term. Adding body velocity would change native Create's selected
relative-motion semantics, rather than fix its frame. Rotation supports full
SO(3). Native world-up lift remains world-up; it is not rotated with the actor.
Tests reject double yaw rotation and non-uniform-scale/translation leakage.

### Drill and Saw: M43-B

Drill inherits `BlockBreakingMovementBehaviour.visitNewPosition` unchanged.
Saw calls that exact super method once, then applies its native vertical-saw
BreakingPos/stall adjustment. Neither intentionally requires a breakable block
before entity interaction. Their active offsets are local facing times `.65f`.

```text
native visit -> getBlockState(pos)
 -> !isRedstoneConductor -> damageEntities
 -> native oriented-contraption nonDamageTicks guard
 -> CreateDamageSources.drill(world) OR .saw(world)
 -> getEntitiesOfClass(Entity.class, new AABB(pos))
 -> native filters -> hurt(nativeSource, clamp(6*|relativeMotion|^.4+1,2,10))
 -> native throw condition and original throw
 -> server canBreak gate -> native BreakingPos/stall/progress only when eligible
```

The proven old Sable bug was before this code: `sable$resolveExternalDrillTarget`
called `findExternalTarget(canBreak, ...)`, then returned when target was null.
AIR cannot satisfy Drill's collision-shape requirement or Saw's sawable-block
requirement. The native entity phase therefore never ran in those cells.

The fix separates only the entity phase, not the block algorithm:

1. Resolve the actual active point (`context.position`, already including inner
   transform/offset), outer-transform it, floor to PARENT_BLOCK, and apply the
   existing loaded-parent/hidden-plot/cross-body point guards.
2. On a physical-cell transition, reproduce the native non-conductor eligibility
   test and invoke **the original native `damageEntities` method** once.
3. A one-callsite WrapOperation replaces the original visit's entity-phase call
   for Sable Drill/Saw only, preventing a second query. Plough and ordinary
   Create delegate that original call unchanged.
4. Continue the existing terrain target path exactly as before; no block target
   is fabricated for AIR and no `canBreak`, progress or destroy call is added.
5. The existing server retarget tick checks physical-cell transitions too, so
   movement of the outer pose cannot leave a stale physical entity area.

This also handles physical AIR beside a separately selected terrain block: the
entity query no longer depends on whether some mining-bound candidate exists.
`SableM43EntityCell` tracks the last eligible physical visit in native context
data; visit and retarget in one tick do not query twice. Re-entry after another
physical cell is a new visit. A stalled actor remains governed by existing stall
logic; no repeated damage-every-tick algorithm is introduced.

Native filters stay intact: ItemEntity and every AbstractContraptionEntity are
excluded; same-vehicle passengers and minecarts carrying this contraption are
excluded; oriented contraption nonDamageTicks and server-only damage remain.
Living entities are not a new custom selection category. Outer ServerSubLevel
is not a new damageable entity. Native client-side Player throw rules remain.

AIR/no entity is harmless. Breakable or unbreakable non-conductors may still
query, as native Create permits. Conducting solids suppress the entity query.
Breakability independently controls progress. There is no copied `hurt` call,
damage source or constant in runtime compatibility. Read-only operation wrappers
observe native `getEntitiesOfClass` and native `hurt`, returning their original
results unchanged.

### Blaze Burner: M43-C

```text
tickActors -> BlazeBurnerMovementBehaviour.tick (client + native heat gate)
 -> getHeadAngle / getTargetAngle
 -> native train-conductor branch (unchanged and out of this fixture)
 -> Minecraft.cameraEntity; non-null, not invisible, actor position non-null
 -> cameraEntity.position().subtract(context.position)
 -> innerCCE.reverseRotation(vector, 1)
 -> degrees(-atan2(local.z, local.x)) - 90
 -> original LerpedFloat chase/tick -> original BlazeBurnerRenderer
```

The camera source is **cameraEntity.position**, not Camera#getPosition or eye
position. This remains exactly the same in first and third person; third-person
camera orbit is not substituted for the native render-view entity position.
Native bytecode: position call at 119, raw position read at 123, subtraction at
126, inner reverseRotation at 130, angle calculation and return at 150-161.

Old subtraction mixed PARENT_ENTITY_SPACE and SABLE_RAW. The single subtraction
operation is now scoped to Sable actors:

```text
visibleActor = canonicalClientRenderPose(1).transformPosition(rawActor)
physicalGaze = nativeCameraEntityPosition - visibleActor
sableVector = inverse(Router) * physicalGaze
nativeLocalVector = innerCCE.reverseRotation(sableVector, 1)  // original call
nativeTargetAngle = original atan2 computation
```

The canonical `ClientSubLevel.renderPose(1)` matches the native tick-based
`reverseRotation(...,1)`. There is no second interpolation engine; native head
LerpedFloat retains render interpolation. Continuous outer yaw/pitch/roll are
valid for this point/vector conversion; native head yaw remains horizontal in
the inner actor's frame, not an invented full-3D head animation.

Only the Sable branch bypasses raw subtraction. The ordinary branch calls the
original subtract operation. The camera is never pushed into a plot; no raw
camera/actor subtraction is computed even for diagnostics. Particle coordinates
remain on their existing Sable particle path. No PoseStack or renderer changes.

## Domains, activation and safety

| Operation | Input | Output | Parent ownership / rejection |
|---|---|---|---|
| Native active point | ACTOR_LOCAL + offset through INNER_CONTRAPTION | SABLE_RAW | existing native CCE |
| Entity cell | SABLE_RAW through current logical pose | PARENT_VISIBLE -> PARENT_BLOCK/AABB | owning parent Level, loaded cell, no hidden plot, no other intersecting body |
| Throw motion | SABLE_LOCAL_VECTOR | PARENT_WORLD_VECTOR | rotation only; physical parent entity required |
| Blaze actor point | SABLE_RAW | interpolated PARENT_VISIBLE | canonical owning client sublevel |
| Blaze camera vector | PARENT_ENTITY_SPACE minus PARENT_VISIBLE | inverse outer -> native inner local vector | same native camera source; no hidden-space camera |

All activation uses existing `SableCreateActorWorldContext.owner`: requires a
native contraption/entity and its anchor in a Sable-owned plot. Outside Sable,
original methods/operations are called unchanged. Existing terrain selection,
normal-world paths, M42 wrappers/preservation, M37 rendering, and other closed
production helpers are not rewritten.

Sable A -> ordinary physical parent entity: supported under native filters.
Sable A -> own hidden state: no external query/impulse there. Hidden A -> hidden B:
rejected by parent-cell guards/no raw fallback. Another physically intersecting
Sable body: existing safe-no-op policy; no new cross-Sable mechanic. The throw
also rejects entities whose chunk is a hidden plot. Blaze uses its owning pose
and native physical camera; there is no search for another burner/body.

Physical entity queries remain Create's **axis-aligned parent block cell** after
transforming the active point, not a globally inflated or arbitrary rotated
query volume. Existing M31 mining-bound eight-corner transformation is untouched.

## Packaged mixin evidence

`BlockBreakingMovementBehaviourMixin` gains four operation boundaries:

| Target method | Boundary | Production member / count |
|---|---|---|
| `visitNewPosition(MovementContext,BlockPos)V` | suppress duplicate native phase for Sable Drill/Saw | `BlockBreakingMovementBehaviour.damageEntities(MovementContext,BlockPos,Level)V` / 1 |
| `throwEntity(MovementContext,Entity)V` | WrapMethod input scope | declared method / 1 |
| `damageEntities(MovementContext,BlockPos,Level)V` | read-only query witness | `Level.m_45976_(Class,AABB)List` / 1 |
| same | read-only native damage witness | `Entity.m_6469_(DamageSource,float)boolean` / 1 |

The client-only `residuals.BlazeBurnerMovementBehaviourMixin` targets:

| Target | Boundary | Count |
|---|---|---|
| `getTargetAngle(MovementContext)F` | `Vec3.m_82546_(Vec3)Vec3` | 1 |
| same | RETURN observer | 3 exact FRETURN sites |

Create-owned methods use their actual unmapped names; Minecraft members have
explicit remapped @At entries and are checked in the packaged refmap. Competing
existing base visit WrapMethod/retarget hooks remain in the same mixin; the
operation replacement is inside the original body, and the direct native entity
method call is outside that single replaced callsite. Saw's super call and
Plough's separate wrapper remain intact. No priority hack or `require=0`.

All **204 old structural checks remain present**, with six added checks: 210
checks, 301 class fingerprints, zero unsupported point kinds. Reviewed canonical
fingerprint: `c0b06d3adbcb2d18f0447a421a0f6e342707a39bafe4e7098de6f64bdc617750`.
ASM inspection is mandatory, but is not a claim of executing a full Mixin launch
or native Minecraft runtime. No interactive game launch was performed.

## Runtime fixture

One outer Sable with four separate native piston lanes prevents shared actor
stall/collision from changing another family's semantics. Lane offsets are
`(0,0,0)`, `(0,0,5)`, `(0,0,10)`, `(0,0,15)` for Plough, Drill, Saw and Blaze.
Each uses the accepted M38 native attachment:

```text
per-lane controller origin C:
C+(-12..-1,0,0)    native east-facing piston poles
C+(0,0,0)         sticky mechanical piston, east, Y drive
C+(0,-2,0)        creative motor, UP; stopped before outer assembly
C+(0,-1,0)        clutch, Y
C+(0,-1,1)        fixture lever
C+(1,0,0)         radial chassis, axis X, UP/SOUTH sticky faces
C+(1,0,1)         native chest, empty mounted storage
C+(1,1,0)         the lane's native actor, EAST (Blaze: smouldering)
global (0,1,-2)   one outer Physics Assembler above iron support
global y=-2       connected iron/control spine between the four lanes
```

BUILD and validation share the same canonical layout. Dry native PistonContraption
assembly must capture exactly 16 blocks and one actor per lane before READY.
The whole outer selection must include the canonical map. Exact SuperGlueEntity
bounds join only the control/hull spine, not an invented payload serializer.
All four motors remain zero before outer assembly; static capture therefore
does not require altering the accepted M42 live-payload preservation adapter.

Placement reuses M38's deterministic 27 nearby loaded candidate origins and
reserves only actual blocks, lane strokes and their yawed counterparts, above
the floor. It does not generate chunks or overwrite unrelated blocks. Marker
creation is checked before BUILD. Failures remove placed cells and verify the
clear footprint before reporting rollback complete.

RUN assembles native actors slowly, pauses all four, and arms targets only after
their native CCE/context positions exist. Three owned parent-world test entities
are placed in future physical AIR cells. A later native cell visit, not a copied
fixture callback, selects/damages/throws them. Drill and Saw also encounter owned
stone/log regression targets farther down their lanes. These native drops go
into the captured native empty chest; no parallel storage is introduced.

Read-only production observations are weakly keyed by MovementContext and bounded
to 64 events; cleared before each run. No production class imports fixture code
or reads fixture tags. Fixtures require owned UUIDs, current context and armed
time before accepting observations. Targets are removed only after the actual
native witness is recorded. Runtime summaries never call `hurt`, `throwEntity`
or `damageEntities` themselves.

Blaze is a real native client callback. A fixture-only Forge channel requests the
current owned vehicle's native angle sample with a per-run nonce; the client
reads `SableBlazeGaze` and returns the actual angle and geometry. The server checks
the requesting player, nonce, owner, vehicle, finite values, visible region and
angle/direction agreement. This is client-observed visual evidence, not a
claim that a server-only test can inspect rendering. Missing client evidence
keeps the gate pending. Native head angle can differ from physical 3D aim because
Create intentionally animates a horizontal local heading; comparison uses that
same native horizontal projection.

Aggregate acceptance requires Plough + Drill AIR + Saw AIR + Blaze + both block
regressions. Plough velocity error <1e-9 and direction dot >1-1e-10 reflect
double-precision quaternion/Vec3 arithmetic; Blaze uses dot >1-1e-10 after native
float-degree conversion. Drill/Saw require a native accepted health decrease,
the intended cell still AIR, an actual candidate query, and no repeated damage
attempt to the same target in one tick. None of these are unconditional PASS.

After aggregate PASS native RETURN-mode retraction completes before the next
yaw/run. Yaw uses +90 degrees around Y (local EAST -> parent NORTH), resets the
test's finite owned entities/targets via a fresh run, and never changes actor
facing or native behavior. Fixture-only body stabilization is the established
M35-M42 physics-handle hold. No production rigid-body motion is modified.

Cleanup resolves the same marker + assembler ownership token for every phase,
including after reload. It removes only tagged test entities/persisted exact
regression targets, retracts each native piston, reanchors the outer body to its
reserved origin, uses native outer disassembly, then removes the canonical
static map. Unrelated restore blockers are diagnosed and preserved. Native
round-trip glue may be removed by exact original bounds only after verified
native outer teardown. Timeouts leave ownership recoverable; repeated cleanup
when absent reports already clean. It never deletes an active unknown CCE/body.

### Commands and expected evidence

```mcfunction
/function sable:m43/cleanup
/function sable:m43/setup
```

Wait for `[M43] SETUP phase=READY`, then click the generated Physics Assembler
**once**. No manual placement/glue/controller changes are required.

```mcfunction
/function sable:m43/run_all
```

Expected actual-state markers (numbers/UUIDs are runtime-derived):

```text
[M43] RUN phase=PRECHECK
[M43] ACTORS_CAPTURED nativePistons=4
[M43] RUN phase=ACTIVE
[M43] [PLOUGH] ENTITY_SELECTED ... directionDot=... magnitudeError=... PASS
[M43] [DRILL] AIR_CELL ... healthBefore>healthAfter hiddenPlotQuery=false duplicateDamage=false PASS
[M43] [SAW] AIR_CELL ... healthBefore>healthAfter hiddenPlotQuery=false duplicateDamage=false PASS
[M43] [BLAZE] GAZE_SAMPLE ... nativeAngle=... directionDot=... hiddenPlotInput=false PASS
[M43] [DRILL] BLOCK_REGRESSION nativeDestroyed=true ... PASS
[M43] [SAW] BLOCK_REGRESSION nativeDestroyed=true ... PASS
[M43] BASELINE PASS
[M43] RUN COMPLETE innerRetracted=true
```

Only then:

```mcfunction
/function sable:m43/yaw90
/function sable:m43/run_all
```

All individual gates must repeat before `[M43] YAW90 PASS` and native retraction.
Finally:

```mcfunction
/function sable:m43/cleanup
```

Expect `[M43] CLEANUP COMPLETE footprintClear=true`. Debug families are bounded:
`SABLE_M43_PLOUGH_ENTITY`, `SABLE_M43_DRILL_AIR_ENTITY`,
`SABLE_M43_SAW_AIR_ENTITY`, `SABLE_M43_BLAZE_GAZE`, gated by the existing
`-Dsable.m31.traceCreateActors=true`. Fixture chat and sampled pending summaries
do not require the debug flag.

## Coverage recomputation

Pinned inventory remains **23 concrete classes / 22 registered types /
27 registration sites / 46 block registrations or variants**. The unregistered
concrete BlockBreaking base is included; inherited/variant registrations are not
silently omitted. Categories recompute as A=2, B=2, C=18, D=1, E=0, F=0.

Every class/registration owner/callback hash still matches the accepted Create
jar, including AllBlocks, AllMovementBehaviours, BuilderTransformers and dyed
Seat registrations. M42 remains C/M42. Four rows become C/M43: Plough, Drill,
Saw, Blaze. No additional residual boundary was found in the repeated pinned
callback/sink inventory; the runtime implications of M43 remain to be validated.
`REAL_GAP_FOUND=0` is the manifest-derived **static implementation coverage**,
not a claim of M43/M41 functional runtime closure.

## Verification and production hashes

The independent `verifyM43MovementBehaviourResiduals` sections are:
`PLOUGH_ENTITY_IMPULSE`, `DRILL_AIR_ENTITY_DAMAGE`, `SAW_AIR_ENTITY_DAMAGE`,
`BLAZE_GAZE`. They check production native order/filter/operation counts, exact
packaged injection/refmap matches, geometry/policy regressions, ordinary-world
bypass, fixture/native separation, packaging, aggregate acceptance and cleanup.
Headless model/coordinate checks do not pretend to execute a real Level/entity.
The old M31/M32 block/runtime canaries remain active and unweakened.

Final verification on 2026-10-15: PASS. The complete invocation finished in
1m 56s with 279 actionable tasks (251 executed, 28 up-to-date):

- `spotlessApply`, `spotlessCheck`, `:forge:compileJava`.
- `:forge:verifyM43MovementBehaviourResiduals`: all four independent sections PASS.
- `:forge:verifyCreateMovementBehaviourCoverage`: PASS, manifest-derived F=0.
- `:forge:verifyCreateProductionFixtureBoundary`: PASS, 147 production classes;
  the pre-existing M37 fixture observer remains explicitly isolated.
- Every retained M31-M42 milestone verifier, including M33.1, M35 moving Roller,
  M39 runtime mixin targets, M40 Basin and M42 door/preservation.
- All earlier render/mixin and production-baseline gates invoked by full `build`.
- `:forge:reobfJar`, `:forge:jarJar`, `:forge:reobfJarJar`.
- `:forge:verifyStandaloneForgeArtifact`, Rapier packaging and full `build`.
- `git diff --check`: PASS. No Minecraft launch/runtime PASS is claimed.

Production changes are limited to:

- Existing BlockBreakingMovementBehaviourMixin: the source-proven M43-A/B
  operation boundaries and read-only witnesses; its terrain algorithm is retained.
- New ResidualActorGeometry: rotation-only/reference acceptance math.
- New SableActorEntityEvidence + Event: bounded read-only native observations.
- New SableBlazeGaze + Sample: canonical physical client gaze geometry/evidence.
- New client BlazeBurnerMovementBehaviourMixin: exact subtraction/return hooks.
- SableForge: fixture registration only.

Existing Plough/Saw-specific wrappers, M31 world target resolvers, M32 context
scopes, M37 chest rendering, M42 production door classes and both nested ownership
adapters must remain byte-identical to the pre-M43 packaged baseline. Whole
Sable class hashes were captured before edits in `build/m43-production-before.json`.

The final whole-Sable audit (`build/m43-hash-audit.json`) compares 913 pre-M43
class files to 930 final files: **911 unchanged, 2 changed, 17 added, 0 removed**.
The only changed retained class files are `SableForge.class` (fixture dispatcher)
and `BlockBreakingMovementBehaviourMixin.class` (M43-A/B). Six additions are the
new production helper/mixin classes and their two evidence records; eleven are
fixture/client/network/layout/state classes. No other retained production or
fixture class changed. The M41 aggregate independently compares all retained
non-command `dev/` production classes, not only the Sable-prefixed snapshot.

## Delivery

Artifact:
`C:\Users\camel\Documents\Всякое\CreateAeronautics\sable-backport\forge\build\libs\sable-forge-1.20.1-2.0.0-all.jar`

Size: **12,962,210 bytes**.

SHA-256: `8B7B59CB5934EB67978E905DDF3015EF8BAFCFD09168299B7A785BCDCBBB918F`.

Complete changed-file list:

- `M43_FINAL_MOVEMENT_BEHAVIOUR_RESIDUALS.md`
- `M41_CREATE_MOVEMENT_BEHAVIOUR_CLOSURE.md`
- `buildSrc/src/main/java/CreateMovementCoverageVerifier.java`
- `buildSrc/src/main/java/M43ResidualBytecodeVerifier.java`
- `config/m41-create-movement-coverage.json`
- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/residuals/ResidualActorGeometry.java`
- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/residuals/SableActorEntityEvidence.java`
- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/residuals/SableBlazeGaze.java`
- `common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/block_breaking_behaviour/BlockBreakingMovementBehaviourMixin.java`
- `common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/residuals/BlazeBurnerMovementBehaviourMixin.java`
- `forge/build.gradle`
- `forge/m43-residual-verification.gradle`
- `forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java`
- `forge/src/main/java/dev/ryanhcode/sable/command/M43ResidualFixtureCommands.java`
- `forge/src/main/java/dev/ryanhcode/sable/command/M43ResidualFixtureLayout.java`
- `forge/src/main/java/dev/ryanhcode/sable/command/M43ResidualFixtureNetwork.java`
- `forge/src/main/java/dev/ryanhcode/sable/command/M43ResidualFixtureClient.java`
- `forge/src/m43ResidualTest/java/dev/ryanhcode/sable/compatibility/create/residuals/M43ResidualGeometryTest.java`
- `forge/src/main/resources/data/sable/functions/m43/setup.mcfunction`
- `forge/src/main/resources/data/sable/functions/m43/run_all.mcfunction`
- `forge/src/main/resources/data/sable/functions/m43/yaw90.mcfunction`
- `forge/src/main/resources/data/sable/functions/m43/cleanup.mcfunction`
- `forge/src/main/resources/sable-common-forge.mixins.json`
- `sable-common-forge.mixins.json`

Final runtime status: M43-A / M43-B / M43-C IMPLEMENTED / runtime pending.
MovementBehaviour functional closure remains pending all native runtime gates.
M44 was not started.
