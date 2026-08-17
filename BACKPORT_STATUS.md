# Target Environment

Minecraft: 1.20.1
Mod loader: Forge
Java: 17

Target modpack inspected: `../target_modpack`.

Discovered local dependency versions:
- Create: 0.5.1.j from `create-1.20.1-0.5.1.j.jar`
- Flywheel: 0.6.11-13, embedded in the Create jar under `META-INF/jarjar`
- Registrate: MC1.20-1.3.3, embedded in the Create jar under `META-INF/jarjar`
- JEI: 15.20.0.112 from `jei-1.20.1-forge-15.20.0.112.jar`
- Forge: 47.4.20, confirmed from the target launcher/runtime log; Create declares Forge `[47.1.3,)`
- Ponder: no standalone Ponder jar found; Create 0.5.1.j carries its Ponder code/resources internally
- Sable: no existing Sable jar found in the target modpack
- Veil: no Veil jar found in the target modpack

# Upstream Baseline

Repository: https://github.com/ryanhcode/sable
Baseline ref: `mc1.21.1-2.0.0-neoforge`
Baseline commit: `b7226222caf4eace63a708bdcd73ef36c971137d`
Working branch: `backport/forge-1.20.1-sable-2.0.0`

The upstream architecture has `common/`, `fabric/`, `neoforge/`, and `sable_rapier/`. The Forge backport is being added as a separate `forge/` module so the 1.21.1 Fabric/NeoForge implementations remain intact for comparison.

# Build Status

Initial state: clean checkout from the official Sable 2.0.0 NeoForge tag.

Current milestone target:
1. Resolve a Forge 1.20.1 development environment.
2. Compile common Sable sources through a Forge 1.20.1 module.
3. Produce a Forge Sable jar.

First compile attempt:
- Command: `.\gradlew.bat :forge:compileJava --stacktrace`
- Result: blocked before Gradle configuration.
- Root cause: the machine PATH currently resolves `java` to Java 8 (`1.8.0_501`), while Gradle 9.5.0 requires JVM 17 or later to run.
- Local search found only `C:\Program Files\Java\jre1.8.0_501\bin\java.exe`; no Java 17/21 install was visible in the common locations checked.

Current local toolchain checks:
- Active `java`: Java 8 (`1.8.0_501`) from `C:\Program Files\Java\jre1.8.0_501\bin\java.exe`
- Active `javac`: not found on PATH
- Installed JDK 17 found at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Workspace-local JDK 21 downloaded to `../.jdk21/temurin21/jdk-21.0.12+8`
- Gradle wrapper: changed from 9.5.0 to 8.14.3 for ForgeGradle 6 compatibility
- ForgeGradle plugin: `net.minecraftforge.gradle` version range `[6.0,6.2)`
- Forge backport source/toolchain target: Java 17

Additional build-system findings:
- Running Gradle under JDK 17 fails before Forge configuration because upstream Fabric Loom 1.16.3 requires a Java 21 Gradle runtime.
- Running Gradle under JDK 21 with wrapper 9.5.0 reaches the Forge module, but ForgeGradle 6 rejects Gradle 9.0+.
- Fabric Loom 1.16.3 declares Gradle plugin API 9.4.0, while ForgeGradle 6 rejects Gradle 9.0+. The Forge backport therefore keeps Fabric/NeoForge plugin versions isolated in their own modules and builds the Forge module with `--configure-on-demand` under Gradle 8.x.
- After isolating the Fabric/NeoForge plugins, the Forge-only build can run Gradle under JDK 17. JDK 21 is only needed when configuring upstream 1.21 Fabric/NeoForge modules.
- The intended local command shape is therefore: run Gradle with JDK 17, keep the wrapper on Gradle 8.x, and avoid configuring the upstream 1.21 Fabric/NeoForge modules during Forge-only tasks.

# Completed Compatibility Changes

- Added this initial status log.
- Identified the first build-system direction: add an isolated Forge module rather than destructively converting `neoforge/`.
- Added initial isolated Forge 1.20.1 build/module scaffolding.
- Added Forge `mods.toml` and an initially empty Forge-specific mixin config.
- Updated the Forge target from the initial Create-minimum `47.1.3` to the target runtime's confirmed `47.4.20`.
- Switched the Gradle wrapper from 9.5.0 to 8.14.3 because ForgeGradle 6 does not support Gradle 9 yet.
- Moved Fabric Loom and NeoForge ModDev plugin versions from the root plugin classpath into their own modules so Forge-only Gradle 8 tasks do not resolve Gradle-9-only Fabric tooling.
- Changed the Forge Create/Flywheel/Registrate classpath to use the target modpack's local Create jar and the Create jar's embedded JarJar dependencies because the guessed Maven coordinates for Create `0.5.1.j` and Flywheel `0.6.11-13` do not resolve.

# Remaining Compiler Errors

Measured with:

`.\gradlew.bat :forge:compileJava --configure-on-demand --stacktrace`

using JDK 17 from `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`.

Current result:
- ForgeGradle config succeeds under Gradle 8.14.3.
- The mapped Forge `1.20.1-47.4.20` jar is generated successfully in the elevated/user Gradle cache path.
- `:forge:compileJava` reaches Java source compilation.
- The task fails in the Mixin annotation processor and source mapping stage.

Current error categories:
- Optional compatibility mixins with unavailable compile-time targets, e.g. ComputerCraft, Exposure, Iris, Jade/Jade Addons, Vista, Sodium.
- Minecraft 1.21.1 -> 1.20.1 method/name descriptor mismatches in common mixins, e.g. `renderLevel`, `canPlace`, `getHorizontalDirection`, `calculateViewVector`, `getMaxZoom`, `handleKeybinds`, `bootstrap`, `tick`, and related injection targets.
- Forge local file dependencies for target Create/Flywheel now allow compilation to start, but ForgeGradle warns that `files(...)` dependencies are not deobfuscated. The next classpath refinement should switch those local jars to a `flatDir`/module-style dependency or another ForgeGradle-compatible local Maven strategy.

# Remaining Runtime Errors

Runtime has not been attempted.

# Known Pre-existing Target Modpack Issues

- Create goggle overlay `IndexOutOfBoundsException`: treated as a pre-existing target modpack issue. This backport should not attempt to fix Create itself.

# Temporarily Disabled Optional Integrations

None disabled in source yet.

Expected deferral candidates if dependencies are unavailable for 1.20.1 Forge: Sodium/Iris, PMWeather, Exposure, Distant Horizons, Vista, Backpacks for Dummies, ComputerCraft, ImGuiMC. Jade and Jade Addons are present in the target modpack.

# Mixin Porting Status

Common mixins remain upstream and have not been checked against Minecraft 1.20.1 method descriptors.

NeoForge-specific mixins remain in `neoforge/` for reference. A Forge-specific mixin config starts empty until individual Forge/1.20.1 targets are ported and verified.

# Dependency Version Map

Minecraft       1.21.1 -> 1.20.1
Java            21     -> 17 for the Forge backport module
NeoForge        21.1.228 -> Forge 47.4.20 target runtime
Create          6.0.10-280 -> 0.5.1.j
Ponder          1.0.82 -> bundled in Create 0.5.1.j, no separate jar found
Flywheel        1.0.6 -> 0.6.11-13
Registrate      MC1.21-1.3.0+67 -> MC1.20-1.3.3
JEI             not used by upstream Sable -> 15.20.0.112 available in target pack
Sable Companion 1.6.0 -> unresolved for 1.20.1
Veil            4.1.4 -> unresolved for 1.20.1; target pack does not include Veil

# Next Recommended Step

Refine local target-modpack jar deobfuscation, then start disabling or porting optional compatibility mixins before addressing the core Minecraft 1.20.1 method descriptor mismatches.
