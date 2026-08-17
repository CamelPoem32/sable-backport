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
- Forge: exact installed launcher version was not present in the provided `target_modpack`; Create declares Forge `[47.1.3,)`, so the initial ForgeGradle target is `47.1.3`
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

# Completed Compatibility Changes

- Added this initial status log.
- Identified the first build-system direction: add an isolated Forge module rather than destructively converting `neoforge/`.
- Added initial isolated Forge 1.20.1 build/module scaffolding.
- Added Forge `mods.toml` and an initially empty Forge-specific mixin config.

# Remaining Compiler Errors

Not measured yet for the Forge module because Gradle cannot start under Java 8.

# Remaining Runtime Errors

Runtime has not been attempted.

# Temporarily Disabled Optional Integrations

None disabled in source yet.

Expected deferral candidates if dependencies are unavailable for 1.20.1 Forge: Sodium/Iris, PMWeather, Exposure, Distant Horizons, Vista, Backpacks for Dummies, ComputerCraft, ImGuiMC. Jade and Jade Addons are present in the target modpack.

# Mixin Porting Status

Common mixins remain upstream and have not been checked against Minecraft 1.20.1 method descriptors.

NeoForge-specific mixins remain in `neoforge/` for reference. A Forge-specific mixin config starts empty until individual Forge/1.20.1 targets are ported and verified.

# Dependency Version Map

Minecraft       1.21.1 -> 1.20.1
Java            21     -> 17 for the Forge backport module
NeoForge        21.1.228 -> Forge 47.1.3 initial target
Create          6.0.10-280 -> 0.5.1.j
Ponder          1.0.82 -> bundled in Create 0.5.1.j, no separate jar found
Flywheel        1.0.6 -> 0.6.11-13
Registrate      MC1.21-1.3.0+67 -> MC1.20-1.3.3
JEI             not used by upstream Sable -> 15.20.0.112 available in target pack
Sable Companion 1.6.0 -> unresolved for 1.20.1
Veil            4.1.4 -> unresolved for 1.20.1; target pack does not include Veil

# Next Recommended Step

Install or point this shell at a Java 17+ JDK, then rerun `.\gradlew.bat :forge:compileJava --stacktrace`. The next expected stage is ForgeGradle/dependency resolution, followed by grouped Java API errors.
