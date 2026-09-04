# M23.1 Spring Teardown Regression

Status after static fix: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

## Runtime Evidence Entering M23.1

The following Spring facts are runtime-proven and are not reopened by M23.1:

- two M22 physical assemblies can be created
- a Spring can be connected between them
- Spring force pulls the bodies
- save/reload while Spring is active succeeds
- Spring relationship survives reload
- active Spring correctly blocks M22 disassembly
- breaking a Spring endpoint releases the disassembly guard

The failing sequence was:

Spring break -> M22 disassembly -> body A restored with only the stone base,
then body B reassembly failed with a null reason/state.

## Upstream Teardown Semantics

Frozen upstream: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

Upstream Spring teardown is local to the Spring pair:

`SpringBlock.onRemove` delegates to Create `IBE.onRemove`.
`SpringBlockEntity.remove()` checks `!level.isClientSide`, `partnerPos != null`,
and `!assembling`, then calls `level.destroyBlock(partnerPos, false)` and clears
`partnerPos`.

It does not remove sublevels, assembler metadata, tracking points, all actors,
payload blocks, or Sable bodies. It does not own the assembly block set.

## Target Root Cause

The target regression crossed two lifecycle boundaries:

1. Spring endpoint removal cleared only the initiating endpoint before destroying
   the paired endpoint. After save/reload, this could leave stale follower or
   controller relationship state visible long enough to confuse teardown
   diagnostics and disassembly gating.
2. The shared Sable `SubLevelAssemblyHelper.moveBlocks` copy phase caught
   per-block restore failures but continued to mark/notify and then destroy all
   source blocks. That made disassembly non-transactional: a single failed copy
   could produce partial destination restoration and source payload loss.

M23.1 does not change Spring force behavior. The fix is ownership and commit
safety around teardown/disassembly.

## Production Fix

- Spring teardown now clears `partnerPos` and `partnerSubLevel` on both endpoint
  block entities before destroying the paired endpoint.
- Spring teardown emits one-shot `SABLE_M23_TEARDOWN` diagnostics with endpoint
  states, owner/partner Sable ids, handle validity, actor registration state,
  and the exact cleanup operations performed.
- `SubLevelAssemblyHelper.moveBlocks` now aborts before destructive source
  cleanup if the destination copy phase records any failure or copies fewer
  states than the source block count.
- Simulated disassembly now snapshots the body before disassembly and before
  source sublevel removal, wraps strict move failures as
  `DISASSEMBLY_BLOCKED moveBlocksFailed=...`, and refuses disassembly if the
  stored source block set is already smaller than the assembler's recorded
  assembled block count.
- Assembly no longer returns an unexplained null for known failure stages.
  `SABLE_M23_REASSEMBLY_FAILURE` reports the first null owner/stage.

## Diagnostics

`SABLE_M23_BODY_SNAPSHOT` reports:

`sableId`, `phase`, `storedBlockCount`, `storedBlockEntityCount`, `rawBounds`,
`logicalPose`, `assemblerPresent`, `assemblerRawPos`, `springEndpointCount`,
`trackingPointCount`, `actorCount`, `expectedPayloadBlockCount`,
`actualPayloadBlockCount`, and `blockSetSha256`.

`/sable m23 fixture spring teardown` creates two asymmetric parent-world M22
structures. The user still assembles both bodies and connects the Spring through
normal gameplay.

No new constraint family and no new production mixin is introduced.
