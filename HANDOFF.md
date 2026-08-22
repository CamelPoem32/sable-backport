# Sable Forge 1.20.1 Backport Handoff

## Read This First

This repository is the working backport of Sable 2.0.0 from Minecraft 1.21.1
NeoForge to Minecraft 1.20.1 Forge. Do not begin M7 or feature work until the
new `../target_modpack` has been inventoried using
`TARGET_REBASE_CHECKLIST.md`.

The build currently encodes the old target modpack:

| Component | Old target |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.20` |
| Create | `0.5.1.j` |
| Flywheel | `0.6.11-13` |
| Registrate | `MC1.20-1.3.3` |
| Veil | `1.0.0.296` |
| Java | `17` |

The user is about to replace that modpack and move Create to Create 6. All old
target hashes, embedded-dependency assumptions, version ranges, staging rules,
and Create-0.5-specific decisions require revalidation. Do not edit those
areas until the replacement modpack is present and inspected.

## Project Identity

- Goal: produce a safe Forge backport of Sable 2.0.0 for the user's target
  modpack, eventually supporting the wider Create Aeronautics stack.
- Upstream Sable ref: `mc1.21.1-2.0.0-neoforge`.
- Upstream Sable commit: `b7226222caf4eace63a708bdcd73ef36c971137d`.
- Backport branch: `backport/forge-1.20.1-sable-2.0.0`.
- M6 source checkpoint: `9ff44c1a870f0c47e8a7df141503ec3bd5b496b6`.
- Current target before rebaseline: Minecraft `1.20.1`, Forge `47.4.20`, Java
  `17`.
- Upstream dependency reference in `gradle.properties`: Minecraft `1.21.1`,
  Create `6.0.10-280`, Flywheel `1.0.6`, Registrate
  `MC1.21-1.3.0+67`, Ponder `1.0.82`, and Veil `4.1.4`.

## Milestone History

- M0-M2: isolated the Forge project graph; staged and ForgeGradle-deobfuscated
  exact old target jars; wired Mixin AP/refmap generation; generated the Mixin
  inventory and excluded optional compatibility Mixins.
- M3: added the real Java 17/Minecraft 1.20.1 Companion common library, reused
  Veil 1.20 where practical, removed selected Java 21 APIs, and established the
  minimal Forge source graph.
- M4: replaced modern Veil/Minecraft packet APIs with a Sable-owned transport
  over Forge 47 `SimpleChannel`; added 12 protocol tests.
- M5: ported the selected Minecraft/Mixin core, added Forge `@Mod` bootstrap,
  eight ServiceLoader platform providers, capability persistence, curated ATs,
  and static package verification. `compileJava` and `build` became green.
- M6: added the runtime harness and diagnostics, fixed retained-core runtime
  issues, reached main menu, loaded and reloaded an empty world, and created,
  synchronized, saved, and restored one stationary named stone sublevel.

## Current Status

### PROVEN WORKING

- Forge-only Gradle graph with `:forge` and `:sable_companion_1_20` under JDK
  17, without `--configure-on-demand`.
- `:forge:compileJava`, `:forge:build`, reobfuscation, Checkstyle, Spotless,
  static package verification, mapped Veil verification, curated AT
  verification, and old-target dependency deobfuscation.
- Companion common API on Java 17, both ServiceLoader providers present, and
  deterministic selection of `ActiveSableCompanion` because priority
  `1000 > 500`.
- Sable-owned TCP protocol `1`, explicit IDs `0..13`, UDP ordinals `0..5`, and
  all 12 network tests covering codecs, ordering, direction, context, and
  malformed input.
- Forge bootstrap, all eight platform providers, both configs, reload
  listeners, datapack synchronization, login/logout, level, and server
  lifecycle markers in the M6 development run.
- Main menu, a Creative Superflat Void world stable for over 60 seconds, clean
  shutdown, fresh-JVM world reload, local UDP cleanup, and plot persistence.
- `/sable spawn block minecraft:stone m6_smoke` followed by
  `/sable info @l`: exactly one stationary sublevel; StartTracking before
  Finalize; same name, pose, mass, and zero velocities after reload.
- Current curated Forge Mixin config: 143 entries. Current generated refmap:
  111 mapped classes. Optional/debug/Create Mixins are absent.

### COMPILES BUT NOT RUNTIME-TESTED

- Most retained core Mixins beyond the paths exercised by main menu, world
  load, stationary sublevel sync/render, save, and reload.
- Every individual method of the eight platform providers. M6 proves provider
  discovery and the lifecycle/persistence paths it exercised, not exhaustive
  behavior.
- Every TCP packet handler at runtime. Their codecs, IDs, directions, context,
  and ordering are tested independently.
- Dedicated-server behavior, multiplayer remote connections, UDP startup and
  cleanup outside the integrated-server path, and same-JVM world reload.
- The standalone reobfuscated mod artifact in an external mods directory.
  Gradle `runClient` uses Companion project output.

### DEFERRED

- Advanced/chunked rendering, shaders, water occlusion, Iris/Sodium bridges,
  editor/debug UI, custom camera modes, settings/toasts, and optional compat.
- Behavior-heavy Minecraft clusters listed in `MIXIN_BACKPORT_MATRIX.md`,
  including Leashable/pathfinding, vibration, projectile-dispenser, recoil,
  and tamed teleport work.
- Create and Flywheel Mixins and visuals. Their source remains intact but is
  outside the selected Forge graph.
- Companion JarJar/final bundling and standalone modpack smoke testing.

### NOT YET PORTED

- Sable Rapier/full physics. The static pipeline lets the runtime boundary
  operate, but M6 does not prove moving-body physics.
- Create contraption/logistics integration and Flywheel visual integration.
- Simulated and Create Aeronautics.
- Full advanced rendering and optional integration behavior.

## Architecture

### Forge Module

`settings.gradle` includes only `:forge` and `:sable_companion_1_20` when
`sableForgeBackport=true`. `forge/build.gradle` reads selected common sources
directly, applies narrow exclusions, configures ForgeGradle/MixinGradle, stages
local dependencies, defines verification tasks, and owns `runClient`.

`dev.ryanhcode.sable.forge.SableForge` is the Forge 47 entrypoint. Forge-specific
config replacements and ServiceLoader implementations live under
`forge/src/main/java/dev/ryanhcode/sable/forge`.

### Companion

`:sable_companion_1_20` is a `java-library`, not a Forge mod. It is based on the
12-source Sable Companion 1.6.0 common artifact and preserves its API and
priority-based provider semantics. Forge consumes it as a project dependency.
Do not give it a mandatory mod ID or replace it with stubs.

### Networking

Common packet code depends on `SablePacketCodec`, direction/context,
registration, sink, and transport abstractions using `FriendlyByteBuf`. The
Forge provider uses `SimpleChannel` and `NetworkEvent.Context`, preserves
explicit packet IDs/directions, enqueues once, and validates sender/thread.
`NETWORK_BACKPORT_MATRIX.md` is the protocol contract.

### Platform Providers

Eight Forge providers implement assembly, chunk events, event subscription,
event publication, loader/platform hooks, plot persistence, and basic sublevel
rendering. Plot persistence uses Forge capability NBT. Auxiliary light and
Flywheel visual registration remain deliberate no-ops for deferred features.

### Veil

The selected core reuses official Veil Forge 1.20.1 `1.0.0.296`. Networking was
replaced only where Veil 1.20 lacked the required API. The development runtime
is mapped and narrowly patched for six published shadow aliases, with bundled
LWJGL split-package classes removed. Re-audit all of this if the new modpack
changes Veil. See `VEIL_1_20_API_MATRIX.md`.

### Mixins And Resources

`gradle/mixin-backport-inventory.gradle` mechanically inventories 373 upstream
Mixins and generates the curated Forge config and matrix. Upstream configs and
deferred source files remain intact. Never globally suppress Mixin target
validation or broadly add `require = 0`; inspect mapped target bytecode before
changing a retained Mixin.

`processResources` translates Minecraft 1.21 singular data directories and tag
names to exact 1.20.1 Forge forms, and marks known absent optional entries as
non-required. Missing Sable/Minecraft selectors remain errors.

## Canonical Commands

Use JDK 17. `--offline` is optional after dependencies are cached.

```powershell
.\gradlew.bat projects
.\gradlew.bat :forge:prepareTargetModpackDependencies
.\gradlew.bat :sable_companion_1_20:verifySableCompanionBackport
.\gradlew.bat :forge:generateMixinBackportMatrix
.\gradlew.bat :forge:networkTest
.\gradlew.bat :forge:verifyTargetModpackDependencies
.\gradlew.bat :forge:verifyVeilDependency
.\gradlew.bat :forge:verifyForgeAccessTransformer
.\gradlew.bat :forge:verifyRunClientClasspath
.\gradlew.bat :forge:compileJava
.\gradlew.bat :forge:compileJava
.\gradlew.bat :forge:build
.\gradlew.bat :forge:runClient
```

Before the target-modpack replacement, expected results are: two projects;
Companion verification succeeds with 12 sources and class major 61; network
tests pass 12/12; the three old local modules resolve through ForgeGradle mapped
artifacts; the AT verifier accepts 35 entries; compile/build succeed; and
`runClient` reaches the M6 smoke state. Once `../target_modpack` is replaced,
the old dependency preparation/verification tasks may intentionally fail until
the rebaseline is implemented.

## Behavioral Invariants

- Preserve TCP IDs `0..13`, UDP ordinals `0..5`, packet field order, packet
  direction, full-sync ordering, snapshot fallback ordering, and main-thread
  dispatch. Do not recreate fake 1.21 networking classes.
- Preserve deterministic Companion max-priority selection and its common/JiJ
  architecture.
- Keep optional Create linkage isolated. Do not load a Create class when Create
  is absent.
- Keep deferred upstream source/configs for reference; exclusions must be
  narrow and documented.
- Preserve Mixin AP/refmap mapping and target validation. Validate retained
  descriptor/control-flow changes against mapped 1.20.1 bytecode.
- Preserve fixed 20 TPS/50 ms behavior justified in M5; do not invent a fake
  `TickRateManager`.
- Every retained or removed AT entry needs provenance in
  `ACCESS_TRANSFORMER_BACKPORT_MATRIX.md`.
- Stationary single-block success is not physics success.

## Known Limitations

- Empty-world and single-block reloads used a fresh JVM because the development
  client exited after Save and Quit; same-JVM duplicate-listener behavior was
  not exercised.
- The M6 stone block overlapped the stone Void platform in the captured view,
  so there is no independent pixel-level render assertion.
- No dedicated server, remote multiplayer, standalone mods-folder artifact, or
  target-modpack runtime has been tested.
- Create/Flywheel were intentionally absent from M6 runtime.
- Companion is not yet bundled; full physics is absent.

## Inspect First

1. `HANDOFF.md` and `TARGET_REBASE_CHECKLIST.md`.
2. `BACKPORT_STATUS.md` and `M6_RUNTIME_SMOKE.md`.
3. `forge/build.gradle`, `settings.gradle`, and `gradle.properties`.
4. `NETWORK_BACKPORT_MATRIX.md`, `MIXIN_BACKPORT_MATRIX.md`,
   `VEIL_1_20_API_MATRIX.md`, and `ACCESS_TRANSFORMER_BACKPORT_MATRIX.md`.
5. `forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java`,
   `SableForgeRuntimeSmoke.java`, the Forge `platform` and `network` packages,
   and `sable_companion_1_20`.

## Target Modpack Rebaseline Procedure

1. Place or inspect the replacement modpack at `../target_modpack`; do not
   change source code first.
2. Inventory exact Minecraft, Forge, Create, Flywheel, Registrate, Ponder, Veil,
   and all Sable-relevant dependency versions. Inspect manifests,
   `META-INF/mods.toml`, and `META-INF/jarjar/metadata.json`, including nested
   jars and hashes.
3. Compare the new inventory with both the old target table above and upstream
   Sable 2.0.0 dependencies.
4. Determine whether each dependency is standalone, embedded/JarJar, optional,
   or transitive. Do not infer this from filenames alone.
5. Produce a written delta and decide whether Minecraft `1.20.1`, Forge
   `47.4.20`, and Java 17 remain the target before changing build properties.
6. Re-audit old Create-0.5-specific work: local staging/extraction coordinates
   and hashes, version ranges, `WrappedServerWorld` detection, the Create
   classpath canary in AT verification, Flywheel 0.6 assumptions, absence of a
   Ponder dependency, and all deferred Create/Flywheel source boundaries.
7. If the new target uses Create 6, prefer its actual APIs and bundled dependency
   layout. Some previously deferred Create 6 backport work may become direct
   reuse, while the Create 0.5 compatibility boundary may become unnecessary.
   Do not delete it until classpath/runtime evidence proves that.
8. Update and run only the rebaseline tasks listed in
   `TARGET_REBASE_CHECKLIST.md`. Stop with a reviewed dependency delta before
   beginning JarJar, rendering, physics, Simulated, or Aeronautics work.

## Recommended Next Steps

The next task is target-modpack rebaseline only. After the new environment is
documented and build assumptions are updated, rerun the complete static suite
and decide a new milestone from the resulting evidence. Do not assume the old
recommendation of Companion JarJar is still the immediate M7 until the new
modpack packaging layout is known.

## M6 Checkpoint Files

Commit `9ff44c1` is the authoritative M6 source delta. Its exact file list is
available with:

```powershell
git show --name-status --format= 9ff44c1
```

The exact 29 tracked files are:

```text
.gitignore
BACKPORT_STATUS.md
M6_RUNTIME_SMOKE.md
MIXIN_BACKPORT_MATRIX.md
buildSrc/build.gradle
buildSrc/src/main/java/VeilDevelopmentRuntimePatcher.java
common/src/main/java/dev/ryanhcode/sable/mixin/camera/camera_zoom/CameraMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/clip_overwrite/BlockGetterMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/EntityRenderDispatcherMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_rotations_and_riding/BlockMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/explosion/ServerLevelMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/particle/ParticleEngineMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/plot/ClientChunkCacheMixin.java
common/src/main/java/dev/ryanhcode/sable/mixinhelpers/camera/camera_rotation/EntitySubLevelRotationHelper.java
common/src/main/java/dev/ryanhcode/sable/mixinhelpers/clip_overwrite/BlockGetterClipHelper.java
common/src/main/java/dev/ryanhcode/sable/physics/config/block_properties/PhysicsBlockPropertiesDefinitionLoader.java
common/src/main/java/dev/ryanhcode/sable/physics/config/block_properties/PhysicsBlockPropertyTypes.java
forge/build.gradle
forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java
forge/src/main/java/dev/ryanhcode/sable/forge/SableForgeClient.java
forge/src/main/java/dev/ryanhcode/sable/forge/SableForgeRuntimeSmoke.java
forge/src/main/java/dev/ryanhcode/sable/forge/network/ForgeSablePacketTransport.java
forge/src/main/java/dev/ryanhcode/sable/forge/network/client/ForgeSableClientPacketHandler.java
forge/src/main/resources/META-INF/services/dev.ryanhcode.sable.companion.SableCompanion
forge/src/main/resources/pack.mcmeta
forge/src/main/resources/sable-common-forge.mixins.json
gradle.properties
gradle/mixin-backport-inventory.gradle
sable_companion_1_20/src/main/java/dev/ryanhcode/sable/companion/SableCompanion.java
```

They include the runtime probe, retained Mixin fixes, Veil development patcher,
resource/build changes, M6 report, generated Mixin matrix, and ignored-output
rule. No world, log, cache, or build artifact belongs in the checkpoint.

## First Prompt For The Next Codex

```text
Read HANDOFF.md, TARGET_REBASE_CHECKLIST.md, BACKPORT_STATUS.md,
M6_RUNTIME_SMOKE.md, NETWORK_BACKPORT_MATRIX.md, MIXIN_BACKPORT_MATRIX.md,
VEIL_1_20_API_MATRIX.md, and ACCESS_TRANSFORMER_BACKPORT_MATRIX.md. Do not
implement M7 or any feature yet. Inspect the replacement ../target_modpack,
perform the documented target-modpack rebaseline, compare it with both the old
Create 0.5.1.j environment and upstream Sable 2.0.0, then report the exact
dependency/build delta and which existing compatibility work needs re-audit.
```
