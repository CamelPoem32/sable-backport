# M42: Sliding Door parent-world handoff

Target: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8, Flywheel 1.0.5.
Exact Create commit: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
Implementation/static acceptance is separate from Minecraft runtime acceptance.
M28-M40 accepted runtime proofs remain closed. No Plough, Drill/Saw or Blaze
Burner implementation is changed and no M43 is started.

## Exact native source and bytecode

Class: `com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour`.
Overrides: `mustTickWhileDisabled()` and `tick(MovementContext)`.

One behavior factory site in `BuilderTransformers.slidingDoor()` is used by five
AllBlocks registrations: ANDESITE_DOOR, BRASS_DOOR, COPPER_DOOR, TRAIN_DOOR and
FRAMED_GLASS_DOOR. Train door is a block variant; this does not add train docking.
There is no separate movement implementation for each door variant.

Pinned sources:
[behavior](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/decoration/slidingDoor/SlidingDoorMovementBehaviour.java),
[registration](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/foundation/data/BuilderTransformers.java),
[actor tick](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java).

Native call graph:

```text
AbstractContraptionEntity.tickActors
  actorPosition = toGlobalVector(center(actorLocal) + activeOffset, 1)
  context.position = actorPosition
  context.rotation = applyRotation(..., 1)
  actor.tick(context)
SlidingDoorMovementBehaviour.tick
  read local StructureBlockInfo / currentlyOpen
  server: tickOpen(context, currentlyOpen)
    shouldOpen / shouldUpdate retain native controller, motion, stall and Open-history rules
    toggleDoor(local): CCE.setBlock, paired half update, invalidateColliders
    facing = getDoorFacing(context)
    target = BlockPos.containing(context.position).relative(facing)
    context.world.getBlockState(target)
    native DoorBlock + OPEN + matching FACING axis predicates
    DoorBlock.setOpen(null, context.world, state, target, shouldOpen)
    if opening: native IRON_DOOR_OPEN sound at actor cell
  client: native SlidingDoorBlockEntity animation and close sound
```

Packaged Create `tickOpen` descriptor is
`(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Z)V`.
Relevant verified instructions:

| Offset | Invoke | Matches in tickOpen |
|---|---|---|
| 78 | toggleDoor(local, contraption, info) | 1 |
| 83 | getDoorFacing(MovementContext):Direction | 1 |
| 92, 213 | BlockPos.m_274446_(Position):BlockPos | 2 |
| 97 | BlockPos.m_121945_(Direction):BlockPos | 1 |
| 108 | Level.m_8055_(BlockPos):BlockState | 1 |
| 197 | DoorBlock.m_153165_(Entity,Level,BlockState,BlockPos,boolean):void | 1 |
| 225 | Level.m_5594_(Player,BlockPos,SoundEvent,SoundSource,float,float):void | 1 |

Native getDoorFacing does not simply return state FACING: it chooses the positive
axis direction, compares the actor-center minus 0.45 * state-facing vector against
contraption.bounds.center, flips its sign on the negative side, applies inner
context.rotation, then rounds to Direction.getNearest. M42 preserves this native
outward-side choice; it does not replace it with state FACING.

## Revalidated gap and narrow hook

The inner entity's global point is still SABLE_RAW because its controller lives
in the owner's storage plot. The Level object is already the parent Level, not
a separate SubLevel dimension. Native getDoorFacing applies only inner rotation.
Consequently the original adjacent lookup/setOpen combines a parent Level with
hidden storage coordinates and an outer-unrotated facing. M41's finding is valid.

The new SlidingDoorMovementBehaviourMixin wraps only four tickOpen invocation
families (five actual points), not the whole actor callback or any global API:

1. Native getDoorFacing result -> owning outer rotation -> physical outward cardinal.
2. Both native BlockPos.containing calls -> owning physical actor point, including opening sound.
3. Adjacent getBlockState -> exact physical target validation, or AIR for rejected targets.
4. Native DoorBlock.setOpen -> same validated position/state/open request, plus read-only before/after evidence.

Every hook calls original unchanged if there is no native contraption/owning Sable
or the world is client-side. There is no global Level/Direction/Entity redirect.
MovementContext fields are never changed, so there is no leaked physical scope or
stale transform to restore. Native controller discovery, local door toggle, paired
local half update, block updates, sound/game-event behavior and animation remain
native. This is adjacent-door handoff, not new elevator/train door-controller discovery.

Minecraft-owned @At members remap through sable.refmap.json; Create-owned method
names do not remap. Require/allow counts are exactly 1, 2, 1, 1, never optional.
The verifier checks the packaged SRG targets/refmap and native class fingerprint.

## Spaces, orientation and ownership

```text
DOOR_ACTOR_LOCAL -> native inner toGlobalVector -> SABLE_RAW
 -> current owning logicalPose.transformPosition -> PARENT_VISIBLE
 -> BlockPos.containing -> PARENT_BLOCK
 -> relative(transformed native outward direction) -> adjacent parent door
```

No original raw target is used as a fallback. Rejected orientations can still
close/animate the captured local door, but cannot query/mutate an external door.

Policy: unit outer UP dot world UP > 0.999 AND composed inner/outer UP dot world UP
> 0.999. Full unrounded facing and transformed native outward cardinal must be
horizontal and dot their nearest cardinal > 0.999. This is a deliberately narrow
M42 safety policy (about 2.56 degrees), not a claim that Create defines this numeric
tolerance. Translation and near-cardinal yaw pass; material pitch/roll and
non-cardinal orientation safely skip external handoff. Scale must produce finite,
nonzero normals/positions. Native cardinal/grid semantics are not broadened to 3D doors.

Current owning body and Level identity are required. Parent chunks must be loaded.
Target and both possible paired-half cells are checked: no hidden plot, no other
Sable body intersection. No nearest-door/global search exists. Ordinary parent
doors are supported; own hidden doors and hidden A -> hidden B are rejected;
physical doors on B are safe no-op. Native local captured-door state remains native.

## Read-only diagnostics and acceptance witness

`-Dsable.m31.traceCreateActors=true` gates `SABLE_M42_SLIDING_DOOR`.
TARGET_RESOLVED / MUTATION_ATTEMPT / MUTATION_COMMITTED / unsupported/rejection
events include owner UUID, local/raw/visible actor positions, native inner outward
and parent facing, target, states, hidden-plot decision. Events occur only on native
door transitions, not a per-frame loop. Evidence is one weak-context record,
independent of fixture tags/classes and available even with debug logs disabled.

The native setOpen wrapper observes real state before/after. It neither changes
OPEN itself nor reports a fabricated successful mutation. Fixture PASS requires
the current run's post-arm native invocation, exact expected target/owner, true ->
false OPEN change and a real ordinary parent-world target. No unconditional PASS.
Either native half may initiate the paired local toggle. The fixture observes the
half with actual native evidence, rather than assuming lower-half iteration first;
an upper-half target is normalized down one cell only for station construction.
The acceptance target remains the exact cell passed by native setOpen.

## One-command fixture and lifecycle

```mcfunction
/function sable:m42/cleanup
/function sable:m42/door
```

Wait for READY, click generated Physics Assembler once, then:

```mcfunction
/function sable:m42/run
```

After baseline PASS:

```mcfunction
/function sable:m42/yaw90
/function sable:m42/run
/function sable:m42/cleanup
```

Only one manual assembly click; no glue/block/controller repair. Run native motion
is command-controlled. Fixture-only physics hold uses the existing RigidBodyHandle
API and targets only the unique M42 marker/body; normal Sable physics is untouched.

Relative map, piston origin (0,0,0):

| Relative coordinate | Block |
|---|---|
| (-12..-1,0,0) | east extension poles |
| (0,0,0) | east sticky piston, Y-axis drive |
| (1,0,0) | native radial chassis / support under captured door |
| (1,0,1) | chest (retained proven chassis topology) |
| (1,1,0), (1,2,0) | north-facing open Andesite Door, lower/upper |
| (0,-2,0), (0,-1,0), (0,-1,1) | Creative Motor, clutch, fixture lever |
| (0,0,-2), (0,0,-1), (0,1,-2) | iron hull, Physics Assembler |
| (5,1,-4) plus upper/support | initially parked ordinary parent iron door |

The stationary target is outside native chassis reach during inner assembly. After
capture the fixture stops the real motor/clutch, waits for zero piston speed and
three settled ticks, derives the target from actual native outward evidence and
places the target there transactionally. Eight transformed corners of each payload
block reject a station/payload collision. Own-body broad bounds are not mistaken
for occupied physical blocks; other bodies remain forbidden.

The relocated parent door is displayed open for 40 ticks while the captured
mechanism stays stopped, before the native-close trial. Fixture-only arming then
sets the captured door's initial OPEN state and clears its
native Open-history key. This is a deterministic native initial-close test, not a
custom production open/close controller or a forged target mutation. Create's next
tick runs shouldOpen/shouldUpdate/toggleDoor/setOpen; the parent door is changed
only by that native path. Both halves are seeded. No train/elevator controller is
invented. Yaw preserves the captured vehicle, resets finite test conditions and
relocates the parent target using current pose. +90 JOML yaw maps local +X to -Z;
the fixture's native outward NORTH becomes WEST, not NORTH hardcoded from PSI.

Origin search: existing bounded deterministic 27-candidate M38 offsets, loaded
chunks only; exact mechanism/short motion/yaw footprint above normal ground.
SELECT -> marker creation/verification -> BUILD -> native attachment/selection
VALIDATE -> READY. Rollback checks placed cells before declaring COMPLETE.

One marker/body/controller resolver is used by all commands and scheduled ticks.
Inner lookup uses controller reference + exact controller/owner/type, not a bare
parent UUID getter. Persistent marker data stores origin, dimension, body UUID,
current parent station and yaw. Ambiguous markers/bodies refuse; uniquely owned
partial/static/orphan states remain cleanup-capable. Cleanup removes validated
station cells, reverses native piston with returned placement, re-resolves after
outer disassembly, then checks/removes only expected deterministic blocks/glue.
Timeout/error removes the Java task, not ownership, so cleanup can resume.

Expected machine-derived markers:

```text
[M42] SETUP phase=READY
[M42] ACTOR_CAPTURED ... doorHalves=2
[M42] TARGET_RESOLVED ... expectedTargetBlock=... hiddenPlotTarget=false
[M42] MUTATION_COMMITTED ... open=true -> open=false
[M42] actorCaptured=true nativeMutation=true ... physicalTarget=true hiddenPlotTarget=false PASS
[M42] YAW90 COMPLETE ...
[M42] CLEANUP COMPLETE
```

## Coverage and verification

23 concrete classes / 22 registered types / 27 sites / 46 variants unchanged.
Sliding Door is now C / COVERED_BY_M42 (runtime pending). Remaining F rows are
Plough, Drill, Saw and Blaze Burner: four rows / three residual families.
Overall functional coverage remains OPEN; no M43 is started.

verifyM42SlidingDoorParentWorld checks native class/registration fingerprint,
packaged four handler families and match counts, SRG refmap, transformed geometry,
orientation rejection, scoped native bypass/target guards, fixture resources and
transaction/lifecycle source invariants. Pure tests cover translation, inner offsets,
retraction, yaw90, near-cardinal tolerance, rejected pitch/roll, invalid observations
and exact fixture footprints. Tests are static, not claimed live game execution.

The aggregate verifier retains M31-M40 gates and permits only the explained M42
production additions plus SableForge's fixture install/register calls against the
M41 hash baseline. No other retained class changes are allowed. Packaged mixin
manifest is explicitly refreshed for this new required mixin; old checks remain.

## Final static/build result

PASS: spotlessApply, spotlessCheck, compileJava, verifyM42SlidingDoorParentWorld,
verifyCreateMovementBehaviourCoverage, verifyCreateProductionFixtureBoundary,
all retained M31-M40 verifiers (including Roller rendering and M39 runtime mixin
targets), retained render/mixin-target gates, verifySableProductionBaseline,
reobfJar, jarJar, reobfJarJar, verifyStandaloneForgeArtifact, full build and
git diff --check. Final full-chain confirmation: BUILD SUCCESSFUL in 35s,
278 actionable tasks (222 executed, 56 up-to-date).

The packaged aggregate has 204 structural checks across 76 mixins and 299
fingerprints. These are bytecode/refmap checks, not a claim that Minecraft or a
complete Mixin transformation harness was run. Production/fixture boundary checks
cover 139 production classes and retain the existing isolated M37 observer rule.

Hash comparison against the M41 baseline: 746 retained production classes are
unchanged; none removed. The only changed existing class is SableForge, solely for
M42 fixture installation/command registration. Five new classes are the Sliding
Door mixin and SableSlidingDoorTarget with its three nested records/enums. Plough,
Drill, Saw, Blaze Burner and closed gameplay/render implementations are unchanged.

Artifact:

```text
forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar
bytes: 12893145
SHA-256: 8A4795721569655D0AB3CB2A805BC4CADCA0F025236C63B2A72A43FAB0A81B5A
```

## Changed files

M42 changes, relative to the accepted M41 workspace:

- common/src/main/java/dev/ryanhcode/sable/compatibility/create/sliding_door/SableSlidingDoorTarget.java
- common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/sliding_door/SlidingDoorMovementBehaviourMixin.java
- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureCommands.java
- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureLayout.java
- forge/src/m42SlidingDoorTest/java/dev/ryanhcode/sable/compatibility/create/sliding_door/M42SlidingDoorGeometryTest.java
- forge/src/main/resources/data/sable/functions/m42/door.mcfunction
- forge/src/main/resources/data/sable/functions/m42/run.mcfunction
- forge/src/main/resources/data/sable/functions/m42/yaw90.mcfunction
- forge/src/main/resources/data/sable/functions/m42/cleanup.mcfunction
- forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java
- forge/src/main/resources/sable-common-forge.mixins.json
- forge/build.gradle
- config/m41-create-movement-coverage.json
- M41_CREATE_MOVEMENT_BEHAVIOUR_CLOSURE.md
- M42_SLIDING_DOOR_PARENT_WORLD.md

The working tree also contains the pre-existing untracked M41
buildSrc/src/main/java/CreateMovementCoverageVerifier.java; M42 did not edit it.

Minecraft was not launched. M28-M40 remain CLOSED / PASS; M41 audit remains PASS.
M42 is IMPLEMENTED / runtime pending. Functional coverage is OPEN with Plough
entity impulse, Drill/Saw air-cell damage and Blaze Burner gaze still uncovered.
No M43 is started.
