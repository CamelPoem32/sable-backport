# M25 Aeronautics Baseline

## Frozen revision

| Field | Frozen value |
| --- | --- |
| Repository | `https://github.com/Creators-of-Aeronautics/Simulated-Project.git` |
| Module | `aeronautics` (`common` plus `neoforge` platform source) |
| Commit | `9e60263fb5cb00033f14af655a7e72cf7aebb3e2` |
| Commit date | `2026-06-12T23:01:15-04:00` |
| Commit subject | `New changelog` |
| Tag/version | No tag contains the frozen commit; Aeronautics 1.3.0 from module property `mod_version=1.3.0` is authoritative. |
| Minecraft | `1.21.1` |
| Loader | NeoForge `21.1.228` |
| Java | 21 |
| Create | `6.0.10-280` / mod version `6.0.10` |
| Flywheel | `1.0.6` |
| Ponder | `1.0.81` |
| Registrate | `MC1.21-1.3.0+67` |
| Sable | `2.0.0` |
| Sable Companion | `1.6.0` |
| Simulated | `1.3.0` from the same repository and commit |
| Other upstream bootstrap dependency | Veil `4.0.1`; not required by the selected Levitite M25 graph |
| Code license | MIT |
| Aeronautics assets license | All Rights Reserved, The Simulated Team / The Creators of Aeronautics |

Aeronautics is not a separately versioned external checkout for this release line. Its source, Simulated 1.3.0, and the Sable 2.0.0 dependency declaration coexist at the exact frozen commit. That makes this commit the coherent Aeronautics baseline for the already-frozen M21-M24 foundation.

## Target adaptation

The target remains Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8, Flywheel 1.0.5, Ponder 1.0.91, Registrate `MC1.20-1.3.3`, and the existing Sable/Simulated backport. Aeronautics is a third real `javafml` mod in the existing Forge artifact, not a copied dependency jar.

The frozen graph contains 200 production Java files: 181 common and 19 NeoForge. It contains 534 resource files: 260 main and 274 generated. M25 selects only the bootstrap and Levitite path recorded in `M25_AERONAUTICS_PORT_MATRIX.md`.
