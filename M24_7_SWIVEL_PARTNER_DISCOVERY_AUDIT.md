# M24.7 Swivel Partner Discovery Audit

Frozen Simulated `9e60263` does not discover Swivel partners by globally scanning for the first compatible family endpoint. `SwivelBearingBlock.beforeMove` marks the bearing as assembling, and `SwivelBearingBlock.afterMove` calls `SwivelBearingBlockEntity.associatePlateWithParent` after movement. The bearing persists `SwivelPlate` and `SubLevelID`, and the link/plate persists `ParentPos` and `ParentSubLevelId`.

The upstream backend handle is owned by `SwivelBearingBlockEntity`. `SwivelBearingPlateBlockEntity` delegates physics ticks back to its stored parent bearing through the parent position; it is not an independent global endpoint candidate. Constraint creation uses the bearing block facing, the stored plate block facing, and the stored plate position. If the containing sublevel and plate sublevel are the same, upstream does not create the handle.

The target M24 generic implementation had widened discovery to a global search across all loaded sublevels for any compatible `M24PhysicalBlockEntity`. That allowed a fresh Swivel bearing to pair with an old loaded link from a previous fixture before fresh body B was assembled. The resulting stale logical pairs repeatedly reached frame validation with visible endpoint errors around 1 to 11 blocks and were correctly rejected, leaving the fresh fixture bodies unconstrained.

M24.7 restores the important production boundary without using fixture UUIDs: Swivel discovery now only commits a logical pair when the candidate is an actual bearing/link pair, their facings oppose, and their reconstructed visible backend anchors are already coincident within a tight upstream-style topology tolerance. Candidate diagnostics are emitted as `SABLE_M24_SWIVEL_PAIR_DISCOVERY` before pair state is written.

Fixture UUIDs remain test-harness metadata only. They can verify that the selected partner is the fresh fixture B body, but production pairing does not depend on them.
