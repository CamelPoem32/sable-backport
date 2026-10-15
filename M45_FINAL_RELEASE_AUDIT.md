# M45 final production and release audit

**M45.2 final acceptance: M28-M45 CLOSED / PASS. Backport: FUNCTIONALLY COMPLETE.
Release candidate: ACCEPTED / RELEASE READY. No M46 required.**
Static/build evidence and user-reported final runtime acceptance are recorded
separately below. Git release tagging is withheld because accepted M45.1 code
and build changes remain uncommitted; the documentation-only commit cannot
truthfully tag their source state. Both original implementation blockers are resolved.
Five production -> fixture calls
are replaced by fixture-owned Forge subscriptions and a neutral synchronous
assembly event. The stale Veil classpath expectation is replaced by a strict
effective staged-runtime proof. Earlier M28-M44 acceptance remains unchanged.

## Release contract

Target: Minecraft **1.20.1**, Forge **47.4.20**, Java **17**, Create **6.0.8**,
Flywheel **1.0.5**. Branch: `backport/forge-1.20.1-sable-2.0.0`.
Upstream Sable: `mc1.21.1-2.0.0-neoforge`,
`b7226222caf4eace63a708bdcd73ef36c971137d`.
Create authority: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

Accepted M44 input: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`,
12,968,732 bytes, SHA-256
`0465A5CB6DC5797A016D62AB7EA105DEDA69EF9A1245F5441B092558D3F3F198`.
That exact hash was checked before editing. The complete top-level `dev/`
class baseline is `build/m45Audit/classes-before.json` (1,005 classes).
The frozen `config/m45-production-class-hashes.json` additionally includes all
32 nested Companion/Rapier application classes: 1,037 application classes in
total. Their baseline was extracted while the whole artifact still matched the
accepted M44 SHA-256, so this is not a post-change baseline reset.

The initial M45 pass was read-only production auditing. M45.1 makes the narrow
dependency-inversion changes documented below, with no accepted gameplay change.
No game is launched. Earlier runtime proofs are user-accepted evidence, not
new runtime observations made by these tasks. The user subsequently supplied
final candidate smoke acceptance in M45.2; that reported evidence now closes
M28-M45 and functional release acceptance. M45.2 runs no build or Minecraft.

## Architecture and frozen ownership

Hidden plots are storage coordinates, not physical entity/render coordinates.
Native Create owns actors, stack/fluid mutations, filters, timing, damage and
passenger relations. Scoped Sable adapters compose native inner transforms with
the current outer pose and validate the physical target. They do not globally
replace Level queries, entity spawns, capabilities, direction or raycasting.

Nested bearing/piston transfer is one ownership transaction: snapshot native
contraption/controller data, restore and verify destination ownership, then
retire the source. Failure rolls back destination state. It does not re-traverse
the hull or reconstruct payloads from fixture templates. Controller identity is
resolved through containing-sublevel/controller context, not only a parent
`getEntity(UUID)` lookup. A logical transfer can reconstruct a new native entity;
the controller/ownership association is authoritative, not an eternal Java reference.

Visible rendering uses small camera-relative transforms; raw plot positions
must never be pushed into PoseStack and canceled with another giant translation.
Normal-world Create keeps its native path. M22 physical restore blockers remain.

## Accepted milestone ledger

Production owner names are searchable source symbols. "Runtime accepted" below
means the user's closed milestone evidence, not a fresh M45 Minecraft run.

| Milestone | Feature / production owner | Runtime evidence | Limitation / status |
|---|---|---|---|
| M0-M9 | Java17/Forge bootstrap, curated mixins/network/platform services, Companion/Rapier ownership | retained build/runtime foundations; provider/namespace gates | target-selected backport, not upstream Java21 modules; retained |
| M10-M13 | sublevel assembly/persistence, multi-block and BE storage, interaction/value settings, native CCE lifecycle/Jade | earlier accepted production baseline | no new historical runtime claim; retained |
| M14-M20 | visible-space blocks/BER/CCE, `VanillaSubLevelRenderTransforms`, `SableForgeCreateContraptionRenderBridge`, `SableCreateRenderBridge` | nested moving/kinetic visuals accepted | retained specialized CPU paths, not global Flywheel disable; CLOSED |
| M21 | Simulated bootstrap / Forge registrations and Companion integration | bootstrap accepted | selected backport scope; CLOSED |
| M22 | assembler ownership / `SubLevelAssembler`, assembly platform | assemble/disassemble and safe refusal accepted | unrelated restore blockers intentionally refuse; CLOSED |
| M23 | Simulated constraints / physics connection integration | constraints accepted | no new controller family implied; CLOSED |
| M24 | Simulated systems and physics integration | systems accepted | frozen; CLOSED |
| M25 | Levitite vehicle lift | parent physics accepted | selected aeronautics scope; CLOSED |
| M26 | Wooden Propeller thrust | thrust accepted | frozen; CLOSED |
| M27 | sail aerodynamics / production physics baseline | forces accepted | frozen; CLOSED |
| M28 | `SableNestedBearingOwnershipTransfer`, steering/bearing CPU bridge | live nested payload and reverse restore accepted | supported transfer transforms; CLOSED / PASS |
| M29 | inclusive entity queries, bounded restored-CCE admission/controller grace | query frames and spawn races accepted | four-tick scoped grace; CLOSED / PASS |
| M30 | retained production baseline and diagnostic policy | earlier proofs retained | consolidation, not new gameplay; CLOSED / PASS |
| M31 | Drill / `SubLevelBlockBreakingUtility` | physical world mutation accepted | target scope retained; CLOSED / PASS |
| M32 | Saw/Harvester/Plough / breaker and actor-world context | terrain, harvest, native drops accepted | residual entity callbacks completed M43; CLOSED / PASS |
| M33 | `SubLevelDeployerInteractionUtility`, mounted refill | native FakePlayer use/refill accepted | no unofficial inventory; CLOSED / PASS |
| M34 | Contact/Haunted Bell / scoped actor hooks | physical contact/pulse accepted | historical audit superseded by M41-43; CLOSED / PASS |
| M35 | `SableRollerTerrain`, nested Roller render | clearing, paving, storage and moving render accepted | level-pose policy; CLOSED / PASS |
| M36 | `SablePortableInterfaceTarget`, native PSI/PFI wrappers | item/fluid transfer, yaw, disconnect, cleanup accepted | no cross-body pipe; CLOSED / PASS |
| M37 | `SableMovingFunnelTarget`, native Funnel wrappers / chest bridge | entity/source capture, moving extraction, storage/render accepted | synthetic INSERT collector limitation; CLOSED / ACCEPTED |
| M38 | `SableMountedDispenserTarget`, Dropper callback bridge | native item/projectile/block effects accepted | audited native behavior scope; CLOSED / PASS |
| M39 | `SableSeatPassengers`, physical interaction/dismount/teardown | player mount/motion/dismount accepted | native relation, not fake per-tick riding; CLOSED / PASS |
| M40 | Basin callback wrapper, `SableBasinOutput` | nested inverted native dump accepted | item output only; CLOSED / PASS |
| M41 | pinned ASM inventory / coverage manifest | audit, not separate gameplay | four residual families subsequently closed; CLOSED / COMPLETE |
| M42 | `SableSlidingDoorTarget`, nested piston ownership transfer | preserved door payload and physical handoff accepted | near-cardinal level orientation; CLOSED / PASS |
| M43 | residual entity geometry, `SableBlazeGaze` | Plough impulse, Drill/Saw air damage, gaze, yaw and cleanup accepted | native horizontal gaze projection retained; CLOSED / PASS |
| M44 | `GoggleOverlayHitPolicy`, `SableGoggleOverlayGuard`, optional TFMG wrapper | native overlay works, assembled-Sable cast crash absent | virtual overlay intentionally unavailable; CLOSED / PASS |
| M45 / M45.1 | final audit, dependency inversion and runtime-model verifier repair | static/build PASS; final smoke user-reported PASS in M45.2 | CLOSED / PASS; ACCEPTED / RELEASE READY |

Fixtures live under `forge/src/main/java/dev/ryanhcode/sable/command/`, with
packaged `data/sable/functions/mNN/` resources. M28-M44 fixture classes are not
gameplay ownership authorities. Historical documents remain evidence; this ledger
does not overwrite their dated runtime-pending statements.

## Machine-readable release evidence

`auditM45ReleaseEvidence` generates `forge/build/m45Audit/`:

- `sources.json`: every Java source in the common/Forge/Companion/Rapier/buildSrc
  roots, its role and whether selected for Forge. `LEGACY_UNUSED` means excluded
  or replaced **for this target**, not proven dead upstream.
- `artifact.json`: recursive jar inventory, class roles/hashes/major versions,
  duplicate entries/classes/packages, services/provider presence, fixture edges,
  explicit bootstrap calls, configs/refmaps/AT and fixture resources.
- `mixins.json`: every retained Sable-configured mixin, side, actual runtime
  target(s), callback descriptor, injector annotation including all operands,
  require/ordinal/shift/slice data and exact refmap entries. Accessor-only mixins
  remain included. An absent per-handler `require` inherits config default 1.
- `mixin-targets.json`: packaged runtime structural matches and target hashes,
  distinct from full Mixin application. M39/M44 specialized smoke gates remain.
- `diagnostics.json`: diagnostic families/property names by selected source.

Build verifiers are not packaged. Selected production classes, fixture tools and
unselected upstream source are counted separately; anonymous/nested bytecode
classes are not misreported as additional source files.

Measured M45.1 classification (868 Java sources in the target roots, including
isolated regression source sets; 1,039 packaged application classes):

| Category | Java sources | Packaged classes |
|---|---:|---:|
| CORE_PRODUCTION | 538 | 653 |
| COMPAT_PRODUCTION | 108 | 143 |
| DEBUG_PRODUCTION | 13 | 38 |
| FIXTURE_RUNTIME | 45 | 205 |
| BUILD_VERIFIER | 45 | 0 |
| LEGACY_UNUSED (excluded/replaced for Forge) | 119 | 0 |
| UNKNOWN | 0 | 0 |

The source inventory includes bundled Simulated/Aeronautics and the explicit
Companion/Rapier Java backports. Third-party library classes are counted by the
recursive bytecode scan but are not mislabeled as Sable production source.
These role categories are not deletion permissions: diagnostically named retained
probes can also signal mandatory visual-admission/lifecycle transitions.
DEBUG_PRODUCTION is retained production code, not "optional code safe to remove".

## Coverage and boundary ownership

Pinned inventory is recomputed recursively from the production Create jar,
including inheritance and registration bytecode, then compared to the accepted
manifest. The independent source owners are `AllBlocks` (21 sites),
`AllMovementBehaviours.registerDefaults` (5), `BuilderTransformers.slidingDoor`
(1). Concrete superclass `BlockBreakingMovementBehaviour` is not independently
registered. Twenty direct block entries + sixteen seat colors + five door
variants + five defaults = 46. No anonymous behavior is omitted.

Expected inventory: **23 concrete, 22 registered behavior types, 27 sites,
46 block registrations/variants; REAL_GAP_FOUND=0**. The canonical per-behavior
source/callback/sink matrix remains `config/m41-create-movement-coverage.json`
and generated `forge/build/m41Audit/create-inventory.json`; M43 owns all former
residual entity/vector/gaze paths. No new MovementBehaviour is implemented here.

| Boundary | Coordinate/ownership adapter | External sink owner |
|---|---|---|
| Drill/Saw/Harvester/Plough | actor-local -> inner raw -> current outer pose; transformed bounds/vectors | native Create mutation/drop/damage; M31/32/43 |
| Deployer | physical ray/player/target via `SubLevelDeployerInteractionUtility` | native FakePlayer + mounted refill; M33 |
| Roller | `SableRollerTerrain` | native break/pave/material rules; M35 |
| PSI/PFI | current physical candidate + transformed facing | native handshake/capabilities; M36 |
| Funnel | physical active cell/entity space | native query/extract/insert/filter; M37 |
| Dropper/Dispenser | physical `BlockSource` context/facing | original selected behavior + mounted mutation; M38 |
| Seat | local seat x inner CCE x outer pose | native passenger relation; parent dismount search; M39 |
| Basin | native dump scoped to parent-visible context | native NBT inventory drain/item creation; M40 |
| Sliding Door | current physical outward neighbor | native door lookup/update/sound/event; M42 |
| Blaze gaze | physical camera delta inverse outer rotation, then native inverse inner rotation | native gaze angle/head render; M43 |
| Goggles | block-hit policy only; no synthetic global hit | native overlay or safe no-op; M44 |

No global entity spawn/capability/world mutation redirect is introduced. Adjacent
interaction/raycast/BER paths remain scoped and classified CLOSED_PATH; virtual
goggle information is KNOWN_ACCEPTED_LIMITATION. New cross-body mechanics and
unselected third-party integrations remain OUT_OF_SCOPE_FUTURE_FEATURE.

## Ownership, storage and lifecycle audit

| Object | Actual ownership / lookup / lifecycle |
|---|---|
| Parent ItemEntity/projectile | parent Level/entity manager; physical spawn/query only; native counts/velocity; no hidden duplicate |
| FakePlayer | native deployer fake player in parent context during operation; scoped state restoration; no parallel actor inventory |
| Seat passenger | parent-world entity; native vehicle relation; scoped physical positioning and safe dismount before inner/outer removal |
| Nested CCE | parent Level facade with Sable-owned raw storage/entity-manager context; containing/controller/inclusive resolver, not bare UUID-only parent lookup |
| Outer Sable | sublevel container/physics owner, not a fake pickable world block; saved plot/controller ownership |
| Fixture entities | explicit semantic tags/UUID ownership; operator cleanup only, not production deletion policy |

Deployer refill, Funnel transfer, Dropper/Dispenser slot mutation and PSI/PFI
capabilities remain native Create. Basin NBT item lists are natively cleared by
dump and restored empty; fluid spilling is not invented. Evidence collectors
observe results rather than copying items. Transfer rollback verifies controller,
block/BE data, actor contexts and mounted storage before retiring the source.
No new inventory or persistence format is introduced.

Sublevel saved data/plot storage and native serialized contraption state remain
the reload authority. Fixture Java task maps are not needed to reload gameplay.
Debug evidence is bounded/weak or explicitly lifecycle-cleared; diagnostic flags
do not gate required ownership/sync logic. No new save/reload runtime is claimed.

## Orientation and cross-Sable policies

These are existing policies, not broadened support:

| Family | Orientation |
|---|---|
| Drill/Saw/Harvester/Plough | transformed points/bounds/directions; native grid/conductor checks; Plough throw vector rotated, native world-up boost retained |
| Deployer | full transformed physical ray; native grid face fallback; no new arbitrary placement rules |
| Roller | level envelope (up dot > .999); native terrain direction/paving |
| PSI/PFI/Funnel/Dispenser | level up Y > .999; nearest cardinal error squared <= .5; unsupported orientation no-op |
| Sliding Door | world/inner up and horizontal cardinal facing dot > .999 |
| Seat | full physical composition; dismount searched in parent collision space |
| Basin | native inverted-up test; composed output point/directional velocity, not a grid-quantized custom spill |
| Blaze | composed visible position/vector; native horizontal head angle |

Ordinary parent world is supported. Actor's own internal storage remains native.
Hidden plot A -> hidden plot B is never a proximity fallback. Where block/grid
resolvers reject another visible Sable, the result remains safe no-op; the release
does not claim universal physical cross-Sable interaction. Native parent entity
queries remain entity-space operations, distinct from hidden block/capability
lookups. See `FINAL_KNOWN_LIMITATIONS.md` for explicit supported envelopes.

## Rendering, diagnostics and optional integrations

Retain ordinary block rendering, specialized kinetic BER CPU paths, bearing top,
moving CCE bridge, native captured chest BER, passenger rendering, ghost/value
box paths and Blaze's narrow gaze correction. No custom player/chest model,
global Flywheel disable, inflated culling bounds or plot-scale cancellation.

Diagnostic policy: KEEP_ALWAYS for bounded lifecycle readiness/failure, unsafe
restore refusal and invariant violations; KEEP_DEBUG_GATED for actor/transfer,
query, BER/value-box/ghost/render traces; KEEP_FIXTURE_ONLY for command state
machines, payload/geometry/timing summaries and bootstrap smoke observers.
Previously removed per-vertex/framebuffer/presentation experiments stay absent.
No additional production diagnostic is removed merely to reduce source count.
Successful `SABLE_SUBLEVEL_READY` is an assembly/rebuild boundary, not a frame log.

Useful retained opt-in JVM properties:

```text
-Dsable.m31.traceCreateActors=true
-Dsable.m29.traceEntityQueries=true
-Dsable.m29.traceSailVisualLifecycle=true
-Dsable.m28.traceNestedBearingPayload=true
-Dsable.m22.traceAssembly=true
-Dsable.m14.traceRendering=true
-Dsable.m13.traceRuntime=true
-Dsable.m20.traceCreateRendering=true
-Dsable.m11.traceBlockEdits=true
-Dsable.m24.traceRuntime=true
-Dsable.m28.traceSteering=true
```

The complete property inventory is generated; smoke-only `sable.runtimeSmoke`,
`sable.targetRuntimeSmoke`, `sable.standaloneRuntimeSmoke` are not gameplay
requirements and should remain unset in normal gameplay.

Optional mixins are selected using Forge's early `LoadingModList`, not a late
empty runtime list. Shared plugin namespace gating checks presence before version
constraints/target application. TFMG wrapper is client-only and exactly
`[1.0.2f]`; it leaves global hitResult unchanged and wraps outside the unsafe
TFMG HEAD callback. Jade/Jade Addons retain their scoped query/interaction paths.
Excluded Iris/Oculus/Vista/ComputerCraft/etc upstream mixins are not secretly
enabled. Create is required by the packaged Simulated/Aeronautics modules even
though Sable's own dependency declaration marks it optional. No absent-Create
standalone gameplay support is claimed for this combined artifact.

Client-only M43/M44 mixins are in the client config section. Guard/gaze classes
are entered only from those client paths; no new common tick calls Minecraft
client renderers. Source entrypoint/side review is not a whole-program classloader
proof or a dedicated-server launch; neither is claimed here. Compiled Java17
checks distinguish legal `Pair.getFirst`/deque methods
from Java21 List/SequencedCollection APIs and excluded upstream Java21 sources.

## Fixture packaging and dependency boundary

Choose **A: retain existing fixtures**. Late source/resource pruning risks
breaking known recovery tools and registrations. No fixture-framework rewrite.
Bootstrap registrations remain explicit in `SableForge`; this is reported, not
hidden as a fictional literal zero references across the entire jar.
The fixture-owned `FunnelFixtureTimingMixin` observes the native succ query and
calls M37's observer; it is classified FIXTURE_RUNTIME despite its mixin package.
The old Create-only scan found no gameplay dependency in its namespace, but that
was not a whole-artifact proof. The strengthened boundary scans all bundled
Sable/Simulated/Aeronautics namespaces, separately inventories bootstrap edges,
and rejects additional production -> fixture operations or ownership constants.
This preserves the explicit existing composition roots `SableForge` and
`SableCommand`; registration-only edges are not allowed to perform test operations.

### Original five edges: repaired in M45.1

| Production callsite | Fixture implementation invoked |
|---|---|
| `forge/src/main/java/dev/eriksonn/aeronautics/forge/AeronauticsForge.java:28` | `M25AeronauticsCommands.tick(ServerLevel)` |
| same file, line 29 | `M26AeronauticsPropulsionCommands.tick(ServerLevel)` |
| same file, line 30 | `M27AerodynamicsCommands.tick(ServerLevel)` |
| `forge/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java:112` | `M24SimulatedSystemsCommands.onM22AssemblyCreated(...)` |
| same file, line 113 | `M27AerodynamicsCommands.onM22AssemblyCreated(...)` |

These were literal packaged bytecode calls in accepted M44, not import/name
matches. The table's line numbers describe the original artifact, not current
source. Their pre-repair semantics are:

| Old observer | Timing / arguments | Fixture state effect |
|---|---|---|
| M25 `tick` | Every Forge `LevelTickEvent.END` for `ServerLevel`, priority NORMAL; event's exact level | Read dimension-keyed `LAST_FIXTURES`, resolve body, enable individual queued-force tracking, capture/update `latestSample`, log selected sample ticks, increment `observationTick`; absent state/body returns |
| M26 `tick` | Same event, immediately after M25; exact same level | Read active propulsion fixture, resolve body, enable tracking, capture/update `latestSample`, bounded logs, increment observation tick; absent state/body returns |
| M27 `tick` | Same event, immediately after M26; exact same level | Resolve active aero body, enable tracking, update `latest`, provider diagnostics/sample logs, increment observation tick; absent state/body returns |
| M24 `onM22AssemblyCreated` | Successful `assemble(ServerLevel)`, after `setChanged`, before M27/transform trace; level, original immutable parent assembler position, returned sublevel | Match fixture's A/B assembler; store body UUID/runtime handle, update bodies-ready lifecycle before autopair; unrelated/no fixture returns |
| M27 `onM22AssemblyCreated` | Same synchronous success boundary, after M24; same three values plus `AssemblyResult.offset()` | Match uncaptured active fixture/exact assembler; derive raw assembler from parent+offset, validate block, capture returned body UUID/local component positions, emit provider diagnostics; no active/matching fixture returns |

All five return `void`, execute server-side synchronously, and have no caller-side
exception suppression. The original assembler catches only `AssemblyException`
around assembly/disassembly, not observer runtime failures. These semantics remain.

Scope: three fixture tick calls and two assembly-completion callbacks. Risk of a
casual deletion: breaking accepted M24-M27 fixture lifecycle/body-capture timing.
Risk of keeping the coupling: fixture implementation remains linked and entered
from normal production lifecycle, preventing clean fixture-independent packaging.
The old verifier checked only `/compatibility/create/` and `/mixin/m`; these two
production owners are outside both prefixes. M45 catches them without weakening
that verifier or reclassifying the production assembler as a test class.

`M45FixtureLifecycleSubscriptions` now owns both listeners. Forge automatically
registers its `@Mod.EventBusSubscriber(modid="sable", bus=FORGE)` static handlers;
no gameplay entrypoint registers/invokes fixture operations, and no operator flag
is needed. Its default NORMAL-priority tick listener retains END + ServerLevel
filtering, once per level event, and M25 -> M26 -> M27 order. It does not switch
to a physics-substep/server-global tick. The observer methods themselves are
byte-identical to M44, including active-fixture guards and force sampling.
The retained plot `ServerLevelMixin` ticks the container at `ServerLevel.tick`
HEAD; `SubLevelContainer.tick` dispatches its physics observer before Forge's
level-END event. Automatic subscriber registration therefore does not move these
samples across the physics step. No other production LevelTick listener was
introduced, and no listener priority was changed.

The only production notification is `ForgeSableAssemblyCompletedEvent`, following
the existing small Forge event classes. Four final production-domain fields carry
ServerLevel, immutable parent assembler BlockPos, returned ServerSubLevel and
immutable assembly offset. It is noncancelable and synchronous, with no fixture
types/tags/state, custom bus, queue or async work. `PhysicsAssemblerBlockEntity`
posts it exactly where the two calls previously stood, after all result/diagnostic
fields and `setChanged()`, before the existing trace. Its fixture subscriber calls
M24 then M27 with equivalent values. A failed/null assembly returns/throws before
the post; disassembly has no post.

`SimAssemblyHelper.assembleFromSingleBlock` still returns only after sublevel
allocation/registration, ownership destination binding, native block/controller
transfer and transaction finish, glue relocation and body snapshot. In its caller,
accepted counts/lifecycle fields and controller change notification precede the
event. No assembly, nested ownership, autopair or subsequent tick is rescheduled.
Forge EventBus 6.2.33 `post` bytecode invokes its exception handler then rethrows
the original Throwable (`athrow` at 139); no new catch hides observer failures.

Packaged ASM checks require one construction/post in the exact success method,
the full production constructor descriptor, state-before-post ordering and the
null-result early return without notification. They also require static Forge
subscriber/SubscribeEvent metadata, observer call order, no catch blocks and no
production Aeronautics tick method. Isolated Java17 tests compile the actual
subscriber/event sources against typed test doubles and execute argument identity,
M24/M27 ordering, START/client exclusions, 100 END events (exactly 300 ordered
M25/M26/M27 observations), and exception propagation. They do not claim live
Forge automatic discovery or Minecraft-world execution.

The whole application bytecode scan now also rejects fixture references in method
descriptors and invokedynamic handles/types. Final direct production -> fixture
lifecycle/operation edges: **0**; **35** explicit composition-root registration
edges remain separately enumerated. UNKNOWN=0. Registration is not permission to
execute fixture lifecycle operations. Removing fixtures would not leave concrete
fixture links in Aeronautics/Physics Assembler gameplay classes.

## Verification and candidate

Final measured verification results, exhaustive mixin review and artifact
identity are recorded below after the release tasks complete. Structural checks
do not replace full Mixin application; the existing M39 target and M44 actual
MixinExtras wrapping-stage smoke are retained. No interactive launch is included.
A passed independent gate never overrides another failure. Candidate approval
requires both repaired boundaries plus the complete composed release chain.

### Retained structural checks (re-run by M45.1)

The historical pre-repair retained-chain invocation completed successfully: **2m 25s,
181 actionable tasks, 164 executed and 17 up-to-date** (`build/m45-retained.log`).
It includes formatting check, compile, fresh Create inventory, all individual
M31-M40 and M42-M44 gates
(including M35 moving rendering, M39 runtime targets, M43 residuals and M44
actual MixinExtras wrapping smoke), standalone artifact and production baseline.
That historical result did not mean the strengthened final boundary passed.
Current M45.1 complete-chain results are recorded separately below.

- Full configured Sable mixin inventory: **233 classes**, **990 structural
  checks**, **396 target/dependency class fingerprints**, zero missing targets
  and zero unchecked injection-point kinds. Three ModifyConstant sites and two
  typed local STORE discriminators are checked explicitly; no slices are present
  (new slices require explicit verifier support rather than silent acceptance).
- Reviewed runtime-target fingerprint:
  `096bf702847f8abaf08e97c5e608412bde60db7e937540d20bb706d5e8bf57cc`.
  The prior M41 Create-focused fingerprint remains unchanged. This is structural
  evidence, not a claim of simulating every transformer/local frame or launching
  a dedicated server. Existing runtime acceptance plus M39/M44 smoke is retained.
- Config namespace gates: 35 `compatibility.create`, two Jade, one Jade Addons,
  one exact-version TFMG, 194 retained core/milestone entries. Milestone hooks
  referencing Create outside the optional namespace are supported by the combined
  artifact's mandatory Create dependency, not an absent-Create promise.
- Available optional target versions: Jade **11.13.3**, Jade Addons **5.5.1**,
  TFMG **1.0.2f**. Early optional selection and retained mixin coexistence gates
  remain composed; absent integrations are not loaded by an unconditional common
  initializer. Existing Radium/Lithium compatibility guards remain retained.
- Recursive scan: **1,629 classfiles**, no duplicate entries/classes, no split
  packages, no bundled Create/Minecraft/Fabric/NeoForge classes. Registrate,
  Flywheel and Ponder remain Create-owned, not copied into Sable.
- Major versions: **1,040 at 61**, **487 at 52**, **93 at 51**, **9 at 50**.
  All 1,039 Sable/Simulated/Aeronautics/Companion/Rapier application classes are
  Java17. Older MixinExtras/LZ4 classes are compatible dependency bytecode, not
  Java21 contamination. No selected Java21 List/Sequenced API reference found.
- Four intended top-level nested libraries: Companion 1.6.0, Rapier 2.0.0,
  LZ4 1.11.0, MixinExtras Forge 0.5.3 (one common 0.5.3 payload inside it).
  The existing native payload/namespace/provider checks are retained; this does
  not manufacture runtime validation of a different OS or dependency version.
- Two Sable mixin configs, one Sable refmap, both retained AT resources and the
  established operator fixture resources remain packaged. No config points to a
  deleted class; no new optional injection or `require=0` was introduced.

### ServiceLoader inventory

All 14 provider rows have a present, public concrete provider and public no-arg
constructor. Packaged hierarchy/JDK hierarchy establishes interface compatibility;
no Minecraft/provider constructor is initialized by this audit. Production
namespace and runtime module gates separately protect provider linkage/ownership.
This is not a live ServiceLoader/physics-native initialization claim.

| Service | Provider / owner |
|---|---|
| `SableCompanion` | outer `ActiveSableCompanion` (default priority 1000), nested `DefaultSableCompanion` (500); intentional two-provider max-priority selection |
| `PhysicsPipelineProvider` | nested `RapierPhysicsPipelineProvider` (1000), outer `StaticPhysicsPipelineProvider` (900); intentional fallback |
| `SablePacketTransport` | outer `ForgeSablePacketTransport` |
| `SableAssemblyPlatform` | outer `SableAssemblyPlatformImpl` |
| `SableChunkEventPlatform` | outer `SableChunkEventPlatformImpl` |
| `SableEventPlatform` | outer `SableEventPlatformImpl` |
| `SableEventPublishPlatform` | outer `SableEventPublishPlatformImpl` |
| `SableLoaderPlatform` | outer `SableLoaderPlatformImpl` |
| `SablePlatform` | outer `SablePlatformImpl` |
| `SablePlotPlatform` | outer `SablePlotPlatformImpl` |
| `SableSubLevelRenderPlatform` | outer `SableSubLevelRenderPlatformImpl` |
| `javax.annotation.processing.Processor` | nested MixinExtras `MixinExtrasAP`; third-party packaging, not a gameplay provider |

Distinct fallback providers are not duplicate provider classes. Detailed fully
qualified names/descriptors and nested jar ownership are in `artifact.json`.

### Changes and release decision

M45.1 changes exactly two existing application classes and adds two. Against
the preserved M44 baseline: **1,035 unchanged, 2 changed, 2 added, 0 removed**.
`config/m45-production-class-hashes.json` is not reset. The separate
`config/m45_1-reviewed-class-delta.json` pins the four exact reviewed new hashes
and reasons; any other class delta or changed approved hash fails the gate.

| Class | Delta / reason |
|---|---|
| `dev.eriksonn.aeronautics.forge.AeronauticsForge` | Changed: remove production fixture listener/imports; mod initialization and config untouched |
| `dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity` | Changed: replace two fixture calls with neutral post at identical successful-completion boundary |
| `dev.ryanhcode.sable.forge.event.ForgeSableAssemblyCompletedEvent` | Added: four-field neutral synchronous production event |
| `dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions` | Added: automatically registered fixture-owned END-tick and assembly observers |

Every M24-M27 fixture implementation, SableForge composition root, M42 Sliding
Door helper/wrappers/nested transfer, M43 residuals, M44 goggle policy, rendering
bridge, storage adapter and all nested Companion/Rapier classes is byte-identical
to accepted M44. No fixture command/resource changes. No physics/aero/propulsion
calculation, assembly timing, visual or item/entity behavior changes. Diagnostics
remain as accepted: zero additional removals/gates, no semantic consolidation.
The new classification/package/hierarchy tests include negative cases for
Java21 bytecode, duplicate classes/split packages, wrong service hierarchy and
fixture-observer role classification. Existing exact descriptor/removal-negative
and M39 broken-jar tests remain unchanged.

The original M45 blocker result is retained below as history, not waived as a
known limitation. M45.1 full-chain candidate approval is now recorded below.

Changed files (all M45 changes; no pre-existing user edits were present):

- `M45_FINAL_RELEASE_AUDIT.md`
- `FINAL_KNOWN_LIMITATIONS.md`
- `RUNTIME_MIXIN_OVERLAP.md` (regenerated by the retained coexistence task)
- `buildSrc/src/main/java/CreateMovementCoverageVerifier.java`
- `buildSrc/src/main/java/M45FinalReleaseVerifier.java`
- `buildSrc/src/main/java/M45LifecycleBoundaryVerifier.java`
- `buildSrc/src/main/java/M45VeilRuntimeVerifier.java`
- `config/m45-production-class-hashes.json`
- `config/m45-runtime-mixin-targets.sha256`
- `config/m45_1-reviewed-class-delta.json`
- `forge/build.gradle`
- `forge/m45-final-release.gradle`
- `forge/src/main/java/dev/eriksonn/aeronautics/forge/AeronauticsForge.java`
- `forge/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java`
- `forge/src/main/java/dev/ryanhcode/sable/forge/event/ForgeSableAssemblyCompletedEvent.java`
- `forge/src/main/java/dev/ryanhcode/sable/command/M45FixtureLifecycleSubscriptions.java`
- `gradle/runtime-preflight.gradle`

Generated evidence/logs remain under ignored `build/` and `forge/build/`; they
are not packaged as new gameplay resources.

### Historical M45 full-chain result (superseded by M45.1 below)

**Before repair: full build FAILED; final-release aggregate BLOCKED.** The normal online
retry completed in **6m 22s**, **293 actionable tasks** (237 executed, 56
up-to-date), recorded in `build/m45-full-online.log`. `--continue` retained
independent results; it did not suppress failure exit status.

Passed: `spotlessApply`, `spotlessCheck`, compile, `verifyM45ArtifactInvariants`,
all individually invoked retained feature gates, retained render/mixin gates,
M39 broken-artifact negative test, M44 wrapping smoke, production baseline,
early optional-mod gating, Companion/Rapier production namespaces, package/native
provider checks, reobfJar/JarJar/reobfJarJar, standalone artifact and standalone
run-configuration verification. The coexistence report regenerated
`RUNTIME_MIXIN_OVERLAP.md`: 174 SAFE_INDEPENDENT, one ORDER_SENSITIVE, zero
NEEDS_BYTECODE_CHECK and zero LIKELY_RUNTIME_CONFLICT in that retained report's
scope. M44's separate TFMG wrapping check remains authoritative for its callback.

Two task failures, reported separately:

1. `verifyCreateProductionFixtureBoundary`: the five confirmed lifecycle edges
   above. Consequently `verifyCreateMovementBehaviourCoverage`, final-release
   aggregate and Forge full-build completion cannot pass their dependencies.
   Independent fresh inventory matches all class/registration/variant pins:
   23 concrete / 22 registered / 27 sites / 46 variants / F=0. This is not a new
   MovementBehaviour functional gap.
2. `verifyRuntimeModuleBoundary`, `gradle/runtime-preflight.gradle:135`:
   `Expected effective runtime file is missing: .../forge/build/m6Runtime/Veil-forge-1.20.1-1.0.0.296-mapped-dev.jar`.
   The file **does exist** (26,408,669 bytes); the exact failed predicate is
   `required == null || !runtimeFiles.contains(required)`, against
   `sourceSets.main.runtimeClasspath`, not a failed filesystem existence check.
   The approved standalone staging/run-configuration verifier passes and
   explicitly installs the prepared artifact. This legacy development-graph
   expectation must be reconciled before M45 aggregate approval; it is not
   evidence of a new gameplay or packaged Veil failure. Its later whitelist also
   still lists the pre-Simulated/Aeronautics mod-ID set and requires review;
   that later branch was not reached, so no false claim of executing it is made.

The first offline attempt additionally lacked cached Veil slim resolution
(`ArtifactResolveException: No cached version available for offline mode`).
Normal dependency resolution successfully prepared Veil with six shadow aliases,
31/33 mapped AT entries and zero unresolved-known members, removing that
environmental obstruction. No filesystem permission failure remains in this run.
Neither failed gate is disabled or relaxed to manufacture release approval.

Frozen bytecode result: **1,037 unchanged, zero changed, zero added, zero removed**
(`forge/build/m45Audit/hash-comparison.json`). The entire rebuilt jar is also
byte-identical to the accepted M44 baseline, including all nested libraries.
No production semantic change needs a new runtime test in M45; the final smoke
remains deferred because release closure is blocked.

Rebuilt output, **not promoted to an M45 release-approved candidate**:
`forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`.
Size: **12,968,732 bytes**. SHA-256:
`0465A5CB6DC5797A016D62AB7EA105DEDA69EF9A1245F5441B092558D3F3F198`.
The final response provides the absolute local artifact path. `git diff --check`
is run after all generated reports/documentation. No Minecraft was launched.

Historical pre-repair status: M28-M44 runtime acceptance preserved (including M37's caveat);
Create 6.0.8 MovementBehaviour functional coverage remains COMPLETE;
**M45 OPEN / RELEASE_BLOCKER_FOUND at that time**. See M45.1 closure below;
this historical failed artifact is not the new candidate.

## M45.1 Veil runtime model and repair

Pinned dependency coordinates are `foundry.veil:Veil-forge-1.20.1:1.0.0.296`
(published mod metadata says `veil` version `1.0.0`; these are not the same
version field). ForgeGradle deobfuscates slim/full variants through `veilMapped`
and `veilRuntimeMapped`; the full mapped input is then prepared into
`forge/build/m6Runtime/Veil-forge-1.20.1-1.0.0.296-mapped-dev.jar`.
`VeilDevelopmentRuntimePatcher` supplies six exact shadow aliases, maps 31 of 33
AT members (zero unresolved-known), and filters bundled LWJGL/versioned entries.
`verifyVeilDependency` retains mapped Minecraft-call and AT canaries and now
reads the actual `veilMapped` preparation configuration, not a stale expectation
that main.compileClasspath must contain Veil. Current selected Java compilation
does not require the removed Veil rendering API (the M20 no-Veil compile/API
gate remains); slim/full configurations are explicit deobf preparation views.
The full prepared mod is the staged runtime view. No Veil integration
Java/render/mixin code changes.

`sourceSets.main.runtimeClasspath` is not the standalone mod graph. The standalone
launcher has empty source roots and loads mapped target mods from
`forge/run/standalone-client/mods`; `prepareStandaloneRuntimeSmoke` explicitly
stages the prepared full Veil. The old gate incorrectly required its canonical
File to appear in main.runtimeClasspath. This was a graph-membership failure,
not a nonexistent-file or production namespace/rendering failure. M45.1 does not
add Veil to an unintended launcher/source-set classpath to satisfy that assertion.

For the shipped SRG all.jar, the real installed target dependencies remain
external production mods. The mapped `m6Runtime` mod is authoritative for the
ForgeGradle userdev standalone test target only, not a new mapped jar requirement
for ordinary installed Minecraft. `verifyStandaloneForgeArtifact` and namespace
gates retain the shipped artifact contract.

The corrected runtime-module gate requires:

- Existing main classpath members for Create/Flywheel/Ponder/MixinExtras, with
  all previous nested-byte equality, exact SHA, split-package, module-name and
  service-provider checks retained.
- Exactly one pinned full Veil preparation input at mapped official 1.20.1
  version; prepared identity and bytes equal a fresh run of the established
  alias/AT patcher against that input/mapping set, not merely file existence.
- Staged Veil exists and equals the prepared bytes; exactly one effective staged
  Veil owner; development graph may omit it but cannot contain a raw/competing
  Veil mod. The standalone launcher remains free of target mod classes.
- Exact top-level staged mod-ID set and no duplicate IDs, as well as the exact
  selected metadata set below. An unrelated mod in the managed staged runtime
  fails instead of being silently whitelisted.

| Intentional mod ID | Artifact / source | Ownership / reason |
|---|---|---|
| `sable` | final all.jar; userdev-derived staged Sable | packaged primary mod |
| `simulated` | same all.jar, selected Simulated backport sources | packaged M21+ systems; added to legacy whitelist |
| `aeronautics` | same all.jar, selected Aeronautics backport sources | packaged M25+ physics systems; added to legacy whitelist |
| `create` | pinned Create 6.0.8 external mod; prepared mapped standalone copy | required Create runtime |
| `flywheel` | pinned 1.0.5, Create-owned nested / byte-identical selected mapped copy | rendering dependency; not bundled in Sable |
| `ponder` | pinned 1.0.91, Create-owned nested / selected standalone prepared copy | Create dependency; not bundled in Sable |
| `veil` | external full 1.0.0.296; prepared mapped staged copy | required selected renderer dependency; not bundled in Sable |

Seven executed negative cases reject: absent prepared artifact, wrong resolved
version, raw/stale prepared bytes, raw development-classpath leak, duplicate
staged Veil, absent staged dependency and unexpected module set. The real staged
directory is independently scanned for duplicate/unexpected mod IDs. Synthetic
negative jars stay under ignored build output and are never staged or packaged.

## M45.1 release-chain result

Targeted run `build/m45_1-first-gates.log`: **PASS, 3m 2s, 63 actionable tasks**
(47 executed, 16 up-to-date). This includes formatting check, compile, packaged
observer canaries/isolated tests, zero-edge boundary, corrected Veil gate with
negative tests, exact coverage aggregate and reviewed hash/mixin invariants.
`spotlessApply` also passed independently (3m 55s).

Final full release chain: **PASS, 3m 47s, 298 actionable tasks** (271 executed,
27 up-to-date), `build/m45_1-release-final.log`; exit status 0. Final formatting
application also passed (3m 30s). No runtime approval is inferred. Both immutable
Create inventory and runtime-target fingerprints remain unchanged: 23 concrete,
22 registered, 27 sites, 46 variants, REAL_GAP_FOUND=0; 233 configured mixins,
990 structural matches, 396 target fingerprints, zero unresolved/unchecked sites.
There is no baseline reset or optional-injection workaround.

| Release gate | Final result |
|---|---|
| `spotlessApply`, `spotlessCheck`, `:forge:compileJava` | PASS |
| `verifyCreateProductionFixtureBoundary` | PASS: 0 direct lifecycle/operation edges, 35 explicitly allowed registrations |
| `verifyCreateMovementBehaviourCoverage` | PASS: 23 concrete / 22 registered / 27 sites / 46 variants / F=0 |
| `verifyRuntimeModuleBoundary` | PASS: exact effective modules/staging, mapped Veil and seven negative cases |
| `verifyVeilDependency` | PASS: mapped preparation view, raw/mapped call canaries, 31 AT remaps and zero unresolved |
| `verifyM45FixtureLifecycleSubscriptions` | PASS: packaged ordering/arguments, success-only post, automatic listener metadata, isolated cadence/arguments/exceptions |
| `verifyM24AssemblyBodyIdCaptureBoundary`, `verifyM27AuthoritativeAssemblyCaptureBoundary` | PASS: historical capture contracts through neutral notification |
| `verifyM45ArtifactInvariants` | PASS: reviewed exact four-class delta, unchanged baseline, all runtime targets/providers/packages |
| Retained M31-M40, M42-M44 feature gates and render/mixin gates | PASS |
| M39 broken-artifact negative test | PASS: old 0/1 injection correctly rejected |
| M44 actual MixinExtras wrapping smoke | PASS |
| Early optional-mod selection, retained coexistence | PASS: 174 safe independent / 1 order-sensitive / 0 unresolved conflict rows |
| Production baseline, Companion/Rapier namespace/provider/native packaging | PASS |
| `reobfJar`, `jarJar`, `reobfJarJar`, `verifyStandaloneForgeArtifact` | PASS |
| `verifyStandaloneRunConfiguration` | PASS: staged mapped artifact derived from exact new final all.jar hash, empty launcher roots, zero stale namespace references |
| `verifySableFinalRelease`, full `build` | PASS |
| `git diff --check` | PASS (rechecked after final report edits) |

The intermediate expanded M45.1 chain (`build/m45_1-full-release.log`) passed
the final aggregate but exposed the same obsolete compile-classpath model in
the separately requested legacy `verifyVeilDependency` helper (`resolved []`).
Its input lookup now uses the actual `veilMapped` preparation configuration;
all mapped-vs-raw Minecraft call/AT checks remain and pass. That intermediate
failure was not ignored or promoted; the final identical full task list above
passes after correction. No filesystem permission or dependency-resolution
blocker remains.

Final artifact (accepted in M45.2, **not** the byte-identical M44 output):

```text
C:\Users\camel\Documents\Всякое\CreateAeronautics\sable-backport\forge\build\libs\sable-forge-1.20.1-2.0.0-all.jar
size: 12,970,238 bytes
SHA-256: 8FB42371EE5FCE3C0D0842AAA0AC6FE2F36A2DE940AFA5ABF917138ADE1355DC
```

This differs from accepted M44 by 1,506 bytes and by SHA-256, as expected from
two changed/two added application classes. Native/nested dependencies and all
unrelated gameplay remain byte-identical. The M45.1 static tasks launched no
Minecraft. Final runtime acceptance was subsequently reported by the user and
is recorded separately in M45.2 below, not inferred from these static checks.

```text
M28-M45: CLOSED / PASS (M37 accepted with its documented fixture limitation)
M41 Create 6.0.8 compatibility coverage: CLOSED / COMPLETE
M45 static release audit: PASS
M45.1 production/fixture boundary: PASS
M45.1 live subscription smoke: PASS
Final runtime smoke: PASS
Backport: FUNCTIONALLY COMPLETE
Release candidate: ACCEPTED / RELEASE READY
M46: NOT REQUIRED
```

## M45.2 Final Runtime Acceptance

### Evidence provenance and static verification

The final runtime results in this section are **accepted user-reported evidence
supplied in the M45.2 task**, not a session independently reproduced during
bookkeeping. No runtime timestamps, unprovided samples, output counts or final
runtime log paths are invented. The inspected local latest/debug logs under
`forge/run/standalone-client/logs`, `forge/run/m6-client/logs` and `forge/logs`
predate M45.1 and do not provide the final smoke session. The standalone-client
historical log does contain the missing-sublevel packet warning; that is separate
historical evidence, not a substitute for the user's final results.

Existing static evidence was inspected, not regenerated:
`build/m45_1-release-final.log` records BUILD SUCCESSFUL in 3m 47s, 298 tasks,
and the architecture, boundary, namespace, standalone and retained feature gates.
`forge/build/m45Audit/hash-comparison.json` retains the 1,035 unchanged / two
changed / two added / zero removed class comparison. M45.1 static architecture,
full build and fixture-owned tick subscription verification remain PASS;
direct production -> fixture lifecycle operations remain 0.

### Live subscription and runtime outcomes

| Gate | Accepted final runtime evidence | Result |
|---|---|---|
| M27 neutral assembly subscription | Real Sable body UUID captured successfully | PASS |
| M27 fixture-owned post-physics observation | Samples received at steps 0, 1, 2, 5 and 20; all recorded numeric physics states FINITE | PASS |
| M27 inspect / cleanup | Inspect PASS, bodyPresent=true; READY_TO_DISASSEMBLE; cleanup PASS, sessionInvalidated=true, productionActorsUntouched=true | PASS |
| M27 basic fixture control payload | aero_surface_basic has no control-surface payload; expectedControlPayloadBlocks=0, controlSurfaceCount=0, controlBearingOwner=UNAVAILABLE | NOT_APPLICABLE, not a compatibility gap |
| M39 passenger lifecycle | Native MOUNT_SUCCESS; PASSENGER_UPDATE hiddenPlotPosition=false; cleanup passengersDetached=true | PASS |
| M42 physical door | Actor discovery; hiddenPlotTarget=false; native mutation; final PASS with nativeMutation=true; inner mechanism cleanup complete | PASS |
| M43 actor regression | Blaze gaze and Plough impulse validations passed; Drill/Saw damaged entities in AIR without duplicate damage; both block regressions passed; BASELINE PASS; RUN COMPLETE innerRetracted=true | PASS |
| Final smoke error/geometry checks | No previous create:goggle_info exception or InjectionError; no investigated hidden-PoseStack or huge-entity-query regression reported | PASS |

These live results verify the new subscriptions in an actual running Minecraft
instance, beyond the earlier isolated/ASM tests. They do not imply arbitrary
control-surface, train or optional-mod coverage outside the accepted scope.
M37's synthetic INSERT receiver limitation remains documented, not erased.

### M38 synchronization observation

The user reported `Received a sub-level movement packet for a non-existent
sub-level` during M38 cleanup and during initial world loading before the final
smoke. Cleanup nevertheless completed successfully without an associated crash
or stack trace. Classification: **observed non-blocking client synchronization
warning; not a confirmed M45.1 regression**. No networking/lifecycle code is
changed to hide it, and no additional implementation milestone is opened.

### Accepted artifact verification

M45.2 inspected the existing file with native PowerShell `Get-FileHash` (SHA256)
and measured its byte length. Both exactly match the user-tested artifact:

```text
filename: sable-forge-1.20.1-2.0.0-all.jar
path: C:\Users\camel\Documents\Всякое\CreateAeronautics\sable-backport\forge\build\libs\sable-forge-1.20.1-2.0.0-all.jar
size: 12,970,238 bytes
SHA-256: 8FB42371EE5FCE3C0D0842AAA0AC6FE2F36A2DE940AFA5ABF917138ADE1355DC
verification: PASS
```

The jar is not rebuilt, renamed, overwritten, repackaged or regenerated. No Java,
fixture, Mixin, Gradle, registration, networking, runtime resource or Rapier/JNI
change is introduced by M45.2. Earlier build/static results are historical
verified evidence, not claims that a new full build was run for bookkeeping.

### Final decision and Git bookkeeping exception

**Sable 2.0.0, NeoForge 1.21.1 -> Forge 1.20.1: FUNCTIONALLY COMPLETE.**
**Create Aeronautics compatibility: ACCEPTED. M45: CLOSED / PASS. Release: READY.**
The full final ledger above is accepted; M41 inventory remains 23 concrete,
22 registered types, 27 sites, 46 variants, REAL_GAP_FOUND=0. M46 is NOT REQUIRED.

The branch is `backport/forge-1.20.1-sable-2.0.0`. Bookkeeping began at
`801eae9d3d21599519725824696d5193739c4a82` with no staged files, but with
uncommitted accepted M45.1 production/build/verifier changes and an already
modified `RUNTIME_MIXIN_OVERLAP.md`. M45.2 stages only this release audit,
`BACKPORT_STATUS.md` and `FINAL_KNOWN_LIMITATIONS.md` for its documentation commit.
All other existing changes are left untouched and uncommitted.

An annotated release tag is **not created**: the documentation commit cannot
represent the accepted jar's source while its M45.1 implementation remains
outside committed history. Existing tags use `mc<version>-<sable-version>-<loader>`;
no existing tag is moved or overwritten. This is a formal Git/source-provenance
bookkeeping obstacle, not a demonstrated gameplay defect or an M46 requirement.
Nothing is pushed. Functional and artifact acceptance are complete, but formal
repository release-tag closure remains incomplete for this explicit reason.

## Retained Final Smoke Procedure (accepted; no rerun requested)

The following is the retained M45.1 procedure, not a new test request in M45.2.
Its final acceptance is the user-reported evidence above, not an assertion that
every command below has a newly archived local log. The original plan used the exact
candidate hash on a backed-up test world. Four representative fixtures plus
one minimal historical observer check suffice; no fixture marathon. Wait 10-20 seconds
after world load first: no InjectionError/transformer crash.

1. Nested live piston preservation + physical block handoff (M42):

   ```mcfunction
   /function sable:m42/cleanup
   /function sable:m42/door
   ```

   Wait READY, click the generated Physics Assembler once, verify preserved
   inner payload, then:

   ```mcfunction
   /function sable:m42/run
   /function sable:m42/cleanup
   ```

2. Native entity/vector/gaze and terrain regression (M43):

   ```mcfunction
   /function sable:m43/cleanup
   /function sable:m43/setup
   ```

   Wait READY, click assembler once, then:

   ```mcfunction
   /function sable:m43/run_all
   ```

   Equip Create goggles normally (`/give @s create:goggles` if needed). Aim at
   ordinary Create physical blocks, the assembled Sable and test entities:
   ordinary overlay works; assembled/entity hits may be blank but must not crash.
   Confirm moving blocks/BER render without flash, then:

   ```mcfunction
   /function sable:m43/cleanup
   ```

3. Native dispenser projectile and transformed facing (M38):

   ```mcfunction
   /function sable:m38/cleanup
   /function sable:m38/projectile
   ```

   Wait READY, click assembler once, then:

   ```mcfunction
   /function sable:m38/run
   /function sable:m38/cleanup
   ```

4. Native parent-world riding and teardown (M39):

   ```mcfunction
   /function sable:m39/cleanup
   /function sable:m39/player
   ```

   Wait READY, click assembler once, then:

   ```mcfunction
   /function sable:m39/run
   ```

   Wait SEAT_CAPTURED, right-click visible Seat, confirm mount/motion; clean while
   seated to exercise safe detach:

   ```mcfunction
   /function sable:m39/cleanup
   ```

5. One minimal observer-ownership check (M27, not a M24-M27 marathon):

   ```mcfunction
   /sable m27 cleanup
   /sable m27 fixture aero_surface_basic
   ```

   Click its generated Physics Assembler once. Require exactly one matching
   `SABLE_M27_FIXTURE_BODY_CAPTURE ... captureStatus=PASS` with stored Sable UUID
   (neutral completion subscription), then bounded
   `SABLE_M27_AERO phase=POST_PHYSICS_SAMPLE ... step=0/1/2/5/20`
   (fixture-owned END-level tick subscription). Wait at least one second, then:

   ```mcfunction
   /sable m27 inspect aero
   /sable m27 prepare_disassembly
   ```

   Require normal inspect evidence and READY_TO_DISASSEMBLE. Click the assembler
   to disassemble natively, then `/sable m27 cleanup`. Cleanup intentionally does
   not delete an assembled Sable: do not skip the native disassembly. This small
   extra check is recommended because isolated tests/ASM prove the observer
   code and registration metadata but do not execute Forge automatic subscriber
   discovery in a live world. No new flag or fixture commands were introduced.

Acceptance: native fixture PASS/evidence markers, cleanup complete/idempotent,
no Sable exception, no InjectionError, no goggle ClassCastException, no hidden
PoseStack violation or unexpected huge entity query. Ignore unrelated known
modpack warnings unless newly caused by Sable. Never claim runtime acceptance
from the static gates alone. No automatic M46.
