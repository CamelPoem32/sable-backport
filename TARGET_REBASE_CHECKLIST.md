# Target Modpack Rebaseline Checklist

Use this checklist before any post-M6 implementation. The replacement
`../target_modpack` is authoritative; filenames alone are not sufficient.

## 1. Protect The M6 Baseline

- Confirm branch `backport/forge-1.20.1-sable-2.0.0` and a clean worktree.
- Record `git rev-parse HEAD` and retain the named M6 checkpoint.
- Do not edit Sable, Mixins, networking, rendering, JarJar, Rapier, Simulated,
  or Aeronautics during inventory.
- Confirm `forge/run`, `forge/logs`, `.gradle`, and all `build` directories are
  ignored.

## 2. Inventory The Replacement

Run from the repository root:

```powershell
Get-ChildItem ..\target_modpack -Recurse -File |
    Select-Object FullName, Length, LastWriteTime
Get-ChildItem ..\target_modpack\mods\*.jar |
    Get-FileHash -Algorithm SHA256
```

For every relevant jar, inspect `META-INF/MANIFEST.MF`, Forge/NeoForge/Fabric
mod metadata, and `META-INF/jarjar/metadata.json`. Inspect nested jars rather
than assuming that Flywheel, Registrate, or Ponder is standalone.

Record exactly:

- [ ] Minecraft version and loader metadata.
- [ ] Forge version and loader range.
- [ ] Java requirement.
- [ ] Create artifact name, exact version, mod metadata version, and SHA-256.
- [ ] Flywheel exact version, standalone/embedded status, coordinates, and
      SHA-256.
- [ ] Registrate exact version, standalone/embedded status, coordinates, and
      SHA-256.
- [ ] Ponder exact version and whether standalone, bundled, embedded, or absent.
- [ ] Veil presence, exact version, artifact type/classifier, dependencies, and
      SHA-256.
- [ ] Sable Companion presence/packaging, if any.
- [ ] Other Sable-relevant mods: MixinExtras, JEI, Sodium/Embeddium/Oculus/Iris,
      Jade, ComputerCraft, Exposure, Vista, and any physics/runtime library.

## 3. Build The Three-Way Comparison

Create a table with columns `component`, `old target`, `new target`, `upstream
Sable 2.0.0`, `packaging`, and `required action`.

Old target reference:

| Component | Version |
|---|---|
| Minecraft | `1.20.1` |
| Forge | `47.4.20` |
| Java | `17` |
| Create | `0.5.1.j` |
| Flywheel | `0.6.11-13` |
| Registrate | `MC1.20-1.3.3` |
| Veil | `1.0.0.296` |

Upstream Sable 2.0.0 reference:

| Component | Version |
|---|---|
| Minecraft | `1.21.1` |
| NeoForge | `21.1.228` |
| Java | `21` |
| Create | `6.0.10-280` |
| Flywheel | `1.0.6` |
| Registrate | `MC1.21-1.3.0+67` |
| Ponder | `1.0.82` |
| Veil | `4.1.4` |

- [ ] Mark every version/package mismatch.
- [ ] Mark every old hash as historical and replace only after verification.
- [ ] Decide whether Minecraft/Forge/Java remain `1.20.1`/`47.4.20`/`17`.
- [ ] Determine whether the new Create 6 line is API-close to upstream Sable's
      Create 6 line or still requires a version-specific adapter.

## 4. Re-Audit Existing Decisions

- [ ] `forge/build.gradle`: Create jar filename discovery, manifest validation,
      expected coordinates, versions, SHA-256 values, JarJar extraction,
      staged filenames, flatDir coordinates, and dependency declarations.
- [ ] `gradle.properties`: Forge target values and Create/Flywheel/Registrate,
      Veil, and loader version ranges.
- [ ] `forge/src/main/resources/META-INF/mods.toml`: mandatory/optional status,
      sides, ordering, and version ranges.
- [ ] `SablePlatformImpl.CreateWrappedLevelCheck`: verify whether the new Create
      jar still contains
      `com.simibubi.create.foundation.utility.worldWrappers.WrappedServerWorld`.
- [ ] `verifyForgeAccessTransformer`: revalidate the Create wrapped-world
      classpath canary; do not retain or delete it without evidence.
- [ ] Flywheel: re-audit the `0.6` no-op/compatibility assumptions against the
      actual new API and packaging.
- [ ] Ponder: determine whether a new explicit dependency/staging rule is needed
      or whether Create bundles it.
- [ ] Veil: rerun the API/version assessment before retaining the Veil 1.20
      dependency, development patcher, or `mods.toml` range.
- [ ] Deferred Create/Flywheel Mixins: compare them with the actual new classes.
      Create 6 may make some upstream code directly reusable; do not enable the
      whole upstream config blindly.

## 5. Files And Tasks To Update

Expected files, only after the comparison is reviewed:

- `gradle.properties`
- `forge/build.gradle`
- `forge/src/main/resources/META-INF/mods.toml`
- `forge/src/main/java/dev/ryanhcode/sable/forge/platform/SablePlatformImpl.java`
  only if wrapped-level evidence changes
- `gradle/mixin-backport-inventory.gradle` and generated
  `MIXIN_BACKPORT_MATRIX.md` only if selected source/Mixins change
- `VEIL_1_20_API_MATRIX.md` if Veil changes
- `ACCESS_TRANSFORMER_BACKPORT_MATRIX.md` if target classes/mappings change
- `BACKPORT_STATUS.md`, `HANDOFF.md`, and this checklist

Rebaseline tasks that may need implementation changes:

- `prepareTargetModpackDependencies`
- `verifyTargetModpackDependencies`
- `verifyVeilDependency`
- `verifyRunClientClasspath`
- `verifyForgeAccessTransformer`
- `verifyForgePackaging`

## 6. Validate The Rebaseline

After updating build assumptions, under the confirmed Java version run:

```powershell
.\gradlew.bat projects
.\gradlew.bat :forge:prepareTargetModpackDependencies
.\gradlew.bat :forge:verifyTargetModpackDependencies
.\gradlew.bat :forge:verifyVeilDependency
.\gradlew.bat :forge:verifyForgeAccessTransformer
.\gradlew.bat :sable_companion_1_20:verifySableCompanionBackport
.\gradlew.bat :forge:generateMixinBackportMatrix
.\gradlew.bat :forge:networkTest
.\gradlew.bat :forge:verifyRunClientClasspath
.\gradlew.bat :forge:compileJava
.\gradlew.bat :forge:compileJava
.\gradlew.bat :forge:build
```

- [ ] No raw local mod jar appears on `compileClasspath`.
- [ ] Every required local module resolves through ForgeGradle deobfuscation.
- [ ] Exact versions and hashes in documentation match observed artifacts.
- [ ] The second compile is up-to-date or otherwise has an explained reason.
- [ ] Existing 12 networking tests still pass.
- [ ] Current Mixin config/refmap counts are recorded, not assumed.
- [ ] Build/package outputs remain ignored.

Do not run `runClient` or begin a feature milestone until the static rebaseline
passes and the dependency delta is documented.

## 7. Stop And Report

Report the three-way dependency table, updated hashes, packaging layout,
changed build assumptions, old compatibility work requiring removal or
revision, static validation results, and a narrowly proposed next milestone.
Do not implement that milestone in the rebaseline task.
