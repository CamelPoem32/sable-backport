# M41: Create MovementBehaviour audit and consolidation

Target: Minecraft 1.20.1 / Forge 47.4.20 / Create 6.0.8 / Flywheel 1.0.5.
Exact Create source commit: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
M28-M40 remain closed according to the user's accepted runtime history. This is
an audit/verifier/docs-only milestone. No production changes, fixtures or M42
implementation are included. **Audit completion is not functional coverage
completion: five affected behavior rows across four residual callback families
are identified below.**

## Recomputed inventory and evidence

Source was fetched from the exact commit, independently of the historical M34
matrix. ASM recursively follows the MovementBehaviour interface through every
class in the pinned production Create jar, including anonymous and inherited
implementations. The accepted machine inventory is
`config/m41-create-movement-coverage.json`; the reproducible detailed callback,
field and invocation report is `forge/build/m41Audit/create-inventory.json`.

The historical 22 is the number of distinct **registered concrete behavior
types**, not every concrete implementation: BlockBreakingMovementBehaviour is
also a concrete, unregistered superclass. There are **23 concrete implementations,
22 registered behavior types, 27 registration instruction sites and 46 affected
block registrations/variants**. There are no extra anonymous MovementBehaviours.
The numbers have no upstream drift; the one-class difference corrects the old
count's definition rather than a change in the target jar.

Registration owners: AllBlocks (21 sites), AllMovementBehaviours.registerDefaults
(5), BuilderTransformers.slidingDoor (1). AllBlocks has 20 direct block registrations
plus the seat helper applied to all 16 dye colors. SlidingDoor's transformer is
applied to five doors. Thus 20 + 16 + 5 + 5 = 46. Mechanical Bearing registers
StabilizedBearingMovementBehaviour; it is not a separate actor behavior class.
Soul campfire is an independent default registration, not an uncounted alias.
Funnel and PSI/PFI share behavior classes. Belt funnels, empty blaze burners,
windmill bearings and tank variants without these registrations are not silently
counted as movement actors.

Primary pinned source links:
[MovementBehaviour interface](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/api/behaviour/movement/MovementBehaviour.java),
[AllBlocks](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/AllBlocks.java),
[default registrations](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/AllMovementBehaviours.java),
[door transformer](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/foundation/data/BuilderTransformers.java).

## Authoritative classification

A = NO_EXTERNAL_BOUNDARY; B = ALREADY_NATIVE_SAFE (existing generic Sable support);
C = COVERED_BY_M28-M40, within the documented scope; D = TRAIN_ONLY / OUT_OF_SCOPE;
E = SAFE_NO_OP_BY_POLICY; F = REAL_GAP_FOUND. A behavior with a genuine residual
callback is F even when its already-tested main operation remains covered.
Policy restrictions within a C row are explicitly listed, not claimed as broad
support for every topology or item.

| Behavior | Registration / blocks | Relevant overrides / inherited callbacks | Effects / coverage owner | Category |
|---|---|---|---|---|
| BasinMovementBehaviour | AllBlocks BASIN | tick; dump helper/lambda | ItemEntity, BE-NBT inventories, M40 Basin wrapper | C |
| BellMovementBehaviour | defaults BELL; AllBlocks PECULIAR_BELL, DESK_BELL | isActive, tick, onSpeedChanged, stopMoving | sound; generic packet distance + moving sound delegate | B |
| BlazeBurnerMovementBehaviour | AllBlocks BLAZE_BURNER | tick, canBeDisabledVia, disableBlockEntityRendering, renderInContraption; invalidate helper | particles safe generically; camera-angle boundary uncovered | F |
| CampfireMovementBehaviour | defaults CAMPFIRE, SOUL_CAMPFIRE | tick | client smoke; generic particle projection | B |
| ContactMovementBehaviour | AllBlocks REDSTONE_CONTACT | active offset, visitNewPosition, stopMoving, cancelStall | contact query/power/scheduled reset; M34 | C |
| ContraptionControlsMovement | AllBlocks CONTRAPTION_CONTROLS | startMoving, stopMoving, tick, renderInContraption, canBeDisabledVia | local filter/disabled state, client animation/elevator floor UI | A |
| ControlsMovementBehaviour | AllBlocks CONTROLS | tick, rendering/disable/control overrides | train controls; carriage-only operation | D |
| DeployerMovementBehaviour | AllBlocks DEPLOYER | active offset, visitNewPosition, tick, cancelStall, stopMoving, writeExtraData, BER/visual | FakePlayer/native use/attack/storage; M33; overflow generic entity kick | C |
| DispenserMovementBehaviour | defaults DISPENSER | getDispenseBehavior; inherits Dropper visit | native mounted registry and stack mutation; M38 restricted catalogue | C |
| DrillMovementBehaviour | AllBlocks MECHANICAL_DRILL | isActive, offset, canBreak, CPU/visual; inherits breaker callbacks | M31 blocks/drops proven; air-cell entity-damage callback suppressed | F |
| DropperMovementBehaviour | defaults DROPPER | visitNewPosition; slot/top-off/failure helpers | native mounted stack selection/drop/events; M38 | C |
| FluidTankMovementBehavior | AllBlocks FLUID_TANK | tick, mustTickWhileDisabled | client-local BE fluid-level chaser; mounted fluid storage separate | A |
| FunnelMovementBehaviour | AllBlocks ANDESITE_FUNNEL, BRASS_FUNNEL factories | active offset, visitNewPosition; extract/succ helpers | parent item/package query/emission and native mounted storage; M37 | C |
| HarvesterMovementBehaviour | AllBlocks MECHANICAL_HARVESTER | isActive, offset, visitNewPosition, CPU/visual/BER | crop query/replant/native drops; M32 | C |
| HauntedBellMovementBehaviour | AllBlocks HAUNTED_BELL | tick, visitNewPosition, writeExtraData; inherits Bell sound/speed/stop | parent pulse and persisted recharge; M34 + generic sound path | C |
| PloughMovementBehaviour | AllBlocks MECHANICAL_PLOUGH | isActive, visitNewPosition, offset, canBreak, stopMoving; throwEntity/onBlockBroken | M32 terrain/drop proven; parent entity impulse frame uncovered | F |
| PortableStorageInterfaceMovement | AllBlocks PORTABLE_STORAGE_INTERFACE, PORTABLE_FLUID_INTERFACE | offset, visitNewPosition, tick, stopMoving, cancelStall, CPU/visual/BER | current candidate/facing, stall, native mounted capabilities; M36 | C |
| RollerMovementBehaviour | AllBlocks MECHANICAL_ROLLER | offset, visitNewPosition, canBreak, destruction/paving helpers, CPU/visual; inherited breaker tick | terrain/mounted materials; M35 + M35.6 rendering | C |
| SawMovementBehaviour | AllBlocks MECHANICAL_SAW | isActive, offset, visitNewPosition, canBreak, onBlockBroken, CPU/visual; inherited breaker | M32 tree/drop/parent cells proven; air-cell entity-damage callback suppressed | F |
| SeatMovementBehaviour | AllBlocks seat helper, all 16 colors | startMoving, visitNewPosition; CCE rider/interaction lifecycle | native riding, physical transforms/dismount; M39 controlled CCE only | C |
| SlidingDoorMovementBehaviour | BuilderTransformers, ANDESITE/BRASS/COPPER/TRAIN/FRAMED_GLASS_DOOR | tick, mustTickWhileDisabled; tickOpen/door-control/facing helpers | local door state native; adjacent-world handoff covered by M42 (runtime pending) | C |
| StabilizedBearingMovementBehaviour | AllBlocks MECHANICAL_BEARING | canBeDisabledVia, BER disable, CPU/visual | render counterrotation; M28 nested bearing ownership/render architecture | C |
| BlockBreakingMovementBehaviour | no direct registration; concrete superclass | startMoving, visitNewPosition, tick, cancelStall, stopMoving; damage/throw/destroy/drop helpers | shared M31/M32/M35 hooks; Plough impulse exception above | C |

Interface defaults are independently audited: isActive, tick, startMoving,
visitNewPosition, getActiveAreaOffset, canBeDisabledVia, onDisabledByControls,
mustTickWhileDisabled, dropItem, onSpeedChanged, stopMoving, cancelStall,
writeExtraData, disableBlockEntityRendering, renderInContraption and createVisual.
There is no readExtraData callback in this target interface. Bytecode reports
include all non-callback helpers, constructors and lambda handles, not only visit.
Inherited callbacks remain in the inventory through explicit superclass links.

Exact declared interface overrides recomputed from ASM (all other callbacks are
inherited from the recorded superclass or audited interface defaults):

| Type | Declared overrides |
|---|---|
| ContraptionControlsMovement | canBeDisabledVia, startMoving, stopMoving, tick, renderInContraption |
| HarvesterMovementBehaviour | isActive, getActiveAreaOffset, visitNewPosition, disableBlockEntityRendering, renderInContraption, createVisual |
| PloughMovementBehaviour | isActive, visitNewPosition, getActiveAreaOffset, stopMoving |
| PortableStorageInterfaceMovement | getActiveAreaOffset, disableBlockEntityRendering, createVisual, renderInContraption, visitNewPosition, tick, stopMoving, cancelStall |
| RollerMovementBehaviour | isActive, disableBlockEntityRendering, createVisual, renderInContraption, getActiveAreaOffset, visitNewPosition |
| SeatMovementBehaviour | startMoving, visitNewPosition |
| ControlsMovementBehaviour | canBeDisabledVia, stopMoving, tick, renderInContraption |
| StabilizedBearingMovementBehaviour | canBeDisabledVia, disableBlockEntityRendering, renderInContraption, createVisual |
| BellMovementBehaviour | isActive, tick, onSpeedChanged, stopMoving |
| CampfireMovementBehaviour | tick |
| DispenserMovementBehaviour | none; inherits Dropper, overrides getDispenseBehavior helper |
| DropperMovementBehaviour | visitNewPosition |
| SlidingDoorMovementBehaviour | mustTickWhileDisabled, tick |
| HauntedBellMovementBehaviour | tick, visitNewPosition, writeExtraData |
| FluidTankMovementBehavior | mustTickWhileDisabled, tick |
| BlockBreakingMovementBehaviour | startMoving, visitNewPosition, cancelStall, stopMoving, tick |
| DeployerMovementBehaviour | getActiveAreaOffset, visitNewPosition, tick, cancelStall, stopMoving, writeExtraData, disableBlockEntityRendering, renderInContraption, createVisual |
| DrillMovementBehaviour | isActive, getActiveAreaOffset, disableBlockEntityRendering, renderInContraption, createVisual |
| SawMovementBehaviour | isActive, getActiveAreaOffset, visitNewPosition, disableBlockEntityRendering, createVisual, renderInContraption |
| FunnelMovementBehaviour | getActiveAreaOffset, visitNewPosition |
| BasinMovementBehaviour | tick |
| BlazeBurnerMovementBehaviour | canBeDisabledVia, tick, disableBlockEntityRendering, renderInContraption |
| ContactMovementBehaviour | getActiveAreaOffset, visitNewPosition, stopMoving, cancelStall |

## Real residual boundaries, not reopened runtime claims

### Adjacent sliding-door handoff

**M42 follow-up:** this M41 finding is now implemented by the narrow tickOpen
invocation bridge. The diagnosis below is retained as historical evidence, not
an unresolved F row. See `M42_SLIDING_DOOR_PARENT_WORLD.md`. Remaining F rows:
Plough, Drill, Saw and Blaze Burner (four rows / three residual families).

Exact `tick -> tickOpen` first toggles its local door, then computes
`BlockPos.containing(context.position).relative(getDoorFacing(context))`, calls
`context.world.getBlockState`, and can invoke `DoorBlock.setOpen`. getDoorFacing
applies only context.rotation. These inputs are SABLE_RAW and inner-only direction.
No packaged Sable SlidingDoorMovementBehaviour hook supplies a visible cell or
outer facing. The old M34 claim that this behavior only changes local blocks was
incorrect. A previously-open generic captured door closing on its initial update
can exercise the adjacent-world lookup even outside trains; elevator door control
is another source-backed route. The query can select a hidden hull-adjacent door,
not the intended physical parent door. This is not an optional sound discrepancy.

[Pinned sliding-door source](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/decoration/slidingDoor/SlidingDoorMovementBehaviour.java).

### Plough entity impulse

Exact `Plough.visitNewPosition -> super.visitNewPosition -> damageEntities ->
Plough.throwEntity -> super.throwEntity -> Entity.setDeltaMovement` targets a
parent entity using the transformed cell, but constructs its boost from raw
context.motion. M32's visit wrapper does not enter ParentSpaceScope for this
call; its drop-only onBlockBroken scope is unrelated. Under outer yaw, inner +X
motion can therefore boost a physical entity along old +X rather than rotated
-Z. Global moveRelative/arrow-hit hooks do not intercept this method. Position
safety and terrain hoe/break/drop runtime proofs remain intact; the untested
entity throw vector is a separate concrete uncovered sink.

[Pinned breaker source](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour.java).

### Drill/Saw air-cell entity damage

Native BlockBreakingMovementBehaviour.visitNewPosition reads the visited state,
calls damageEntities when it is not a redstone conductor, and only AFTER that
tests canBreak. Drill and Saw supply non-null damage sources. The current Sable
wrapper calls findExternalTarget with canBreak as its candidate predicate; if
there is no breakable block it returns before invoking the native visit. Thus an
air-cell visit that should inspect parent entities never reaches the native
query. Generic contraption collision/run-over damage is a different operation,
not evidence of an equivalent actor-specific cell query. No policy explicitly
declares this damage callback unsupported. This is F for these two subclasses,
not evidence against their closed block/tree/drop runtime proof.
Packaged BlockBreakingKineticBlockEntity.isBreakable bytecode explicitly tests
AirBlock and returns false before its remaining block checks; the air-cell
counterexample does not depend on guessing the canBreak result.

### Blaze Burner client gaze

Exact tick/getHeadAngle/getTargetAngle subtracts raw context.position from the
parent camera Entity.position, then applies only inner CCE reverseRotation.
Particles are correctly kicked/projected by existing generic hooks, but this
camera-relative direction calculation has no corresponding inverse outer pose.
The head gaze can point incorrectly for a heated moving burner. This is a
render-animation boundary, not a missing fire/storage mechanic or giant PoseStack
translation. No production fix is included in M41.

[Pinned burner source](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/processing/burner/BlazeBurnerMovementBehaviour.java).

## Sink and transform-ownership ledger

All actor-class call instructions, field reads and invokedynamic helper handles
are enumerated in the machine report, including SRG names and full descriptors.
The pinned class fingerprints cover callback additions, helper changes and inherited
implementations; the pinned whole Create artifact SHA is also checked by the
existing dependency gate. Semantic sink ownership follows below.

| Family / sinks | Input -> output | Owner / activation / bypass |
|---|---|---|
| Drill/Saw: block state, collision shape, breaking progress, destroyBlockAs/tree queue, drops, entity damage AABB | local -> inner raw point/eight-corner bounds -> visible parent cell | SableCreateActorWorldContext + CreateActorTargetGeometry + SubLevelBlockBreakingUtility + breaker/Saw mixins; specific actor + native contraption + body; null owner calls original; air-cell damage visit remains F |
| Harvester: crop validation, destroy/replant, survival, default drops | raw active point -> parent cell; drop context temporarily visible | HarvesterMovementBehaviourMixin + shared point resolver; native ItemHandlerHelper and BlockHelper unchanged |
| Plough: soil below target, UseOnContext/hoe, falling block/drop, entity throw | parent target correct; impulse remains raw (F) | Plough mixin + breaker routing; no claim that entity impulse is covered |
| Deployer: shouldActivate, schematic snapshot/place, fake-player ray/use/attack, progress, inventory refill/disposal | active raw -> visible target; activate temporarily sees physical position/motion/rotation | Deployer mixin + SubLevelDeployerInteractionUtility + DeployerHandler mixin; normal native refill. Excess fallback drop is kicked at addFreshEntity before entity-manager insertion |
| Contact: getBlockState, setBlockAndUpdate, scheduleTick, elevator floor broadcast | raw visited -> parent visible cell; composed facing; stored lastContact is physical | M34 Contact mixin; cancel/stop schedule stored physical target |
| Haunted Bell: pulse chunk/packet/entity effect, sound | raw visited -> physical pulse cell | M34 HauntedBell mixin; native radius remains native; inherited sound uses existing generic projection |
| Roller: multi-cell clearance, destroy, pave/material extraction, sounds/effects | composed physical footprint + world-up parent terrain | SableRollerTerrain + Roller/breaker hooks; unsupported orientation returns no footprint |
| PSI/PFI: block/BE candidate, facing/distance, WorkingPos keepalive, item/fluid capability | current raw active -> physical search cell/facing | SablePortableInterfaceTarget + PSI movement mixin; native startTransferringTo/timers/capabilities; never persistent Sable capability wrapper |
| Funnel: collision query, ItemEntity/PackageEntity AABB, spawn, filter/world context, sound, mounted insert/extract | current raw active -> parent cell/physical emitted point | SableMovingFunnelTarget + Funnel movement mixin; native methods own amounts, filters, discard and inventory writes |
| Dropper/Dispenser: mounted slot, MountedDispenseBehavior registry, front cell, adapted BlockSource, item/projectile/fire, events | raw source -> visible source/facing and composed context | SableMountedDispenserTarget + Dropper movement mixin; original selected behavior runs, restricted catalogue only |
| Seat: solid-cell lookup, Entity position/teleport/riding, passenger packet, dismount floor/AABB | seat local -> inner -> outer physical passenger; loaded parent safe-floor search | SableSeatPassengers + Seat mixins/physical interaction; controlled CCE, same Level parent LivingEntity, no cross-body rider adoption |
| Basin: ItemEntity constructor/addFreshEntity/velocity, BE inventory NBT, client contents | raw point + inner UP -> physical point + composed UP | M40 Basin tick scope + read-only SableBasinOutput; native dump/clear/serialize owns data |
| Bells/door sounds: playSound/playLocalSound | plot position -> physical broadcast-distance and SoundEngine delegate | existing plot.PlayerListMixin, sublevel_sounds.ClientLevelMixin/SoundEngineMixin; ordinary sound unchanged |
| Campfire/burner smoke and native actor particles | plot position -> physical particle, rotated velocity | particle LevelRenderer distance check + ParticleEngine.add -> initialKickOut; not fake actor render models |
| Generic overflow ItemEntity | raw temporary constructor -> physical before server insertion | entity_kicking.ServerLevelMixin + EntitySubLevelUtil.kickEntity; item not retained plot entity; no double projection when source already visible |
| Tank/control local BE lookup, local door toggling, disabled state, recharge, actor serialization | local -> contraption-local storage/render data | native storage world; not parent-world mutation. SlidingDoor's SECOND world-door leg is the F exception |

Level identity alone does not decide a coordinate domain: nested CCE entities and
MovementContext.world use the parent Level, while their controller/position may
be plot-indexed. The shared ParentSpaceScope and ParentInteractionScope restore
all fields in close/finally. Target state/caches are revalidated by actual current
pose; no new global Level/Entity mutation interception is introduced here.

## Orientation policy (current code, not broadened)

| Family | Translation/yaw | Material pitch/roll |
|---|---|---|
| Drill/Saw | transformed point/eight-corner volume; arbitrary yaw | geometric target supported; native closest-direction/active predicates still apply; no blanket pitch gate |
| Harvester/Plough terrain | transformed active cell; arbitrary yaw | native parent-grid crop/soil-below semantics, not tilted farmland; Plough entity impulse remains F |
| Deployer | full transformed ray/direction; native closest face | full geometric ray; native grid/place rules remain authoritative |
| Roller | arbitrary level yaw | SAFE_NO_OP unless outer UP dot world UP > 0.999; physical facing must satisfy native footprint rules |
| PSI/PFI, Funnel, Dispenser | level nearest-cardinal facing within native error envelope | SAFE_NO_OP: visible UP.y must exceed 0.999; unit facing/cardinal squared error <= 0.5 |
| Seat | full pose composition; species/world-up passenger-height correction | no artificial pitch rejection; native physical safe-floor dismount search |
| Basin | full point/direction composition; native nearestDirection(UP)==DOWN triggers dump | full rotation is necessary for native inversion; no invented fluid spill or cardinal-yaw gate |
| Contact | full direction transform -> native nearest cardinal | no independent pitch gate; native opposite-facing comparison |
| Bell/campfire particles | generic physical projection | generic pose/particle transform; sound follows physical pose |
| Sliding Door / burner gaze | outer facing/camera transform absent on identified leg | F, not silently quantized or claimed safe |

UP dot 0.999 corresponds to approximately 2.56 degrees for unit scale. PSI's
0.5 squared direction error is the exact native tolerance (about 41.41 degrees
from the nearest cardinal for unit vectors), not an invented tighter yaw gate.
These policies differ deliberately by world-grid versus geometric interaction.
No thresholds or support envelopes were changed in M41.

## Cross-Sable policy

| Family | A -> parent | A -> own nested state | A -> B physical surface | hidden A -> hidden B |
|---|---|---|---|---|
| Breakers/terrain/Deployer | covered physical resolver | own mounted storage remains native; external resolver is not internal transfer | reject different-body target intersection | rejected raw-plot target |
| Roller | physical footprint | own mounted material storage | reject footprint different-body overlap | no raw fallback |
| PSI/PFI | moving -> stationary parent only | native same-world path where eligible, not claimed arbitrary pairing | unsupported/no pair discovery | explicitly rejected |
| Funnel | physical parent items/packages | own native mounted storage | no cross-body inventory architecture; different-body target-cell overlap rejected | raw entity candidates filtered |
| Dispenser | audited parent source/front | own mounted inventory | source/front different-body overlap rejected | raw source/front rejected |
| Seat | parent LivingEntity/native relation | raw logical passengers outside bridge | tracked-other-body mount rejected; arbitrary cross-body transport not supported | no nearest-hidden-seat search |
| Basin/generic emitted entities | parent entity | actor NBT/mounted owner unchanged | no direct cross-body block mutation; physical entities may later collide normally | spawn projection is owning pose, not nearest physical body |
| Sound/particles | physical effect | no inventory boundary | normal physical audiovisual overlap, not block transfer | plot ownership resolves effect before display |
| Sliding Door | missing handoff (F) | local toggle native | unimplemented | raw adjacent-door query is a real unresolved risk, not a promised no-op |

Physical parent ItemEntities have no immutable Sable-body inventory ownership;
normal later entity collision/pickup is distinct from hidden plot pairing.
Do not confuse broad assertions about SAFE_NO_OP with native pulse radii or later
physical entities entering another body's area. Those remain normal physics/effects.

## Entity ownership and lifecycle

| Entity | Level / manager | Lookup / UUID / teardown |
|---|---|---|
| ItemEntity output/pickup | parent ServerLevel manager after physical spawn/kick; client counterpart | parent UUID lookup/physical AABB; native stack update/discard; no duplicate Sable entity |
| Arrow/projectile | parent ServerLevel | native mounted behavior, parent spawn/query; native projectile lifetime; fixture UUID tags are not production ownership |
| DeployerFakePlayer | parent ServerLevel reference, private context temporaryData, not a second world inventory | native getPlayer; inventory saved to BE data on stop; native discard; M33 physical interaction scope |
| Seat player/pig | parent server/client Level; parent entity manager | native passenger relation and UUID-seat mapping; M39 detaches before inner/outer teardown, refuses vetoed teardown |
| ControlledContraptionEntity | parent Level object; logically owned by SubLevel plot/controller; entity section visibility may differ from a simple UUID getter | authoritative controller + UUID + inclusive semantic search, not assumed parent getEntity alone; recreated lifecycle may create new instance/UUID; rebind only proven controller/seat identity |
| Outer Sable body/container | ServerSubLevelContainer holds bodies for a parent Level; ServerSubLevel is a PhysicsPipelineBody, not a Minecraft Entity or Level | Sable body UUID/container/controller ownership, not vanilla entity-manager ownership; sublevel removal isn't a separate Minecraft dimension transfer |

M39's resolver deliberately combines controller reference, parent lookup and
inclusive iteration, then checks actual controller, body, native piston, seat and
visible pose. A ServerSubLevel is not a Minecraft Level. No fixture class is used
by Seat production helpers to implement riding. Logout/death vanilla relation
cleanup remains native; outer detach checks existing passengers, not stale fixture
Java objects. UUID is stable for a live instance, not promised stable across native
disassembly/recreation. Server authority and native passenger packets are retained.

## Mounted storage and teardown audit

- Deployer: native getPlayer inventory + native tryGrabbingItem/tryDisposeOfExcess;
  no Sable pump. The refill trace only reads native counts. Excess fallback goes
  through native default drop and generic kick; it is not a custom stack copier.
- Funnel: ItemHelper extraction / ItemHandlerHelper insertion operate on the real
  mounted aggregate; native ItemEntity/PackageEntity remainder handling owns changes.
  There is no persistent parent block capability cache in this movement behavior.
- Dropper/Dispenser: captured MountedItemStorage; native slot/top-off/copy/behavior
  remainder/setStackInSlot; audit gate does not substitute the behavior instance.
  M38 supports default output, arrow and flint-and-steel fallback. Other registered
  behavior families remain SAFE_NO_OP_BY_POLICY; buckets, eggs, TNT, equipment,
  vehicles, shears/bottles are not silently promoted to supported.
- PSI/PFI: native mounted ItemHandler/FluidHandler connected capabilities, timers,
  keepalive and reset; no direct transfer helper or inventory duplication.
- Basin: InputItems/OutputItems actor BE NBT; native inversion clears/serializes
  once. Basin is not a parallel fallback-mounted inventory. Fluids have no native
  movement dump and remain NO_MOVEMENT_OUTPUT.
- Breakers/harvester/tree/plough/roller: native drop/material operations; helper
  scopes change geometry, not ItemStacks. Stored break/last-contact/WorkingPos
  coordinates are physical and retarget/reset when pose changes.
- Inner/native stop/disassembly/reload, M28 outer transaction/snapshot/restore and
  M39 detach retain their existing closed gates. No direct deletion of unknown CCE
  or production adoption of fixture recovery tags. M40 native one-shot dumped
  contents restore empty rather than resurrecting a second inventory.

No new orphan, duplication or lost-storage path was proven by this audit. This is
source/bytecode reasoning backed by retained runtime proofs, not a new world-reload
test for every possible modded inventory/entity implementation.

## Rendering and goggles (separate from gameplay)

Ordinary block rendering, specialized BE rendering (M17/M20), mechanical bearing
top (M28), nested CPU contraption bridge (M13/M14), moving Deployer/Roller/PSI
partials and mounted Chest BER remain unchanged. Their packaged target members
and retained verifiers are checked. Scope is owning Sable context only: Flywheel
is not disabled globally, bounds are not inflated, culling is not globally bypassed.
The nested bridge composes small camera-relative outer pose, inner native pose,
then actor local offset; storage positions may be used for light/state queries,
not giant PoseStack translation/cancellation. Seat uses native passenger renderer
at physical entity positions. No custom player/chest/Basin model is introduced.

The Blaze Burner gaze finding concerns the input to an animation angle; it does
not mean every moving burner model is invisible or that M14's PoseStack invariant
failed. Existing hooks are not removed just because a recent fixture omitted them.

Create GoggleOverlayRenderer.renderOverlay's packaged bytecode first performs
INSTANCEOF BlockHitResult before its CHECKCAST (offsets 40 and 48). No retained
Sable mixin targets GoggleOverlayRenderer. Sable clip methods return BlockHitResult;
the Seat interaction bridge handles CCE rays without replacing this local guarded
cast. The supplied generic ClassCastException has no exact transformed stack or
callback owner proving Sable responsibility. **BACKLOG_ONLY**, separate UI/raycast
investigation; no goggle fix is folded into M41.

## Diagnostics and fixture boundary

Retained diagnostic families are classified here from the source audit,
including older non-actor infrastructure.

| Families / flags | Classification |
|---|---|
| assembly/disassembly failure, transaction refusal/rollback, invariant corruption errors | KEEP_PRODUCTION; actionable transition/error logs |
| M10/M14 rendering; M11 runtime/block edits; M13; M20; M22 assembly; M24; M28 steering | KEEP_DEBUG_GATED via SableDiagnosticFlags flags |
| SABLE_M31_CREATE_ACTOR, Deployer mounted trace, M35 Roller render, M36 PSI, M37 Funnel/Chest, M38 Dispenser, M39 Seat, M40 Basin | KEEP_DEBUG_GATED via sable.m31.traceCreateActors; bounded transition/heartbeat/weak-context evidence |
| M28-M36 nested ownership, registration/transfer and normal-world CCE sync diagnostics | KEEP_DEBUG_GATED where gated; failure/refusal records KEEP_PRODUCTION |
| command-only M10-M40 setup/geometry/material/ownership/cleanup and exception reports | FIXTURE_ONLY, explicit command/task lifecycle; setup-time reports may be unconditional |
| FunnelFixtureTimingMixin | FIXTURE_ONLY read-only native succ observer; explicit boundary exception, not a gameplay resolver |
| obsolete high-volume instrumentation | OBSOLETE_REMOVE: none proven safe to remove in this pass |

No diagnostic production bytecode was removed or rewritten. Some older first-seen
renderer key sets are unbounded over a very long debug-enabled session: this is a
future bounded-debug cleanup candidate, not justification to remove required render
fallback hooks. Debug flags are off by default; failure logs intentionally remain.
An explicit legacy exception is SABLE_M19_GHOST: an unconditional first-target
message in the functional placement-ghost renderer. It is KEEP_PRODUCTION pending
separate bounded-log review, not misclassified as debug-gated.

verifyCreateProductionFixtureBoundary scans packaged compatibility/milestone
classes for fixture command/state/tag references. The one intentionally packaged
M37 FunnelFixtureTimingMixin calls only observeNativeSucc; it is explicitly treated
as fixture instrumentation, not allowed as an implicit exception for gameplay code.
All gameplay compatibility classes must be independent of fixtures. Fixture code
may call production helpers. This classification is transparent rather than claiming
there are zero fixture references anywhere in the jar.

Reusable infrastructure: M35 block/BE lookup, native controls/assembly transaction,
M38 loaded-origin reservation and pure layout decisions, M39 semantic vehicle
identity, native cleanup state phases. Future candidates: shared marker metadata,
qualified-before-ambiguity lookup, origin search, cleanup diagnostics and rollback
footprint scans. Historical stale-marker recovery remains fixture-only, necessary
for old saved test worlds; no giant framework refactor or automatic deletion.

## Closed-milestone ledger

Runtime proof below is accepted project history supplied by the user, not a new
Minecraft execution during M41. Historical implementation docs may retain their
original pre-runtime labels; they are not used to erase later accepted evidence.

| Milestone | Feature / runtime proof | Production owner / fixture owner | Caveat / status |
|---|---|---|---|
| M28 | nested bearings, steering, rendering/lifecycle | SableNestedBearingOwnershipTransfer and bearing hooks / M28 command suite | static discovery steering-gated; live snapshot path; CLOSED |
| M29 | parent/sublevel entity query and rendering ownership | entity query helpers / M29 tests | no arbitrary cross-body transfer; CLOSED |
| M30 | production baseline consolidation | retained boundary gates / existing regression commands | no new actor behavior; CLOSED |
| M31 | Drill parent block mutation | breaker target/geometry/mixin / M31CreateActor commands | air-cell entity-damage path newly identified, block proof preserved; CLOSED |
| M32 | Saw/Harvester/Plough terrain | individual movement mixins + shared breaker / M32 commands | Plough impulse and Saw air-cell entity visit found separately; terrain proof retained; CLOSED |
| M33 | Deployer native use and mounted refill | Deployer/Handler + mounted read-only trace / M33 commands | not a parallel inventory; CLOSED |
| M34 | Contact/Haunted Bell and inventory audit | two actor mixins / M34 commands | old door internal-only classification corrected here; CLOSED |
| M35 | Roller clearing/paving/material use/yaw/moving render | SableRollerTerrain + actor/render hooks / M35 fixture | level orientation envelope; CLOSED |
| M36 | PSI/PFI transfer/yaw/disconnect/cleanup | PSI actor/target/render / M36 fixture | initial moving->stationary parent topology only; CLOSED |
| M37 | Funnel parent item capture/emission, chest capture/render | Funnel target/mixin + nested Chest BER / M37 fixture | accepted with unperfected synthetic INSERT collector fixture; CLOSED / ACCEPTED |
| M38 | Dropper/Dispenser native parent item/projectile/block effects | Dropper actor + dispenser target / M38 fixture | audited catalogue only; CLOSED |
| M39 | parent player mount/move/dismount and teardown | SableSeatPassengers + Seat hooks / M39 fixture | controlled native CCE, not trains/hull seat; CLOSED |
| M40 | native Basin inversion parent drops/empty restore | Basin tick scope/output observer / M40 fixture | item-only, no processing/fluid-spill feature; CLOSED |

## Verification design and next phase

verifyCreateMovementBehaviourCoverage compares the exhaustive hierarchy, exact
registration selectors, variant helper sites, callback/API bytecode fingerprints,
classification and owner manifest. Owners cannot silently disappear. It runs all
retained M31-M40 gates and the production fixture-dependency gate.

The packaged structural mixin audit reads enabled mixins/refmap and target Create,
Sable and Forge SRG bytecode: selected method descriptors, HEAD/RETURN/member points,
ordinal feasibility and exact recorded match counts. Specialized point selectors
are explicitly reported separately. This is a structural gate, not a claim to
simulate Mixin transformation ordering, local capture or every third-party plugin.
Existing exact M39 and earlier specialized rendering canaries remain mandatory.
The M39 previously-broken artifact negative test is retained. No require=0 band-aid.

The final packaged structural gate passes 200 checks across 75 configured mixins and
297 target/mixin/hierarchy class fingerprints. The one zero-count renderer bridge
is retained explicitly alongside its eligible typed overload; it is not a missing
injection across the selected method set. Create's embedded production Flywheel
and Ponder bytes take precedence over mapped-dev dependencies. Accessor field
descriptors, invokers, overwrites and exact member points are inspected. The full
report is `forge/build/m41Audit/mixin-targets.json`; its canonical fingerprint is
pinned in the accepted manifest. There are no unsupported explicit @At kinds in
this selected set. These checks do not execute handlers or prove @Local capture.

Before/after production class hashes are compared. No compatibility feature is
implemented; no new runtime acceptance is requested. Findings remain explicit F
rows instead of manufacturing COVERAGE COMPLETE. Recommended **one next milestone**:
adjacent Sliding Door parent-world handoff, with current cell/facing and ownership
validation. Drill/Saw air-cell entity visits, Plough impulse and burner gaze remain separately recorded residuals;
do not start them or M42 automatically. Non-MovementBehaviour/UI/backlog work can
follow only after these boundaries are resolved or explicitly scoped by the user.

## Verification and delivery

All requested static/build gates passed on 2026-10-06:

- spotlessApply, spotlessCheck and :forge:compileJava.
- :forge:verifyCreateMovementBehaviourCoverage: PASS; REAL_GAP_FOUND=5 is
  explicitly reported, not relabeled functional coverage complete.
- :forge:verifyCreateProductionFixtureBoundary: PASS for 134 production
  compatibility/milestone classes, with the separately classified read-only
  FunnelFixtureTimingMixin fixture observer described above.
- Every retained M31-M40 verifier, M35 moving-Roller rendering, M39 runtime
  mixin targets and the earlier render/mixin-target checks included by build.
- :forge:verifySableProductionBaseline, :forge:reobfJar, :forge:jarJar,
  :forge:reobfJarJar, :forge:verifyStandaloneForgeArtifact and full build.
- git diff --check: PASS.

The explicit complete verification chain succeeded with 277 actionable tasks
(221 executed, 56 up-to-date). The preceding uncached complete chain also passed;
the final report/aggregate/standalone/full-build rerun passed in 36 seconds with
166 actionable tasks (116 executed, 50 up-to-date).
747 retained production class hashes were compared before/after: **0 changes**.
The whole all.jar SHA-256 is also unchanged. No Minecraft was launched; no new
runtime acceptance or fixture marathon is needed for these audit-only changes.

Artifact: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`

Size: **12,851,655 bytes**.

SHA-256: `42D5F3665E8E6AD91E27E5560C946777B1BA6E34F291D94345C59680DA30BF8E`.

Changed files (no production, fixture or rendering implementation changes):

- `buildSrc/src/main/java/CreateMovementCoverageVerifier.java`
- `forge/build.gradle`
- `config/m41-create-movement-coverage.json`
- `M41_CREATE_MOVEMENT_BEHAVIOUR_CLOSURE.md`

Final status: M28-M40 accepted runtime proofs remain CLOSED. M41 audit and
verifier implementation are complete and static gates PASS. **Create 6.0.8
MovementBehaviour coverage closure remains OPEN**, because the five F rows above
need explicit resolution or an agreed scope policy. No next milestone was started.

## M42 follow-up (2026-10-07)

The user subsequently authorized Sliding Door work only. The current machine
manifest classifies it as C / COVERED_BY_M42, implemented/runtime pending. Native
block/behavior/registration counts remain 23 / 22 / 27 / 46. Four behavior rows
(Plough, Drill, Saw, Blaze Burner) represent the remaining three residual families.
The historical M41 artifact/hash and no-production-change statements above refer
to M41 delivery, not the later M42 jar. M42 adds only the door adapter/mixin plus
fixture dispatch. Other retained production hashes are required unchanged.
The refreshed packaged structural manifest has 204 checks; no old canary is
removed. Overall MovementBehaviour coverage remains OPEN.

## M43 follow-up (2026-10-15)

M42 is now runtime accepted by the user. M43 addresses the four remaining F rows
(three families): Plough throw vector, Drill and Saw physical entity visits in
non-conducting cells independent of terrain eligibility, and Blaze Burner gaze.
The current manifest assigns these rows C / M43. Inventory and registration
fingerprints are rechecked, not regenerated or relaxed: 23 concrete classes,
22 registered classes, 27 registration sites and 46 block variants. The resulting
F count is computed from every manifest row, not hardcoded to zero.

See `M43_FINAL_MOVEMENT_BEHAVIOUR_RESIDUALS.md` for exact native callsites, packaged
injection counts, source ownership and runtime gates. Static coverage is not
runtime closure. M43 and M41 functional closure remain runtime pending until the
Plough, Drill, Saw and Blaze fixture gates all succeed in Minecraft. The old
M41 production-hash/artifact numbers above remain historical delivery evidence.
