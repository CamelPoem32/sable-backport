# M21 Simulated Baseline

## Frozen Upstream

- Repository URL: `https://github.com/Creators-of-Aeronautics/Simulated-Project.git`
- Local upstream remote: `upstream`
- Upstream project/module name: `simulated-project`, module `simulated`
- Exact tag: no git tag present in the available repository
- Exact commit SHA: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Commit subject/date: `New changelog`, `2026-06-12 23:01:15 -0400`
- Release line: Simulated `1.3.0`
- Minecraft version: `1.21.1`
- Loader: NeoForge
- Java version: `21`
- Create version: `6.0.10-280` / mod version `6.0.10`
- Flywheel version: `1.0.6`
- Ponder version: `1.0.81`
- Registrate version: `MC1.21-1.3.0+67`
- Sable dependency: `2.0.0`
- Sable Companion dependency: `1.6.0`
- Sable mod version range: `[2.0.0,3.0.0)`
- Aeronautics relationship: Simulated is included by the Simulated-Project multi-module repository and is normally bundled with Aeronautics through the `aeronautics-bundled` target.
- License: code MIT; assets All Rights Reserved per upstream `LICENSE.md`.

## Why This Revision

The requested Sable baseline is `mc1.21.1-2.0.0-neoforge` at
`b7226222caf4eace63a708bdcd73ef36c971137d`, committed on
`2026-06-12 22:52:34 -0400`.

The Simulated repository has no release tags in the available checkout. Its
commit `076bbf592b3200ea17583606838c010cd55a6192` changed the dependency range
to Sable `2.0.0` at `2026-06-12 22:44:32 -0400`, and
`9e60263fb5cb00033f14af655a7e72cf7aebb3e2` immediately followed with the
release changelog at `2026-06-12 23:01:15 -0400`. That is the nearest coherent
Simulated release-line commit tied to Sable `2.0.0`.

The current local Simulated checkout is newer (`4406c68`, Simulated `1.3.1`,
Sable `2.0.4-37`, Veil `4.3.2`) and is therefore not the M21 baseline.

## Source And Resource Directories

- Production Java source directories:
  - `simulated/common/src/main/java`
  - `simulated/neoforge/src/main/java`
- Resource directories:
  - `simulated/common/src/main/resources`
  - `simulated/neoforge/src/main/resources`
  - `simulated/common/src/generated/resources`
- Production Java source count at frozen commit: `634`
- Resource file count in main NeoForge/common resource roots at frozen commit: `690`

## Target Backport Boundary

M21 adapts a Forge-only bootstrap surface inside the existing `:forge` module.
It does not import NeoForge runtime metadata, Java 21 bytecode, upstream Veil
network payloads, full Registrate graph, physics constraints, docking, sensors,
or vehicle gameplay. Those remain explicitly recorded in
`M21_SIMULATED_PORT_MATRIX.md` for later milestones.
