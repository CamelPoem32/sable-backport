# M22 Assembly Port Matrix

M22 reuses `M21_SIMULATED_PORT_MATRIX.md` and selects only the transitive
production graph required for basic Physics Assembler assembly/disassembly.

## Counts

| State | Count |
| --- | ---: |
| PORT_NOW | 0 |
| ADAPT_NOW | 8 |
| STRUCTURAL_ONLY | 1 |
| DEFER_M23 | 10 |
| NOT_APPLICABLE_1_20_1 | 3 |

## Selected Sources

| Upstream path | Target path | Purpose | Dependencies | Java 21 | NeoForge | MC API | Create | Sable | Action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlock.java` | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlock.java` | canonical assembler block interaction and Sable move listener | Minecraft, Create IBE/IWrenchable, Sable listener | switch expression only | no | face-attached API differs from M21 stub | deployer fake-player deferred | `BlockSubLevelAssemblyListener` same | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java` | `forge/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java` | lifecycle owner | Minecraft, Create SmartBlockEntity, Sable physics/constraints, Veil packets | no | no | HolderLookup save API | Create behaviour tips deferred | Sable assembly/helper same for core | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerGUIHandler.java` | none | client hold/lever UX | Client, Veil packets, sounds | no | no | client overlay APIs differ | no | client containing lookup | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/network/packets/physics_assembler/PhysicsAssemblerFlickAndHoldLeverPacket.java` | none | client lever animation packet | Veil network | no | no | packet API differs | no | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/network/packets/physics_assembler/PhysicsAssemblerFailedPacket.java` | none | client failure animation packet | Veil network | no | no | packet API differs | no | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/network/packets/AssemblePacket.java` | none | client-to-server assemble trigger | Veil network | no | no | packet API differs | no | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java` | `forge/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java` | Sable assembly/disassembly bridge | Minecraft, Create contraptions/glue, Sable assembly helper | Stream.toList avoided | no | entity-manager internals avoided | Create moving contraption merge deferred | core helper same | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/util/assembly/SimAssemblyContraption.java` | `forge/src/main/java/dev/simulated_team/simulated/util/assembly/SimAssemblyContraption.java` | structure collection | Minecraft, Create movement checks, Simulated movement checks | no | no | compatible | Create API same package in 6.0.8 | no | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/util/assembly/SimAssemblyException.java` | `forge/src/main/java/dev/simulated_team/simulated/util/assembly/SimAssemblyException.java` | lifecycle errors | Create AssemblyException, Sim config/lang | no | no | compatible | compatible | no | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/service/SimAssemblyService.java` | `forge/src/main/java/dev/simulated_team/simulated/service/SimAssemblyService.java` | loader service abstraction for stickiness | Minecraft | no | service loader impl differs | Forge stickiness surface differs | no | no | ADAPT_NOW |
| `simulated/neoforge/src/main/java/dev/simulated_team/simulated/neoforge/service/NeoForgeSimAssemblyService.java` | folded into `SimAssemblyService` | NeoForge stickiness implementation | NeoForge service loader | no | yes | `BlockState.canStickTo` behavior differs | no | no | NOT_APPLICABLE_1_20_1 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/config/server/blocks/SimAssembly.java` | `forge/src/main/java/dev/simulated_team/simulated/index/SimulatedConfig.java` | assembly config defaults | Create config framework | no | no | compatible | Create config framework not retained | no | ADAPT_NOW |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/assembly_preventer/DisassemblyPrevention.java` | guarded inline primary/current-sublevel check | primary assembler disassembly gate | Sable mixin extension | no | no | compatible | no | mixin extension not ported | STRUCTURAL_ONLY |
| `simulated/common/src/main/java/dev/simulated_team/simulated/mixin_interface/assembly_preventer/PrimaryAssemblerExtension.java` | none | primary assembler metadata on ServerSubLevel | mixin duck | no | no | compatible | no | requires mixin | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/mixin/assembly_preventer/ServerSubLevelMixin.java` | none | stores primary assembler on sublevel | Mixin | no | no | compatible | no | target descriptor verification needed | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/index/SimBlockMovementChecks.java` | partial behavior in `SimAssemblyContraption` | Simulated-specific additional block rules | future Simulated blocks | no | no | compatible | BlockMovementChecks | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/honey_glue/HoneyGlueEntity.java` | none | honey glue connectivity entity | entity registration/network/render | no | no | entity sync differs | no | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/entities/honey_glue/HoneyGlueItem.java` | existing item remains nonfunctional | honey glue placement | entity/network | no | no | compatible | no | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/mixin/accessor/ContraptionAccessor.java` | none | upstream Create moving contraption glue extraction | Mixin accessor | no | no | descriptor target differs | Create internals | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/mixin/accessor/ControlledContraptionEntityAccessor.java` | none | upstream Create moving contraption disassembly bridge | Mixin accessor | no | no | descriptor target differs | Create internals | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/mixin_interface/create_assembly/IControlContraptionExtension.java` | none | Create controlled contraption extension | Mixin duck | no | no | compatible | Create internals | no | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/index/SimSoundEvents.java` | none | assembler sounds | Registrate/Veil sound wrapper | no | no | compatible | Registrate | no | NOT_APPLICABLE_1_20_1 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/physics_staff/PhysicsStaffServerHandler.java` | none | removes physics staff lock before disassembly | constraints/staff | no | no | compatible | no | Sable constraints | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/util/SimMathUtils.java` | none | closest yaw for alignment constraint | JOML | no | no | compatible | no | constraints | DEFER_M23 |
| `simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/behaviour/HoldTipBehaviour.java` | none | Create hover tip UX | Create SmartBlockEntity behavior | no | no | compatible | behavior tooltip | no | NOT_APPLICABLE_1_20_1 |

## Notes

The M22 implementation deliberately does not add any new registry entries. It
activates the already runtime-proven M21 `physics_assembler` block and block
entity. The only Forge-specific semantic adapter is the stickiness service:
NeoForge upstream calls `BlockState.canStickTo`; the target treats ordinary
non-air blocks as stickable for this basic lifecycle boundary while retaining
the upstream traversal, size limit, Create movement checks, and unmovable-block
guards.
