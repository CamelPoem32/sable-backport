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

> **Target-modpack rebaseline required:** the versions and hashes below describe
> the old target modpack. The user is replacing it and moving Create to Create
> 6. Before any M7 work, inventory `../target_modpack` using
> `TARGET_REBASE_CHECKLIST.md`. Revalidate all dependency hashes, embedded-jar
> assumptions, version ranges, Create 0.5 compatibility decisions, Flywheel
> assumptions, and Ponder packaging. See `HANDOFF.md`.

The upstream `common`, `fabric`, `neoforge`, and `sable_rapier` modules remain available for reference. M0-M2 established trustworthy Forge build plumbing. M3 added the Java 17/1.20.1 Companion library, official Veil 1.20, selected Java 17 rewrites, and the minimal Forge source graph. M4 replaced the missing modern Veil/Minecraft packet surface with a tested Forge 47 transport. M5 ported the selected Minecraft core/Mixins, added the Forge bootstrap and eight platform providers, and produced a statically verified Forge package. M6 now passes the Forge main-menu, empty-world, runtime-boundary, persistence, and stationary single-block smoke gates. Deferred advanced rendering, full physics/Sable Rapier, Create/Flywheel integration, Simulated, and Aeronautics remain unported.

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
2. **Create 0.5.1.j:** only wrapped-level detection is implemented. Create 6 contraption/logistics integrations remain deferred and absent from the Forge Mixin config.
3. **Flywheel 0.6:** visual registration is deliberately a no-op; Flywheel 1.0 render integration remains deferred.
4. **Advanced rendering/gameplay:** chunked rendering, shader/water/Iris/Sodium bridges, Leashable/pathfinding, projectile dispenser, vibration, toast/settings, and other listed clusters remain intact but excluded.
5. **Packaging:** Companion works on the Gradle `runClient` project classpath and remains intentionally absent from the Forge JarJar output. Standalone modpack testing requires that packaging step.

M6 completed the first `runClient` smoke milestone. The remaining frontier is
standalone packaging and deferred-feature work, subject first to the required
target-modpack rebaseline.

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
2. **Classpath boundary:** `verifyRunClientClasspath` confirms the merged Active/fallback Companion descriptor, mapped and development-patched Veil plus required runtime libraries, MixinExtras, and Forge 1.20 resource paths. Create, Flywheel, and Registrate are absent. `WrappedServerWorld` appears only in `SablePlatformImpl$CreateWrappedLevelCheck`, and both Create and Flywheel remain genuinely optional in `mods.toml`.
3. **Deterministic Companion:** provider discovery saw Active and fallback implementations, verified effective priorities `1000` and `500`, proved Active is the unique maximum, and confirmed that `SableCompanion.INSTANCE` selected Active. Selection does not depend on classpath enumeration order.
4. **Runtime Mixin boundary:** the current generated config has 143 entries and the current 98,398-byte refmap maps 111 classes. Retained 1.20.1 descriptor/call-site corrections were limited to selected classes and checked against mapped targets. The scroll-only `MouseHandlerMixin` is deferred with the absent custom camera enum; no global target suppression or broad `require = 0` was added.
5. **Main menu and empty world:** Sable and Veil reach the title screen. The disposable Creative Superflat Void world `M6_Smoke_Empty` loads, ticks for more than 60 seconds, exposes `/sable`, starts local UDP, synchronizes datapack definitions, and produces no repeated chunk, tick, Mixin, or resource error.
6. **Providers/config/network:** all eight Forge platform providers and `ForgeSablePacketTransport` resolve. Protocol `1` registers TCP IDs `0..13`; packet handlers report the expected main thread. CLIENT and COMMON configs load once, reload listeners register, all three server levels load, and player/server lifecycle ordering passes.
7. **Resource compatibility:** processed resources map the 1.21 singular tag/structure directories to 1.20 plural paths, translate exact common-tag names to Forge tags, and make absent 1.21-only optional entries non-required. Unknown optional-mod physics selectors are skipped while missing Minecraft/Sable selectors remain errors.
8. **Reload/persistence:** Save and Quit closes UDP, logs out the player, saves all dimensions, unloads all levels, and stops the server. The dev client exited after returning to title, so empty-world reload used a fresh client JVM; persistence passed, while a same-JVM duplicate-listener assertion was not exercised.
9. **Single-block gate:** the verified commands `/sable spawn block minecraft:stone m6_smoke` and `/sable info @l` create exactly one stationary named sublevel. StartTracking precedes Finalize. A fresh reload restores the same name, position `-6.5 -60.0 6.5`, identity/state, mass `2.0`, and zero velocities, with occupancy, tracking, and region files present. This proves runtime, synchronization, retained single-block rendering path, and persistence boundaries only, not working physics.
10. **Validation and next scope:** Companion verification, all 12 network tests, target dependency/Veil/AT checks, `compileJava`, `build`, and the final `runClient` shutdown pass. Companion JarJar remains the narrow prerequisite for standalone artifact testing. Advanced/chunked rendering, custom camera modes, full physics/Sable Rapier, Create/Flywheel features, Simulated, and Aeronautics remain deferred. Full evidence is in `M6_RUNTIME_SMOKE.md`.

## Known Target Modpack Issue

Create's pre-existing goggle overlay `IndexOutOfBoundsException` remains out of scope; this backport must not modify Create to address it.
