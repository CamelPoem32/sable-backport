# Create 6 Target Runtime Smoke

Date: 2026-08-23

## Standalone Packaged-Artifact Runtime Acceptance Complete (2026-08-23)

The final M7 standalone packaged-artifact smoke is complete. The accepted run
logged:

```text
SABLE_STANDALONE_RUNTIME phase=gate2-3 status=PASS
SABLE_STANDALONE_RUNTIME phase=lifecycle status=PASS
SABLE_STANDALONE_RUNTIME phase=gate5 status=PASS
SABLE_STANDALONE_RUNTIME phase=complete status=PASS
BUILD SUCCESSFUL
```

Treat M7 as closed. Do not change the standalone mapper, provenance parser, or
packaged-artifact harness unless new evidence requires it. The next milestone is
M8 Rapier / real Sable physics planning and static staging; no Minecraft launch
belongs to that planning task.

## M8.2 Rapier Native/JNI Static Acceptance (2026-08-24)

No Minecraft client/server was launched, no native library was loaded, and no
Rust build/regeneration was performed. The new
`.\gradlew.bat --offline :forge:verifyRapierNativeBackportStatic` task passed.

The verifier statically decompresses
`natives/sable_rapier/sable_rapier_binaries.zip.l4z`, proves the exact six
Windows/Linux/macOS x86_64/aarch64 native binaries, validates deterministic
sizes/SHA-256 inventory, checks `53` Java `Rapier3D` native methods against
`53/53` JNI symbol coverage in every packaged binary, verifies matching Rust JNI
export names and static `JNIEnv`/`JClass` receiver shape, and confirms the loader
resource path `/natives/sable_rapier/sable_rapier_binaries.zip.l4z` plus
extraction directory `.sable/natives`.

Packaging remains disabled for runtime selection: Rapier is isolated from
`sourceSets.main`, the common service descriptor still selects
`StaticPhysicsPipelineProvider`, the Rapier service descriptor remains owned by
`sable_rapier`, the native payload has one source owner, and no
Create/Flywheel/Ponder/Veil jars are bundled by Rapier resources. M8.3 should
package the already-staged Rapier Java/resources/native payload as a distinct
Forge JarJar/common-library boundary and statically prove ServiceLoader selects
Rapier while Static remains fallback, before any runtime smoke.

## M8.3 Rapier Packaged-Artifact Static Acceptance (2026-08-24)

No Minecraft client/server was launched, no native library was loaded, and no
Rust build/regeneration was performed. The new
`.\gradlew.bat --offline :forge:verifyRapierPackagedArtifact` task passed.

The final Sable Forge `-all.jar` now nests one distinct Rapier common-library
JarJar artifact:

```text
dev.ryanhcode.sable-rapier:sable-rapier-common-1.20.1:2.0.0
META-INF/jarjar/sable-rapier-common-1.20.1-2.0.0.jar
```

The nested Rapier jar contains `16` Rapier classes, the Rapier
`PhysicsPipelineProvider` service descriptor, native README/license resources,
and exactly one copy of
`natives/sable_rapier/sable_rapier_binaries.zip.l4z`
(`SHA-256 427CAA80B6B7D365703C3196B20AA29097EBF7FE5A61E20ED8C57B2A71BA0401`).
It contains no Forge/NeoForge mod metadata, Companion classes, outer Sable
classes, Create/Flywheel/Ponder/Registrate/Veil jars, development outputs, or
split packages with outer Sable/Companion.

Rapier has Minecraft member references, so the packaged nested library is
derived from the accepted named/userdev Rapier jar by
`RapierProductionJarMapper` using the existing `createMcpToSrg/output.tsrg`
mapping. Static namespace proof: `46` production/SRG Minecraft member
references, `0` named Minecraft member references remaining. The outer Sable
production reobf artifact remains the distributable target; the Rapier mapper is
only for the new nested Rapier production library.

Rapier requires `net.jpountz.lz4.LZ4FrameInputStream` at runtime. M8.3 therefore
deliberately nests exactly one `at.yawk.lz4:lz4-java:1.11.0` runtime jar at
`META-INF/jarjar/lz4-java-1.11.0.jar`
(`SHA-256 535C5578CAB5DCD0A438E202DF80091632B873C0370C25D9B1C1AD1D73577207`).
`org.apache.maven:maven-artifact:3.8.5` remains compile-only: Rapier runtime
bytecode contains no Maven Artifact references, and Maven Artifact is not
bundled.

The effective static provider set is:

```text
RapierPhysicsPipelineProvider priority=1000
StaticPhysicsPipelineProvider priority=900
winner=RapierPhysicsPipelineProvider
```

M8.4 should add a static gravity/collision smoke boundary around one existing
stone sublevel with Rapier selected, still without Create/Flywheel deferred
Mixins or advanced rendering. Runtime proof remains a later one-launch
milestone.

## M8 Rapier Runtime Acceptance Complete (2026-08-24)

M8.4 standalone preparation and M8.5 packaged-artifact runtime acceptance are
closed. The accepted one-process standalone runtime proved packaged nested
Rapier provenance, nested LZ4 provenance, Rapier provider selection over Static
fallback, native/JNI initialization, fresh dynamic sublevel creation, gravity,
collision/settling, finite state, clean save, and clean shutdown.

Accepted runtime markers:

```text
SABLE_STANDALONE_RUNTIME phase=provenance status=PASS
SABLE_STANDALONE_RUNTIME phase=gate1 status=PASS
SABLE_RAPIER_SMOKE phase=provider status=PASS provider=RapierPhysicsPipelineProvider
SABLE_RAPIER_SMOKE phase=native status=PASS
SABLE_RAPIER_SMOKE phase=spawn status=PASS freshObject=m8_rapier_gravity_smoke initialY=88.0 mass=2.0 initial linear/angular velocity=0
SABLE_RAPIER_SMOKE phase=gravity status=PASS initialY=88.0 minY=81.3507080078125 currentY=81.49720764160156
SABLE_RAPIER_SMOKE phase=collision status=PASS platformY=80 stableTicks=20 finalY=81.49720764160156 finalVy=6.769740593881579E-6
SABLE_STANDALONE_RUNTIME phase=gate2-3 status=PASS
SABLE_STANDALONE_RUNTIME phase=lifecycle status=PASS
SABLE_STANDALONE_RUNTIME phase=gate5 status=PASS
SABLE_STANDALONE_RUNTIME phase=complete status=PASS
BUILD SUCCESSFUL
```

Non-blocking observation: the run logged three early
`Received a sub-level movement packet for a non-existent sub-level` messages.
They did not prevent provider/native/gravity/collision/lifecycle acceptance.
Do not fix them unless later evidence shows a user-visible runtime issue.

## M9.1 Sable ↔ Lithium Static Compatibility Status (2026-08-24)

No Minecraft client/server was launched. The actual Lithium provider in the
target modpack is `radium-mc1.20.1-0.12.4+git.26c9d8e.jar`
(`version = "0.12.4+git.26c9d8e"`, `provides=["lithium"]`,
SHA-256 `B42584E2672D6B5329959EC2B0F342395B19389FED0EE4D86EA71072F42F77A0`).

Radium/Lithium's enabled
`entity.collisions.unpushable_cramming.EntityMixin` injects
`onBlockCached` into `Entity#getFeetBlockState`/production `m_146900_()` at
`@At(value="INVOKE_ASSIGN", target=Level/World#getBlockState(BlockPos),
shift=AFTER)` and calls `lithiumOnBlockCacheSet` with the cached
`feetBlockState`. Neighbor hooks in the same mixin clear that cache from
`setPos(DDD)V` and `baseTick()V`.

Sable's conflict was its priority-1100 `@Overwrite` of
`Entity#getFeetBlockState`, which replaced the vanilla method before Lithium's
default-priority injection could find the expected lookup/assignment. M9.1
removes that overwrite and preserves the vanilla lookup using two MixinExtras
`@ModifyExpressionValue` handlers: one forces a cache refresh only while Sable
is tracking a sublevel, and one substitutes the Sable-aware sublevel block
state after the vanilla `Level#getBlockState(BlockPos)` call while maintaining
`sable$inBlockStatePos`.

`.\gradlew.bat --offline :forge:verifyLithiumSableCompatibility -x :forge:compileJava`
passed against the current class outputs and exact Radium jar, proving the
Lithium `INVOKE_ASSIGN` canary, Sable overwrite removal, Sable expression
modifiers, and same-mixin neighbor hooks. Full `compileJava`/`build` and a
fresh production artifact check remain blocked by recurring Windows/ForgeGradle
generated-output locks on `forge/build/downloadMcpConfig/output.zip` and
`sable_companion_1_20/build/libs/sable-companion-common-1.20.1-1.6.0.jar`.

## M9.2 Veil Runtime Boundary Static Status (2026-08-24)

No Minecraft client/server was launched. The production pack's new crash is
inside Veil shader bootstrap (`could not preload blit shader` because
`VeilRenderSystem.renderer()` is null) with Embeddium, Oculus, and an active
shaderpack present. The retained Sable Forge core/Rapier graph does not require
Veil's renderer/runtime for the accepted feature set.

Current retained Veil references were classified as:

- active mandatory runtime before M9.2: Veil platform registry helper types in
  physics block-property and force-group holders;
- active client-only before M9.2: mixin-plugin platform/mod checks and retained
  vanilla sublevel render dispatcher bridge/frustum types;
- deferred/excluded: debug UI, Veil network gizmo packets, shader processors,
  water occlusion, fancy/chunked rendering, Sodium/Embeddium reacharound
  rendering, and Veil-specific shader/framebuffer code;
- enabled Mixin configs: no direct Veil target/reference.

M9.2 removes Veil from normal Forge compile/runtime dependencies and removes the
mandatory `mods.toml` Veil dependency. Core physics now uses local
`SableRegistryObject` holders instead of Veil `RegistrationProvider`/
`RegistryObject`; `AbstractSableMixinPlugin` uses `SableLoaderPlatform`;
retained vanilla rendering no longer exposes Veil `CullFrustum`, `MatrixStack`,
or `VeilRenderBridge` types. Rapier implementation/mapper/native payload,
Companion, and Create/Flywheel/Ponder staging are unchanged.

Static evidence from current outputs: `activeCompiledVeilRefs=0`,
source `mods.toml` has no Veil dependency, and enabled mixin configs have `0`
Veil references. `:forge:verifyVeilRuntimeBoundary` exists and correctly fails
on the currently stale built jar because it still has `currentJarVeilRefs=11`
and old Veil dependency metadata. A refreshed production artifact is still
blocked by recurring Windows/ForgeGradle `downloadMcpConfig/output.zip`
AccessDenied; no Veil/Oculus compatibility patch was attempted.

## M9.6 Companion Production Namespace Static Status (2026-08-25)

No Minecraft client/server was launched. The production failure was a named
`INVOKEVIRTUAL Entity.position()Lnet/minecraft/world/phys/Vec3;` left in the
Companion library nested at
`META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar` while ModLauncher was
running in SRG namespace. The exact mapping from
`createMcpToSrg/output.tsrg` is `Entity.position() -> m_20182_`; the same
`getFeetPos` method also contains other Minecraft calls such as
`Entity.getEyeHeight() -> m_20192_`, so the repair is a full Companion
namespace pass, not a literal one-method patch.

The provenance gap was that Forge JarJar nested the Java 17 Companion common
jar directly from `:sable_companion_1_20:jar`. The outer Sable artifact is
reobfuscated by the normal Forge path and nested Rapier is remapped by
`RapierProductionJarMapper`, but Companion had no equivalent named -> SRG
production step. `remapCompanionBackportJarToProduction` now maps the Companion
jar using the same hierarchy-aware mapper and
`packageCompanionIntoFinalForgeArtifact` replaces the nested Companion before
Rapier/LZ4 packaging.

Standalone userdev staging now extracts the production nested Companion jar and
maps it back from SRG -> named with `SableStandaloneUserdevMapper`, matching the
existing nested Rapier userdev treatment. `verifyCompanionProductionNamespace`
is wired to inspect the final nested Companion jar, assert
`SableCompanion.getFeetPos` uses `Entity.m_20182_`, scan every Companion
Minecraft member reference including Handles/ConstantDynamic data, prove the
ServiceLoader descriptor is unchanged, and verify the standalone nested
Companion has no stale SRG references.

`spotlessApply` and buildSrc compilation passed. `:forge:build` and the
artifact-backed Companion/Rapier/access/Lithium verifiers were blocked before
project compilation by the recurring external Windows
`forge/build/downloadMcpConfig/output.zip` `AccessDeniedException`.
`:forge:verifyAttributeRegistrationBoundary` and
`:forge:verifyVeilRuntimeBoundary` still pass.

Packaging-row follow-up: `packageCompanionIntoFinalForgeArtifact` failed
because it looked for artifact id `sable-companion-common-1.20.1`, but the
established JarJar metadata row is
`dev.ryanhcode.sable-companion:sable_companion_1_20:1.6.0` with nested path
`META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar`. The task now uses
that canonical identity, does not require a filename-style artifact row, and
the Companion namespace verifier checks that the final nested jar bytes match
the SRG remap output SHA
`203BB5E93CBD8E01482891A46638AA11E9CA1215D2D781C746724C603BEC28D0`.
`spotlessApply` passed after the fix; the build/verifier refresh remains
blocked first by the same external `downloadMcpConfig/output.zip`
`AccessDeniedException`.

## M9 Production Spawn Command Boundary (2026-08-25)

No Minecraft client/server was launched. `/sable spawn block <block> <name>` is
not a smoke-only command: source and current production bytecode register it via
`SableSpawnCommands.register`, it has an execute handler for the named block
path, and the normal success path calls `SubLevelContainer.allocateNewSubLevel`,
places/finalizes the single block, sends `commands.sable.spawn.success`, and
returns Brigadier result `1`.

The production report (autocomplete present, execution prints nothing,
`/sable info @l` finds no sublevels, and `execute store success` leaves the
score unchanged) points at an unchecked runtime failure escaping before
`sendSuccess`/`return 1`, not at a missing command node or a smoke flag gate.
The handler now preserves the same normal creation path but converts runtime
spawn/finalization failures into a user-visible Brigadier
`CommandSyntaxException`, logs the original exception, and rolls back any
partially allocated sublevel. The failure translation key is
`commands.sable.spawn.block.failure`.

New `:forge:verifySableSpawnCommandBoundary` passed and proves the production
registration reaches the handler, success returns `1`, normal sublevel creation
is used, and neither `sable.runtimeSmoke` nor `sable.standaloneRuntimeSmoke`
controls the path. `spotlessApply` passed. `:forge:compileJava` was blocked
before javac by the known external Windows
`forge/build/downloadMcpConfig/output.zip` `AccessDeniedException`; no
Minecraft launch was attempted.

Follow-up production log evidence exposed an independent packaging regression:
one rebuilt `sable-forge-1.20.1-2.0.0-all.jar` selected
`StaticPhysicsPipelineProvider` because that artifact contained only nested
Companion + MixinExtras and the outer physics provider service descriptor only
listed Static. Nested Rapier and LZ4 entries/metadata were absent, matching the
real log's lack of JarJar discovery for
`sable-rapier-common-1.20.1-2.0.0.jar` and `lz4-java-1.11.0.jar`.

Root cause is task graph reachability after M9.6: `reobfJarJar`/normal
`build` could refresh the final `-all.jar` without necessarily running the
later Companion->Rapier/LZ4 final packaging verifier chain. `:forge:build` now
depends on `:forge:verifyRapierPackagedArtifact`, and the Companion production
remap is ordered after the base `reobfJarJar` so the intended final pipeline is
base all-jar -> Companion replacement -> Rapier/LZ4 nesting -> final package
verification. The verifier already requires exactly one nested Companion,
Rapier, LZ4, and MixinExtras metadata row and the Rapier provider descriptor.
The current on-disk jar again contains Rapier and LZ4, so the packaging
regression is considered fixed, but it still needs the M9.7 mapper refresh
below before the next production run.

The old Static-only final artifact SHA-256 was
`E3A46DE3A263D4DCD22AE462E79D9539BF860D34FF3E671B253672E53B364807`; it is not
acceptable for production.

## M9.5 Rapier Production Namespace Static Status (2026-08-24)

No Minecraft client/server was launched. The production failure was an
`INVOKEVIRTUAL ServerLevel.getBlockState(BlockPos)` left in the nested Rapier
jar while ModLauncher used SRG naming. The declaration hierarchy is
`ServerLevel -> Level -> LevelAccessor -> CommonLevelAccessor -> LevelReader ->
BlockAndTintGetter -> BlockGetter`; the exact mapping is
`BlockGetter.getBlockState(BlockPos) -> m_8055_`.

`RapierProductionJarMapper` now resolves inherited methods and fields through
the actual mapped Forge/Minecraft hierarchy for ordinary instructions, Handles,
invokedynamic bootstrap data, LDC Handles, and recursive ConstantDynamic data.
The full scan also rejects unresolved Minecraft members, closing the old
exact-owner verifier gap. A direct static probe remapped 74 member references,
including 19 inherited references, and produced
`ServerLevel.m_8055_(BlockPos)` with 61 verified SRG references, zero stale
named references, and zero unresolved references.

`:forge:verifyRapierProductionNamespace` is wired to inspect the nested Rapier
jar in the final `sable-forge-1.20.1-2.0.0-all.jar` and preserve the existing
Rapier/native/LZ4/Companion package boundary. `spotlessApply`, buildSrc compile,
`:forge:verifyAttributeRegistrationBoundary`, and
`:forge:verifyVeilRuntimeBoundary` passed. The final build and artifact-backed
Rapier/access/Lithium verifiers were blocked before project compilation by the
known external Windows `forge/build/downloadMcpConfig/output.zip`
`AccessDeniedException`.

## M9.7 Rapier Production InvokeDynamic/SAM Namespace Static Status (2026-08-25)

No Minecraft client/server was launched. Production now selects
`RapierPhysicsPipelineProvider`, then fails for ordinary block changes when a
LambdaMetafactory call-site in
`RapierVoxelColliderBakery.buildPhysicsDataForBlock` still uses the named
Minecraft SAM method `Shapes$DoubleLineConsumer.consume(DDDDDD)V`. The exact
production/SRG mapping is
`Shapes$DoubleLineConsumer.consume(DDDDDD)V -> m_83161_`. The neighboring
`VoxelShape.forAllBoxes(Shapes$DoubleLineConsumer)` call is already mapped to
`m_83286_`, so the remaining defect is specifically
`InvokeDynamicInsnNode.name`, not ordinary MethodInsn/FieldInsn/Handle mapping.

`RapierProductionJarMapper` now performs a general post-remap LambdaMetafactory
SAM-name pass: it resolves the functional-interface owner from the invokedynamic
return type, the erased SAM descriptor from bootstrap argument `0`, remaps only
Minecraft/Mojang SAM methods through the existing hierarchy-aware mapping
logic, and leaves unrelated invokedynamic call-sites unchanged. The production
reference report now includes invokedynamic/SAM names; the Rapier verifier
explicitly requires
`INDY_SAM net/minecraft/world/phys/shapes/Shapes$DoubleLineConsumer.m_83161_(DDDDDD)V`
and rejects stale `consume`.

Static status: `:buildSrc:compileJava` passed and `:forge:spotlessApply`
passed. The requested Forge build/verifier chain is still blocked by recurring
external Windows/ForgeGradle generated-output locks: first
`forge/build/downloadMcpConfig/output.zip`, then after one narrow generated-file
cleanup `sable_companion_1_20/build/libs/sable-companion-common-1.20.1-1.6.0.jar`,
and finally `downloadMcpConfig/output.zip` again. The current on-disk final jar
SHA-256 is
`1FA01B9F581548E1446D574C3A305786758529D5968F7728C502174E0D314D0E`; it contains
Rapier/LZ4 but was not refreshed after the M9.7 mapper change and must not be
used as the next production test artifact.

## M9.8 Production Spawn Command Tree Static Status (2026-08-25)

No Minecraft client/server was launched. Fresh production evidence proves world
startup, Rapier provider selection, and `/sable info @l`, but
`/sable spawn block minecraft:stone m9_production_smoke` prints nothing,
creates nothing, and leaves `execute store success` unchanged. Source and
current production bytecode inspection did not reveal a missing terminal
Brigadier callback: the command tree is `sable` -> `spawn` -> `block` ->
required `block` argument -> optional required `name` argument, and
`namedSpawnFinale` attaches `.executes(...)` to both `<block>` and
`<block> <name>`. The current production bytecode shows the callback chain
`lambda$namedSpawnFinale$14 -> NamedSpawnInvoker.run -> lambda$register$10 ->
spawnBlock`.

The missing production evidence is therefore an observability gap rather than a
proven tree-registration defect: the old handler could fail before any
user-visible output and before the command name appeared in logs. The handler
now logs `SABLE_SPAWN phase=entered ...` before sublevel-container lookup,
logs `SABLE_SPAWN phase=complete ...` after successful initialization, returns
`1` on success, and keeps the existing rollback +
`CommandSyntaxException` failure boundary. No smoke property gates the
production path.

`verifySableSpawnCommandBoundary` now verifies the root/event-bus registration,
the `spawn block <block> <name>` terminal callback shape, the
`namedSpawnFinale` terminal executes path, entry/complete telemetry, success
return value, normal `allocateNewSubLevel` creation, useful failure handling,
and absence of `sable.runtimeSmoke`/`sable.standaloneRuntimeSmoke` gating.
Validation is blocked before tasks run: offline mode lacks cached
`net.minecraft:client:1.20.1`, and sandboxed online Gradle resolution receives
`Permission denied: connect`. The current on-disk final jar SHA-256 is
`E56BD732B36F85776D23928A98A81EB0732B77A255037151404D4F0256BB4EBB`; it has not
been rebuilt with the M9.8 probes.

## M9.14 Rapier/Sable ABI and Spawn Rollback Static Status (2026-08-26)

No Minecraft client/server was launched. Fresh production evidence progressed
past all prior ChunkMap access fixes and reached `SABLE_SPAWN phase=chunk_created`
for a plot chunk centered at `[1280064,1280064]`, then failed during the block
placement/physics notification path with:

```text
NoSuchMethodError:
dev.ryanhcode.sable.util.LevelAccelerator.getBlockState(BlockPos):BlockState
```

Static inspection proved the final outer Sable artifact contains
`LevelAccelerator.m_8055_(BlockPos):BlockState`, because
`LevelAccelerator#getBlockState(BlockPos)` implements the Minecraft
`BlockGetter#getBlockState` override, whose production/SRG name is `m_8055_`.
The nested production Rapier jar still called the Sable-owned named method
`LevelAccelerator.getBlockState(BlockPos)`. This is a cross-artifact ABI problem,
not a direct `net/minecraft` namespace problem.

`RapierProductionJarMapper` now includes compiled Sable classes in the remap
hierarchy and maps Sable-owned method references when their class hierarchy
proves that the referenced method implements or overrides a mapped Minecraft
method. `verifyRapierProductionNamespace` now also performs a packaged ABI scan
from nested Rapier to final packaged Sable/Companion classes and has a canary
for `RapierPhysicsPipeline.handleChunkSectionAddition` resolving
`LevelAccelerator.m_8055_` instead of stale `getBlockState`.

The failed spawn had already allocated and registered a `ServerSubLevel`, marked
occupancy dirty, notified observers, created a plot chunk/holder, inserted it
into `ChunkMap.updatingChunkMap`, marked the chunk map modified, initialized
lighting/chunk status, and begun block placement/physics notification. The old
inner rollback handled `RuntimeException` only, so the `NoSuchMethodError` path
could leave lower-level plot/chunk/save state behind after `phase=chunk_created`.
The spawn path now rolls back any post-allocation `Throwable` through canonical
`SubLevelContainer.removeSubLevel(..., REMOVED)` and logs
`rollback_begin`/`rollback_complete`/`rollback_failed` without changing success
semantics.

The worldgen warning at approximately `[20481024, -64, 20481040]` matches
`1280064 * 16`, so it is aligned with the Sable plot chunk coordinates. The
spawn code still constructs an empty `LevelChunk` directly; the warning is
currently treated as vanilla/Forge systems observing the inserted far-out chunk
status transition, not proof that Sable intentionally ran terrain generation.

Validation: `:buildSrc:compileJava` PASS, `:forge:spotlessApply` PASS, and
`:forge:verifySableSpawnCommandBoundary` PASS. Fresh `:forge:build`,
`:forge:verifyProductionMinecraftAccessBoundary`, and the new packaged ABI
verification are blocked before compile/reobf by the recurring ForgeGradle
`AccessDeniedException` on `forge/build/downloadMcpConfig/output.zip`. No fresh
M9.14 all-jar was produced or scanned. Current stale on-disk `-all.jar` SHA-256:
`E931B72B160F32B939A8B600F6AE26DE9DC658F80017FE078C7B02A9EB831156`.

## M9.15 Single-Block Mass Initialization Static Status (2026-08-26)

No Minecraft client/server was launched. Fresh production evidence reached
`SABLE_SPAWN phase=block_set old=minecraft:air new=minecraft:stone`, then the
command's own guard threw `Initial spawn block produced invalid mass data`.
The exact guard is `serverSubLevel.getMassTracker().isInvalid()`, meaning the
merged mass remains `<= 0.0`. The command now logs a bounded
`SABLE_SPAWN phase=mass_check` line with merged mass, self mass,
center-of-mass values, plot bounds, block state, and physics-system presence
before rejecting invalid data.

The expected mass source for ordinary stone remains valid: `minecraft:stone`
matches `#c:stones`, which is included by `#sable:heavy`; the heavy block
properties set `sable:mass = 2.0`. The synchronous update chain is
`LevelChunk.setBlockState` -> retained `LevelChunk` Mixin ->
`SableCommonEvents.handleBlockChange` ->
`SubLevelPhysicsSystem.handleBlockChange` ->
`updateMassDataFromBlockChange` / `PhysicsChunkTicketManager` /
`RapierPhysicsPipeline`, followed by the command's mass-tracker rebuild and
merged-mass validation.

The production command differed from the working assembly/M8 path by placing
the first block through `EmbeddedPlotLevelAccessor#setBlock`, which delegates
to parent `ServerLevel#setBlock` at the far plot coordinate. The earlier
worldgen warning at approximately `1280064 * 16` aligned with that plot
coordinate and is consistent with the parent-level path touching vanilla/Forge
chunk generation surfaces. The command now mirrors the proven assembly storage
boundary for the first block: write directly to the created
`PlotChunkHolder`'s `LevelChunk`, require a non-null previous state, re-read the
stored state from the same chunk, and only then run normal finalization.

Rollback is unchanged in architecture and still uses canonical
`SubLevelContainer.removeSubLevel(..., REMOVED)` for any post-allocation
`Throwable`. `verifySableSpawnCommandBoundary` now proves entry/complete/failed
diagnostics, rollback diagnostics, direct initial plot-chunk write, persisted
state verification, mass-check diagnostics, `return 1` only after finalization,
and no smoke-property dependency. No additional save-hang leak is statically
proven; the shutdown observation should be retested only after a fresh artifact
is built and manually launched.

Validation: `:forge:spotlessApply` PASS and
`:forge:verifySableSpawnCommandBoundary` PASS. `:forge:build`,
`:forge:verifyProductionMinecraftAccessBoundary`, and
`:forge:verifyRapierProductionNamespace` all stopped before javac/artifact
verification at the known ForgeGradle
`AccessDeniedException: forge/build/downloadMcpConfig/output.zip`. Current
on-disk `-all.jar` SHA-256 is
`DBB9D7E45A3FE5890B528A5C8739B9B528A9F68AA9ACF1BC9C599F96368396B3`, but it is
not fresh M9.15 acceptance evidence.

## M9.16 ClipContext Client Raycast Access Static Status (2026-08-26)

No Minecraft client/server was launched. Fresh production runtime proved the
M9.15 spawn/mass path: the command wrote and re-read `minecraft:stone`, logged
`mass=2.0`, `selfMass=2.0`, `physicsSystemPresent=true`, then reached
`registered`, `complete`, and `exit outcome=success:1`. The later crash is an
independent client raycast access issue:
`BlockGetterClipHelper.copyContext` directly read private
`ClipContext.collisionContext` / `f_45686_`.

The copied `ClipContext` state is now handled by the narrowest available
boundary. `from=f_45682_` and `to=f_45683_` have public getters, while
`block=f_45684_`, `fluid=f_45685_`, and
`collisionContext=f_45686_` are `private final` fields with no public getter in
1.20.1. `ClipContextAccessor` exposes only those three fields, and
`BlockGetterClipHelper.copyContext` constructs the copied context from the
transformed `from`/`to`, original block/fluid modes, and the same
entity-derived collision-context semantics as before.

The generic production access verifier did scan `mixinhelpers/**`, but it
accepted the direct field reads because the Forge AT still listed the
ClipContext fields, classifying them as `SAFE_AT`. The obsolete AT rows for
`f_45684_`, `f_45685_`, and `f_45686_` are removed, and the verifier now treats
those fields as accessor-managed so ordinary helper bytecode may not read them
directly even if a stale AT is reintroduced. Specific source and final-jar
canaries require `ClipContextAccessor`, retained mixin config/refmap wiring,
and no direct helper access.

Source-level audit of active `dev/ryanhcode/sable/mixinhelpers/**` found no
other direct SRG field, `.block`, `.fluid`, `.collisionContext`, reflection, or
private-lookup hazards. The prior shutdown evidence is not changed: after the
client crash the integrated server saved all dimensions, so no new rollback or
successful-spawn cleanup was added.

Validation: `:forge:spotlessApply` PASS and
`:forge:verifySableSpawnCommandBoundary` PASS. `:forge:build`,
`:forge:verifyProductionMinecraftAccessBoundary`, and
`:forge:verifyRapierProductionNamespace` were blocked before javac/fresh
artifact scanning by the recurring ForgeGradle
`AccessDeniedException: forge/build/downloadMcpConfig/output.zip`. Current
on-disk `-all.jar` SHA-256 is
`9334B59A86F856960F9F029145A72CD180376BACC98E8CB5484369753DC5602B`, but it is
not fresh M9.16 acceptance evidence.

## M9.17 Player Collision and Basic Single-Block Render Static Status (2026-08-26)

No Minecraft client/server was launched. Fresh production runtime proved the
M9.15/M9.16 smoke spawn itself now succeeds: the command reaches `block_set`,
`mass_check mass=2.0 selfMass=2.0`, `registered`, `complete`, and
`exit outcome=success:1`. The new failure is after success, when ordinary
player movement/raycast-adjacent logic asks for the block beneath the player.

The failing path is the retained
`entities_stick_sublevels.effects.EntityMixin#sable$preGetOnPos` hook. It
transformed the player's feet into sublevel-local plot coordinates and returned
that `BlockPos`. Vanilla `Entity.getBlockStateOn()` then used the parent
`Level` / `ServerLevel`, causing `ServerChunkCache.getChunkBlocking()` to try
to load the far plot-storage coordinate instead of reading Sable plot storage.
That explains the production exception `No chunk holder after ticket has been
added` at the internal plot chunk near `[1280064,1280064]`.

`SubLevelBlockStateLookup` is now the shared active boundary for this corridor.
It resolves a plot block position through the owning sublevel's plot chunk
holder and chunk, and returns air when the plot chunk is absent; it does not
call parent `Level.getBlockState`, `getChunk`, or `getChunkBlocking`. The
get-on-pos mixin now uses it for sublevel feet checks and redirects
`getBlockStateOn` so both tracking and pre-tracking local positions avoid
parent-world chunk loading. The adjacent active
`entity_sublevel_collision` feet-block lookup and `entities_in_blocks`
inside-block scan now use the same plot-aware lookup. No fake vanilla chunks,
new tickets, or smoke-coordinate special cases were added.

The invisible single-block symptom was investigated independently. The retained
basic render lifecycle is still present:
`ClientSubLevel` creates/resizes render data, `VanillaSubLevelRenderDispatcher`
selects `VanillaSingleSubLevelRenderData` for single-block plots,
`SingleBlockSubLevelWrapper` exposes the cached state, and Forge
`SableSubLevelRenderPlatformImpl` tessellates via vanilla model APIs. The first
missing stage was state acquisition: the dispatcher/render data were reading
the client parent level at plot coordinates, which could cache air. They now
read state/block entities through `SubLevelBlockStateLookup`. Deferred
chunked/advanced/Veil/Flywheel rendering remains deferred.

Validation: `:forge:spotlessApply` PASS,
`:forge:verifySubLevelEntityCollisionBoundary` PASS,
`:forge:verifyBasicSubLevelRenderLifecycle` PASS, and
`:forge:verifySableSpawnCommandBoundary` PASS. `:forge:build`,
`:forge:verifyProductionMinecraftAccessBoundary`, and
`:forge:verifyRapierProductionNamespace` were blocked before javac/fresh
artifact scanning by the recurring ForgeGradle
`AccessDeniedException: forge/build/downloadMcpConfig/output.zip`. Current
on-disk `-all.jar` SHA-256 is
`9302E5230DE31FCA95F6F3157804AEE61185985A5D6969BFD5A3939157188F35`, but it is
not fresh M9.17 acceptance evidence.

## M9.18 Remaining Plot-Coordinate Escapes and Render Diagnostics Static Status (2026-08-26)

No Minecraft client/server was launched. User production evidence corrected
the M9.17 artifact status: the M9.17 build was installed and both
`forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar` and the `.minecraft/mods`
copy matched SHA-256
`01832B23B1311D03FD4663FD9D27EC5083423742BE02C969239F8D6D5FCDC79F`.
The new failures are therefore incomplete plot-coordinate handling, not stale
artifact behavior.

The remaining player-tick path maps to
`climbing_sub_levels.LivingEntityMixin#sable$redirectPos`. That mixin is
enabled in `sable-common-forge.mixins.json` and appears in the compiled refmap.
Its redirect transforms the living entity position into an intersecting
sublevel's local/plot coordinates, then the M9.17 code still called parent
`Level.getBlockState(pos)`. M9.17 missed it because the verifier only covered
the edited `entities_stick_sublevels.effects.EntityMixin`/feet/inside-block
source corridors, not this separate climbing redirect body or its compiled
bytecode.

`SubLevelBlockStateLookup` now has generic plot-aware `(BlockGetter, BlockPos)`
block/fluid helpers. If the receiver is a parent `Level` and the queried
position belongs to an active Sable plot, the helper resolves the owning
sublevel and reads the plot chunk directly; otherwise it delegates to vanilla
`BlockGetter`. `climbing_sub_levels.LivingEntityMixin#sable$redirectPos` now
uses the owning sublevel lookup. `BlockGetterClipHelper.originalClip` and its
generated lambda now use the generic block/fluid helpers, so transformed
line-of-sight/raycast positions no longer route internal plot coordinates to
ordinary parent `ServerChunkCache` loading.

Additional active transformed-position escape sites fixed in the same pass:
block placement intersection, fluid spread edge checks, `CanFallAtleast`
sublevel support, explosion ray contribution checks, `EatBlockGoal` after
sublevel position redirection, camera fluid/fog checks, and tamed teleport
obstruction checks. These are semantic lookup-boundary fixes only; no Rapier,
spawn, packet registration, packaging, fake chunk, forced-ticket, or
coordinate-specific changes were made.

The basic render path still has this lifecycle:
server registration -> tracking/full-sync bundle -> client
`handleStartTracking` -> `ClientSubLevelContainer.allocateSubLevel` -> client
chunk packet replacement -> finalize -> `ClientSubLevel.updateRenderData` ->
`VanillaSubLevelRenderDispatcher` -> `VanillaSingleSubLevelRenderData` ->
single-block draw. Production still lacks evidence for which stage hides the
spawned stone, so one-shot diagnostics were added for the next run:
`SABLE_CLIENT phase=sublevel_create_received`, `sublevel_registered`,
`block_state_received`, `sublevel_finalized`, and `SABLE_RENDER` phases
`dispatch`, `state`, and `draw`. The current first suspected invisible-render
boundary is client tracking/chunk-state delivery or renderer selection, not
the already-proven server spawn/mass path.

`verifySubLevelEntityCollisionBoundary` was strengthened to depend on
`compileJava`, include the missed climbing/clip/adjacent corridors, and inspect
compiled class canaries for `sable$redirectPos` and
`BlockGetterClipHelper.lambda$originalClip$0`. `verifyBasicSubLevelRenderLifecycle`
now requires the client packet/chunk/render diagnostics in compiled classes.

Validation this turn: `:forge:spotlessApply` PASS. `:forge:build` and the
requested build-backed verifiers stopped before javac/fresh artifact generation
on the recurring ForgeGradle `AccessDeniedException:
forge/build/downloadMcpConfig/output.zip`. Current on-disk all-jar SHA-256 is
`01832B23B1311D03FD4663FD9D27EC5083423742BE02C969239F8D6D5FCDC79F`, which is
the prior M9.17 artifact and not fresh M9.18 acceptance evidence.

## M9.19 Generic Plot-Coordinate Boundary and Render Dispatch Static Status (2026-08-26)

No Minecraft client/server was launched. Fresh production evidence after the
M9.18 rebuild localized rendering: client sublevel create/register/chunk-state
and finalize all fire, and `VanillaSingleSubLevelRenderData` logs
`SABLE_RENDER phase=state ... minecraft:stone`. The missing stages are the
top-level frame dispatch/draw markers.

Static tracing from the frame render loop showed that the enabled retained
`sublevel_render.LevelRendererMixin` invoked `renderAfterSections`, but no
enabled mixin invoked `SubLevelRenderDispatcher.renderSectionLayer`. That hook
existed only in the deferred `sublevel_render.impl.vanilla.LevelRendererMixin`,
which also imports Veil bridge/layer types and was removed from the retained
runtime during M9.2. The data path survived; the frame-layer dispatch hook did
not. The retained LevelRenderer mixin now has a minimal Veil-free
`renderSectionLayer` injection before `ShaderInstance.clear()` that calls the
dispatcher and preserves the normal single-block transform/state/tessellation
path.

The new entity failures are ordinary vanilla methods, not the earlier patched
redirect frames: `LivingEntity.getBlockSpeedFactor()`/`onSoulSpeedBlock()` and
`LivingEntity.travel(Vec3)` call parent `Level.getBlockState` while the
entity-stick system has the entity in Sable plot coordinates and still attached
to the parent `ServerLevel`. Upstream's architectural boundary is the
plot-grid chunk cache: generic parent-level lookups at plot coordinates must
resolve to Sable plot chunks, not vanilla overworld chunk loading. Forge
1.20.1's `ServerChunkCache.getChunk(int,int,ChunkStatus,boolean)` can enter the
blocking/ticket path directly, so the previous future/now/lighting hooks were
insufficient. `ServerChunkCacheMixin` now intercepts that exact 1.20.1 method
at HEAD and returns the plot chunk or Sable empty chunk for plot-grid
coordinates.

`verifySubLevelEntityCollisionBoundary` was strengthened to require this
generic `ServerChunkCache.getChunk` plot boundary in source/compiled classes,
so coverage is no longer limited to selected patched call sites. It records
that this boundary covers the observed `getBlockSpeedFactor` and `travel`
failures. `verifyBasicSubLevelRenderLifecycle` now requires the active mixin
config plus retained LevelRenderer source/bytecode to contain the Veil-free
`renderSectionLayer` dispatch hook; it must not pass on renderer classes and
diagnostic strings alone.

Validation: `:forge:spotlessApply` PASS. `:forge:build` and the requested
build-backed verifiers stopped before javac/fresh artifact generation on the
recurring ForgeGradle `AccessDeniedException:
forge/build/downloadMcpConfig/output.zip`. Current on-disk all-jar SHA-256 is
`DB1840CCD36AABEF3EE09D46FA4F4CBF4753F402AA9DD8F8CBEA07D8028775C7`; it is not
fresh M9.19 acceptance evidence.

## Standalone InvokeDynamic Handle Userdev Remap Fix (2026-08-23)

No Minecraft client or server was launched during this follow-up.

After Gate 1 passed, the next standalone runtime failure was a
`NoSuchMethodError` caused by stale SRG member names in `invokedynamic`
bootstrap handles. Ordinary `MethodInsnNode` references in the same classes
were already remapped correctly; the missing path was ASM `Handle` metadata.

Required mappings:

```text
ChunkHolder.m_287213_()Lnet/minecraft/server/level/FullChunkStatus;
-> ChunkHolder.getFullStatus()Lnet/minecraft/server/level/FullChunkStatus;

ServerGamePacketListenerImpl.m_9829_(Lnet/minecraft/network/protocol/Packet;)V
-> ServerGamePacketListenerImpl.send(Lnet/minecraft/network/protocol/Packet;)V
```

`ServerLevelPlot` production all-jar `BootstrapMethods` contained
`REF_invokeVirtual ChunkHolder.m_287213_` and
`REF_invokeVirtual ServerGamePacketListenerImpl.m_9829_`. The mapped
standalone userdev artifact now contains
`REF_invokeVirtual ChunkHolder.getFullStatus` and
`REF_invokeVirtual ServerGamePacketListenerImpl.send`, with zero stale
`m_287213_` / `m_9829_` handles remaining.

`SableStandaloneUserdevMapper` now remaps member `Handle` values generally in:

- `InvokeDynamicInsnNode.bsm`;
- every `InvokeDynamicInsnNode.bsmArgs` element;
- `ConstantDynamic` bootstrap methods and recursive bootstrap arguments;
- `LdcInsnNode` constants containing `Handle` or `ConstantDynamic`.

It preserves handle tag, owner, descriptor, and interface flag, remaps field
handles through FD mappings, remaps method handles through MD mappings, and uses
the existing complete hierarchy evidence where an apparent owner inherits a
Minecraft mapped member. The verifier now scans every retained mapped Sable
class for stale known-remappable member references in ordinary instructions,
handles, LDC handles, and recursive `ConstantDynamic` values.

Validation:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:build :forge:verifyStandaloneRunConfiguration` | PASS; `handleReferenceRemaps=7`, `constantDynamicHandleReferenceRemaps=0`, whole-artifact stale scan `ordinary=0`, `handles=0`, `constantDynamicOrLdcHandles=0`, remaining `0` |

The next task should launch exactly one
`.\gradlew.bat --offline :forge:runStandaloneClient` process.

## Standalone Reload Bridge Userdev Remap Fix (2026-08-23)

No Minecraft client or server was launched during this follow-up.

The latest standalone runtime proved Gate 1: `companion` and `gate1` both
logged `PASS`, all mods reached `DONE`, and initial resource reload completed.
The next failure happened only while opening/creating the smoke world:

```text
AbstractMethodError: Missing implementation of resolved method
SimplePreparableReloadListener.apply(Object, ResourceManager, ProfilerFiller)
```

Static bytecode inspection found the failure class across the retained Sable
server-data reload listeners registered by Forge. The first registered listener
is `PhysicsBlockPropertiesDefinitionLoader`; the same bridge shape existed in
`DimensionPhysicsData.ReloadListener` and
`FloatingBlockMaterialDataHandler.ReloadListener`.

Source typed declaration:
`apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)`.
Final production all-jar declarations:
typed `apply(Map, ...)` plus bridge/synthetic
`m_5787_(Object, ResourceManager, ProfilerFiller)`. Previous mapped userdev
artifact left that bridge stale as `m_5787_`, while Forge userdev expects the
named erased method
`SimplePreparableReloadListener.apply(Object, ResourceManager, ProfilerFiller)`.

Mapping proof:

```text
MD: net/minecraft/server/packs/resources/SimplePreparableReloadListener/m_5787_
(Ljava/lang/Object;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V
-> apply
```

`SableStandaloneUserdevMapper` now resolves superclass/interface hierarchy
through the external Minecraft classpath as well as Sable classes, and remaps
proved hierarchy methods including `ACC_BRIDGE` / `ACC_SYNTHETIC`
declarations. Static canaries require every concrete Sable
`SimplePreparableReloadListener` subclass to expose both typed `apply(Map,...)`
and erased bridge `apply(Object,...)` in the mapped userdev artifact, with no
stale `m_5787_` declarations/references.

Validation:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:build :forge:verifyStandaloneRunConfiguration` | PASS; `bridgeHierarchyDeclarationRemaps=9`, `bridgeHierarchyReferenceRemaps=0`, unresolved hierarchy declarations/references `0` |

The next task should launch exactly one
`.\gradlew.bat --offline :forge:runStandaloneClient` process.

## Standalone Nested SecureJar Provenance Fix (2026-08-23)

No Minecraft client or server was launched during this follow-up.

The latest standalone runtime again reached a healthy Forge state: all mods
reached `DONE` and initial resource reload completed. It was stopped only by a
nested Companion provenance false negative. Forge/SecureJar reported the
Companion API from a nested URL shaped like:

```text
union:/.../sable-forge-1.20.1-2.0.0-all-userdev.jar%23198_/META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar%23218!/dev/ryanhcode/sable/companion/SableCompanion.class
```

The standalone smoke parser now treats SecureJar URLs structurally instead of
assuming every internal id is followed by `!`. It accepts arbitrary numeric
ids and extracts:

- outer artifact:
  `run/standalone-client/mods/sable-forge-1.20.1-2.0.0-all-userdev.jar`;
- nested entry:
  `META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar`;
- resource:
  `dev/ryanhcode/sable/companion/SableCompanion.class`.

Static canaries now cover both top-level
`union:/C:/work/run/standalone-client/mods/sable-forge-1.20.1-2.0.0-all-userdev.jar%23198!/dev/example/Class.class`
and nested
`union:/C:/x/outer.jar%23198_/META-INF/jarjar/inner.jar%23218!/dev/Y.class`
forms. The second canary must parse as `outer=C:/x/outer.jar`,
`nested=META-INF/jarjar/inner.jar`, `resource=dev/Y.class`.

Validation:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:build :forge:verifyStandaloneRunConfiguration` | PASS; final all-jar rebuilt, mapped standalone artifact regenerated from it, nested SecureJar union URL canary passed |

The next task should launch exactly one
`.\gradlew.bat --offline :forge:runStandaloneClient` process.

## Standalone Smoke Provenance Fix (2026-08-23)

No Minecraft client or server was launched during this follow-up.

The latest standalone runtime reached a healthy Forge state: all mods reached
`DONE` and initial resource reload completed. It was stopped only by a
standalone smoke provenance false negative:

```text
Sable standalone smoke did not resolve from the staged Sable artifact:
union:/.../run/standalone-client/mods/sable-forge-1.20.1-2.0.0-all-userdev.jar%23198!/dev/ryanhcode/sable/forge/SableForgeStandaloneRuntimeSmoke.class
```

That `union:` URL is valid Forge/SecureJar evidence that the class came from
the staged `all-userdev.jar`. The smoke verifier now normalizes resource URLs
before comparing the outer artifact: it URI-decodes `%23`, strips the
SecureJar `#<id>` suffix, strips the `!/resource` suffix, and compares the
normalized outer jar path under `run/standalone-client/mods`. Companion
provenance remains strict: Active provider from the staged outer Sable jar,
Companion API/default provider from the nested
`META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar`, and no external
Companion/development output.

Static canary:

```text
union:/C:/work/run/standalone-client/mods/sable-forge-1.20.1-2.0.0-all-userdev.jar%23198!/dev/example/Class.class
-> C:/work/run/standalone-client/mods/sable-forge-1.20.1-2.0.0-all-userdev.jar
```

Validation:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:build :forge:verifyStandaloneRunConfiguration` | PASS; final all-jar rebuilt, mapped standalone artifact regenerated from it, SecureJar union URL canary passed |

The next task should launch exactly one
`.\gradlew.bat --offline :forge:runStandaloneClient` process.

## Current Standalone Packaged-Artifact Runtime Attempt (2026-08-23 15:40)

Static setup before launch passed:

- `prepareStandaloneSableUserdevArtifact` derives
  `sable-forge-1.20.1-2.0.0-all-userdev.jar` from the final production
  `sable-forge-1.20.1-2.0.0-all.jar` only;
- production `MinecraftMixin` retains `f_91073_`; mapped userdev artifact
  exposes `level`;
- production `GameRendererMixin` retains `f_109059_`; mapped userdev artifact
  exposes `minecraft`;
- Companion and MixinExtras remain nested JarJar libraries; production
  distributable remains unchanged;
- standalone launcher surfaces remain clean of Sable dev output and target-mod
  dependency leaks;
- effective Registrate supplier count remains exactly one:
  `create-1.20.1-6.0.8-mapped-dev.jar!/META-INF/jarjar/Registrate-MC1.20-1.3.3.jar`.

Exactly one `.\gradlew.bat --offline :forge:runStandaloneClient` process was
launched. No relaunch was performed.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Packaged artifact boot | **FAIL before title** | Sable's previous `MinecraftMixin.f_91073_` failure did not recur; Forge loaded `sable-forge-1.20.1-2.0.0-all-userdev.jar` from `run/standalone-client/mods` |
| Companion runtime source | **NOT REACHED** | failure occurred while applying Flywheel Mixins before Sable construction/provider selection |
| 2 — Short world regression | **NOT RUN** | title screen was not reached |
| 3 — Clean exit | **NOT RUN** | process exited with a Mixin apply failure |

Failure:

```text
Mixin [flywheel.impl.mixins.json:MinecraftMixin] ... FAILED during APPLY
@Shadow field f_91036_ was not located in net.minecraft.client.Minecraft.
```

Root cause: the standalone userdev runtime still staged raw production
Flywheel/Ponder jars while `forgeclientuserdev` runs against named/dev
Minecraft classes. This is the same namespace class of issue as the Sable
artifact, now exposed in an external target mod.

No relaunch was performed. A static follow-up fix was applied:

- standalone staging now copies ForgeGradle mapped Flywheel/Ponder artifacts:
  `flywheel-forge-1.20.1-1.0.5_mapped_official_1.20.1.jar` and
  `Ponder-Forge-1.20.1-1.0.91_mapped_official_1.20.1.jar`;
- verifier checks Flywheel's `MinecraftMixin` canary is mapped from
  `f_91036_` to `resourceManager`;
- the staged mods directory now contains mapped Sable, mapped Create, mapped
  Flywheel, mapped Ponder, and mapped Veil.

Post-failure validation passed:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:spotlessApply :forge:verifyStandaloneRunConfiguration :forge:verifyStandaloneForgeArtifact :forge:build` | PASS |

Current blocker: **runtime PASS for the packaged artifact remains unproven**.
The next task should use exactly one `runStandaloneClient` process with mapped
Sable/Flywheel/Ponder/Create/Veil staged for Forge userdev.

## Current Standalone Packaged-Artifact Runtime Attempt (2026-08-23 15:03)

Static split-package proof/fix:

- final outer `sable-forge-1.20.1-2.0.0-all.jar` and nested
  `META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar` were mechanically
  enumerated by Java package;
- outer Sable classes and nested Companion classes had **no class-package
  intersection**;
- `dev.ryanhcode.sable.companion.impl` classes existed only in nested
  Companion: `DefaultSableCompanion`, `DefaultSableCompanion$DistHelper`, and
  `SableCompanionUtil`;
- outer Sable owned `dev.ryanhcode.sable.ActiveSableCompanion`; no source move
  was needed;
- root cause was the outer Sable ServiceLoader descriptor naming nested
  `DefaultSableCompanion`, causing module `sable` to claim the nested
  Companion implementation package;
- fix: outer descriptor now lists only `ActiveSableCompanion`; nested
  Companion keeps its own `DefaultSableCompanion` descriptor. Artifact
  verification now rejects outer/nested JarJar split packages and verifies
  Active/default provider ownership and priority.

Pre-launch validation passed:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :sable_companion_1_20:verifySableCompanionBackport :forge:verifyStandaloneForgeArtifact :forge:verifyStandaloneRunConfiguration :forge:compileJava :forge:build` | PASS |

Exactly one `.\gradlew.bat --offline :forge:runStandaloneClient` process was
launched. No second client, `runClient`, manual Minecraft launch, or separate
server was started.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Packaged artifact boot | **FAIL before title** | Previous Sable/Companion split-package failure did not recur; Forge/ModLauncher owned startup and discovered Sable/Create/Flywheel/Ponder/Veil |
| Companion runtime source | **NOT REACHED** | module resolution failed before Sable construction/provider selection |
| 2 — Short world regression | **NOT RUN** | title screen was not reached |
| 3 — Clean exit | **NOT RUN** | process exited with a module-layer exception |

Failure:

```text
java.lang.module.ResolutionException: Modules Registrate.MC1._20 and
Registrate export package com.tterrag.registrate.providers.loot to module sable
```

Targeted `debug.log` and launch-surface inspection showed the physical
`run/standalone-client/mods` directory was clean. The extra `Registrate` module
was instead supplied from ForgeGradle's mapped dependency path:
`C:\Users\camel\.gradle\caches\forge_gradle\deobf_dependencies\local\target\Registrate\MC1.20-1.3.3_mapped_official_1.20.1\Registrate-MC1.20-1.3.3_mapped_official_1.20.1.jar`.
The competing `Registrate.MC1._20` supplier was Create's nested JarJar:
`run/standalone-client/mods/create-1.20.1-6.0.8-mapped-dev.jar!/META-INF/jarjar/Registrate-MC1.20-1.3.3.jar`.
This was caused by the empty `standaloneRuntimeLauncher` source set inheriting
`sourceSets.main.compileClasspath`, which let mapped target dependencies reach
Forge/ModLauncher outside the staged mods directory.

No relaunch was performed. A static follow-up harness fix was applied:

- `runStandaloneClient` filters target mod artifacts from the launcher/module
  classpath so they are supplied only from `run/standalone-client/mods` and
  Create-owned JarJar;
- `verifyStandaloneRunConfiguration` now rejects leaked Companion or target-mod
  entries on JavaExec classpath, generated legacy classpath, JVM args, and
  MOD_CLASSES, checks the exact five staged standalone mods, and proves Create's
  nested Registrate canaries.

Post-failure validation passed:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:spotlessApply :forge:verifyStandaloneRunConfiguration :forge:verifyStandaloneForgeArtifact :forge:build` | PASS |

Current blocker: **runtime PASS for the packaged artifact remains unproven**.
The next task should use exactly one `runStandaloneClient` process with the
validated Companion and effective-target-supplier fixes.

## Current Standalone Packaged-Artifact Runtime Attempt (2026-08-23 14:45)

Exactly one `.\gradlew.bat --offline :forge:runStandaloneClient` process was
launched. No second client, `runClient`, manual Minecraft launch, or separate
server was started.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Packaged artifact boot | **FAIL before title** | Forge/ModLauncher owned startup; `sable-forge-1.20.1-2.0.0-all.jar` was discovered as the `sable` mod; Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, and Veil `1.0.0` were discovered |
| Companion runtime source | **FAIL before selection** | module resolution saw Companion both in the packaged JarJar and on the standalone runtime classpath |
| 2 — Short world regression | **NOT RUN** | title screen was not reached |
| 3 — Clean exit | **NOT RUN** | process exited with a module-layer exception |

Targeted log evidence from `forge/run/standalone-client/logs/debug.log`:

- `ModLauncher running ... --launchTarget forgeclientuserdev`;
- `Found valid mod file sable-forge-1.20.1-2.0.0-all.jar with {sable}`;
- `Selected file sable-companion-common-1.20.1-1.6.0.jar for modid sable.companion.common`;
- failure:

```text
java.lang.module.ResolutionException: Modules sable and sable.companion.common
export package dev.ryanhcode.sable.companion.impl to module veil
```

Root cause: `:forge` still exposed `:sable_companion_1_20` as a runtime API
dependency, so the standalone JavaExec/module path carried Companion outside
the final Sable JarJar. The final artifact itself still packages Companion
through JarJar as intended.

No relaunch was performed. A static follow-up fix was applied and validated:

- `:forge` now uses `compileOnlyApi(project(':sable_companion_1_20'))` plus the
  existing explicit `jarJar(project(':sable_companion_1_20'))` row;
- `verifyStandaloneRunConfiguration` now rejects any external
  `sable_companion_1_20` / `sable-companion-common-*` entry on the standalone
  JavaExec classpath;
- post-failure validation passed:
  `verifyStandaloneRunConfiguration`, `verifyStandaloneForgeArtifact`, and
  `:forge:build`.

Current blocker: **runtime PASS for the packaged artifact remains unproven**.
The next task should use exactly one `runStandaloneClient` process with the
validated Companion classpath fix and unchanged dependency/feature scope.

## Current Standalone Packaged-Artifact Runtime Attempt (2026-08-23 14:38)

Static precheck before launch passed:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:verifyStandaloneForgeArtifact` | PASS |
| `.\gradlew.bat --offline :forge:verifyStandaloneRunConfiguration` | PASS |

Exactly one `.\gradlew.bat --offline :forge:runStandaloneClient` process was
then launched. No `runClient`, second standalone client, manual Minecraft
launch, or separate server was started.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Packaged artifact boot | **FAIL before title** | Forge/ModLauncher owned startup this time: `ModLauncher running ... --launchTarget forgeclientuserdev`; Mixin subsystem initialized through ModLauncher |
| Companion runtime source | **NOT REACHED** | Sable mod construction did not occur before the launcher failed |
| 2 — Short world regression | **NOT RUN** | title screen was not reached |
| 3 — Clean exit | **NOT RUN** | process exited with a launch-time exception |

Failure evidence in `forge/run/standalone-client/logs/latest.log` and
`debug.log` shows the launcher fix worked: `forgeclientuserdev` was selected and
the previous vanilla `mcp.client.Start` / ignored-Mixin-args failure did not
recur. The new failure is a standalone run-configuration filesystem issue:

```text
java.io.UncheckedIOException: java.io.IOException: Invalid paths argument,
contained no existing paths:
forge/build/resources/standaloneRuntimeLauncher,
forge/build/classes/java/standaloneRuntimeLauncher
```

Root cause: the deliberately empty `standaloneRuntimeLauncher` source set
prevents Sable development output from entering `MOD_CLASSES`, but Gradle did
not physically create its empty output roots because it contains no files.
Forge userdev still passed those roots through `MOD_CLASSES`, and SecureJar
rejects non-existent roots.

After the one failed runtime process, no relaunch was performed. A static
follow-up fix was applied and validated:

- new `prepareStandaloneRuntimeLauncherRoots` creates the empty
  `standaloneRuntimeLauncher` resource/classes roots before the standalone run;
- `verifyStandaloneRunConfiguration` now requires those roots to exist and to
  contain no files, preserving the no-development-output guarantee;
- post-failure static validation passed:
  `spotlessApply`, `verifyStandaloneRunConfiguration`,
  `verifyStandaloneForgeArtifact`, and `:forge:build`.

Current blocker: **runtime PASS for the packaged artifact remains unproven**.
The next task should use exactly one `runStandaloneClient` process with the now
validated empty-root prep fix and unchanged dependency/feature scope.

## Current Standalone Artifact Packaging Smoke (2026-08-23 14:27)

Scope: package the existing Java 17 `:sable_companion_1_20` common library into
the final Forge Sable artifact, validate the final reobfuscated JarJar output,
and make one short standalone-style runtime attempt without enabling deferred
Create/Flywheel Mixins, rendering, Rapier, Simulated, or Aeronautics.

Packaging result: **STATIC PASS**.

- Final artifact: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`.
- Companion is packaged as a common JarJar library, not a Forge mod:
  `META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar`.
- Companion JarJar metadata:
  `dev.ryanhcode.sable-companion:sable_companion_1_20:1.6.0`,
  range `[1.6.0,)`, `isObfuscated=true`.
- Companion contains `SableCompanion`, `DefaultSableCompanion`, its
  `META-INF/services/dev.ryanhcode.sable.companion.SableCompanion` descriptor,
  and `META-INF/LICENSE_sable_companion`; it contains no `mods.toml` or
  NeoForge metadata.
- The outer Sable artifact retains the Sable `@Mod` entrypoint, `mods.toml`,
  `sable.refmap.json`, curated Mixin configs, Forge access transformer, and
  required Sable ServiceLoader descriptors.
- Companion classes are absent from the outer jar and present exactly once
  inside the Companion JarJar.
- MixinExtras Forge `0.5.3` remains the only additional Sable-owned JarJar row.
- Create, Flywheel, Ponder, Registrate, and Veil are not bundled; Veil remains
  an external runtime dependency.

Static validation passed with JDK 17 and `--offline`:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :sable_companion_1_20:verifySableCompanionBackport` | PASS |
| `.\gradlew.bat --offline :forge:networkTest` | PASS |
| `.\gradlew.bat --offline :forge:verifyTargetModpackDependencies` | PASS |
| `.\gradlew.bat --offline :forge:verifyRuntimeModuleBoundary` | PASS |
| `.\gradlew.bat --offline :forge:compileJava` | PASS |
| `.\gradlew.bat --offline :forge:verifyStandaloneForgeArtifact` | PASS |
| `.\gradlew.bat --offline :forge:verifyStandaloneRunConfiguration` | PASS after the launcher fix below |
| `.\gradlew.bat --offline :forge:build` | PASS |

One standalone client process was attempted through
`.\gradlew.bat --offline :forge:runStandaloneClient`. It failed before title:

```text
Completely ignored arguments: [--mixin.config, sable-common-forge.mixins.json, --mixin.config, sable-forge.mixins.json]
java.lang.NullPointerException: Cannot invoke
"net.minecraftforge.fml.loading.progress.ProgressMeter.label(String)" because
"net.minecraftforge.fml.loading.ImmediateWindowHandler.earlyProgress" is null
```

Root cause: the first standalone run configuration used `mcp.client.Start`,
which entered vanilla `net.minecraft.client.main.Main`; Forge/ModLauncher did
not own the launch, the Mixin arguments were ignored, and Forge's early-window
progress handler crashed before `latest.log` initialized. `latest.log` is empty;
the only file log is `forge/run/standalone-client/logs/debug.log`, and the
useful failure evidence is the Gradle console stack above.

After that one failed process, no second Minecraft launch was performed. The
run configuration was repaired statically for the next task:

- main is now `cpw.mods.bootstraplauncher.BootstrapLauncher`;
- args now include `--launchTarget forgeclientuserdev` plus the Forge `47.4.20`
  userdev arguments from the local Forge userdev `config.json`;
- an empty `standaloneRuntimeLauncher` source set prevents ForgeGradle from
  defaulting the standalone run to Sable's development output;
- `verifyStandaloneRunConfiguration` now requires the BootstrapLauncher main,
  `forgeclientuserdev`, exactly the empty launcher source set, and no
  development mod declarations.

Current blocker: **runtime PASS for the packaged artifact remains unproven**.
The next milestone should use exactly one client JVM with the repaired
standalone run configuration and should not change dependency selection or
feature scope first.

## Current Final Result: Create 6 Same-JVM Runtime Smoke PASS (2026-08-23 13:56)

The lifecycle smoke harness was fixed without changing production lifecycle
behavior. It now captures lifecycle baselines at completed gate boundaries and
validates per-integrated-server deltas instead of assuming process-global
counters start at zero. The corrected accounting distinguishes:

- process/client-global one-time state: Sable bootstrap, config registration,
  packet/channel registration, runtime probe installation, client-global
  listener registration, and provider initialization;
- per-integrated-server state: server start/stop, command registration, server
  reload-listener registration, player login/logout, and UDP ownership;
- client logout cleanup events that can legitimately occur before a world
  lifecycle baseline.

The dedicated static lifecycle canary was updated to require the baseline /
delta harness shape. Static validation passed with JDK 17 and `--offline`:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:verifyBootstrapLifecycleBoundary` | PASS |
| `.\gradlew.bat --offline :forge:compileJava` | PASS; 0 errors, existing 4 Forge deprecation warnings |
| `.\gradlew.bat --offline :forge:build` | PASS; reobfuscation, Checkstyle, Spotless, AT and packaging checks |

After those static gates, exactly one
`.\gradlew.bat --offline :forge:runClient` process was launched. It completed
successfully; no second client, manual Minecraft launch, or Forge server was
started.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Main menu | **PASS** | target Create 6 stack initialized |
| 2 — World | **PASS** | disposable smoke world opened and first-world lifecycle baseline captured |
| 3 — Single-block Sable sublevel | **PASS** | `create6_runtime_smoke` mass `2.0`, zero velocities, ordered StartTracking -> Finalize |
| 4 — Same-JVM reload | **PASS** | first Save and Quit returned to title; same JVM reopened the world; same named stone sublevel restored with mass `2.0`; second-server active delta audit passed |
| 5 — Clean shutdown | **PASS** | second Save and Quit, integrated-server shutdown, client/network/UDP cleanup, and second-server stopped delta audit passed |

Key runtime evidence from `forge/run/m6-client/logs/latest.log`:

- first world active baseline:
  `starting=1 started=1 stopping=0 stopped=0 commands=1 reloadListeners=1 playerLogins=1 playerLogouts=0 clientLogouts=1`;
- first server stop delta duplicate-registration audit passed;
- first stopped baseline:
  `starting=1 started=1 stopping=1 stopped=1 commands=1 reloadListeners=1 playerLogins=1 playerLogouts=1 clientLogouts=2`;
- second world active baseline:
  `starting=2 started=2 stopping=1 stopped=1 commands=2 reloadListeners=2 playerLogins=2 playerLogouts=1 clientLogouts=3`;
- second server active and second server stopped delta audits passed;
- `/sable info` reported `Mass: 2.0` before and after same-JVM reload;
- `SABLE_TARGET_RUNTIME phase=gate5 status=PASS CLEAN_EXIT...`;
- `SABLE_TARGET_RUNTIME phase=complete status=PASS all five target-runtime gates passed in one client JVM`.

This proves the retained Sable Forge core remains runtime-compatible in the
presence of the rebaselined Create 6 stack for the scoped gates: title, world,
single-block sublevel initialization, same-JVM reload, and clean shutdown. It
does not prove deferred Create/Flywheel Mixins, chunked/advanced rendering,
Rapier/full physics, Simulated, Aeronautics, or Companion JarJar.

## Current One-Launch Result: Post Gate-3 Repair Runtime Smoke (2026-08-23 13:42)

Exactly one `.\gradlew.bat --offline :forge:runClient` process was launched for
the post-repair Create 6 runtime smoke. No second client, manual Minecraft
launch, or Forge server was started. Dependency selection, Mixin priorities,
and deferred-feature scope were unchanged.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Main menu | **PASS** | title reached; Sable, Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod `1.0.0`, Companion, Registrate `Registrate.MC1._20`, and MixinExtras `0.5.3` evidence passed |
| 2 — World | **PASS** | disposable smoke world opened; player joined; ticking/chunk loading probe passed; `/sable` registered |
| 3 — Single-block Sable sublevel | **PASS** | `/sable spawn block minecraft:stone create6_runtime_smoke` and `/sable info @l` both returned `1`; the object was named correctly, origin block was accepted by the harness, mass was `2.0`, velocities were zero, ChangeBounds was emitted, and StartTracking -> Finalize ordering passed |
| 4 — Same-JVM reload | **FAIL before reopen** | after Save and Quit, the lifecycle verifier expected one client logout but observed two; the same JVM did not reopen the world |
| 5 — Clean shutdown | **NOT RUN** | Gate 4 lifecycle assertion crashed the harness before the explicit clean-exit gate |

Gate 3 runtime details:

- `/sable info` reported `create6_runtime_smoke` at position
  `-6.5 -107.73569043825906 6.5`.
- `/sable info` reported orientation `0.0 0.0 0.0 1.0`.
- `/sable info` reported `Mass: 2.0`.
- `/sable info` reported zero linear and angular velocity.
- `ClientboundStartTrackingSubLevelPacket` arrived before
  `ClientboundFinalizeSubLevelPacket`; `ClientboundChangeBoundsSubLevelPacket`
  followed.
- No extreme-Y removal was logged before the first Save and Quit.
- The updated sublevel region `M6_Smoke_Empty/sublevels/r.-1.0.0.slvls` was
  written during the first server stop.

The current harness does not print the numeric plot/global bounds. Bounds were
nevertheless runtime-valid enough for the repaired command to return, emit
`ChangeBounds`, pass Gate 3, avoid the previous empty-bounds/extreme-Y removal,
and save the sublevel. A future harness improvement should log the exact
numeric plot and global bounds before Same-JVM reload.

Gate 4 blocker: the first Save-and-Quit reached integrated-server stop cycle
`1`, but `SableForgeRuntimeSmoke.verifyFirstIntegratedServerStopped()` still
expects exactly one `ClientPlayerNetworkEvent.LoggingOut` by that point. This
run logged a pre-world client logout cleanup cycle `1` during world startup and
the expected Save-and-Quit cleanup cycle `2`, so the verifier failed with:

```text
java.lang.IllegalStateException: SABLE_M6 first server stopped client logouts=2, expected=1
```

Crash evidence is in `forge/run/m6-client/logs/latest.log` and
`forge/run/m6-client/crash-reports/crash-2026-08-23_13.44.10-client.txt`
(crash UUID `3fe3a6d9-4e65-4a7c-84c0-9c7508eb356e`). The same-JVM reload,
duplicate-registration audit for the second server, and clean shutdown remain
unproven because the harness stopped before reopening the world.

Recommended next fix: statically repair the lifecycle verifier to distinguish
baseline/pre-world client logout cleanup from Save-and-Quit cleanup, or to
record the first-server-stop delta from a baseline captured after Gate 2. Do
not change dependency selection, Mixin priorities, networking, or deferred
feature scope for that harness/lifecycle repair.

## Current Static Repair: Gate 3 Single-Block Initialization (2026-08-23)

No Minecraft client, manual client, or Forge server was launched for this
repair. Dependency selection, Mixin priorities, and the retained/deferred
feature boundary were unchanged.

Root cause: `/sable spawn block minecraft:stone <name>` created the sublevel,
allocated an empty plot chunk, placed the block through
`EmbeddedPlotLevelAccessor.setBlock`, and then only updated `lastPose`. The new
server sublevel had already been added to the physics system with an empty mass
tracker and empty plot bounds. The command therefore relied on the live
`LevelChunk` block-change callback to associate the placed block with the plot,
expand bounds, and increment mass before the object was queried or ticked. In
the Create 6 smoke, that initialization had not been established by the time
`/sable info` and the harness assertion ran: the block existed at the embedded
origin, but mass remained `0.0`; the later tick transformed the empty
`BoundingBox3i.EMPTY` sentinel and removed the object for an extreme Y range.

The Create/Ponder wrapped-level boundary was inspected and is not the proven
cause of this failure. The command operates on the normal integrated
`ServerLevel`; `SablePlatform.isWrappedLevel(...)` and
`PonderWrappedLevelCheck` are retained for optional wrapped-level exclusion but
do not drive `/sable spawn block`.

Why M6 previously worked: M6 proved the same command could produce and reload a
stationary stone sublevel with mass `2.0`, and its evidence included the
expected packet/update boundary after creation plus a fresh-JVM reload. It did
not prove that the command itself synchronously finalized initial mass/bounds
before the first immediate same-JVM assertion. The Create 6 runtime harness made
that ordering hole visible by checking the newly spawned object immediately in
the same server lifecycle.

Production fix: after the single-block command places the block, the retained
spawn path now calls a narrow finalization helper that:

- resolves the actual plot chunk for the embedded origin;
- applies the block change to the `PlotChunkHolder`;
- rebuilds plot bounds and performs normal plot expansion;
- rejects empty/non-positive bounds immediately;
- rebuilds the server sublevel mass tracker from actual world/plot contents;
- updates merged mass data and rejects invalid mass data;
- updates the sublevel global bounds before `lastPose` is recorded.

The fix does not special-case `minecraft:stone`, hardcode mass `2.0`, suppress
bounds validation, change dependency selection, change Mixin priorities, or
enable deferred Create/Flywheel features. Mass still comes from the normal
physics-property lookup used by `MassTracker.build(...)`.

Regression coverage: new Gradle gate
`:forge:verifySingleBlockSpawnInitialization` verifies that the
`/sable spawn block` production path captures the pre-placement state, places
the block, and immediately runs the finalization helper before
`updateLastPose()`. It also verifies the helper keeps the block association,
bounds rebuild, plot expansion, mass rebuild, invalid-bound/invalid-mass
assertions, and no stone/mass hardcoding.

Static validation passed with JDK 17 and `--offline`:

| Command | Result |
|---|---|
| `.\gradlew.bat --offline :forge:verifySingleBlockSpawnInitialization` | PASS |
| `.\gradlew.bat --offline :forge:compileJava` | PASS; 0 errors, existing 4 Forge deprecation warnings |
| `.\gradlew.bat --offline :forge:networkTest` | PASS; up-to-date 12/12 |
| `.\gradlew.bat --offline :forge:verifyRuntimeModuleBoundary` | PASS |
| `.\gradlew.bat --offline :forge:build` | PASS; reobfuscation, Checkstyle, Spotless, AT and packaging checks |

The first managed compile attempt hit the known Windows short-path
`AccessDeniedException`; the same offline Gradle validation outside that
filesystem wrapper passed. No retained Mixin changed, so
`verifyRetainedMixinCoexistence` was not rerun for this repair.

Remaining risk before the next runtime attempt: this is a static/root-cause
repair. The next proof still requires exactly one later `runClient` process to
re-attempt Gate 3, then same-JVM reload and clean shutdown, without changing
dependency selection or feature scope.

## Current One-Launch Result: Final Runtime Smoke Attempt (2026-08-23 13:14)

Exactly one `.\gradlew.bat --offline :forge:runClient` process was launched for
the final Create 6 runtime smoke milestone. No second client, manual Minecraft
launch, or Forge server was started. Dependency selection and the retained /
deferred feature boundary were unchanged.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Main menu | **PASS** | title reached; Sable, Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod `1.0.0`, Companion, eight platform providers, packet transport, Registrate, and MixinExtras evidence passed |
| 2 — World | **PASS** | disposable smoke world opened; player joined; ticking/chunk loading probe passed; `/sable` registered |
| 3 — Single-block Sable sublevel | **FAIL** | `/sable spawn block minecraft:stone create6_runtime_smoke` and `/sable info @l` both returned `1`, but the new object reported mass `0.0` instead of the harness-expected stationary stone mass `2.0` and was removed for an extreme Y range |
| 4 — Same-JVM reload | **NOT RUN** | Gate 3 crashed the harness before Save and Quit / reopen |
| 5 — Clean shutdown | **NOT RUN** | the integrated server stopped during crash cleanup, but the explicit clean-exit gate was not reached |

Runtime module evidence reached the title screen cleanly. The harness logged
MixinExtras Forge/common `0.5.3` as the effective implementation before
descriptive module canaries. It then proved Create `6.0.8`, Flywheel `1.0.5`,
Create-owned Registrate module `Registrate.MC1._20` from
`Registrate-MC1.20-1.3.3.jar`, Ponder `1.0.91`, and Veil on the intended
runtime classpath. The filtered Create development artifact did not reintroduce
the removed MixinExtras Forge `0.4.1` wrapper.

World/runtime evidence before the failure:

- Forge packet transport registered protocol `1` with packet IDs `0..13`.
- `/sable` command registration and server reload-listener registration each
  occurred for integrated-server cycle `1`.
- The smoke harness removed the previous `m6_smoke` object, then created
  `create6_runtime_smoke`.
- `ClientboundStartTrackingSubLevelPacket` and
  `ClientboundFinalizeSubLevelPacket` were both observed before the assertion.
- `/sable info` printed the expected name and zero velocities, but `Mass: 0.0`.
- Sable then logged that
  `ServerSubLevel[name=create6_runtime_smoke, ...]` had an extreme Y coordinate
  range and removed it.

The fatal exception is:

```text
java.lang.IllegalStateException: Expected stationary stone mass 2.0, found 0.0
```

Captured evidence is in `forge/run/m6-client/logs/latest.log` and
`forge/run/m6-client/crash-reports/crash-2026-08-23_13.16.08-client.txt`.
The crash report records the top-level failure as
`Integrated-server gate failed`, caused by the mass assertion above.

Lifecycle evidence is intentionally incomplete: the failed JVM observed
integrated-server stop and client logout cleanup during crash handling, but it
did not exercise same-JVM world reload or the explicit duplicate-registration
checks. The equal-priority Sable/Create `Entity.move` TAIL Mixins both applied
without Mixin injection failure; no runtime semantic conflict was observed
before the single-block mass/bounds failure.

Narrow next fix: statically inspect and instrument the retained
single-block-spawn path around `/sable spawn block`,
`EmbeddedPlotLevelAccessor.setBlock`, plot bounds, and
`SubLevelPhysicsSystem.updateMassDataFromBlockChange`. Prove whether the placed
stone block is associated with the new `ServerSubLevel` and why the mass
tracker / local Y bounds remain invalid immediately after spawn. Do not change
dependency selection, Mixin priority, or deferred-feature scope for that
investigation.

## Current Static Harness Correction (2026-08-23)

No Minecraft client, manual client, or Forge server was launched for this
correction. The previous one-launch runtime attempt had already reached a
stable title screen; its only failure was the smoke harness treating the JarJar
artifact identifier `Registrate` as the effective module name.

The harness and `verifyRuntimeModuleBoundary` now derive the expected
Registrate module name from the actual mapped nested runtime jar filename
`Registrate-MC1.20-1.3.3.jar` using Forge/SecureJar filename semantics. The
derived name is `Registrate.MC1._20`. The exact-one
`AbstractRegistrate.class` resource assertion is retained. Detailed
MixinExtras Forge/common uniqueness and version evidence now runs before the
descriptive Registrate module-name canary.

Static validation passed with JDK 17 and `--offline`:
`verifyRuntimeModuleBoundary`, `verifyRunClientClasspath`,
`verifyRetainedMixinCoexistence`, `verifyBootstrapLifecycleBoundary`,
`networkTest`, `compileJava`, and `build`. The next step is one final
single-JVM runtime smoke attempt with the same dependency selection and
deferred-feature boundary.

## Current One-Launch Result: Runtime Evidence Probe (2026-08-23 12:47)

Exactly one `:forge:runClient` process was used for this milestone. It reached
the title-screen harness condition: the initial resource reload had finished,
the title screen had remained stable for 20 client ticks, and all listed mods
were `DONE`. Gate 1 was nevertheless recorded as **FAIL** because the newly
added evidence probe asserted the wrong name for the one effective Registrate
automatic module:

```text
com.tterrag.registrate.AbstractRegistrate module=Registrate.MC1._20,
expected=Registrate
```

This is a harness expectation error, not the former duplicate-module failure.
The class-resource multiplicity check immediately preceding the name check
passed, proving exactly one `AbstractRegistrate.class` resource. ModLauncher
constructed the module layer successfully; there was no
`ResolutionException`. The client exited through the harness crash path with
code `-1`. No second client or server was launched.

| Gate | Result | Evidence |
|---|---|---|
| 1 — Main menu | **FAIL (probe assertion after reaching title)** | all mods/providers loaded; unique Registrate resource resolved from actual module `Registrate.MC1._20`; harness expected `Registrate` |
| 2 — World | **NOT RUN** | Gate 1 did not transition; `M6_Smoke_Empty/level.dat` remained dated 2026-08-21 |
| 3 — Sable sublevel | **NOT RUN** | world was not opened or modified |
| 4 — Same-JVM reload | **NOT RUN** | no integrated server was started |
| 5 — Clean shutdown | **NOT RUN** | harness deliberately crashed the client after the Gate 1 assertion |

Runtime evidence collected before the assertion:

- Forge JarJar logged `Found 2 dependencies`: Create-owned Registrate plus
  MixinExtras common `0.5.3`. Flywheel and Ponder were selected from their
  explicit mapped source modules. No standalone Registrate or Create-owned
  MixinExtras Forge `0.4.1` wrapper appeared.
- MixinExtras logged
  `MixinExtrasServiceImpl(version=0.5.3)` before Mixin preparation.
- Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod `1.0.0`
  (artifact `1.0.0.296`), Forge `47.4.20`, and Minecraft `1.20.1` reached
  `DONE`/completed initialization.
- The unique-resource/module probe passed for filtered Create module `create`
  and Flywheel module `flywheel`. Registrate's unique-resource check passed,
  then exposed its actual automatic-module name `Registrate.MC1._20`.
- Active and fallback Companion providers were discovered, Active Companion
  won by priority, all eight Sable platform providers passed, the client render
  provider passed, and Forge packet transport registered protocol `1` with 14
  packet IDs `0..13`.
- Common/client config each loaded once; common/client runtime probes installed
  once; the client reload listener registered once; common setup ran once.
  Integrated-server lifecycle, command/reload-listener cycles, and UDP server
  counts remained zero because no world opened. Client logout cleanup ran once
  during the crash exit.
- Both the Sable sublevel-collision and Create contraption-interaction Mixins
  applied successfully to `Entity`. No Mixin application/injection conflict was
  logged. Ordinary in-world movement did not occur, so semantic observation of
  their equal-priority `Entity.move` TAIL ordering remains pending.

The exact log is `forge/run/m6-client/logs/latest.log`; the crash evidence is
`forge/run/m6-client/crash-reports/crash-2026-08-23_12.48.32-client.txt`
(crash UUID `3a29ba1f-3efe-4de3-8de6-46f0f2c1d50b`). The only fatal exception
is the Sable target-smoke assertion above.

The next narrow harness correction is to expect the mapped JarJar automatic
module name `Registrate.MC1._20`, while retaining the exact-one resource check.
The static module report should likewise derive the automatic name from the
mapped nested jar filename instead of the JarJar artifact identifier. Move the
MixinExtras wrapper/common uniqueness and version checks before any descriptive
module-name canary so one mismatch cannot hide their detailed evidence. Do not
alter dependency selection or runtime packaging based on this result.

## Previous One-Launch Result: Registrate Collision

The milestone used exactly one `:forge:runClient` process, as required. The
process failed during ModLauncher module-layer construction, before Minecraft
reached the title screen. Therefore the Create 6 runtime smoke is **not yet
proven**:

| Gate | Result | Evidence |
|---|---|---|
| 1 — Main menu | **FAIL (pre-title bootstrap)** | Java module resolution rejected two Registrate package suppliers |
| 2 — World | **NOT RUN** | Gate 1 did not complete |
| 3 — Sable sublevel | **NOT RUN** | Gate 1 did not complete |
| 4 — Same-JVM reload | **NOT RUN** | Gate 1 did not complete |
| 5 — Clean exit | **NOT RUN** | Gate 1 did not complete; failed process exited with code 1 |

No second Minecraft process was launched. The world was never opened or
modified by this attempt.

## Exact Runtime Baseline

Minecraft is `1.20.1`, Forge is `47.4.20`, and the launch used Eclipse Adoptium
Java `17.0.20`. Sable is `2.0.0`; Companion `1.6.0` is supplied by the merged
development output. The exact target runtime form after the diagnosed fix is:

| Component | Runtime coordinate/form | Target source SHA-256 | Current mapped/development SHA-256 |
|---|---|---|---|
| Create | filtered mapped development jar derived from `local.target:create-1.20.1:6.0.8_mapped_official_1.20.1` | `6FBB910C367DBCE8E4FC7E5BF64B6EDD4DE980906ED00AF8E47E4AF843C0D9B0` | `13FCA92CD9C89611943A28ACA296E444AA38365F10F8EDEF427419038B65756E` |
| Flywheel | `local.target:flywheel-forge-1.20.1:1.0.5_mapped_official_1.20.1` | `316CA250F19244956B5F0CD75329309EA65A77B4B8DA854389B6A9222E7F427C` | `948B94FDAE49C7A4974EDC6E52EE6132F551D8009CEB3BD2DF6C130A7C0FA170` |
| Ponder | `local.target:Ponder-Forge-1.20.1:1.0.91_mapped_official_1.20.1` | `86E6B64372ABA6D9C56F2C35725EA26D8FEBF2C75EED9950566E7F2849443B34` | `52EA96FEE0B26F00EB161C70FBF561BD315B7B2B0403D1A5833520023037480A` |
| Registrate | Create-owned `META-INF/jarjar/Registrate-MC1.20-1.3.3.jar`, loaded once | `226862D4638B77273F4627FBAC871AA0B3AF584DDE377F4CE2CB0C7CC228CF00` | `AB90001E9EC42922DA4E499CDAFF6D7F1F6E78432CE71555397931804E94E5FB` inside mapped Create |
| Veil | patched mapped development runtime for `foundry.veil:Veil-forge-1.20.1:1.0.0.296` | raw slim `296C693C659A81B9BAA0C778D29A5AB89C56BBB46B5245A3BC2213BD0485F492` | `3E7EC631FD805B6042812A27AF8CF7F8BDC8424CB7AD1A60D073E82BDC37CACD` |

Create's MixinExtras Forge `0.4.1` and common `0.4.1` source payload retain
their documented source hashes. They are validated during target staging, but
the older Forge wrapper and its metadata row are removed from the mapped
development Create jar. Runtime uses Sable's standalone MixinExtras Forge
`0.5.3` (`89D60F6BF1F29664319ACFA80E777ABC03FDE674370AF52E94A9A2E452B98833`)
and its exact common `0.5.3` JarJar payload
(`D0020CAFF27B478E5CBACE1C0C1D74B755AF9F5D351B619BA1EC45C0DE8CF3C9`).
Sable was not downgraded.

The corrected development runtime deliberately has only three target jars:
filtered mapped Create plus mapped Flywheel and Ponder. Registrate is a
hash-checked, mapped JarJar payload inside mapped Create. This is still the
same exact Registrate `MC1.20-1.3.3`; it avoids exposing the same package from
two Java modules.

## Failure And Narrow Fix

The first and only launch logged:

```text
java.lang.module.ResolutionException: Module Registrate contains package
com.tterrag.registrate, module Registrate.MC1._20 exports package
com.tterrag.registrate to Registrate
```

Flywheel and Ponder were recognized as explicit mapped source mods and selected
over their Create JarJar copies. Registrate has no `mods.toml`; adding its
standalone mapped jar as a source placed automatic module `Registrate.MC1._20`
beside Create's JarJar module `Registrate`. Both exported
`com.tterrag.registrate`, so Java rejected the module layer.

The narrow build-only fix removes standalone Registrate from `runtimeOnly`.
Registrate remains staged and mapped for compilation and static verification,
while runtime loading is delegated to Create's own exact JarJar payload. The
runtime verifier now requires:

- mapped standalone Create `6.0.8`, Flywheel `1.0.5`, and Ponder `1.0.91`;
- no standalone Registrate runtime artifact;
- the mapped Registrate JarJar hash shown above and its
  `AbstractRegistrate.class` canary inside mapped Create;
- mapped/patched Veil `1.0.0.296`;
- absence of Create `0.5.1.j` and Flywheel `0.6.11-13`;
- the existing optional Ponder linkage isolation and disabled deferred Mixin
  graph.

No networking, Mixin, rendering, physics, Companion, or feature code was
changed to address this failure.

## Static Runtime Preflight (2026-08-23)

No Minecraft client or server was launched during this follow-up. Static
reconstruction proved that the same duplicate-module failure class also
applied to MixinExtras: standalone Forge wrapper `0.5.3` and Create's nested
Forge wrapper `0.4.1` both have automatic module name `mixinextras` and expose
the Forge bootstrap package. Forge can select the newer nested common payload,
but that does not remove the second outer wrapper from the module surface.

The development-only Create preparation now removes exactly the older
MixinExtras wrapper payload and its one JarJar metadata row. It preserves
Create's exact mapped Registrate, Flywheel, and Ponder entries. The effective
module names are now:

| Runtime element | Module name | Packages | Resolution |
|---|---:|---:|---|
| filtered Create `6.0.8` | `create` | 286 | direct development source |
| Flywheel `1.0.5` | `flywheel` | 66 | mapped source; byte-identical to Create request |
| Ponder `1.0.91` | `ponder` | 50 | mapped source; byte-identical to Create request |
| Registrate `MC1.20-1.3.3` | `Registrate.MC1._20` | 7 | Create JarJar only; derived from mapped nested filename |
| MixinExtras Forge `0.5.3` | `mixinextras` | 1 | standalone Sable dependency |
| MixinExtras common `0.5.3` | `MixinExtras` | 49 | JarJar inside preceding wrapper |
| patched Veil artifact `1.0.0.296` | `veil` | 150 | external development runtime |

There are no duplicate effective module names, split packages, identical
duplicate service providers, raw/mapped target pairs, old Create/Flywheel
jars, or standalone Registrate. Runtime mod IDs are exactly `sable`, `create`,
`flywheel`, `ponder`, and `veil`. Ponder contributes its six platform service
interfaces/providers, Veil contributes four, and MixinExtras common contributes
only its annotation processor descriptor; no provider is duplicated across
effective modules. Existing Sable packaging checks continue to require the
nine merged Sable/Companion service descriptors and their exact providers.

Veil's artifact coordinate remains `1.0.0.296`, but its own `mods.toml`
declares mod version `1.0.0`. The target smoke harness now checks the Forge
`ModList` value `1.0.0` while reporting the artifact coordinate separately.

The mechanical retained-Mixin inventory is in
`RUNTIME_MIXIN_OVERLAP.md`. It compared all enabled Sable Forge Mixins with
Create, Flywheel, Ponder, and Veil configs and found 164 shared-target pairs
that are `SAFE_INDEPENDENT`, one `ORDER_SENSITIVE`, zero
`NEEDS_BYTECODE_CHECK`, and zero `LIKELY_RUNTIME_CONFLICT`. The sole ordered
pair is Sable `entity_sublevel_collision.EntityMixin` and Create
`EntityContraptionInteractionMixin` at `Entity.move` `TAIL`. Mapped 1.20.1
bytecode confirms both handlers are priority 1100, non-cancellable, target the
existing descriptor/return boundary, and have no shared direct field writes.
No static incompatibility was proven, so no retained Mixin was changed. The
next run must still observe this interaction because equal-priority TAIL order
is the remaining semantic risk.

Bootstrap/lifecycle preflight confirmed exact dependency ranges and
optionality, unique Sable mod/network channel ownership, single common/client
config registration, single command/reload-listener ownership, guarded runtime
probe installation, per-server UDP ownership and cleanup, per-connection UDP
cleanup, and explicit first/second integrated-server lifecycle counters. The
outer `SablePlatformImpl` has no Create/Catnip/Flywheel constant-pool link;
`WrappedServerLevel` remains confined to
`SablePlatformImpl$PonderWrappedLevelCheck` after the Create-loaded check.

## Validation Commands

All commands used JDK 17 and `--offline` in the managed runner.

| Command | Result |
|---|---|
| `.\gradlew.bat projects` | PASS; only `:forge` and `:sable_companion_1_20` |
| `.\gradlew.bat :sable_companion_1_20:verifySableCompanionBackport` | PASS |
| `.\gradlew.bat :forge:prepareTargetModpackDependencies` | PASS; exact four-entry inventory and nested hashes |
| `.\gradlew.bat :forge:verifyTargetModpackDependencies` | PASS; all four compile modules mapped, no raw staged jar |
| `.\gradlew.bat :forge:verifyVeilDependency` | PASS |
| `.\gradlew.bat :forge:verifyForgeAccessTransformer` | PASS; 35 entries and Ponder boundary |
| `.\gradlew.bat :forge:networkTest` | PASS; 12/12 |
| `.\gradlew.bat :forge:compileJava` | PASS; 0 errors, four existing Forge deprecation warnings |
| `.\gradlew.bat :forge:verifyRunClientClasspath` before launch | PASS, but did not yet reject duplicate standalone Registrate |
| `.\gradlew.bat :forge:build` before launch | PASS after Spotless normalized edited line endings; reobfuscation, Checkstyle, and Spotless green |
| `.\gradlew.bat :forge:runClient` | **FAIL**; the one permitted process ended pre-title with the Registrate module collision |
| `.\gradlew.bat :forge:verifyRunClientClasspath` after fix | PASS; exact corrected runtime shape and mapped nested Registrate hash |
| `.\gradlew.bat :forge:build` after fix | PASS; reobfuscation, Checkstyle, Spotless, AT, and packaging checks |
| `.\gradlew.bat :forge:verifyRuntimeModuleBoundary` | PASS; seven effective modules, exact JarJar selection, no duplicate modules/packages/providers/raw pairs |
| `.\gradlew.bat :forge:verifyRetainedMixinCoexistence` | PASS; 164 safe shared-target pairs, one bytecode-checked ordered pair, no conflicts |
| `.\gradlew.bat :forge:verifyBootstrapLifecycleBoundary` | PASS; metadata, channel, service/config/event, UDP, and same-JVM ownership canaries |
| `.\gradlew.bat --offline :forge:runClient` current evidence attempt | **FAIL at Gate 1 probe after stable title**; unique Registrate resource belonged to `Registrate.MC1._20`, harness expected `Registrate`; no relaunch |
| `.\gradlew.bat --offline :forge:verifyRuntimeModuleBoundary :forge:verifyRunClientClasspath :forge:verifyRetainedMixinCoexistence :forge:verifyBootstrapLifecycleBoundary :forge:networkTest :forge:compileJava :forge:build` after harness fix | PASS; Registrate module derived as `Registrate.MC1._20`; no runtime launch |
| `.\gradlew.bat --offline :forge:runClient` final runtime smoke attempt | **FAIL at Gate 3**; Gate 1 and Gate 2 passed, but `create6_runtime_smoke` had mass `0.0` instead of `2.0` and was removed for an extreme Y coordinate range; no relaunch |
| `.\gradlew.bat --offline :forge:verifySingleBlockSpawnInitialization :forge:networkTest :forge:verifyRuntimeModuleBoundary :forge:build` after Gate 3 repair | PASS; spawn finalization gate, network protocol tests, runtime module boundary, compile/reobf/build/Checkstyle/Spotless all green; no runtime launch |
| `.\gradlew.bat --offline :forge:runClient` post Gate-3 repair runtime smoke | **FAIL at Gate 4 setup**; Gate 3 passed with mass `2.0`, zero velocities, ChangeBounds, and ordered StartTracking -> Finalize, but the lifecycle verifier saw `client logouts=2` at first server stop and crashed before same-JVM reopen |
| `.\gradlew.bat --offline :forge:verifyBootstrapLifecycleBoundary :forge:compileJava :forge:build` after lifecycle-delta repair | PASS; lifecycle canary, compile, reobfuscation, Checkstyle, Spotless, AT and packaging checks |
| `.\gradlew.bat --offline :forge:runClient` final lifecycle-delta runtime smoke | PASS; all five target-runtime gates passed in one client JVM |

The complete post-preflight regression suite also passed `projects`, Companion
verification, target preparation/verification, Veil verification, Forge AT,
`verifyRunClientClasspath`, `networkTest` 12/12, `compileJava` with zero
errors, and `build` including reobfuscation, Checkstyle, and Spotless. The
suite used JDK 17 and `--offline`; it did not invoke `runClient`.

An initial managed-sandbox compile attempt encountered the documented Windows
short-path `AccessDeniedException`; the same compile outside that filesystem
wrapper passed. This was build infrastructure, not a source or networking
failure.

## Deferred Boundaries

Nothing deferred was enabled. Create-specific and Flywheel Mixins, advanced or
chunked rendering, Rapier/full physics, Simulated, Aeronautics, Companion
JarJar, and feature-family ports remain deferred. The final lifecycle-delta
one-launch attempt proves the retained Sable core reaches title, opens a
world, creates the named stationary stone sublevel with mass `2.0`, restores it
in the same JVM, and shuts down cleanly in the presence of the Create 6 stack.

## Recommended Next Milestone

The Create 6 retained-core runtime rebaseline is complete for the scoped smoke
gates. The recommended next milestone is to choose the next explicitly bounded
feature family, likely Companion JarJar/standalone packaging or one isolated
deferred Create/Flywheel compatibility slice. Do not infer coverage for
Create/Flywheel Mixins, rendering, Rapier, Simulated, Aeronautics, Companion
JarJar, networking redesign, or Mixin priority changes from this smoke.

## M10 Manual Test Command Harness (2026-08-26)

No Minecraft client/server was launched for harness implementation. Use these
commands later in the real Forge 1.20.1 target modpack, one M10 gate at a time.

Local edit coordinates are integer offsets from the selected sub-level plot
center block, the same origin used by `spawn_l`. `spawn_l` creates one sublevel
with blocks at `(0,0,0)`, `(1,0,0)`, `(2,0,0)`, `(0,0,1)`, `(0,0,2)`.

### M10.1

```text
/sable m10 spawn_l m10_l
/sable m10 inspect @l
/sable m10 validate @l
```

Expected invariants: one sublevel, `blockCount=5`, finite positive mass, finite
COM, valid asymmetric X/Z bounds, `physicsSystemPresent=true`, and
`rigidBodyPresent=true`. Server-side validation now also requires
`collisionGeometryPresent=true` and nonzero uploaded collision geometry; if that
is absent, `validate` must report `SABLE_M10_VALIDATE status=FAIL
reason=no_collision_geometry` rather than a false pass. With the current stone
properties, five stone blocks should report aggregate mass near `10.000000`.

Client rendering remains a manual/runtime criterion. For this five-block spawn,
the basic renderer should emit a one-shot client log similar to
`SABLE_M10_RENDER id=... storedBlocks=5 renderedBlocks=5 ...`, and all five
blocks must be visible as one rigid L structure.

Optional material variant:

```text
/sable m10 spawn_l m10_l_oak minecraft:oak_planks
```

### M10.2

Pose convention: yaw around global Y uses the existing Sable command sign
(`-yaw` internally), then pitch around X, then roll around Z. Command inputs are
degrees. Linear velocity is global meters per second. Angular velocity command
inputs are global degrees per second and inspect reports both radians/second and
degrees/second.

```text
/sable m10 set_pose @l 45 10 20
/sable m10 inspect @l
/sable m10 set_velocity @l 0 0 0 0 20 0
/sable m10 inspect @l
/sable m10 stop @l
/sable m10 inspect @l
```

### M10.3

```text
/sable m10 add_block @l 2 0 1 minecraft:stone
/sable m10 inspect @l
/sable m10 validate @l
/sable m10 remove_block @l 2 0 1
/sable m10 inspect @l
/sable m10 validate @l
```

Expected invariants: add/remove update block count, bounds if the edit changes
an edge, aggregate mass, COM, collider data, and normal client block/bounds sync
without command-specific render packets.

### M10.4

```text
/sable m10 set_pose @l 30 12 17
/sable m10 set_velocity @l 0.05 0 0 0 5 0
/sable m10 inspect @l
```

Then Save & Quit, reopen the same world, and run:

```text
/sable m10 inspect @l
/sable m10 validate @l
```

Compare name/id, block count, mass, COM, bounds, orientation/pose, and velocity
state before and after load. IDs are expected to persist through the existing
sublevel serializer.

### M10.5

Stationary prep:

```text
/sable m10 stop @l
/sable m10 inspect @l
```

Small-motion prep:

```text
/sable m10 set_velocity @l 0.03 0 0 0 3 0
/sable m10 inspect @l
```

Manual checklist: stand on structure, walk across it, jump on it, jump off it,
collide with the side, look/target blocks, interact while stationary, interact
with small linear/angular motion, then stop with:

```text
/sable m10 stop @l
```

## M11.1 Create BlockEntity Baseline

Run these on a stationary M10 L sublevel. M11.1 checks ordinary Create
BlockEntity creation, server ticking, client synchronization, and stationary
Create-owned rendering. It does not test kinetic-network propagation.

```text
/sable m10 spawn_l m11_be_l
/sable m10 stop @l
/sable m10 add_block @l 3 0 0 create:shaft
/sable m10 add_block @l 4 0 0 create:cogwheel
/sable m10 add_block @l 5 0 0 create:creative_motor
/sable m10 inspect @l
/sable m11 inspect @l
```

Expected `SABLE_M11_INSPECT` invariants: the three Create blocks have real
BlockEntities in the sublevel plot, each reports its concrete class and level,
removed=false, and the normal ticker is present when the block supplies one.
The client should show the shaft, cogwheel, and creative motor using Create's
own renderer semantics. The Sable rigid body must remain stationary and usable
for collision and player interaction.

Removal/replacement smoke checks:

```text
/sable m10 remove_block @l 4 0 0
/sable m11 inspect @l
/sable m10 add_block @l 4 0 0 create:cogwheel
/sable m11 inspect @l
```

Save/reload, M11.2 kinetic-network construction, contraptions, and moving-frame
visual integration are deferred to later milestones.

## M11.2 Create Kinetics And Interaction

No Minecraft client/server was launched for this implementation pass. M11.2A
uses a deterministic Create topology instead of default-state manual placement.
M11.2B uses the normal right-click/item-use path so the Create wrench can act on
Sable sublevel blocks without a Create-specific rotation command.

### M11.2A

```text
/sable m11 spawn_kinetic m11_kinetic
/sable m11 inspect @l
/sable m11 validate_kinetic @l
```

The topology is one Sable sublevel with:

```text
(0,0,0) create:creative_motor[facing=east]
(1,0,0) create:shaft[axis=x]
(2,0,0) create:shaft[axis=x]
```

Expected invariants after Create has ticked/attached kinetics: all three
BlockEntities exist, are initialized, have nonzero finite speed, share the same
kinetic network when Create exposes it, and the two shafts have the same RPM as
the motor for this same-axis shaft chain. The command does not write Create
speed, source, or network state manually.

### M11.2B

Give yourself a Create wrench, then right-click the visible Sable sublevel
motor/shaft blocks:

```text
/give @s create:wrench
/sable m11 inspect @l
/sable m11 validate_kinetic @l
```

Manual checklist: right-click the shaft, the second shaft, and the creative
motor while the sublevel is stationary. Then optionally set a nontrivial Sable
pose and repeat one interaction:

```text
/sable m10 set_pose @l 30 10 15
/sable m10 stop @l
/sable m11 inspect @l
```

Expected behavior: the hit targets the visible rigid-body block, the action uses
the player's held wrench and hand, Create receives the local clicked face/hit,
any BlockState change synchronizes back to the client, Sable collision/render
data update through the normal block-edit lifecycle, and Create's kinetic
network is allowed to rebuild normally. The bridge logs one concise
`SABLE_M11_INTERACT ...` line per server-side sublevel interaction.

M11.2 still does not accept contraptions, bearings, trains, or Flywheel
moving-frame visual integration.

## M12 Scale And Ordinary Create Compatibility

No Minecraft client/server was launched while preparing this harness. M12 is
intended to be tested in one runtime session, stopping at the first meaningful
failure. Use `@l` immediately after a spawn if convenient, but after Save & Quit
prefer `/sable m12 list` and the persisted-name selector form:

```text
@e[name=m12_grid,limit=1]
```

### Phase 1: M12.1 Large Multi-Chunk Sublevel

```text
/sable m12 spawn_chunk_grid m12_grid
/sable m12 inspect @l
/sable m12 validate @l
/sable m12 acceptance @l
```

Expected server invariants: one Sable sublevel, roughly 140 stone blocks,
at least four actual hidden plot ChunkPos values, finite positive mass,
valid local/plot bounds, registered rigid body, and nonempty collision
geometry. Manually walk/jump across the platform and inspect that all chunks
render without seams. Then exercise pose/motion:

```text
/sable m10 set_pose @l 20 0 10
/sable m10 set_velocity @l 0 2 0 0 20 0
/sable m10 stop @l
```

### Phase 2: M12.2 Real Chunk Boundary Edits

```text
/sable m12 boundary_info @e[name=m12_grid,limit=1]
/sable m12 add_boundary_block @e[name=m12_grid,limit=1] A minecraft:stone
/sable m12 add_boundary_block @e[name=m12_grid,limit=1] B minecraft:stone
/sable m12 add_boundary_block @e[name=m12_grid,limit=1] C minecraft:stone
/sable m12 add_boundary_block @e[name=m12_grid,limit=1] D minecraft:stone
/sable m12 validate_boundaries @e[name=m12_grid,limit=1]
/sable m12 remove_boundary_block @e[name=m12_grid,limit=1] A
/sable m12 remove_boundary_block @e[name=m12_grid,limit=1] B
/sable m12 remove_boundary_block @e[name=m12_grid,limit=1] C
/sable m12 remove_boundary_block @e[name=m12_grid,limit=1] D
/sable m12 validate_boundaries @e[name=m12_grid,limit=1]
```

Expected: slots A/B sit on opposite sides of a real X chunk seam, C/D on
opposite sides of a real Z chunk seam. Edits update server storage, mass,
collision, client rendering, and persistence through the normal Sable edit
lifecycle. Visual seam correctness remains manual.

### Phase 3: M12.3 Cross-Chunk Kinetic Span

```text
/sable m12 spawn_kinetic_span m12_span
/sable m12 inspect @l
/sable m12 validate_kinetic_span @l
/sable m12 acceptance @l
```

Expected topology: one `create:creative_motor[facing=east]` driving sixteen
`create:shaft[axis=x]` blocks across at least one actual hidden plot chunk
boundary. Create must provide all speed/source/network state; Sable does not
write it directly. Expected RPM relationship is 1:1 magnitude along the
same-axis shaft chain.

Manual interaction checks:

```text
/give @s create:wrench
```

Right-click one span shaft with the wrench to break the compatible axis, then:

```text
/sable m12 validate_kinetic_span @e[name=m12_span,limit=1]
```

Expected: FAIL for a real connectivity/speed/topology reason. Wrench it back
to `axis=x`, wait for Create to rebuild, then rerun validation and expect PASS.
Hold RMB on the motor, change speed through Create's normal UI, then inspect:

```text
/sable m12 inspect @e[name=m12_span,limit=1]
/sable m12 validate_kinetic_span @e[name=m12_span,limit=1]
```

Expected: motor and shaft speeds change together and remain in one network.

### Phase 4: M12.4 Representative Create Suite

```text
/sable m12 spawn_create_suite m12_suite
/sable m12 inspect_create_suite @l
/sable m12 validate_create_suite @l
/sable m12 acceptance @l
```

The suite contains ordinary stationary Create representatives only:

```text
create:shaft[axis=x]
create:cogwheel[axis=x]
create:large_cogwheel[axis=y]
create:gearbox[axis=x]
create:creative_motor[facing=east]
create:depot
create:rotation_speed_controller[axis=x]
create:speedometer[facing=east,axis_along_first=false]
```

Expected server invariants: expected blocks exist, BlockEntities exist where
the BlockState requires them, Sable physics remains registered, and validation
does not claim visual/UI success. Manually verify rendering and normal
interactions that apply to each block.

### Phase 5: M12.5 Persistence/Reload

Before saving:

```text
/sable m12 persistence_snapshot @e[name=m12_grid,limit=1]
/sable m12 persistence_snapshot @e[name=m12_span,limit=1]
/sable m12 persistence_snapshot @e[name=m12_suite,limit=1]
```

Save & Quit, reopen the same world, then recover live targets:

```text
/sable m12 list
/sable m12 persistence_snapshot @e[name=m12_grid,limit=1]
/sable m12 validate @e[name=m12_grid,limit=1]
/sable m12 persistence_snapshot @e[name=m12_span,limit=1]
/sable m12 validate_kinetic_span @e[name=m12_span,limit=1]
/sable m12 persistence_snapshot @e[name=m12_suite,limit=1]
/sable m12 validate_create_suite @e[name=m12_suite,limit=1]
```

Expected: snapshots remain consistent with live state, Create kinetics resume,
wrench and value-settings interactions still work, and Sable rigid-body motion
still works. UUIDs/names printed by `m12 list` identify the live reloaded
sublevels.

### Phase 6: M12.6 Scale/Render/Performance Fixture

Run medium only after earlier phases pass:

```text
/sable m12 spawn_scale_grid m12_scale_medium medium
/sable m12 scale_info @l
/sable m12 validate @l
/sable m12 acceptance @l
```

Expected: about 400 blocks, bounded chunk count, sane mass/collision, no log
spam, and acceptable manual render/collision behavior. Only if medium works:

```text
/sable m12 spawn_scale_grid m12_scale_large large
/sable m12 scale_info @l
/sable m12 validate @l
/sable m12 acceptance @l
```

Expected: about 900 blocks with the same server invariants. M12 does not
measure FPS automatically; stutter, missing rendered chunks, duplicate
rendering, or Embeddium/Oculus regressions are manual observations.
