# M28 static/contraption render ownership audit

## Exact Create 6.0.8 mutation path

`MechanicalBearingBlockEntity.assemble()` builds a `BearingContraption`, calls
`BearingContraption.removeBlocksFromWorld(level, BlockPos.ZERO)`, creates the
`ControlledContraptionEntity`, and adds that entity to the level.

`Contraption.removeBlocksFromWorld()` derives each source position as:

```text
StructureBlockInfo.pos + Contraption.anchor + suppliedOffset
```

The bearing supplies `BlockPos.ZERO`. Create removes the block entity and calls
`Level.setBlock(sourcePos, AIR, 122)` (or WATER for a waterlogged source). Its
second notification pass calls `sendBlockUpdated` and `markAndNotifyBlock`.
Disassembly uses Create's matching placement path, so source ownership is
expected to transition `STATIC -> CONTRAPTION -> STATIC`.

## Sable render storage

`VanillaSingleSubLevelRenderData.renderBlocks` is a render cache, not an
authoritative assembly snapshot. `rebuild()` scans the live client plot
`LevelChunkSection` states. `ClientLevelPlot.onBlockChange()` invalidates that
cache. Thus the three relevant states are independently observable:

1. server/client live plot state;
2. cached `RenderBlock` state;
3. active Create contraption ownership.

The ordinary `create:white_sail` blocks in the Golden Aircraft are not by
themselves evidence of duplication. The canonical moving payload is
`simulated:white_symmetric_sail`. Ownership must match by source plot position,
not block type.

## Compatibility ownership rule

`SableCreateContraptionBlockOwnership` indexes active client contraptions by
the exact Create source-position formula. The vanilla Sable static renderer
does not emit a cached block when an alive contraption in the same Sable owns
that exact source position. Unrelated static sails remain renderable.

When the controlled entity disappears during normal placement/disassembly,
the ownership entry disappears and the restored live block is rendered again.
No block is deleted from persistence by this render rule.

`SABLE_M28_RENDER_OWNERSHIP path=STATIC` reports the live plot state, cached
state, exact captured position, owner entity, and whether static geometry was
suppressed or restored. `path=CONTRAPTION` reports the matching Create geometry
emission. `SABLE_M28_CREATE_ROTATION capturedSourceStates` provides the server
view of the source plot state while the contraption is active.

Manual runtime remains authoritative for deciding whether the supplied
`create:white_sail` observations are exact-position stale copies or legitimate
static aircraft sails. M28 is not closed until one visible moving payload shows
exactly one render owner and normal ownership restoration after disassembly.
