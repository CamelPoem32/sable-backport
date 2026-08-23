# M6 Forge Runtime Smoke Report

> Historical runtime evidence: this smoke used the pre-rebaseline Create
> `0.5.1.j` environment. The 2026-08-22 Create `6.0.8` rebaseline was static
> only and replaced the old wrapper bytecode boundary with Ponder `1.0.91`
> `WrappedServerLevel`. A new runtime launch is intentionally deferred.

## Result

M6 passed the main-menu, empty-world, runtime-boundary, persistence, and named
single-block gates on Forge `47.4.20`, Minecraft `1.20.1`, and Java `17`.
The final selected Forge core still compiles and packages successfully. This
milestone proves the stationary core runtime, synchronization, retained
single-block rendering path, and persistence boundary. It does not prove
working physics because `sable_rapier` and the full physics integration remain
absent.

The current generated Forge Mixin config contains 143 entries. The final M6
refmap is 98,398 bytes and contains mappings for 111 classes. The M5 count of
112 is retained as historical evidence only; `MouseHandlerMixin` was narrowly
deferred during M6 with the already-deferred custom camera enum feature.

## Runtime Harness

- `minecraft.runs.client` uses Java 17, `forge/run/m6-client`, INFO logging,
  verbose Mixin diagnostics, and `sable.runtimeSmoke=true`.
- The `m6SableRun` development source set exposes Sable and Companion as one
  exploded Forge mod without turning Companion into a standalone mod.
- `verifyRunClientClasspath` confirms both Companion implementations and their
  deterministic merged provider descriptor, mapped/patched Veil, required Veil
  runtime libraries, and the absence of Create, Flywheel, and Registrate.
- The only selected Create bytecode reference remains
  `SablePlatformImpl$CreateWrappedLevelCheck`; the outer platform provider does
  not link `WrappedServerWorld`.
- Companion selection is priority-based: `ActiveSableCompanion` has effective
  priority 1000, the fallback has 500, and the active implementation was
  selected independently of provider enumeration order.
- The property-gated `SABLE_M6` probe is inert outside this run configuration.
  It validates all eight platform providers, the packet transport, protocol
  `1`, TCP IDs `0..13`, configs, packet threads, and lifecycle ordering.
- Companion JarJar is not needed for Gradle `runClient`; the project output is
  present directly. It remains required before standalone modpack testing.

## Gate Results

| Gate | Result | Evidence |
|---|---|---|
| Main menu | PASS | Sable and Veil loaded; both Companion providers were visible; Active won by priority; all platform providers and the packet transport resolved; no retained Mixin or linkage crash occurred. |
| Empty world | PASS | `M6_Smoke_Empty` loaded as a Creative Superflat Void world, ticked for more than 60 seconds, registered `/sable`, completed reload listeners and datapack sync, and kept local UDP/TCP activity clean. |
| Runtime boundary | PASS | Both configs loaded once, 14 TCP registrations were present, data packets ran on the expected main thread, all three server dimensions loaded, and login/server lifecycle ordering passed. |
| Empty-world reload | PASS with harness caveat | Save and Quit closed UDP, logged out the player, stopped the server, unloaded all levels, and saved empty Sable state. The dev client exited after returning to title, so reload used a fresh client JVM; persistence passed, but same-JVM duplicate-listener behavior was not exercised. |
| Single-block boundary | PASS | The exact commands `/sable spawn block minecraft:stone m6_smoke` and `/sable info @l` produced one stationary named sublevel. StartTracking preceded Finalize, persistence files were written, and a fresh reload restored the same sublevel and state. |

The final `info @l` result before and after reload was:

```text
Found 1 sub-levels:
m6_smoke:
    Position: -6.5 -60.0 6.5
    Orientation: 0.0 0.0 0.0 1.0
    Mass: 2.0
    Linear Velocity: 0.0 0.0 0.0
    Angular Velocity: 0.0 0.0 0.0
```

The final reload emitted `ClientboundStartTrackingSubLevelPacket` followed by
`ClientboundFinalizeSubLevelPacket` before the command confirmed the restored
identity. Initial creation also emitted ChangeBounds, StopMoving, and
SnapshotInfo after the Start/Finalize boundary. No selected renderer exception
was logged. The test block overlapped the stone Void platform in the captured
view, so the report does not claim an independent visual pixel assertion.

## Retained-Core Fixes

Runtime failures were fixed only inside the selected Forge graph:

- `ClientChunkCacheMixin.drop` now targets the inspected 1.20.1 `(int, int)`
  descriptor. Other retained descriptor/call-site corrections cover Camera
  zoom, Block fall rotation, explosion creation, particle cracking, entity
  hitbox rendering, and the clip overwrite helper.
- The custom camera enum and scroll-only zoom feature remain intact upstream
  but are excluded from the Forge target; no fake 1.21 camera API was added.
- The Companion singleton now requests providers through its own defining class
  loader while retaining upstream max-priority selection semantics.
- The Veil development runtime is mapped, strips bundled split-package LWJGL
  classes, and translates six mapped shadow aliases required by the Forge dev
  environment. The production dependency and deobfuscation contract are not
  replaced.
- Forge resources map 1.21 singular data directories to 1.20 plural paths,
  translate known common tags to exact Forge 1.20 tags, and mark unavailable
  1.21-only optional entries as non-required. Unknown optional-mod physics
  selectors, such as absent Create blocks, are skipped; missing Minecraft or
  Sable selectors remain errors.
- Physics property registration tracks deterministic IDs independently of the
  deferred registry view, preventing duplicate or unstable IDs during runtime
  registration.

Every changed retained Mixin was checked against the current generated config
and mapped 1.20.1 target. No global validation suppression or broad
`require = 0` was introduced.

## Persistence Evidence

After the final save, the disposable world contained:

| File | Bytes |
|---|---:|
| `data/capabilities.dat` | 48 |
| `data/sable_sub_level_force_load_tickets.dat` | 60 |
| `data/sable_sub_level_occupancy.dat` | 78 |
| `data/sable_tracking_points.dat` | 232 |
| `sublevels/r.-1.0.0.slvls` | 12,288 |
| `sublevels/r.-1.0.slvlr` | 4,352 |

The final shutdown again closed the client UDP channel, saved sublevels for all
three dimensions, unloaded client/server levels, and emitted `server stopped`.

## Validation

The M6 code path passed:

```powershell
.\gradlew.bat :forge:verifyRunClientClasspath
.\gradlew.bat :sable_companion_1_20:verifySableCompanionBackport
.\gradlew.bat :forge:networkTest
.\gradlew.bat :forge:verifyTargetModpackDependencies
.\gradlew.bat :forge:verifyVeilDependency
.\gradlew.bat :forge:verifyForgeAccessTransformer
.\gradlew.bat :forge:compileJava
.\gradlew.bat :forge:build
.\gradlew.bat :forge:runClient
```

`:forge:networkTest` remains 12/12. The final `runClient` invocation ended with
`BUILD SUCCESSFUL` after the persistence check and clean shutdown.

## Deferred Work

Advanced/chunked rendering, custom camera modes, full physics/Sable Rapier,
Create 0.5.1 integration beyond guarded wrapped-level detection, Flywheel 0.6
visuals, Simulated, and Aeronautics remain deferred. The narrow next milestone
should add Companion JarJar and smoke-test the standalone Forge artifact before
starting any deferred feature family.
