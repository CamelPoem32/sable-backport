# M23.2: Physics Assembler Serialization And Rapier BlockGetter

Status after static implementation: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

Do not close M23 until the manual runtime gates prove assembler save/disassembly,
Spring teardown/disassembly/reassembly, and Rapier piston/block-update collision.

## Blocker A: Physics Assembler NBT Serialization

Runtime failure:

```text
java.lang.NullPointerException:
Cannot invoke "String.isEmpty()" because "p_129298_" is null
at net.minecraft.nbt.StringTag
at net.minecraft.nbt.CompoundTag
at dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity.saveAdditional
```

Exact target line: line 194 in
`forge/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java`.

Field report:

```text
fieldName=lastFailure
NBTKey=M22LastFailure
```

| Field | Value |
| --- | --- |
| fieldName | `lastFailure` |
| NBTKey | `M22LastFailure` |
| failing operation | `tag.putString("M22LastFailure", this.lastFailure)` |
| fieldValue | `null` after catching a Create `AssemblyException` whose Java message is unset |
| whereInitialized | Backport M22 diagnostic default `none` |
| whereCleared | Successful M22 assembly/disassembly resets it to `none` |
| whereCorrupted | M22/M23.1 catch path assigned `exception.getMessage()` directly |
| whetherUpstreamHasField | no |
| whetherM22AddedField | yes, backport-only diagnostic state |
| whetherM23/M23.1AddedField | M23.1 added adjacent diagnostic strings, but line 194 is the M22 field |

Frozen upstream Simulated commit
`9e60263fb5cb00033f14af655a7e72cf7aebb3e2` persists the Physics Assembler with
`AssemblyException.write/read` for `lastException` and a boolean `IsPrimary`.
In short: upstream persists lastException through AssemblyException.write/read and IsPrimary.
It does not persist `M22LastFailure`,
`M22LifecycleState`, `M23LastDisassemblyResult`, or
`M23LastAssemblyFailureStage`. Those are backport-only diagnostics and must not
corrupt gameplay NBT.

Correct semantics:

- Optional diagnostic strings are normalized to canonical defaults before write.
- Missing diagnostic keys load as canonical defaults.
- Literal `"null"` is never serialized.
- Create `AssemblyException` text is read from `exception.component.getString()`
  when `exception.getMessage()` is absent.
- If both channels are empty, the fallback is a concrete
  `ASSEMBLY_EXCEPTION_WITH_NULL_MESSAGE exceptionClass=...`, not `null` or
  `unknown`.

Why save and disassembly both failed: both paths serialize the same BlockEntity.
World/sublevel save called `saveAdditional`; M22 disassembly called
`BlockEntity.saveWithFullMetadata` inside `SubLevelAssemblyHelper.moveBlocks`.
Both reached the same null `M22LastFailure` value.

M23.1 source-preservation guard result: proven working. Runtime showed
`phase=after_disassembly_move_failed_source_preserved` with unchanged
`storedBlockCount`, payload count, and `blockSetSha256`; the disassembly move
aborted before deleting the source sublevel.

## Blocker B: Rapier BlockGetter Contract

Runtime failure:

```text
java.lang.AbstractMethodError:
dev.ryanhcode.sable.physics.impl.rapier.collider.PhysicsColliderBlockGetter
does not define or inherit implementation of:
net.minecraft.world.level.BlockGetter.getBlockEntity(BlockPos)
production/SRG: m_7702_(BlockPos)
```

The target source already had a named `getBlockEntity(BlockPos)` method, and the
named `rapierBackport` class contained that declaration. The first missing
production method was therefore not source absence, but a nested Rapier
production-jar remapping failure: inherited Minecraft interface method
declarations remained named instead of being renamed to their 1.20.1 SRG names.

MC 1.20.1 `BlockGetter` / inherited `LevelHeightAccessor` contract:

| Method | 1.21.1 contract | 1.20.1 production contract | Target implementation |
| --- | --- | --- | --- |
| `getBlockEntity(BlockPos)` | `getBlockEntity` | `m_7702_` | `PhysicsColliderBlockGetter.m_7702_` |
| `getBlockState(BlockPos)` | `getBlockState` | `m_8055_` | `PhysicsColliderBlockGetter.m_8055_` |
| `getFluidState(BlockPos)` | `getFluidState` | `m_6425_` | `PhysicsColliderBlockGetter.m_6425_` |
| `getHeight()` | `getHeight` | `m_141928_` | `PhysicsColliderBlockGetter.m_141928_` |
| `getMinBuildHeight()` | `getMinBuildHeight` | `m_141937_` | `PhysicsColliderBlockGetter.m_141937_` |

`getBlockEntity` semantics:

- `RapierVoxelColliderBakery` asks block collision shapes through a synthetic
  one-block `PhysicsColliderBlockGetter`.
- The queried block state exists at synthetic `BlockPos.ZERO`; neighbors remain
  air, preserving the existing architecture that prevents neighbor-dependent
  shape crashes.
- For world block updates where the real parent-world position is known,
  `BlockPos.ZERO` is remapped to the backing `LevelAccelerator` / level
  `getBlockEntity(realPos)`.
- For state-only sublevel and kinematic contraption uploads, no real backing
  BlockEntity position exists, so BlockEntity lookup remains absent.

Why MovingPistonBlock exposed the bug: the vanilla moving-piston collision path
queries `BlockGetter.getBlockEntity(pos)` to recover moving-piston progress and
moved block data. In production, the JVM dispatched to `m_7702_`; the nested
Rapier class only declared the named method, so dispatch failed before shape
semantics could run.

Fix:

- `PhysicsColliderBlockGetter` now stores an optional real `sourcePos`.
- Positioned collider builds delegate `getBlockEntity(BlockPos.ZERO)` to the
  backing block getter at `sourcePos`.
- World chunk/block updates pass their global `BlockPos` into the bakery.
- State-only cached calls remain available for sublevel/contraption uploads.
- `RapierProductionJarMapper` now remaps inherited Minecraft method
  declarations, not only method references and lambda SAM names.

No production mixins were added.

Spring force and Spring persistence were not changed; runtime already proved
those gates, and neither blocker was caused by the Spring force law.
