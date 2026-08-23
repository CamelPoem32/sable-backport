# Target Modpack Rebaseline Checklist

Use this checklist before any post-M6 implementation. The replacement
`../target_modpack` is authoritative; filenames alone are not sufficient.

## Rebaseline Result: Complete 2026-08-22

This checklist was executed against the replacement pack before feature work.
The pack has 308 valid unique jars. The Create manifest and metadata establish
Minecraft `1.20.1` and Create `6.0.8`; the replacement files do not contain a
separate launcher profile proving a Forge build number, so the already proven
project baseline remains Forge `47.4.20` / Java `17`. All inspected target
classes are Java 17-compatible (class major at most 61).

Create `create-1.20.1-6.0.8.jar` has SHA-256
`6FBB910C367DBCE8E4FC7E5BF64B6EDD4DE980906ED00AF8E47E4AF843C0D9B0` and
exactly four top-level JarJar records:

| Coordinate | Version / range | Nested path | SHA-256 |
|---|---|---|---|
| `dev.engine-room.flywheel:flywheel-forge-1.20.1` | `1.0.5`; `[1.0,2.0)` | `META-INF/jarjar/flywheel-forge-1.20.1-1.0.5.jar` | `316CA250F19244956B5F0CD75329309EA65A77B4B8DA854389B6A9222E7F427C` |
| `com.tterrag.registrate:Registrate` | `MC1.20-1.3.3`; `[MC1.20-1.3.3,)` | `META-INF/jarjar/Registrate-MC1.20-1.3.3.jar` | `226862D4638B77273F4627FBAC871AA0B3AF584DDE377F4CE2CB0C7CC228CF00` |
| `net.createmod.ponder:Ponder-Forge-1.20.1` | `1.0.91`; `[1.0.91,)` | `META-INF/jarjar/Ponder-Forge-1.20.1-1.0.91.jar` | `86E6B64372ABA6D9C56F2C35725EA26D8FEBF2C75EED9950566E7F2849443B34` |
| `io.github.llamalad7:mixinextras-forge` | `0.4.1`; `[0.4.1,)` | `META-INF/jarjar/mixinextras-forge-0.4.1.jar` | `9D48CB0A40299D283248FDAD8B02C6D175C45B27F9BEC48EF63D7EE8A4EE3066` |

Every record has `isObfuscated: false`. The MixinExtras Forge jar additionally
contains `META-INF/jars/MixinExtras-0.4.1.jar`, SHA-256
`D13C480D4E84128E76E8F509346F8137CEAA195091B3CFEAF4EBBA584CA68374`.
Create, Flywheel, Registrate, and Ponder are the four mapped local modules;
MixinExtras is recursively validated but Sable retains its own `0.5.3`.

The pack contains neither Veil nor Sable Companion. Veil
`foundry.veil:Veil-forge-1.20.1:1.0.0.296` therefore remains an additional
required runtime dependency. Companion packaging remains a separate deferred
milestone. No Minecraft launch was performed for this rebaseline.

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

- [x] Minecraft version and available loader metadata (the replacement has no
      separate loader profile; retain the proven Forge baseline).
- [x] Forge version and loader range retained from the proven project baseline.
- [x] Java requirement checked from target class-file majors.
- [x] Create artifact name, exact version, mod metadata version, and SHA-256.
- [x] Flywheel exact version, standalone/embedded status, coordinates, and
      SHA-256.
- [x] Registrate exact version, standalone/embedded status, coordinates, and
      SHA-256.
- [x] Ponder exact version and whether standalone, bundled, embedded, or absent.
- [x] Veil presence, exact version, artifact type/classifier, dependencies, and
      SHA-256.
- [x] Sable Companion presence/packaging, if any.
- [x] Other Sable-relevant mods: MixinExtras, JEI, Sodium/Embeddium/Oculus/Iris,
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

- [x] Mark every version/package mismatch.
- [x] Mark every old hash as historical and replace only after verification.
- [x] Decide whether Minecraft/Forge/Java remain `1.20.1`/`47.4.20`/`17`.
- [x] Determine whether the new Create 6 line is API-close to upstream Sable's
      Create 6 line or still requires a version-specific adapter.

## 4. Re-Audit Existing Decisions

- [x] `forge/build.gradle`: Create jar filename discovery, manifest validation,
      expected coordinates, versions, SHA-256 values, JarJar extraction,
      staged filenames, flatDir coordinates, and dependency declarations.
- [x] `gradle.properties`: Forge target values and Create/Flywheel/Registrate,
      Veil, and loader version ranges.
- [x] `forge/src/main/resources/META-INF/mods.toml`: mandatory/optional status,
      sides, ordering, and version ranges.
- [x] `SablePlatformImpl.CreateWrappedLevelCheck`: verified that the old Create
      class is absent; replaced it with the isolated Ponder `1.0.91`
      `WrappedServerLevel` check.
- [x] `verifyForgeAccessTransformer`: revalidate the Create wrapped-world
      classpath canary; do not retain or delete it without evidence.
- [x] Flywheel: re-audit the `0.6` no-op/compatibility assumptions against the
      actual new API and packaging.
- [x] Ponder: determine whether a new explicit dependency/staging rule is needed
      or whether Create bundles it.
- [x] Veil: rerun the dependency/API canaries before retaining the Veil 1.20
      dependency, development patcher, or `mods.toml` range.
- [x] Deferred Create/Flywheel Mixins: compare them with the actual new classes.
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

- [x] No raw local mod jar appears on `compileClasspath`.
- [x] Every required local module resolves through ForgeGradle deobfuscation.
- [x] Exact versions and hashes in documentation match observed artifacts.
- [x] The build's repeated compile was up-to-date.
- [x] Existing 12 networking tests still pass.
- [x] Current Mixin config/refmap counts remain the recorded M6 values; no
      Mixin selection changed.
- [x] Build/package outputs remain ignored.

Do not run `runClient` or begin a feature milestone until the static rebaseline
passes and the dependency delta is documented.

## 7. Stop And Report

Report the three-way dependency table, updated hashes, packaging layout,
changed build assumptions, old compatibility work requiring removal or
revision, static validation results, and a narrowly proposed next milestone.
Do not implement that milestone in the rebaseline task.
