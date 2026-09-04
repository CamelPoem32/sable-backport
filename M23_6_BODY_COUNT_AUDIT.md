# M23.6 Body Count Audit

Status: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

## Count Source

`bodyABlockCount` and `bodyBBlockCount` in `/sable m23 inspect bodies` came
from:

```java
SimAssemblyHelper.collectBlocks(source.getLevel(), body).size()
```

That helper scans every loaded `PlotChunkHolder` in the body's hidden raw Sable
plot, walks the holder's non-air local bounding box, and counts actual non-air
`BlockState`s. It is not a selected-block count, bounds volume, metadata count,
tracking point count, actor count, or BlockEntity count.

Therefore a value of `23` means either:

- the selected candidate really has 23 non-air raw plot blocks, or
- the command selected a different assembled Physics Assembler body than the
  intended fresh fixture body.

## Diagnostic Fix

`/sable m23 inspect bodies` now emits `SABLE_M23_BODY_COUNT_AUDIT` for candidate
Physics Assembler bodies. It reports selected fixture expectation, stored raw
count, non-air count, payload count, BlockEntity count, tracking point count,
actor count, raw bounds, bounds volume, mass, and uploaded collision block
count. It also emits `SABLE_M23_BODY_COUNT_AUDIT_BLOCKS` with at most the first
32 raw non-air blocks as raw position, local position, and block id.

Fixture validation now chooses the nearest valid six-block Physics Assembler
bodies and then sorts those valid bodies by X for body A/body B labelling. It no
longer blindly uses the first two globally X-sorted Physics Assembler bodies.
This prevents older or unrelated Sables in the world from producing a misleading
`bodyABlockCount=23` for the fresh Spring fixture.

## Plot Reuse Boundary

The static plot-reuse boundary remains:

- `SubLevelAssemblyHelper.assembleBlocks` allocates a new sublevel.
- The new plot creates a fresh empty chunk with AIR section state storage.
- Only the selected block set is moved into that plot.
- Removed plots release occupancy for reuse.

No Spring force law, Spring targeting, Spring teardown, glue traversal semantics,
or Rapier terrain collision was changed for M23.6.
