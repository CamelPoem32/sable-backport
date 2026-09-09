# M24.1 Fixture Lifecycle Audit

M24.1 repairs the shared fixture and endpoint validation path used by the M24 physical families. It does not add new gameplay families, does not start Aeronautics, and does not modify the runtime-proven M22/M23 Spring, glue, terrain collision, or disassembly semantics.

Frozen Simulated upstream: `Creators-of-Aeronautics/Simulated-Project` commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Shared Finding

The M24 command surface used an independent fixture path instead of the M23 runtime-proven two-body fixture validation gate. The fixture payloads were M22-compatible, but the M24 command layer did not prove that the right-clicked Physics Assemblers produced two visible, collidable, distinct Sable bodies before family endpoint logic was trusted.

`M24PhysicalBlockEntity.inspect()` also reported `READY` whenever no backend handle was active. That allowed invalid states such as `ownerSable=static_world`, `partnerSable=unresolved`, and `constraintMode=PARTIAL` to look like usable fixture state.

M24.1 adds the shared `/sable m24 bodies` diagnostic gate and `SABLE_M24_BODY_LIFECYCLE` transition output. SABLE_TO_SABLE fixtures must now resolve two distinct sublevels with stored blocks, Physics Assembler inclusion, endpoint inclusion, a registered body, and uploaded collision geometry before inspect/constraint validation can proceed.

## Family Matrix

| family | fixtureBuilder | parentWorldBlocks | physicsAssemblerCount | glueMechanism | expectedBodyCount | expectedBlocksPerBody | constraintEndpointBlocks | supportPlatform | assemblyInteraction | bodyDiscovery | constraintCreationTrigger |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| torsion | `M24SimulatedSystemsCommands.fixture(TORSION_SPRING)` | two compact 6-block M22 payloads | 2 | Create Super Glue | 2 for fixture symmetry, at least 1 required for torsion behavior | 6 | `simulated:torsion_spring` | ordinary `minecraft:stone`, unglued | normal Physics Assembler right-click | `/sable m24 bodies` resolves raw blocks from visible assembler/component positions | onboard structural/Create-kinetic state only |
| swivel | `fixture(SWIVEL_BEARING)` | two compact 6-block M22 payloads | 2 | Create Super Glue | 2 | 6 | `simulated:swivel_bearing` on both bodies | ordinary `minecraft:stone`, unglued | normal Physics Assembler right-click | authoritative fixture metadata plus sublevel raw/visible lookup | automatic M24 BE pairing after both bodies validate |
| rope | `fixture(ROPE_CONNECTOR)` | two compact 6-block M22 payloads | 2 | Create Super Glue | 2 | 6 | `simulated:rope_connector` on both bodies | ordinary `minecraft:stone`, unglued | normal Physics Assembler right-click | authoritative fixture metadata plus sublevel raw/visible lookup | automatic M24 BE pairing after both bodies validate |
| winch | `fixture(ROPE_WINCH)` | two compact 6-block M22 payloads | 2 | Create Super Glue | 2 | 6 | `simulated:rope_winch` and `simulated:rope_connector` | ordinary `minecraft:stone`, unglued | normal Physics Assembler right-click | authoritative fixture metadata plus sublevel raw/visible lookup | automatic M24 BE pairing after both bodies validate |
| docking | `fixture(DOCKING_CONNECTOR)` | two compact 6-block M22 payloads | 2 | Create Super Glue | 2 | 6 | `simulated:docking_connector` and `simulated:paired_docking_connector` | ordinary `minecraft:stone`, unglued | normal Physics Assembler right-click | authoritative fixture metadata plus sublevel raw/visible lookup | automatic M24 BE pairing after both bodies validate |
| altitude sensor | no standalone M24 fixture command yet | command tree only exposes inspect/status | n/a | n/a | owning block must be in a real assembled Sable for runtime proof | n/a | `simulated:altitude_sensor` | n/a | normal M22 assembly path when tested | owner sublevel lookup via raw plot containment fallback | onboard visible-coordinate read |
| velocity sensor | no standalone M24 fixture command yet | command tree only exposes inspect/status | n/a | n/a | owning block must be in a real assembled Sable for runtime proof | n/a | `simulated:velocity_sensor` | n/a | normal M22 assembly path when tested | owner sublevel lookup via raw plot containment fallback | Sable body velocity read |
| optical sensor | no standalone M24 fixture command yet | command tree only exposes inspect/status | n/a | n/a | owning block must be in a real assembled Sable for runtime proof | n/a | `simulated:optical_sensor` | n/a | normal M22 assembly path when tested | owner sublevel lookup via raw plot containment fallback | visible-space raycast |
| steering wheel | no standalone M24 fixture command yet | command tree only exposes inspect/status | n/a | n/a | owning block must be in a real assembled Sable for runtime proof | n/a | `simulated:steering_wheel` | n/a | normal M22 assembly path when tested | owner sublevel lookup via raw plot containment fallback | onboard input only; Aeronautics deferred |

## Generic M24 Block Classes

`M24PhysicalComponentBlock` and `M24PhysicalBlockEntity` are Forge-backport convenience abstractions, not one-to-one copies of every frozen upstream block class. M24.1 keeps them only as shared bootstrap/fixture infrastructure and verifies they do not bypass M22 assembly. The endpoint BE now reports `WAITING_FOR_VALID_BODIES`, `PARTIAL_ENDPOINT_RESOLUTION`, `ERROR_SAME_BODY`, `WAITING_FOR_BACKEND`, or `ACTIVE` instead of using `READY` for partial constraint-backed states.

## Runtime Boundary

Static verification cannot prove client rendering or physical contact. The M24.1 runtime gate remains required: after assembling fixture A and B, `/sable m24 bodies` must show two distinct Sables with `storedBlockCount > 0`, `bodyRegistered=true`, and `collisionGeometryPresent=true` before a family-specific inspect result can be considered meaningful.
