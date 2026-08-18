# Sable Forge 1.20.1 Backport Status

## Target And Baseline

- Minecraft: `1.20.1`
- Forge: `47.4.20`
- Java: `17`
- Create: `0.5.1.j`
- Flywheel: `0.6.11-13`
- Registrate: `MC1.20-1.3.3`
- Upstream Sable ref: `mc1.21.1-2.0.0-neoforge`
- Upstream commit: `b7226222caf4eace63a708bdcd73ef36c971137d`
- Backport branch: `backport/forge-1.20.1-sable-2.0.0`

The upstream `common`, `fabric`, `neoforge`, and `sable_rapier` modules remain available for reference. M0-M2 established trustworthy Forge build plumbing. M3 adds a real local Companion common library, consumes official Veil 1.20, rewrites selected Java 21 collection calls for Java 17, and narrows the Forge source graph without beginning Minecraft, Create, Flywheel, Simulated, or Aeronautics semantic porting.

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

`:forge:prepareTargetModpackDependencies` reads `../target_modpack/mods/create-1.20.1-0.5.1.j.jar`, validates its manifest and hash, parses `META-INF/jarjar/metadata.json`, validates the embedded module coordinates/versions, and extracts the exact nested jars. Outputs are recreated under the ignored directory `forge/build/targetModpackRepository`; no target-modpack jar is copied into a tracked source directory.

| Module discovered in Create | Version | SHA-256 |
|---|---|---|
| `local.target:create-1.20.1` | `0.5.1.j` | `A18763BE04F6A7921D2E3BC5E9150B0BB5BC89B5EF45A607E93939972021F932` |
| `com.jozufozu.flywheel:flywheel-forge-1.20.1` | `0.6.11-13` | `FD71EF28C2FC2694E661366A254CE1519791A39E7EC660A87A07212173AFF178` |
| `com.tterrag.registrate:Registrate` | `MC1.20-1.3.3` | `226862D4638B77273F4627FBAC871AA0B3AF584DDE377F4CE2CB0C7CC228CF00` |

The staging directory is a `flatDir` repository, but dependencies are declared with module coordinates through `compileOnly fg.deobf(...)`; no `files(...)` dependency remains. `:forge:verifyTargetModpackDependencies` resolves `compileClasspath` and confirmed:

- `local.target:create-1.20.1:0.5.1.j_mapped_official_1.20.1`
- `local.target:flywheel-forge-1.20.1:0.6.11-13_mapped_official_1.20.1`
- `local.target:Registrate:MC1.20-1.3.3_mapped_official_1.20.1`

All three resolve from `~/.gradle/caches/forge_gradle/deobf_dependencies`, not from the raw staging directory. The previous ForgeGradle warning about undeobfuscated file dependencies is gone. Create, Flywheel, and Registrate classes now resolve using official development mappings.

## Mixin Annotation Processing

The Forge module uses the official ForgeGradle workflow with `org.spongepowered:mixingradle:0.7-SNAPSHOT`, Mixin `0.8.5`, and MixinExtras common/AP `0.5.3`. `sourceSets.main` is attached to `sable.refmap.json`. Target validation remains enabled, and no global `require = 0` workaround was added.

The compiler now reports:

- Mixin annotation processor active.
- FG3 `searge` obfuscation service active.
- mappings loaded from `forge/build/createMcpToSrg/output.tsrg`.
- refmap written and published to `forge/build/classes/java/main/sable.refmap.json` even when Java compilation stops at the intentional source frontier.

The current partial M3 refmap is 120,048 bytes and contains mappings for 134 Mixin classes. Confirmed former false-negative canaries still map correctly:

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

- Curated common config: 178 entries (`121` common and `57` client entries).
- Forge-specific config: intentionally empty except normal metadata/refmap.
- Missing upstream Forge `SableMixinPlugin`: not referenced; a future Forge loader implementation remains unresolved.
- Physically deferred/excluded common sources: 76 classes (`22` M0-M2 optional/debug Mixins plus `54` M3 advanced visual/debug sources). Two upstream config classes are separately replaced by Forge-specific implementations.
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

Actions are `185 INVESTIGATE`, `170 DEFER`, and `18 EXCLUDE_FROM_FORGE_TARGET`. Thus 188 entries are deferred/excluded by action, 195 entries are absent from the curated common runtime config, and 76 common Java classes are physically outside this compiler target. The category totals remain unchanged because M3 changes action/selection, not the mechanical upstream classification.

## Compiler Frontier

Consecutive final M3 `:forge:compileJava` invocations reach javac and Mixin AP and intentionally stop at javac's 100-error cap with 46 warnings. Companion packages resolve, supported Veil 1.20 APIs resolve, the curated refmap is generated, and no deferred advanced visual/debug source appears in compiler or AP diagnostics. On the immediate repeat, dependency preparation, Companion compilation/jar, SRG extraction, and MCP-to-SRG generation are up to date. The Java task itself must rerun after failure because Gradle cannot publish a successful class snapshot.

Remaining failures are grouped as follows:

1. **Veil networking absent from Veil 1.20.** `VeilPacketManager` and `PacketContext` remain unresolved by design. Their 23 upstream imports are the narrow M4 transport boundary.
2. **Minecraft 1.21 packet/codec APIs.** `RegistryFriendlyByteBuf`, `StreamCodec`, `ByteBufCodecs`, common/custom payload packets, `CommonListenerCookie`, `DisconnectionDetails`, and `BandwidthDebugMonitor` require a coordinated 1.20.1 packet port, not mechanical renames.
3. **Minecraft 1.21 classes/packages and renderer structure.** Examples include `PathfindingContext`, `Leashable`, `DeltaTracker`, the moved `ChunkStatus`, `OptionsSubScreen`, `SystemToastId`, `TickRateManager`, wind-charge classes, and `SectionRenderDispatcher.RenderSection`.
4. **Genuine Mixin descriptors/control flow.** Confirmed failures include `canInteractWithEntity`, `ClientChunkCache#getChunk`, `Connection#disconnect`, two `@Overwrite` mappings, targetless 1.21 classes, and interface-injector behavior in `VibrationSystemTickerMixin`.
5. **Later Create 6/Flywheel 1.0 incompatibilities.** Exact Create 0.5.1.j and Flywheel 0.6.11-13 are mapped and available, but semantic compatibility work remains deliberately outside M3 and may appear beyond the current 100-error cap.

No runtime or `runClient` attempt has been made.

## Sable Companion Assessment

Mechanical inventory found 131 common source files containing 222 Companion imports. Required surface types are:

- `SableCompanion`, `SubLevelAccess`, and `ClientSubLevelAccess`.
- `Pose3d`/`Pose3dc` and `BoundingBox3d`/`BoundingBox3dc`/`BoundingBox3i`/`BoundingBox3ic`.
- `JOMLConversion`.

`ActiveSableCompanion` implements method groups for containment, projection, sublevel-inclusive lookup, distance, velocity/air-relative velocity, tracking/vehicle lookup, client-level access, and plot-grid checks.

M3 provides that API as `:sable_companion_1_20`, a `java-library` using ForgeGradle only for official Minecraft 1.20.1 mappings. Its baseline is the 12-source `sable-companion-common-1.21.1:1.6.0` source artifact (SHA-256 `74236A40A00AF0B2CF61B34E071468E3A547528154047E3548EAA0745B808C95`) plus the upstream MIT license and default `ServiceLoader` descriptor. The only required Java 17 edits were four `List.getFirst()` calls changed to indexed access. The jar has class major 61, contains the default provider, and contains no `mods.toml`, NeoForge metadata, or mandatory mod ID.

Conclusion: Companion remains a public compile API and bundled/JiJ runtime common library, not a Forge mod or stub. `:forge` consumes it as a project API dependency for development. Final JarJar/runtime packaging is deferred; no Forge-specific Companion implementation is indicated. See the [official Sable Companion repository](https://github.com/ryanhcode/sable-companion).

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

M3 uses official `foundry.veil:Veil-forge-1.20.1:1.0.0.296` through `implementation fg.deobf(...)` and declares Veil in Forge `mods.toml`. `verifyVeilDependency` compares the raw and compile artifacts without depending on a cache filename:

- Raw SHA-256: `605CE124B12841EC4E0603CD7E2B48CA84DE92772E8FD768A23A3F142A4EACBD`.
- Mapped SHA-256: `05C17DCE2C8466A93DB7FCB96930665D2AC1097642014224AA027E39564FFC27`.
- Canonical paths and hashes differ, the raw jar is absent from `compileClasspath`, raw `VeilRenderSystem.class` contains `m_91087_`, and the mapped class contains `getInstance`.

Registry/platform and basic client renderer APIs are reused directly. Render profiling uses vanilla `ProfilerFiller`; Sodium detection uses `Veil.platform().isSodiumLoaded()`. The modern Veil packet APIs have no practical 1.20 equivalent and remain visible for M4. Advanced shader/framebuffer/editor integrations are deferred. See `VEIL_1_20_API_MATRIX.md` for the occurrence-counted 82-import decision matrix.

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

## Known Target Modpack Issue

Create's pre-existing goggle overlay `IndexOutOfBoundsException` remains out of scope; this backport must not modify Create to address it.
