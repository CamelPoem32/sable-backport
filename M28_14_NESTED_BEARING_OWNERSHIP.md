# M28.14 Nested Mechanical Bearing Ownership

## M28.14.1 capture hotfix

Create permits a selected Mechanical Bearing to have no current kinetic network. The original M28.14
capture gate passed that nullable `bearing.network` directly to an immutable steering-network set,
whose `contains(null)` contract throws. M28.14.1 treats a null network as no steering-network match
for a bearing without a live nested contraption. A live nested contraption remains authoritative and
is snapshotted before that optional association is considered.

Capture validation remains read-only. The transfer session is published only after validation passes,
and all source ownership mutation remains in the later commit phase after destination reconstruction.

## Proven selection boundary

`SimAssemblyContraption.moveBlock` only admits a neighboring block to the outer Sable traversal frontier when
the pair has glue, Create reports an attachment toward the shared face, or both block states mutually stick.
The independent Mechanical Bearing payload has none of those links. The physical sail immediately in front of
the bearing is therefore rejected as `REJECTED_NO_ATTACHMENT_PATH` before `moveBlock` ever evaluates it as a
frontier candidate. This is intentional topology: adding glue would let the nested bearing traverse into the
stationary hull.

## Ownership architecture

M28.14 preserves nested bearings in the existing M22 block-transfer transaction:

1. Before outer assembly or disassembly, every selected Mechanical Bearing snapshots either its live
   `BearingContraption` or the exact Create-discovered physical payload immediately in front of a disassembled
   bearing.
2. The snapshot stores Create's full server contraption NBT, exact local block/state/block-entity-NBT map,
   controller-facing anchor, angle, movement mode, and remaining sequenced travel.
3. A payload snapshot must be disjoint from the selected outer hull. Any overlap aborts the transfer instead of
   expanding the nested payload into the aircraft.
4. After the destination bearing block entity is loaded, the snapshot is read into a new `BearingContraption`.
   Its exact saved block set is remapped through M22's source-to-destination transform. Destination structure
   traversal is never rerun.
5. A new `ControlledContraptionEntity` is registered and attached. Its exact set, states, block-entity NBT,
   angle, movement mode, and registration are validated before source ownership is released.
6. Source payload blocks or the source nested entity are removed only after every destination reconstruction
   succeeds. Every captured block must then have exactly one owner: nested contraption only.
7. The same path runs in reverse during outer disassembly, and a next-tick check verifies registration survival.

## Bearing top

Exact Create 6.0.8 constructs `BearingVisual.topInstance` at `getVisualPosition()` and `beginFrame` updates only
its rotation. For a Sable-contained bearing that initial position is the hidden plot coordinate, while
`BearingRenderer` declines its CPU top whenever visualization is supported. M28.14 returns visualization support
as false only for that bearing's Sable-contained BER invocation. Create's own `BEARING_TOP` CPU path then renders
inside the established small visible Sable pose. Normal-world Flywheel behavior is unchanged, and no hidden plot
translation is introduced into a visible matrix.

Runtime validation remains required for the assembled aircraft, reverse disassembly, and visible bearing top.
