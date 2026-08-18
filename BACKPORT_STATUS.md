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

The upstream `common`, `fabric`, `neoforge`, and `sable_rapier` modules remain intact for reference. This milestone changes build plumbing only; it does not begin Minecraft, Create, Flywheel, Veil, or Companion semantic porting.

## Canonical Workflow

`sableForgeBackport=true` is the default in `gradle.properties`. With `JAVA_HOME` pointing to JDK 17, the canonical compiler command is:

```powershell
.\gradlew.bat :forge:compileJava
```

No `--configure-on-demand` flag is required. Set `sableForgeBackport=false` only when intentionally inspecting the untouched Java 21 upstream project graph.

Gradle remains pinned to `8.14.3`. MixinGradle resolves annotation-processor inputs before ForgeGradle 6 adds its repository content filters, and Gradle 8.14 freezes those descriptors first. The supported ForgeGradle switch `systemProp.net.minecraftforge.gradle.filter.repos=false` avoids that late mutation. `verifyTargetModpackDependencies` enforces the relevant safety property directly by rejecting raw local artifacts and requiring mapped versions outside the staging repository.

## Forge-Only Configuration

When backport mode is enabled, `settings.gradle` includes only `:forge`. The Forge module reads common Java/resources directly from `common/src/main` and does not configure Fabric Loom, NeoForge ModDev, or the Java 21 modules.

Validated under JDK 17:

```powershell
.\gradlew.bat projects
.\gradlew.bat :forge:tasks
```

`projects` reports only root `sable` and `:forge`. `:forge:tasks` succeeds without configuration-on-demand and exposes the preparation, verification, inventory, and refmap tasks.

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

The current partial refmap is about 129 KiB and contains mappings for 144 Mixin classes. Confirmed former false-negative canaries now map correctly:

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

- Curated common config: 193 entries (`124` core Minecraft and `69` core client).
- Forge-specific config: intentionally empty except normal metadata/refmap.
- Missing upstream Forge `SableMixinPlugin`: not referenced; a future Forge loader implementation remains unresolved.
- Physically excluded common sources: 22 classes (`12` optional compatibility/Sodium and `10` debug/game-test).
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

Actions are `200 INVESTIGATE`, `155 DEFER`, and `18 EXCLUDE_FROM_FORGE_TARGET`. Thus 173 entries are initially deferred/excluded by action, 180 entries are absent from the curated common runtime config, and only 22 common Java classes require physical source exclusion for this compiler target.

## Compiler Frontier

Two consecutive canonical `:forge:compileJava` invocations reach javac and Mixin AP. Both intentionally fail at the source/API frontier with javac's first 100 errors and 51 warnings. On the second pass, SRG extraction and MCP-to-SRG generation are up to date; the failed Java task still reruns because no successful class snapshot exists.

Remaining failures are grouped as follows:

1. **Sable Companion compile API absent.** The first 100 errors are dominated by missing `dev.ryanhcode.sable.companion` and `.math` types. This is intentionally visible; no stubs were added.
2. **Veil compile API absent.** Networking types such as `VeilPacketManager` are already visible in the first javac page. Rendering, registry, editor/debug, and platform surfaces remain behind the same dependency boundary.
3. **Minecraft 1.21 types/packages absent or moved.** Confirmed examples include `PathfindingContext`, `Leashable`, `net.minecraft.world.level.chunk.status.ChunkStatus`, 1.21 common/custom packet types, `DisconnectionDetails`, and renamed block entity/render-section targets.
4. **Genuine Mixin target/descriptor differences.** Current examples include `canInteractWithEntity`, `ClientChunkCache#getChunk`, `Connection#disconnect`, two `@Overwrite` mappings, and interface-injector behavior in `VibrationSystemTickerMixin`.
5. **Later Java 21 and upstream API differences.** Java 17 language/library uses and Create 6/Flywheel 1.0/renderer/network changes remain expected after the first 100-error cap, but were not altered or hidden in M0-M2.

No runtime or `runClient` attempt has been made.

## Sable Companion Assessment

Mechanical inventory found 131 common source files containing 222 Companion imports. Required surface types are:

- `SableCompanion`, `SubLevelAccess`, and `ClientSubLevelAccess`.
- `Pose3d`/`Pose3dc` and `BoundingBox3d`/`BoundingBox3dc`/`BoundingBox3i`/`BoundingBox3ic`.
- `JOMLConversion`.

`ActiveSableCompanion` implements method groups for containment, projection, sublevel-inclusive lookup, distance, velocity/air-relative velocity, tracking/vehicle lookup, client-level access, and plot-grid checks.

Conclusion: Companion is a public compile API and a bundled/JiJ runtime common library, not merely an optional compatibility hook. Its API references Minecraft classes, and the upstream `sable-companion-common-1.21.1:1.6.0` artifact is built for the Java 21/1.21 line. A Java 17/Minecraft 1.20.1 common artifact will eventually be required. The official project describes the common library as the JiJ-safe default implementation, and the upstream NeoForge build bundles that common artifact; no separate Forge-specific Companion artifact is indicated. No Companion stub or separate backport project was created in this milestone. See the [official Sable Companion repository](https://github.com/ryanhcode/sable-companion).

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

All referenced categories matter to `compileJava` until a deliberate source boundary or replacement dependency exists. Networking and registry surfaces are likely required for world load and physics/sublevel runtime. Basic client world rendering is needed after world load, but fancy shaders, editor/debug UI, Iris/Sodium bridges, advanced framebuffer/shader paths, and profiler integrations are deferrable visuals. Main-menu viability should require only loader/client bootstrap and registry/platform wiring, not advanced sublevel rendering.

Preferred future direction: establish a minimal Companion-aware core plus Veil-independent networking/registry boundary, and defer Veil-only advanced client/render/debug classes. This is an assessment only; no Veil build, stub, semantic rewrite, or broad source exclusion was started.

## M0-M2 Acceptance Report

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
14. **Next milestone:** M3, resolve Java 17 incompatibilities and define the minimal Companion-aware, Veil-independent common source boundary using this trustworthy compiler frontier. Do not begin Create/Flywheel/Minecraft semantic ports until that boundary is explicit.

## Known Target Modpack Issue

Create's pre-existing goggle overlay `IndexOutOfBoundsException` remains out of scope; this backport must not modify Create to address it.
