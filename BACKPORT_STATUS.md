# Sable Forge 1.20.1 Backport Status

## Target And Baseline

- Minecraft: `1.20.1`
- Forge: `47.4.20`
- Java: `17`
- Create: `6.0.8`
- Flywheel: `1.0.5`
- Registrate: `MC1.20-1.3.3`
- Ponder: `1.0.91`
- Upstream Sable ref: `mc1.21.1-2.0.0-neoforge`
- Upstream commit: `b7226222caf4eace63a708bdcd73ef36c971137d`
- Backport branch: `backport/forge-1.20.1-sable-2.0.0`

> **Target-modpack rebaseline completed 2026-08-22:** the replacement pack was
> inspected from its jars and nested JarJar metadata. The static suite is green
> on the exact Create `6.0.8` / Flywheel `1.0.5` / Registrate
> `MC1.20-1.3.3` / Ponder `1.0.91` baseline. No Minecraft process was launched;
> the prior M6 runtime result remains historical evidence for the old target.

The upstream `common`, `fabric`, `neoforge`, and `sable_rapier` modules remain available for reference. M0-M2 established trustworthy Forge build plumbing. M3 added the Java 17/1.20.1 Companion library, official Veil 1.20, selected Java 17 rewrites, and the minimal Forge source graph. M4 replaced the missing modern Veil/Minecraft packet surface with a tested Forge 47 transport. M5 ported the selected Minecraft core/Mixins, added the Forge bootstrap and eight platform providers, and produced a statically verified Forge package. M6 now passes the Forge main-menu, empty-world, runtime-boundary, persistence, and stationary single-block smoke gates. Deferred advanced rendering, full physics/Sable Rapier, Create/Flywheel integration, Simulated, and Aeronautics remain unported.

> **M7 standalone packaged-artifact runtime acceptance complete (2026-08-23):**
> the final packaged Sable artifact now boots through Forge/ModLauncher without
> Gradle development Sable or Companion outputs. The accepted runtime logged
> `SABLE_STANDALONE_RUNTIME phase=gate2-3 status=PASS`,
> `phase=lifecycle status=PASS`, `phase=gate5 status=PASS`, and
> `phase=complete status=PASS`, followed by `BUILD SUCCESSFUL`. Treat the
> standalone mapper, provenance parser, packaged Companion JarJar, module
> boundary, AT remapping, and harness work as closed unless new evidence appears.

> **M8 Rapier / real-physics planning status (2026-08-23):** upstream real
> physics lives in `:sable_rapier`, which provides
> `dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipelineProvider`,
> `RapierPhysicsPipeline`, Java JNI bridge `Rapier3D`, Rapier handle/collider
> classes, and Rust natives under `sable_rapier/src/main/rust`. The provider is
> discovered through
> `META-INF/services/dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider`;
> `RapierPhysicsPipelineProvider` has default priority `1000`, so it wins over
> `StaticPhysicsPipelineProvider` priority `900` once the Rapier service entry
> and classes are packaged. Current Forge backport mode includes only `:forge`
> and `:sable_companion_1_20`; `:sable_rapier` is not configured, its Java
> sources/resources are not in the Forge source set, and the common service file
> still selects the static fallback.
>
> Rapier dependencies to stage for M8 are: Java-side `at.yawk.lz4:lz4-java:1.11.0`
> or equivalent `net.jpountz.lz4` provider for `LZ4FrameInputStream`,
> `org.apache.maven:maven-artifact:3.8.5`, Companion common `1.6.0`, existing
> JOML/fastutil/Minecraft dependencies, and the bundled Rust/JNI native archive
> `natives/sable_rapier/sable_rapier_binaries.zip.l4z`. Rust native inputs are
> workspace crates `marten` and `sable_rapier`, `jni 0.21.1`, `rapier3d` from
> `https://github.com/ryanhcode/rapier` at
> `38e92f117590862481a53df6fc69a5d893e29186` with `simd-nightly` and
> `parallel`, `rayon 1.10.0`, `dashmap 7.0.0-rc2`, `log 0.4.22`, `fern 0.6.2`,
> `colored 2.1.0`, and `humantime 2.1.0`, built with Rust
> `nightly-2026-01-29` / edition 2024. Runtime native loading extracts the
> LZ4-compressed zip to `.sable/natives` and `System.load`s
> `sable_rapier_x86_64_windows.dll` or `sable_rapier_aarch64_windows.dll` on
> Windows, and `sable_rapier_x86_64_linux.so` or
> `sable_rapier_aarch64_linux.so` on Linux.
>
> Java 17 risk is narrow: `RapierPhysicsPipeline` uses Java 21 pattern-switch
> syntax in its constraint factory and must be rewritten to Java 17 `instanceof`
> logic; records/local records are Java 17-compatible. Forge 1.20.1 risks are
> mostly packaging and namespace/plumbing: do not apply NeoForge ModDev to the
> backport, do not enable deferred Create/Flywheel/rendering Mixins, keep
> Companion nested exactly once, merge/verify ServiceLoader descriptors so
> Rapier wins while Static remains fallback, preserve native resources/licenses,
> and statically prove the Rapier classes compile against the current retained
> 1.20.1 API. No repo-local or cached compatible Java 17 / MC 1.20.1 published
> Rapier artifact is present; plan to backport/package the upstream binding from
> source and existing native archive first.
>
> M8 checkpoints: (1) add an isolated Forge Rapier staging source set/library
> from `sable_rapier`, rewrite only Java 17-incompatible syntax, add exact Java
> dependencies, merge service descriptors, and end with
> `verifyRapierBackportStatic`, `compileJava`, and `build` only; (2) verify
> native archive contents, platform-name selection, extraction target, license
> provenance, JarJar/module split-package safety, and no dev-output leaks; (3)
> package Rapier as a Forge JarJar game library in Sable and prove
> ServiceLoader selects `RapierPhysicsPipelineProvider` while Static remains a
> lower-priority fallback; (4) add static smoke probes for a single
> gravity-enabled sublevel without Create/rendering feature scope; (5) only in a
> later runtime milestone, launch one client and prove one named stone sublevel
> moves/responds to gravity/collision in the existing smoke world.

> **M8.1 isolated Rapier Java staging closed (2026-08-24):**
> `.\gradlew.bat --offline :forge:verifyRapierBackportStatic` passed with
> `rapierSources=14`, `rapierClasses=16`, provider priority `1000`, Static
> fallback priority `900`, native payload `9046307` bytes, and
> `splitPackages=0`. The later post-clean retry was blocked before Rapier javac
> by the recurring Windows/ForgeGradle
> `forge/build/downloadMcpConfig/output.zip` lock and is treated as external
> build-infrastructure noise unless new evidence ties it to Rapier.
>
> **M8.2 Rapier native payload/JNI static acceptance complete (2026-08-24):**
> `.\gradlew.bat --offline :forge:verifyRapierNativeBackportStatic` passed
> without launching Minecraft, loading natives, compiling Rust, or regenerating
> the archive. The verifier LZ4-decompresses
> `natives/sable_rapier/sable_rapier_binaries.zip.l4z`
> (`SHA-256 427CAA80B6B7D365703C3196B20AA29097EBF7FE5A61E20ED8C57B2A71BA0401`)
> and proves the exact six-file matrix:
> `sable_rapier_x86_64_windows.dll` `1652224`
> `6096095D71DE3D9FA8EBBF495FC145CE023D2E2D74CA25AFCAB98FEE101E7480`;
> `sable_rapier_aarch64_windows.dll` `1335296`
> `B760BB8EA1965A88DE6C3D27F0FB46E9367EA8BE14234549EA9A4F757C33E477`;
> `sable_rapier_x86_64_linux.so` `1882016`
> `10A5F47C5BAE19F0D1C3222F914F6C480D65A7939AD1CD6815896803CEEAC7D8`;
> `sable_rapier_aarch64_linux.so` `1589048`
> `51A004C2D9FFE20E873978EDD3A760A4CB769DABF8B5631717A3AF912A1FD51D`;
> `sable_rapier_x86_64_macos.dylib` `4671896`
> `7549A775D2B7496CBAEB731F3AC0D5230F6BE7BC10538BC758B1BF779C511D65`;
> `sable_rapier_aarch64_macos.dylib` `4239008`
> `A28402FF298AE9D9F97AD9ED8264F75011B9B2C04C724FFE3793F0AC60959EC0`.
> `Rapier3D` has `53` static Java native methods; all six binaries expose
> `53/53` expected JNI names by static symbol-string scan, and Rust source
> declares the same `53` exports with static `JNIEnv` + `JClass` receivers. The
> loader maps `arm`/`aarch64` to `aarch64`, all other architectures to
> `x86_64`, selects Windows/macOS/Linux filenames, falls back to Linux with a
> logged error for unknown OSes, reads
> `/natives/sable_rapier/sable_rapier_binaries.zip.l4z`, extracts to
> `.sable/natives`, overwrites the selected native every load, and has no
> post-extraction hash validation. License/provenance files present:
> repository `LICENSE.md`, native `LICENSE-RAPIER`, and native README pointing
> at the modified Rapier source/license; no separate native third-party NOTICE
> bundle is present. Packaging boundary remains static-only: Rapier is not in
> `sourceSets.main`, common runtime service still selects Static fallback, Rapier
> service ownership is isolated to `sable_rapier` resources, the native payload
> has one source owner, and no Create/Flywheel/Ponder/Veil jars are bundled by
> Rapier resources.
>
> **M8.3 Rapier packaged-artifact static acceptance complete (2026-08-24):**
> `.\gradlew.bat --offline :forge:verifyRapierPackagedArtifact` passed without
> launching Minecraft, loading natives, compiling Rust, or regenerating the
> Forge runtime graph. Rapier is now packaged as one distinct nested production
> JarJar library,
> `dev.ryanhcode.sable-rapier:sable-rapier-common-1.20.1:2.0.0`, at
> `META-INF/jarjar/sable-rapier-common-1.20.1-2.0.0.jar`. The nested library
> contains `16` Rapier classes, the Rapier
> `PhysicsPipelineProvider` service descriptor, native README/license resources,
> and the opaque native archive exactly once. Rapier classes are absent from the
> outer Sable jar, Companion and Rapier have no class/package split, and
> Create/Flywheel/Ponder/Registrate/Veil remain external.
>
> Because Rapier bytecode references Minecraft members, M8.3 derives the nested
> production Rapier jar from the accepted named/userdev Rapier staging jar using
> `RapierProductionJarMapper` and the existing `createMcpToSrg/output.tsrg`
> mapping. The verifier proves `46` production/SRG Minecraft member references,
> `0` named Minecraft member references remaining, and unchanged production
> outer-Sable reobf semantics. The native archive remains
> `SHA-256 427CAA80B6B7D365703C3196B20AA29097EBF7FE5A61E20ED8C57B2A71BA0401`.
> Rapier requires `net.jpountz.lz4.LZ4FrameInputStream` at runtime, so
> `at.yawk.lz4:lz4-java:1.11.0` is deliberately nested once at
> `META-INF/jarjar/lz4-java-1.11.0.jar`
> (`SHA-256 535C5578CAB5DCD0A438E202DF80091632B873C0370C25D9B1C1AD1D73577207`).
> `org.apache.maven:maven-artifact:3.8.5` remains compile-only for this boundary:
> Rapier runtime bytecode has no Maven Artifact references and it is not bundled.
>
> The effective static provider set is now Rapier priority `1000` plus Static
> fallback priority `900`; current max-priority ServiceLoader semantics select
> `RapierPhysicsPipelineProvider` while retaining `StaticPhysicsPipelineProvider`.
> The first M8.3 attempt exposed the known Windows/ForgeGradle
> `downloadMcpConfig/output.zip` lock through an unnecessary MixinGradle hook on
> an intermediate `Jar` task. The final verifier uses a plain deterministic ZIP
> task over already-compiled Rapier outputs, so it remains cheap and isolated.

## Canonical Workflow

`sableForgeBackport=true` is the default in `gradle.properties`. With `JAVA_HOME` pointing to JDK 17, the canonical compiler command is:

```powershell
.\gradlew.bat :forge:compileJava
```

No `--configure-on-demand` flag is required. Set `sableForgeBackport=false` only when intentionally inspecting the untouched Java 21 upstream project graph. `gradlew.bat` starts Gradle from the Windows short-path form of the current workspace when available; this prevents ForgeGradle worker arguments from corrupting the Cyrillic parent directory while leaving Gradle's logical project paths unchanged. `org.gradle.problems.report=false` avoids the corresponding Gradle 8.14 HTML problem-report path bug.

Gradle remains pinned to `8.14.3`. MixinGradle resolves annotation-processor inputs before ForgeGradle 6 adds its repository content filters, and Gradle 8.14 freezes those descriptors first. The supported ForgeGradle switch `systemProp.net.minecraftforge.gradle.filter.repos=false` avoids that late mutation. `verifyTargetModpackDependencies` enforces the relevant safety property directly by rejecting raw local artifacts and requiring mapped versions outside the staging repository.

## Forge-Only Configuration

When backport mode is enabled, `settings.gradle` includes only `:forge` and `:sable_companion_1_20`. The Forge module reads selected common Java/resources directly from `common/src/main`; the Companion module is a Java library. Fabric Loom, NeoForge ModDev, `sable_rapier`, and the upstream Java 21 modules are not configured.

Validated under JDK 17:

```powershell
.\gradlew.bat projects
.\gradlew.bat :forge:tasks
```

`projects` reports root `sable`, `:forge`, and `:sable_companion_1_20`. `:forge:tasks` succeeds without configuration-on-demand and exposes the preparation, verification, inventory, and refmap tasks.

The final verification commands used `--offline` after the required artifacts were cached because this managed runner blocks socket access from Java. That flag is an execution-environment workaround, not a project-selection or build requirement; the canonical user command above remains unchanged.

## Target Modpack Dependencies

`:forge:prepareTargetModpackDependencies` reads
`../target_modpack/mods/create-1.20.1-6.0.8.jar`, validates its Minecraft and
Create manifest/mod metadata plus exact SHA-256, parses
`META-INF/jarjar/metadata.json`, requires the complete four-entry inventory,
and validates every nested coordinate, version, range, path, obfuscation flag,
and hash. The four mapped modules are recreated under the ignored directory
`forge/build/targetModpackRepository`; no target-modpack jar is copied into a
tracked source directory.

| Module | Exact version / JarJar range | Packaging path | SHA-256 |
|---|---|---|---|
| `local.target:create-1.20.1` | `6.0.8` | outer `create-1.20.1-6.0.8.jar` | `6FBB910C367DBCE8E4FC7E5BF64B6EDD4DE980906ED00AF8E47E4AF843C0D9B0` |
| `dev.engine-room.flywheel:flywheel-forge-1.20.1` | `1.0.5`; `[1.0,2.0)` | `META-INF/jarjar/flywheel-forge-1.20.1-1.0.5.jar` | `316CA250F19244956B5F0CD75329309EA65A77B4B8DA854389B6A9222E7F427C` |
| `com.tterrag.registrate:Registrate` | `MC1.20-1.3.3`; `[MC1.20-1.3.3,)` | `META-INF/jarjar/Registrate-MC1.20-1.3.3.jar` | `226862D4638B77273F4627FBAC871AA0B3AF584DDE377F4CE2CB0C7CC228CF00` |
| `net.createmod.ponder:Ponder-Forge-1.20.1` | `1.0.91`; `[1.0.91,)` | `META-INF/jarjar/Ponder-Forge-1.20.1-1.0.91.jar` | `86E6B64372ABA6D9C56F2C35725EA26D8FEBF2C75EED9950566E7F2849443B34` |
| `io.github.llamalad7:mixinextras-forge` | `0.4.1`; `[0.4.1,)` | `META-INF/jarjar/mixinextras-forge-0.4.1.jar` | `9D48CB0A40299D283248FDAD8B02C6D175C45B27F9BEC48EF63D7EE8A4EE3066` |
| MixinExtras internal payload | `0.4.1` | `META-INF/jars/MixinExtras-0.4.1.jar` inside the preceding jar | `D13C480D4E84128E76E8F509346F8137CEAA195091B3CFEAF4EBBA584CA68374` |

All four Create JarJar entries declare `isObfuscated: false`. MixinExtras
`0.4.1` and its internal common payload are validated as part of Create's
runtime packaging, but are not exposed as a fifth mapped local module because
Sable already owns MixinExtras Forge `0.5.3`. The staging directory is a
`flatDir` repository, but dependencies are declared with module coordinates
through `compileOnly fg.deobf(...)`; no `files(...)` dependency remains.
`:forge:verifyTargetModpackDependencies` resolves `compileClasspath` and
confirmed:

- `local.target:create-1.20.1:6.0.8_mapped_official_1.20.1`
- `local.target:flywheel-forge-1.20.1:1.0.5_mapped_official_1.20.1`
- `local.target:Registrate:MC1.20-1.3.3_mapped_official_1.20.1`
- `local.target:Ponder-Forge-1.20.1:1.0.91_mapped_official_1.20.1`

All four resolve from ForgeGradle's `deobf_dependencies` cache, not from the
raw staging directory. Create and Flywheel remain optional and are constrained
to `[6.0.8]` and `[1.0.5]` in Sable's `mods.toml`. Ponder has no independent
Sable dependency block: Create owns it as a mandatory dependency, while
Sable's only Ponder linkage is isolated behind the optional Create-loaded
boundary. The replacement target contains neither Veil nor Companion. Veil
`1.0.0.296` remains Sable's required external runtime dependency; Companion
packaging remains deferred.

## Mixin Annotation Processing

The Forge module uses the official ForgeGradle workflow with `org.spongepowered:mixingradle:0.7-SNAPSHOT`, Mixin `0.8.5`, and MixinExtras common/AP `0.5.3`. `sourceSets.main` is attached to `sable.refmap.json`. Target validation remains enabled, and no global `require = 0` workaround was added.

The compiler now reports:

- Mixin annotation processor active.
- FG3 `searge` obfuscation service active.
- mappings loaded from `forge/build/createMcpToSrg/output.tsrg`.
- refmap written and published to `forge/build/classes/java/main/sable.refmap.json` even when Java compilation stops at the intentional source frontier.

The current M6 refmap is 98,398 bytes and contains mappings for 111 selected Mixin classes. The historical final M5 refmap contained 112; M6 narrowly deferred `MouseHandlerMixin` with the already-deferred custom camera enum feature. Confirmed former false-negative canaries still map correctly:

| Development target | Confirmed SRG mapping |
|---|---|
| `canPlace` | `m_7059_` |
| `getHorizontalDirection` | `m_8125_` |
| `tick` | `m_8119_` for the applicable entity targets |
| `calculateViewVector` | `m_20171_` |
| `getMaxZoom` | `m_90566_` |
| `renderLevel` | `m_109599_` / `m_109089_` for LevelRenderer/GameRenderer |

These were not trustworthy incompatibilities before AP/FG mappings were connected. Remaining AP errors now point to genuine 1.21-to-1.20 target/type/descriptor differences.

## Mixin Selection

The upstream configs are unchanged. Forge `mods.toml` references the generated `sable-common-forge.mixins.json` plus `sable-forge.mixins.json`.

- Curated common config: 143 entries (`103` common and `40` client entries).
- Forge-specific config: intentionally empty except normal metadata/refmap.
- Missing upstream Forge `SableMixinPlugin`: not referenced; a future Forge loader implementation remains unresolved.
- Physically deferred/excluded common sources: 120 classes/pattern matches. Three upstream classes (`SableConfig`, `SableClientConfig`, and `SableAttributes`) are separately replaced by Forge-specific implementations.
- Optional/debug compiler and refmap matches after exclusion: zero for ComputerCraft, Exposure, Vista, Iris, Sodium, Embeddium, Oculus, ImGui, Jade, Jade Addons, `game_test`, `debug_render`, and `loaded_chunk_debug`.
- NeoForge sources remain outside the Forge source set.

`:forge:generateMixinBackportMatrix` parses both upstream configs and mechanically extracts annotation targets, easy method targets, source config, package, dependency, category, and initial action. It deterministically generates `MIXIN_BACKPORT_MATRIX.md` and the curated common config.

| Category | Count |
|---|---:|
| `CORE_MINECRAFT` | 124 |
| `CORE_CLIENT` | 69 |
| `CREATE_CORE` | 96 |
| `CREATE_CLIENT` | 49 |
| `OPTIONAL_COMPAT` | 18 |
| `LOADER_SPECIFIC` | 7 |
| `DEBUG_TEST_UI` | 10 |
| `UNKNOWN` | 0 |

Actions are `150 INVESTIGATE`, `205 DEFER`, and `18 EXCLUDE_FROM_FORGE_TARGET`. Thus 223 entries are deferred/excluded by action and 230 entries are absent from the curated common runtime config. The category totals remain unchanged because M6 changes action/selection, not the mechanical upstream classification.

## Compiler Frontier

M5a was gated before platform work. After the Forge-specific AT was correctly translated from official names to ForgeGradle 6 SRG names, the compiler moved from `39 errors / 2 warnings`, to `1 error / 2 warnings`, then to `0 errors / 2 warnings`. Its required second invocation reported `:forge:compileJava UP-TO-DATE`. M5b then compiled the Forge bootstrap and all eight providers at `0 errors / 3 deprecation warnings`.

The M5 refmap was `99,596` bytes with 112 selected Mixin classes. After the M6 camera deferral, the current refmap is `98,398` bytes with 111 mapped classes. The three Forge reach calls use `remap = false` because they are Forge-added methods with stable runtime names; their exact call sites were verified in mapped `ServerGamePacketListenerImpl` bytecode rather than globally suppressing target validation.

There is no remaining compile-time error frontier in the selected Forge core graph. M6 moved the trustworthy frontier through the first runtime and persistence boundary:

1. **Runtime smoke validation:** main menu, empty-world ticking, ServiceLoader discovery, config/event timing, TCP/UDP lifecycle, plot persistence, and one stationary named single-block sublevel now pass. See `M6_RUNTIME_SMOKE.md`.
2. **Create 6.0.8:** only Ponder wrapped-level detection is implemented. Create contraption/logistics integrations remain deferred and absent from the Forge Mixin config.
3. **Flywheel 1.0.5:** visual registration remains deliberately a no-op; Flywheel render integration is still deferred and must be re-audited against its new `dev.engine_room.flywheel` API.
4. **Advanced rendering/gameplay:** chunked rendering, shader/water/Iris/Sodium bridges, Leashable/pathfinding, projectile dispenser, vibration, toast/settings, and other listed clusters remain intact but excluded.
5. **Packaging:** Companion is now included in the final Forge all-jar through
   Forge JarJar as `dev.ryanhcode.sable-companion:sable_companion_1_20:1.6.0`
   with range `[1.6.0,)`. The final artifact verifier inspects JarJar metadata
   and the nested Companion jar, confirms Java 17 classes and ServiceLoader
   resources, rejects Companion mod metadata, rejects duplicate Companion
   classes outside/inside JarJar, and confirms Create/Flywheel/Ponder/Veil are
   not bundled. The first standalone runtime attempt failed before title due to
   an incorrect launcher main; the static run-configuration fix is validated,
   but the packaged-artifact runtime smoke remains unproven.

M6 completed the first `runClient` smoke milestone, and the standalone Companion
JarJar packaging step is statically complete. The remaining immediate frontier
is one successful packaged-artifact runtime smoke using the repaired
BootstrapLauncher standalone run configuration, followed by deferred-feature
work.

## Sable Companion Assessment

Mechanical inventory found 131 common source files containing 222 Companion imports. Required surface types are:

- `SableCompanion`, `SubLevelAccess`, and `ClientSubLevelAccess`.
- `Pose3d`/`Pose3dc` and `BoundingBox3d`/`BoundingBox3dc`/`BoundingBox3i`/`BoundingBox3ic`.
- `JOMLConversion`.

`ActiveSableCompanion` implements method groups for containment, projection, sublevel-inclusive lookup, distance, velocity/air-relative velocity, tracking/vehicle lookup, client-level access, and plot-grid checks.

M3 provides that API as `:sable_companion_1_20`, a `java-library` using ForgeGradle only for official Minecraft 1.20.1 mappings. Its baseline is the 12-source `sable-companion-common-1.21.1:1.6.0` source artifact (SHA-256 `74236A40A00AF0B2CF61B34E071468E3A547528154047E3548EAA0745B808C95`) plus the upstream MIT license and default `ServiceLoader` descriptor. The only required Java 17 edits were four `List.getFirst()` calls changed to indexed access. The jar has class major 61, contains the default provider, and contains no `mods.toml`, NeoForge metadata, or mandatory mod ID.

Conclusion: Companion remains a public compile API and bundled/JiJ runtime
common library, not a Forge mod or stub. `:forge` consumes it as a project API
dependency for development and packages it into the final Forge all-jar with
Forge JarJar for distribution. No Forge-specific Companion implementation is
indicated. See the [official Sable Companion repository](https://github.com/ryanhcode/sable-companion).

## Veil Assessment

Mechanical inventory found 51 common source files containing 82 Veil imports:

| Classification | Imports |
|---|---:|
| `CLIENT_RENDERING` | 42 |
| `NETWORKING` | 22 |
| `DEBUG_UI` | 6 |
| `REGISTRY` | 4 |
| `GENERIC_UTILITY` / platform | 1 |
| `OTHER` | 7 |

M3 uses official `foundry.veil:Veil-forge-1.20.1:1.0.0.296` through
`compileOnly fg.deobf(...)`, supplies the filtered mapped development runtime,
and declares Veil in Forge `mods.toml`. `verifyVeilDependency` compares the raw
and compile artifacts without depending on a cache filename:

- Raw slim SHA-256 observed by the current gate: `296C693C659A81B9BAA0C778D29A5AB89C56BBB46B5245A3BC2213BD0485F492`.
- ForgeGradle-mapped slim SHA-256 observed by the current gate: `C3C4EE5C2277D6006B2DE4BEC4A50C0CE422985DA1177434B1C3AE33030714B0`.
- Canonical paths and hashes differ, the raw jar is absent from `compileClasspath`, raw `VeilRenderSystem.class` contains `m_91087_`, and the mapped class contains `getInstance`.

Registry/platform and basic client renderer APIs are reused directly. Render profiling uses vanilla `ProfilerFiller`; Sodium detection uses `Veil.platform().isSodiumLoaded()`. M4 replaces the modern Veil packet APIs, which have no practical Veil 1.20 equivalent, with the Sable-owned Forge transport documented in `NETWORK_BACKPORT_MATRIX.md`. Advanced shader/framebuffer/editor integrations remain deferred. See `VEIL_1_20_API_MATRIX.md` for the occurrence-counted 82-import decision matrix.

## M0-M2 Acceptance Report (Historical)

1. **Files changed:** `settings.gradle`, `gradle.properties`, `buildSrc/build.gradle`, `forge/build.gradle`, `forge/src/main/resources/META-INF/mods.toml`, `forge/src/main/resources/sable-forge.mixins.json`, new `forge/src/main/resources/sable-common-forge.mixins.json`, new `gradle/mixin-backport-inventory.gradle`, generated `MIXIN_BACKPORT_MATRIX.md`, and this status file.
2. **Canonical command:** JDK 17 plus `.\gradlew.bat :forge:compileJava`.
3. **Forge isolation:** confirmed; only `:forge` is included and no Loom/NeoForge Java 21 tooling configures.
4. **Dependency staging:** hash-validated Create/JarJar extraction to ignored `forge/build/targetModpackRepository`, exposed through module-style `flatDir` coordinates and `fg.deobf`.
5. **Exact staged versions:** Create `0.5.1.j`, Flywheel `0.6.11-13`, Registrate `MC1.20-1.3.3`.
6. **Deobfuscation:** confirmed for all three by `_mapped_official_1.20.1` resolved artifacts in ForgeGradle's deobf cache.
7. **Mixin/refmap:** MixinGradle/AP is mapped and `forge/build/classes/java/main/sable.refmap.json` exists.
8. **Mixin categories:** 124 core Minecraft, 69 core client, 96 Create core, 49 Create client, 18 optional, 7 loader-specific, 10 debug/test, 0 unknown.
9. **Excluded/deferred:** 173 action entries; 180 absent from the curated config; 22 common source classes physically excluded.
10. **Recovered Mixin mappings:** `canPlace`, `getHorizontalDirection`, `tick`, `calculateViewVector`, `getMaxZoom`, and `renderLevel` now produce SRG refmap entries.
11. **Remaining compiler roots:** Companion, Veil, Minecraft 1.21 type/package changes, genuine Mixin descriptor/control-flow changes, and later Java 21/Create 6/Flywheel 1.0 incompatibilities.
12. **Companion:** common public compile + bundled/JiJ runtime API; a 1.20.1/Java 17 common artifact is needed later, with no evidence for a separate Forge artifact.
13. **Veil:** networking/registry likely core runtime-facing; advanced rendering, shader bridges, and debug UI are preferred deferrals.
14. **Then-recommended milestone:** M3, now completed below.

## M3 Acceptance Report

1. **Companion module:** `:sable_companion_1_20` is a real Java 17/Minecraft 1.20.1 common library and `:forge` consumes it through `api project(':sable_companion_1_20')`. `compileJava`, `jar`, and `verifySableCompanionBackport` pass; runtime JarJar remains deferred.
2. **Veil boundary:** official Veil `1.0.0.296` is mapped by ForgeGradle and verified by path, hash, raw-jar absence, and bytecode canaries. The compile no longer reports missing registry/platform/basic renderer Veil types. The 82-import matrix totals are 30 `USE_VEIL_1_20`, 4 `SMALL_ADAPTER`, 23 `REPLACE_WITH_FORGE`, and 25 `DEFER`.
3. **Source selection:** `generateMixinBackportMatrix` confirms 373 upstream Mixins, 178 curated Forge Mixins, actions of 185 `INVESTIGATE`, 170 `DEFER`, and 18 `EXCLUDE_FROM_FORGE_TARGET`, plus 76 physically deferred/excluded common files. The generated refmap has zero entries matching optional integrations, debug/game-test packages, shader/directional-lighting, water occlusion, fancy rendering, Iris/Sodium renderer bridges, `GameRendererAccessor`, or `SuspendedParticleMixin`.
4. **Exact M3 exclusion groups:** `compatibility/SableIrisCompat.java`, `debug/**`, advanced Mixin packages/classes under `config/GameRendererAccessor`, `dynamic_directional_shading`, `particle/SuspendedParticleMixin`, `sky_light_shadow`, `sublevel_render/fancy`, modern vanilla layered rendering, and `water_occlusion`; corresponding mixinterfaces; two gizmo packets; `render/dynamic_shade`, `render/region`, `render/sky_light_shadow`, and `render/water_occlusion`; fancy/reach-around dispatchers and texture cache; fancy/staging render implementations; storage inspector; and sublevel water-occlusion implementations. The authoritative path patterns and exact count assertion live in `forge/build.gradle` and `gradle/mixin-backport-inventory.gradle`.
5. **Selected bridge callers:** client bootstrap, block-change events, command/TCP registration, Mixin plugin renderer detection, vanilla renderer dispatch, and chunk-render data no longer call intentionally deferred shader, gizmo, water, Iris, or Sodium renderer implementations. `SODIUM_REACHAROUND` remains visibly unsupported. The selected plot Mixin no longer exposes the deferred loaded-chunk debug accessor.
6. **Forge config replacements:** Forge-specific `SableConfig` and minimal `SableClientConfig` preserve selected getter call sites through `ForgeConfigSpec`-backed wrappers. Runtime config registration is intentionally deferred.
7. **Java 17 rewrites:** selected list/set endpoint calls were converted to indexed or iterator operations in assembly commands/helper, selectors, rope points, lift-provider traversal, heat-map queues, interpolation buffers, and two selected Mixins. `SequencedSet` became insertion-ordered `LinkedHashSet`; `Math.clamp` became `Mth.clamp`. Valid deque, Netty pipeline, Mojang/fastutil pair, and fastutil linked-map calls remain unchanged. Deferred renderer/debug Java 21 calls remain untouched.
8. **Build files and documentation:** `settings.gradle`, `gradle.properties`, `gradlew.bat`, `forge/build.gradle`, Forge `mods.toml`, curated Mixin config/generator, selected common bridge/Java 17 sources, new Forge config replacements, new Companion module, `MIXIN_BACKPORT_MATRIX.md`, `VEIL_1_20_API_MATRIX.md`, and this status file.
9. **Compiler result:** `:forge:compileJava` intentionally fails at the next trustworthy 100-error frontier with 46 warnings while still publishing `forge/build/classes/java/main/sable.refmap.json`. Companion and supported Veil symbols are absent from the error set; only missing Veil networking remains.
10. **Recommended M4:** replace only the missing `VeilPacketManager`/`PacketContext` surface and the coupled Minecraft 1.20.1 packet codec/payload types with a Forge 47 transport. Preserve packet behavior and ordering; leave renderer Mixins, Create 6, Flywheel 1.0, Simulated, and Aeronautics untouched.

## M4 Acceptance Report

1. **Common abstraction:** added `SablePacketCodec`, direction/context/definition/registration/sink/transport contracts, a pure 0-13 packet catalog, and marker-only `SableTCPPacket`. DFU payloads use `FriendlyByteBuf.readWithCodec`/`writeWithCodec` and `NbtOps`; no fake 1.21 or Veil networking API was introduced.
2. **Forge transport:** `ForgeSablePacketTransport` is discovered through `META-INF/services`, owns `sable:main` protocol `1`, registers direction-bound `SimpleChannel` messages, uses `NetworkEvent.Context.enqueueWork`, rejects direction mismatches, requires a server sender, creates client state through a Dist-safe helper, and centralizes the typed vanilla-packet cast.
3. **Packet behavior:** handler bodies were moved without semantic changes into side-specific handler classes. All send sites use the transport facade. Full-sync order remains Start, optional RecentlySplit, vanilla chunks/lights, Finalize; snapshot fallback remains SnapshotInfo then Snapshot; synchronization sinks preserve iteration order.
4. **UDP:** ordinals 0-5 and flows are explicit. Encoder and decoder use `FriendlyByteBuf` and reject invalid IDs, wrong direction, malformed payloads, and trailing bytes. `ProtocolSwapHandler`, `BandwidthDebugMonitor`, and `MonitorFrameDecoder` were removed; the 1.20.1 no-argument frame decoder is used.
5. **Physics DFU compatibility:** the absent 1.21 `Codec.dispatchedMap` helper was backported with an equivalent dynamic-map codec that still delegates each property value to `PhysicsBlockPropertyTypes`. The public physics definition and floating-material DFU codecs remain the packet source of truth.
6. **Independent tests:** `:forge:networkTest` compiles the production protocol slice independently and passes 12 tests covering all 14 TCP and 6 UDP payloads, golden order, unread-byte checks, ID/flow uniqueness, gizmo absence, Forge scheduling/context behavior, and Netty malformed/direction rejection.
7. **Dependency checks:** `verifyTargetModpackDependencies` and `verifyVeilDependency` pass. Create, Flywheel, Registrate, and Veil resolve to ForgeGradle-mapped artifacts; Veil verification still uses path/hash/class-content evidence rather than accepting a cache filename alone.
8. **Compiler result:** two consecutive canonical compiles reach the stable `84 errors, 46 warnings` Minecraft/Mixin/renderer frontier. Selected networking sources contain none of the removed Veil or Minecraft 1.21 packet symbols. `forge/build/classes/java/main/sable.refmap.json` exists at 120,048 bytes with 134 mapped Mixin classes.
9. **Deferred lifecycle:** `DisconnectionDetails` and `CommonListenerCookie` remain for descriptor/control-flow porting. M4 validates the UDP protocol and transport but does not claim runtime UDP startup or disconnect cleanup. No `runClient` attempt was made.
10. **Recommended M5:** port the non-render Minecraft 1.20.1 type/package differences and validate core Mixin descriptors, including the two UDP lifecycle Mixins, while keeping Create/Flywheel and renderer implementation work out of scope. Re-run `networkTest` as a regression gate and use the resulting compiler frontier to split later renderer and Create milestones.

## M5 Acceptance Report

1. **M5a gate:** selected Minecraft 1.20.1 source and Mixin ports were completed before adding any platform code. Chunk futures use `SableChunkFutures` around 1.20's `Either`; moved/renamed block-entity and chunk types are mapped directly; wind-charge-only behavior was removed without changing other projectile behavior; respawn, collision, reach, disconnect, and player-login hooks target inspected 1.20.1 descriptors and control flow.
2. **Tick semantics:** upstream `ServerLevelMixin` skipped Sable plot ticks when 1.21's level tick manager was frozen; server tracking and client interpolation used its tick interval. Mapped 1.20.1 bytecode shows `IntegratedServer.tickServer` does not call `MinecraftServer.tickServer` while paused, and `MinecraftServer.tickChildren` is the path that invokes `ServerLevel.tick`. Therefore ticking the plot exactly once per actual `ServerLevel.tick` invocation preserves the upstream freeze invariant without emulating `TickRateManager`. Tracking and interpolation use the fixed vanilla 20 TPS / 50 ms interval.
3. **M5 deferrals:** files remain intact but the selected graph excludes chunked sublevel rendering and block-entity/chunk renderer helpers; Leashable/pathfinding/tamed teleport/entity-shadow clusters; settings/toasts/new-camera UI; projectile-dispenser, vibration, recoil, portal/stand-up/sleeping behavior; and their curated Mixin entries. Multi-block render-data creation throws an explicit unsupported exception rather than returning placeholder data. Basic single-block rendering remains compiled.
4. **M5a compiler snapshots:** M4 baseline was `84 errors / 46 warnings`. After direct source ports and the corrected SRG AT, checkpoints were `39/2`, `1/2`, then `0/2`; the required repeat was `UP-TO-DATE`. The temporary 100-error snapshot was diagnosed as an invalid official-name AT input to ForgeGradle and is not a source frontier.
5. **Forge bootstrap:** `dev.ryanhcode.sable.forge.SableForge` is the Forge 47 `@Mod` entrypoint. It initializes common/client Sable state, packet registration, commands, reload listeners, attributes, common/client config, datapack synchronization, logout cleanup, client reloads, and crash-report metadata using Forge event buses and lifecycle events.
6. **Platform providers:** ServiceLoader descriptors and Forge implementations exist for all eight common interfaces: assembly snapshot capture, chunk events, event subscription/publication, loader version lookup, generic platform hooks, plot persistence, and basic sublevel rendering. Plot attachment persistence uses Forge `LevelChunk.writeCapsToNBT/readCapsFromNBT`. Auxiliary light is a documented no-op because it is a NeoForge attachment used by deferred advanced visuals; Flywheel visual registration is likewise deferred while vanilla single-block rendering remains available.
7. **Create boundary:** `verifyForgeAccessTransformer` inspects the mapped Create `0.5.1.j` artifact and confirms `com/simibubi/create/foundation/utility/worldWrappers/WrappedServerWorld.class`. `SablePlatform.isWrappedLevel` checks that Create is loaded, then isolates the `instanceof` in a nested optional helper. No broader Create adapter was introduced.
8. **Access transformers:** the upstream common AT is unchanged. The curated Forge AT contains 35 entries using exact SRG names required by ForgeGradle 6. `ACCESS_TRANSFORMER_BACKPORT_MATRIX.md` records every upstream target, dependent source, purpose, exact 1.20 mapping or deferral proof, and final action. `verifyForgeAccessTransformer` parses the transformed class files and rejects missing classes/members/descriptors, non-public retained entries, retained finals, missing provenance rows, and a missing Create wrapped-world canary.
9. **Static packaging:** `verifyForgePackaging` confirms the `@Mod` class, nine ServiceLoader descriptors (eight platform plus networking), provider classes, Forge `mods.toml`, two curated Mixin configs, `sable.refmap.json`, curated `FMLAT`, and absence of representative deferred class prefixes. It separately confirms Companion classes, provider descriptor, MIT license, Java-library packaging, and absence of mod metadata; Companion is intentionally not bundled before the JarJar milestone.
10. **Final compile/package state:** M5b compiles with `0 errors / 3 deprecation warnings`, and the required repeat compile is `UP-TO-DATE`. The final refmap is 99,596 bytes with 112 mapped selected Mixins. Companion, network (12 tests), target dependency, Veil, Forge AT, static packaging, Checkstyle, Spotless, reobfuscation, and `:forge:build` validation all pass.
11. **Recommended M6:** perform a narrow Forge runtime smoke milestone: launch to main menu, create/load a disposable world, validate provider discovery/config/event ordering, verify a basic single-block sublevel and capability persistence, and capture runtime Mixin failures. Keep Create/Flywheel semantic integration and advanced rendering out of that milestone unless a runtime blocker proves one is unavoidable.

## M6 Acceptance Report

1. **Run harness:** ForgeGradle now has a Java 17 client run at `forge/run/m6-client` with verbose Mixin diagnostics and the inert-by-default `sable.runtimeSmoke` probe. `m6SableRun` combines Sable and Companion outputs only for the exploded development mod; Companion remains a common library rather than a standalone Forge mod.
2. **Classpath boundary:** `verifyRunClientClasspath` confirms the merged Active/fallback Companion descriptor, mapped and development-patched Veil plus required runtime libraries, MixinExtras, and Forge 1.20 resource paths. Create, Flywheel, Registrate, and Ponder are absent. `WrappedServerLevel` appears only in `SablePlatformImpl$PonderWrappedLevelCheck`, the outer provider has no Ponder bytecode reference, and Create/Flywheel remain genuinely optional in `mods.toml`.
3. **Deterministic Companion:** provider discovery saw Active and fallback implementations, verified effective priorities `1000` and `500`, proved Active is the unique maximum, and confirmed that `SableCompanion.INSTANCE` selected Active. Selection does not depend on classpath enumeration order.
4. **Runtime Mixin boundary:** the current generated config has 143 entries and the current 98,398-byte refmap maps 111 classes. Retained 1.20.1 descriptor/call-site corrections were limited to selected classes and checked against mapped targets. The scroll-only `MouseHandlerMixin` is deferred with the absent custom camera enum; no global target suppression or broad `require = 0` was added.
5. **Main menu and empty world:** Sable and Veil reach the title screen. The disposable Creative Superflat Void world `M6_Smoke_Empty` loads, ticks for more than 60 seconds, exposes `/sable`, starts local UDP, synchronizes datapack definitions, and produces no repeated chunk, tick, Mixin, or resource error.
6. **Providers/config/network:** all eight Forge platform providers and `ForgeSablePacketTransport` resolve. Protocol `1` registers TCP IDs `0..13`; packet handlers report the expected main thread. CLIENT and COMMON configs load once, reload listeners register, all three server levels load, and player/server lifecycle ordering passes.
7. **Resource compatibility:** processed resources map the 1.21 singular tag/structure directories to 1.20 plural paths, translate exact common-tag names to Forge tags, and make absent 1.21-only optional entries non-required. Unknown optional-mod physics selectors are skipped while missing Minecraft/Sable selectors remain errors.
8. **Reload/persistence:** Save and Quit closes UDP, logs out the player, saves all dimensions, unloads all levels, and stops the server. The dev client exited after returning to title, so empty-world reload used a fresh client JVM; persistence passed, while a same-JVM duplicate-listener assertion was not exercised.
9. **Single-block gate:** the verified commands `/sable spawn block minecraft:stone m6_smoke` and `/sable info @l` create exactly one stationary named sublevel. StartTracking precedes Finalize. A fresh reload restores the same name, position `-6.5 -60.0 6.5`, identity/state, mass `2.0`, and zero velocities, with occupancy, tracking, and region files present. This proves runtime, synchronization, retained single-block rendering path, and persistence boundaries only, not working physics.
10. **Validation and next scope:** Companion verification, all 12 network tests, target dependency/Veil/AT checks, `compileJava`, `build`, and the final `runClient` shutdown pass. Companion JarJar remains the narrow prerequisite for standalone artifact testing. Advanced/chunked rendering, custom camera modes, full physics/Sable Rapier, Create/Flywheel features, Simulated, and Aeronautics remain deferred. Full evidence is in `M6_RUNTIME_SMOKE.md`.

## Target Rebaseline Acceptance Report (2026-08-22)

The replacement `../target_modpack` contains 308 valid unique jars. Its Create
jar is the authority for the directly relevant nested stack:

| Component | Old target | Replacement target | Upstream Sable 2.0.0 | Packaging / action |
|---|---|---|---|---|
| Minecraft | `1.20.1` | `1.20.1` | `1.21.1` | unchanged Forge backport baseline |
| Loader | Forge `47.4.20` | no replacement profile metadata; retain proven Forge `47.4.20` | NeoForge `21.1.228` | unchanged project baseline |
| Java | `17` | target classes are at most major 61; retain `17` | `21` | unchanged Java 17 ports |
| Create | `0.5.1.j` | `6.0.8` | `6.0.10-280` | outer target jar; exact `[6.0.8]` optional Sable range |
| Flywheel | `0.6.11-13`, `com.jozufozu.flywheel` | `1.0.5`, `dev.engine-room.flywheel` | `1.0.6` | Create JarJar; exact `[1.0.5]` optional Sable range |
| Registrate | `MC1.20-1.3.3` | `MC1.20-1.3.3` | `MC1.21-1.3.0+67` | Create JarJar; mapped local module retained |
| Ponder | absent from old staging | `1.0.91` | `1.0.82` | Create JarJar; new fourth mapped local module |
| MixinExtras in Create | old packaging assumption | Forge `0.4.1` with internal common `0.4.1` | Sable owns Forge `0.5.3` | validate both nested hashes; do not map or downgrade |
| Veil | external `1.0.0.296` | absent from replacement pack | `4.1.4` | retain proven Forge 1.20 integration; add to eventual pack |
| Companion | development/common library | absent from replacement pack | common API `1.6.0` | packaging remains deferred |

The old Create-specific `WrappedServerWorld` import and canary were removed.
Create `6.0.8` does not contain that class. Ponder `1.0.91` supplies
`net.createmod.catnip.levelWrappers.WrappedServerLevel`, now referenced only by
the nested `SablePlatformImpl$PonderWrappedLevelCheck`. The outer provider first
checks that Create is loaded and has no Ponder bytecode reference, preserving
optional classloading isolation. The 35 vanilla Minecraft 1.20.1 AT mappings
did not change; their external dependency canary now proves the Ponder class
and proves the obsolete Create wrapper is absent.

The deferred source graph was not enabled or edited. Mechanical inspection
found 213 of 214 distinct imported Create/Catnip/Flywheel target types in the
new stack. The sole missing type remains
`com.simibubi.create.AllDataComponents` in `SchematicPlacePacketMixin`, a
Minecraft 1.21 data-component boundary. Existing networking, Java 17 ports,
Minecraft-core Mixins, Companion, and Veil need regression validation only.
The old Flywheel package/API assumptions and all deferred Create 0.5 method and
descriptor assumptions need re-audit before any individual feature is selected.

Static gates, all with JDK 17, passed:

| Command | Result |
|---|---|
| `.\gradlew.bat projects` | pass; only `:forge` and `:sable_companion_1_20` |
| `.\gradlew.bat :sable_companion_1_20:verifySableCompanionBackport` | pass; 12 sources, Java 17, common ServiceLoader library |
| `.\gradlew.bat :forge:prepareTargetModpackDependencies` | pass; complete four-entry JarJar inventory plus both MixinExtras hashes |
| `.\gradlew.bat :forge:verifyTargetModpackDependencies` | pass; four mapped local modules, no raw staged jar |
| `.\gradlew.bat :forge:verifyVeilDependency` | pass; external Veil `1.0.0.296` raw/mapped canaries |
| `.\gradlew.bat :forge:verifyForgeAccessTransformer` | pass; 35 entries and new Ponder boundary |
| `.\gradlew.bat :forge:verifyRunClientClasspath` | pass; exact filtered Create 6 runtime, no standalone Registrate/old stack, and optional Ponder bytecode isolated to the nested helper |
| `.\gradlew.bat :forge:networkTest` | pass; 12/12 tests across three suites |
| `.\gradlew.bat :forge:compileJava` | pass; 0 errors, 4 Forge deprecation warnings |
| `.\gradlew.bat :forge:build` | pass; reobfuscation, Checkstyle, Spotless, AT and packaging checks |

No `runClient` or other Minecraft launch was performed. The Windows network
test no longer puts the generated Companion jar on javac's classpath: the
isolated protocol source set compiles the 12 Java 17 Companion sources directly
and filters the project jar, avoiding its short-path file lock without changing
network code or protocol behavior.

## Create 6 Runtime Smoke Attempt (2026-08-23)

The target-runtime milestone used exactly one `:forge:runClient` process. It
failed before the title screen during Java module-layer construction because
Registrate was present twice: as the explicit standalone mapped runtime module
`Registrate.MC1._20` and as Create's JarJar module `Registrate`. Both exported
`com.tterrag.registrate`. Gate 1 therefore failed pre-title; Gates 2–5,
including same-JVM reload, were not run. No second Minecraft process was
launched and the disposable world was not opened.

The narrow build fix removes standalone Registrate from `runtimeOnly` while
retaining it as an exact staged/mapped compile module. The corrected runtime
uses mapped standalone Create `6.0.8`, Flywheel `1.0.5`, and Ponder `1.0.91`;
mapped Create supplies its exact Registrate `MC1.20-1.3.3` JarJar once. The
mapped nested Registrate SHA-256 is
`AB90001E9EC42922DA4E499CDAFF6D7F1F6E78432CE71555397931804E94E5FB`.
Patched mapped Veil `1.0.0.296` remains external.

After the fix, `verifyRunClientClasspath` proves that corrected shape, the
mapped nested Registrate canary/hash, old-stack absence, Ponder isolation, and
the still-disabled deferred Mixin graph. `:forge:build` passes reobfuscation,
Checkstyle, Spotless, AT, and packaging validation. Runtime remains unproven
until a separate one-launch retry. See `TARGET_RUNTIME_SMOKE.md` for the exact
matrix, command results, failure evidence, and gate table.

## Create 6 Static Runtime Preflight (2026-08-23)

No Minecraft client or server was launched. The comprehensive static pass
found and fixed one more instance of the Registrate failure class before
spending another launch: Create owns a nested MixinExtras Forge `0.4.1`
wrapper, while Sable supplies standalone MixinExtras Forge `0.5.3`. Both outer
wrappers use automatic module name `mixinextras` and expose the Forge bootstrap
package. Resolving their internal common payload upward to `0.5.3` does not by
itself remove the duplicate outer module.

The mapped development Create jar is now prepared with exactly that older
wrapper payload and JarJar metadata row removed. Create's mapped Registrate,
Flywheel, and Ponder entries remain intact. The filtered Create SHA-256 is
`13FCA92CD9C89611943A28ACA296E444AA38365F10F8EDEF427419038B65756E`.
Runtime keeps Sable's MixinExtras Forge `0.5.3`
(`89D60F6BF1F29664319ACFA80E777ABC03FDE674370AF52E94A9A2E452B98833`)
and its common `0.5.3` payload
(`D0020CAFF27B478E5CBACE1C0C1D74B755AF9F5D351B619BA1EC45C0DE8CF3C9`).
The source Create `0.4.1` wrapper/common hashes remain validated during target
staging; Sable was not downgraded.

`:forge:verifyRuntimeModuleBoundary` reconstructs the effective mod/game
library surface and now fails on the Registrate/MixinExtras class of defect.
It proved unique module/package ownership, although the first implementation
reported Registrate from its JarJar artifact identifier as `Registrate` rather
than deriving the mapped automatic-module name. Runtime later established that
the exact effective names are `create`, `flywheel`, `ponder`,
`Registrate.MC1._20`,
`mixinextras`, `MixinExtras`, and `veil`; package counts 286/66/50/7/1/49/150;
no split packages; no repeated provider implementations; unique runtime mod
IDs; no old/raw/mapped pairs; and no standalone Registrate. Create's nested
Flywheel/Ponder bytes exactly match the mapped source modules Forge selects,
and mapped Registrate retains SHA-256
`AB90001E9EC42922DA4E499CDAFF6D7F1F6E78432CE71555397931804E94E5FB`.

`:forge:verifyRetainedMixinCoexistence` mechanically compared the enabled Sable
Forge config with Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, and Veil.
The generated `RUNTIME_MIXIN_OVERLAP.md` records 164
`SAFE_INDEPENDENT` shared-target pairs, one `ORDER_SENSITIVE`, zero
`NEEDS_BYTECODE_CHECK`, and zero `LIKELY_RUNTIME_CONFLICT`. The sole ordered
pair is the Sable sublevel-collision and Create contraption-interaction
handlers at mapped `Entity.move` `TAIL`. Both are priority 1100,
non-cancellable, use the present 1.20.1 descriptor/return boundary, and have no
shared direct field writes. No concrete incompatibility was proven and no
retained Mixin was changed. Equal-priority semantic ordering remains a focused
runtime observation for the next launch.

`:forge:verifyBootstrapLifecycleBoundary` proved exact `mods.toml` ranges and
optionality, one Sable network channel, one common/client config registration,
one command/reload-listener owner, guarded smoke-probe installation,
server-instance UDP cleanup, connection-instance UDP cleanup, and explicit
first/second integrated-server lifecycle counters. Constant-pool scanning
still finds no Create/Catnip/Flywheel type in retained outer classes;
`WrappedServerLevel` remains confined to
`SablePlatformImpl$PonderWrappedLevelCheck` after the Create presence check.

Veil remains external artifact `1.0.0.296`; its embedded Forge mod version is
`1.0.0`. The next runtime harness now validates `ModList` against `1.0.0`
instead of incorrectly expecting the artifact coordinate.

Post-preflight static gates all pass under JDK 17: `projects`, Companion
verification, target preparation/verification, Veil verification, Forge AT,
`verifyRunClientClasspath`, the three new preflight gates, `networkTest` 12/12,
`compileJava` with zero errors, and `build` with reobfuscation, Checkstyle, and
Spotless. No deferred Create/Flywheel Mixins, rendering, Rapier, Simulated,
Aeronautics, or Companion JarJar work was enabled.

## Create 6 Runtime Evidence Attempt (2026-08-23 12:47)

Exactly one `:forge:runClient` process was launched, and no separate server or
second client was started. The client passed module-layer construction and
reached the stable title-screen harness condition. Gate 1 then failed only
because the strengthened evidence probe expected Registrate module
`Registrate`; the one loaded mapped JarJar class actually belongs to automatic
module `Registrate.MC1._20`:

```text
com.tterrag.registrate.AbstractRegistrate module=Registrate.MC1._20,
expected=Registrate
```

The resource-count assertion immediately before that comparison passed, so
there was exactly one `AbstractRegistrate.class` resource. There was no
`ResolutionException`, proving the prior duplicate Registrate failure is gone.
Forge JarJar found exactly two remaining nested dependencies: Create-owned
Registrate and Sable MixinExtras common `0.5.3`; Flywheel/Ponder were selected
from their explicit mapped source modules. MixinExtras initialized
`MixinExtrasServiceImpl(version=0.5.3)`, and no Create-owned Forge `0.4.1`
wrapper/module collision occurred.

Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod `1.0.0`
(artifact `1.0.0.296`), Forge, and Minecraft initialized. Filtered Create and
Flywheel each passed exact-one class-resource and module-name probes. Active
Companion selection, all eight platform providers, the client render provider,
Forge packet transport, protocol `1`, and packet IDs `0..13` passed. Common and
client config each loaded once, both runtime probes installed once, the client
reload listener registered once, and common setup ran once.

Because Gate 1 did not transition, the disposable world was not opened:
`level.dat` retained its 2026-08-21 timestamp. Gates 2–5, integrated-server
lifecycle counters, server command/reload-listener counters, UDP endpoints,
sublevel creation, persistence, and same-JVM reload were not exercised. Client
logout cleanup ran once during the harness crash exit. Both equal-priority
`Entity.move` Mixins applied without an injection error, but no in-world
movement occurred, so their semantic runtime observation remains pending.

The captured evidence is in `forge/run/m6-client/logs/latest.log` and
`forge/run/m6-client/crash-reports/crash-2026-08-23_12.48.32-client.txt`
(crash UUID `3a29ba1f-3efe-4de3-8de6-46f0f2c1d50b`). The next narrow change is
only a smoke-verifier correction: expect `Registrate.MC1._20`, derive the same
name in the static module report, and run MixinExtras detailed probes before
descriptive module-name canaries. Dependency selection and retained/deferred
feature boundaries must remain unchanged. No relaunch was performed in this
milestone.

## Create 6 Harness Static Correction (2026-08-23)

No Minecraft client, manual client, or Forge server was launched. The smoke
harness no longer treats the JarJar artifact identifier `Registrate` as the
effective module name. It derives the expected Registrate module from the mapped
nested runtime filename `Registrate-MC1.20-1.3.3.jar` using Forge/SecureJar
filename semantics, producing `Registrate.MC1._20`, while retaining the
exact-one `AbstractRegistrate.class` resource assertion.

`:forge:verifyRuntimeModuleBoundary` now derives the same name from the
extracted mapped nested runtime jar and reports:

```text
registrate:Registrate.MC1._20
Derived Registrate module Registrate.MC1._20 from Registrate-MC1.20-1.3.3.jar
```

Detailed MixinExtras Forge/common uniqueness and version evidence now runs
before the descriptive Registrate module-name canary, so a future harmless name
mismatch cannot hide MixinExtras evidence. The requested static suite passed
under JDK 17 with `--offline`: `verifyRuntimeModuleBoundary`,
`verifyRunClientClasspath`, `verifyRetainedMixinCoexistence`,
`verifyBootstrapLifecycleBoundary`, `networkTest`, `compileJava`, and `build`.
The repo is ready for one final single-JVM runtime smoke attempt with unchanged
dependency selection and unchanged deferred-feature boundaries.

## Create 6 Final Runtime Smoke Attempt (2026-08-23 13:14)

Exactly one `.\gradlew.bat --offline :forge:runClient` process was launched.
No second client, manual Minecraft launch, or Forge server was started.
Dependency selection and feature scope were unchanged.

Gate 1 passed: the title screen was reached and the runtime evidence confirmed
Sable, Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod `1.0.0`
(artifact `1.0.0.296`), Companion, all eight Sable platform providers, packet
transport, Create-owned Registrate module `Registrate.MC1._20`, and
MixinExtras Forge/common `0.5.3`. The filtered Create development artifact did
not reintroduce the removed MixinExtras Forge `0.4.1` wrapper.

Gate 2 passed: the disposable smoke world opened, the player joined, ticking /
chunk-loading probes passed, and `/sable` registered for integrated-server
cycle `1`.

Gate 3 failed after `/sable spawn block minecraft:stone create6_runtime_smoke`
and `/sable info @l` both returned `1`. The object existed and reported zero
velocities, but `/sable info` showed `Mass: 0.0`; the harness expected the
stationary stone sublevel mass `2.0`. Sable then removed
`ServerSubLevel[name=create6_runtime_smoke, ...]` for an extreme Y coordinate
range. The fatal exception was:

```text
java.lang.IllegalStateException: Expected stationary stone mass 2.0, found 0.0
```

Gates 4 and 5 were not run because Gate 3 crashed the one permitted JVM before
Save and Quit / same-JVM reopen. Crash cleanup did observe integrated-server
stop and client logout cleanup, but duplicate-registration and persistence
checks remain unproven. The equal-priority Sable/Create `Entity.move` TAIL
Mixins both applied without injection failure; no semantic conflict was
observed before the mass/bounds failure.

Evidence is in `forge/run/m6-client/logs/latest.log` and
`forge/run/m6-client/crash-reports/crash-2026-08-23_13.16.08-client.txt`.
The next blocker is a narrow static-first repair of retained single-block
sublevel spawn mass/bounds initialization around `/sable spawn block`,
`EmbeddedPlotLevelAccessor.setBlock`, plot/block association, and
`SubLevelPhysicsSystem.updateMassDataFromBlockChange`. Do not change dependency
selection, Mixin priority, networking, or deferred-feature scope for that
investigation.

## Gate 3 Single-Block Initialization Static Repair (2026-08-23)

No Minecraft client, manual client, or Forge server was launched. Dependency
selection, Mixin priorities, and deferred-feature scope were unchanged.

The proven defect was retained-core initialization ordering in
`/sable spawn block`: the command allocated an empty plot, placed one block via
`EmbeddedPlotLevelAccessor.setBlock`, and then recorded `lastPose` without
synchronously finalizing the new object's plot bounds or mass tracker. The
server sublevel had already entered the physics system with empty mass/bounds,
so the command depended on the live block-change callback completing before the
immediate `/sable info` / harness assertion. In the Create 6 smoke the object
existed and the origin block was `minecraft:stone`, but the tracker was still
mass `0.0`; the later tick transformed the empty bounds sentinel and removed
the object for an extreme Y range.

The Create/Ponder wrapped-level path was inspected and eliminated as the
proven cause: the command runs on the integrated `ServerLevel`, while
`SablePlatform.isWrappedLevel(...)` / `PonderWrappedLevelCheck` only guard
optional wrapped-level exclusions.

The fix adds a narrow post-placement finalization step for the single-block
spawn path. It resolves the actual plot chunk for the embedded origin, applies
the block change to the `PlotChunkHolder`, rebuilds plot bounds, performs
normal plot expansion, rejects empty bounds, rebuilds mass from actual
world/plot contents through the normal physics-property registry, updates
merged mass/global bounds, and rejects invalid mass data before the command
records `lastPose`. It does not special-case stone, hardcode mass `2.0`,
disable bounds validation, alter networking, or change any dependency/Mixin
scope.

M6 previously passed because the historical runtime path observed the expected
creation/update boundary and fresh-JVM reload restored mass `2.0`; it did not
prove that the command itself self-finalized mass/bounds before an immediate
same-JVM assertion. The Create 6 smoke exposed that ordering hole.

New static regression gate:
`:forge:verifySingleBlockSpawnInitialization`. It verifies the production
spawn-block path reads the old state, places the block, runs finalization before
`updateLastPose()`, proves block association / bounds / mass rebuild markers,
and rejects stone or mass hardcoding in the helper.

Validation with JDK 17 and `--offline` passed:

- `:forge:verifySingleBlockSpawnInitialization`
- `:forge:compileJava` — 0 errors, existing 4 Forge deprecation warnings
- `:forge:networkTest` — up-to-date/pass
- `:forge:verifyRuntimeModuleBoundary`
- `:forge:build` — reobfuscation, Checkstyle, Spotless, AT and packaging checks

The first managed compile attempt hit the known Windows short-path
`AccessDeniedException`; rerunning the same offline Gradle validation outside
that filesystem wrapper passed. No retained Mixin changed, so
`verifyRetainedMixinCoexistence` was not rerun. Remaining risk: the repair is
static only. The next runtime milestone should use one fresh `runClient` JVM to
retry Gate 3 and then continue to same-JVM reload and clean shutdown if Gate 3
passes.

## Create 6 Post Gate-3 Repair Runtime Smoke (2026-08-23 13:42)

Exactly one `.\gradlew.bat --offline :forge:runClient` process was launched.
No second client, manual Minecraft launch, or Forge server was started.
Dependency selection, Mixin priorities, and deferred-feature scope were
unchanged.

Gates 1 and 2 passed again: the title screen and disposable smoke world loaded
with Sable, Create `6.0.8`, Flywheel `1.0.5`, Ponder `1.0.91`, Veil mod
`1.0.0`, Companion, Create-owned Registrate `Registrate.MC1._20`, and
MixinExtras `0.5.3`.

Gate 3 passed after the static repair. The exact commands
`/sable spawn block minecraft:stone create6_runtime_smoke` and
`/sable info @l` both returned `1`. Runtime evidence reported:

- name: `create6_runtime_smoke`
- position: `-6.5 -107.73569043825906 6.5`
- orientation: `0.0 0.0 0.0 1.0`
- mass: `2.0`
- linear velocity: `0.0 0.0 0.0`
- angular velocity: `0.0 0.0 0.0`
- `ClientboundStartTrackingSubLevelPacket` before
  `ClientboundFinalizeSubLevelPacket`, followed by
  `ClientboundChangeBoundsSubLevelPacket`

No extreme-Y removal was logged before the first Save and Quit, and the updated
sublevel region `M6_Smoke_Empty/sublevels/r.-1.0.0.slvls` was written during
the first server stop. The current harness does not print numeric plot/global
bounds, so the exact bound coordinates are not captured in this run; the
runtime evidence proves non-empty/valid bounds through command completion,
ChangeBounds emission, Gate 3 PASS, no extreme removal, and save output.

Gate 4 failed before same-JVM reopen. After Save and Quit, integrated-server
stop cycle `1` was observed, but the existing lifecycle verifier expected one
client logout cleanup and observed two. The run logged a pre-world client
logout cleanup cycle `1` during world startup, then the expected Save-and-Quit
cleanup cycle `2`. The fatal assertion was:

```text
java.lang.IllegalStateException: SABLE_M6 first server stopped client logouts=2, expected=1
```

Gate 5 was not run. Evidence is in `forge/run/m6-client/logs/latest.log` and
`forge/run/m6-client/crash-reports/crash-2026-08-23_13.44.10-client.txt`
(crash UUID `3fe3a6d9-4e65-4a7c-84c0-9c7508eb356e`). The next blocker is a
static harness/lifecycle repair: distinguish baseline/pre-world client logout
cleanup from Save-and-Quit cleanup, or assert deltas from a baseline captured
after Gate 2. Same-JVM reload, duplicate-registration audit for the second
integrated server, and clean shutdown remain unproven until the next single-JVM
runtime attempt.

## Create 6 Same-JVM Runtime Smoke Acceptance (2026-08-23 13:56)

The lifecycle smoke harness was corrected without changing production
lifecycle behavior. It now captures lifecycle baselines at completed gate
boundaries and validates deltas for per-integrated-server events instead of
assuming process-global counters begin at zero. This distinguishes one-time
process/client-global registration from per-server start/stop, command/reload
listener, player login/logout, UDP, and client logout cleanup events.

Static validation passed with JDK 17 and `--offline` before runtime:

- `:forge:verifyBootstrapLifecycleBoundary`
- `:forge:compileJava` — 0 errors, existing 4 Forge deprecation warnings
- `:forge:build` — reobfuscation, Checkstyle, Spotless, AT and packaging checks

After static validation, exactly one
`.\gradlew.bat --offline :forge:runClient` process was launched. It completed
successfully; no second client, manual Minecraft launch, or Forge server was
started.

All Create 6 runtime smoke gates passed:

- Gate 1: title/main menu with Sable, Create `6.0.8`, Flywheel `1.0.5`,
  Ponder `1.0.91`, Veil mod `1.0.0`, Registrate `Registrate.MC1._20`, and
  MixinExtras `0.5.3`.
- Gate 2: disposable world opened, player joined, and first-world lifecycle
  baseline was captured after the legitimate pre-world client logout cleanup.
- Gate 3: `create6_runtime_smoke` existed as the single expected
  `minecraft:stone` object, mass `2.0`, zero velocities, ordered
  StartTracking -> Finalize, and no extreme-Y removal.
- Gate 4: first Save and Quit returned to title without exiting the client;
  the same JVM reopened the same world; the same named stone sublevel restored
  with mass `2.0`; second-server active delta duplicate-registration audit
  passed.
- Gate 5: second Save and Quit plus final client exit passed; integrated
  server, client networking, UDP, and second-server stopped delta audit were
  clean.

Relevant lifecycle baselines in `forge/run/m6-client/logs/latest.log`:

- first world active:
  `starting=1 started=1 stopping=0 stopped=0 commands=1 reloadListeners=1 playerLogins=1 playerLogouts=0 clientLogouts=1`
- first stopped:
  `starting=1 started=1 stopping=1 stopped=1 commands=1 reloadListeners=1 playerLogins=1 playerLogouts=1 clientLogouts=2`
- second world active:
  `starting=2 started=2 stopping=1 stopped=1 commands=2 reloadListeners=2 playerLogins=2 playerLogouts=1 clientLogouts=3`

`SABLE_TARGET_RUNTIME phase=complete status=PASS` confirms all five target
runtime gates passed in one client JVM. This proves only the retained Sable
core runtime against the Create 6 target stack. Deferred Create/Flywheel
Mixins, chunked/advanced rendering, Rapier/full physics, Simulated,
Aeronautics, and Companion JarJar remain outside this milestone.

## Known Target Modpack Issue

Create's pre-existing goggle overlay `IndexOutOfBoundsException` remains out of scope; this backport must not modify Create to address it.
