# M42.2: Live Sliding Door piston ownership across outer assembly

Target: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8, Flywheel 1.0.5.
Create commit: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
M42 only. M28-M40 remain accepted; M41 functional coverage stays OPEN. No M43.

## Exact controller and original ordering

The actual BUILD code uses `create:sticky_mechanical_piston`, FACING EAST, with a
`MechanicalPistonBlockEntity`. Its native entity is `ControlledContraptionEntity`
holding `PistonContraption`, not a bearing or gantry.

The moving payload is 12 native poles, the generated sticky head, one radial
chassis, one chest and both Andesite Sliding Door halves: 17 captured blocks.
The original attachment fix's full block map is unchanged.

Original setup called `new PistonContraption(EAST,false).assemble(...)` only for a
dry validation. That collects data but does NOT create a live entity. Setup then
immediately granted READY. A real live entity could start on subsequent kinetic
ticks, independently of that validation. A powered lever was placed using update
flags 2; `setFixtureLever(true)` returns without a neighbor update if already true.
Consequently READY was not evidence that the clutch/controller was stopped or
that a live native entity was being retained. A clutch BE reporting -32 describes
its kinetic side; it alone does not prove output-side controller speed.

Native live creation call graph, source and packaged bytecode audited:

```text
CreativeMotor speed / clutch transmission -> LinearActuatorBlockEntity.onSpeedChanged
 -> assembleNextTick -> LinearActuatorBlockEntity.tick
 -> MechanicalPistonBlockEntity.assemble
 -> PistonContraption.assemble / startMoving
 -> removeBlocksFromWorld
 -> ControlledContraptionEntity.create(level, controller, native contraption)
 -> resetContraptionToOffset -> level.addFreshEntity
```

## Proven missing preservation boundary

M28/M40 use `SableNestedBearingOwnershipTransfer`, called by
`SimAssemblyHelper` and `SubLevelAssemblyHelper`:

```text
begin -> capture -> bindTransform -> afterBlockTransfer
 -> beforeSourceDestruction -> afterBlockMove -> finish
```

Its original capture loop explicitly selected only
`MechanicalBearingBlockEntity`, and its snapshots specifically required
`BearingContraption`. A live piston could not produce a bearing snapshot, however
correct its chassis or door topology. This explains the absent preservation
marker; increasing the selected-block count cannot repair it.

Create `SmartBlockEntity.setRemoved()` calls `remove()` when not chunk-unloading.
`LinearActuatorBlockEntity.remove()` sets its removal flag and calls the virtual
`disassemble()` on the server. `MechanicalPistonBlockEntity.disassemble()` invokes
the old entity's native disassembly, clears `running` and `movedContraption`, and
can restore/drop its payload in the old world. Controller NBT contains Running,
Offset, Waiting and ExtensionLength, but not the live Java entity association.
Copying that NBT alone cannot preserve the live vehicle.

Root cause classification: **F, unsupported live controller family in the existing
ownership-transfer path**, combined with a fixture that did not require a live,
paused payload before READY. Native restoration/discard is the source-proven
failure path, not a Sliding Door callback defect.

The supplied old runtime contains no pre/post entity UUID, controller snapshot or
block census. It proves the clutch survived and the visible mechanism was lost;
it cannot individually prove where every old door/chest block ended up, or exclude
native end-of-travel disassembly before the click. This report does not invent
those observations. The new identity diagnostics distinguish them explicitly.

## Static selection is not entity ownership

Before live assembly the 23 static BUILD blocks are controller + 12 poles + four
payload blocks + motor/clutch/lever/two iron blocks/assembler. Once the native
piston is live, poles and payload leave world storage. Only seven static blocks
remain: piston controller, motor, clutch, lever, two iron blocks and assembler.
The generated head belongs to the entity at the controller's logical cell; it is
not a second world block/controller BE.

READY now requires an actual native live 17-block payload, a zero-speed pause with
MOVE_NEVER_PLACE, three settled ticks, and exact seven-block outer static selection.
Motor speed zero makes the pause independent of a stale lever/clutch update.
No fixture reconstruction of the payload occurs.

## Production repair

`SableNestedPistonOwnershipTransfer` is a narrow controller adapter within the
existing transaction, not another fixture serializer. It activates only for a
selected Mechanical Piston with a live native PistonContraption containing a
SlidingDoorBlock actor. Non-piston and non-door captures retain the old paths.
Ordinary Create outside outer Sable transfer never calls the adapter.

The existing transaction delegates capture, transform validation, destination
restore, pre-commit validation, source commit, verification and rollback. The
bearing algorithm is unchanged; an empty piston session has no effects.

The adapter:

1. Validates native controller/entity identity, ownership, liveness and no passengers.
2. Rejects overlapping static/payload ownership, except Create's generated head at
   the controller cell.
3. Saves the original native `PistonContraption.writeNBT(false)` data. Create owns
   block entities, actors, actor Data and mounted storage; no stack-copy algorithm.
4. Reconstructs via native `PistonContraption.readNBT`, translates its anchor,
   entity position and any persisted actor position, preserving local block and
   storage coordinates. Native offset/motion/controller NBT are retained.
5. Checks exact block state/NBT, actor count/Data, mounted-storage NBT, offset and
   native controller fields before publishing the new entity.
6. Publishes controller association before `addFreshEntity`, then uses native
   `attach`. Parent-level/Sable ownership and controller association are verified.
7. Only after every destination is ready, detaches the old controller association
   and discards the source CCE BEFORE source BE removal. Native disassembly cannot
   resurrect the old payload during source destruction.
8. On failure before commit, detaches/discards replacements while preserving the
   original entity/controller; the existing outer transaction rolls back its body.

Like M28, reconstruction creates a NEW entity UUID/id, not the same Java instance.
That is expected ENTITY_REPLACED with preserved native payload, not ENTITY_LOST.
No new packet, renderer or operation wrapper is introduced.

This adapter explicitly supports translation-only block ownership transfer.
Rotated outer disassembly while the piston remains live refuses before copying;
the transaction is finished/cleared on that refusal. Fixture cleanup retracts the
inner piston and resets outer yaw first. Outer physical pose rotation at runtime
is still composed by the unchanged Sable/Create transform architecture.

## Identity observation and RUN

READY prints `INNER_PRE_OUTER_ASSEMBLY` with controller state/BE/running/speed,
entity UUID/id/class/Level/removal state, full payload count and chassis/chest/door
presence. UUID/id are persisted as observations, not an indefinite Entity reference.
After outer assembly the same semantic resolver uses controller association,
controller position and Sable owner, then automatically prints
`INNER_POST_OUTER_ASSEMBLY`, original/resolved UUIDs, same-instance status and an
exact payload check. This is the preservation gate, not the door-mutation PASS.

New fixtures put an M42 ownership UUID in the assembler's Forge persistent data;
native BE capture carries it. Known-body lookup no longer requires that the lost
door still be present. A uniquely matching old fixture can use marker/origin,
the deterministic controller signature and exact body footprint. Multiple matches
refuse, other fixture ownership excludes a candidate, and no nearest-body/entity
ID adoption is used. Missing-payload bodies are ASSEMBLED_RECOVERABLE.

`run` now always logs/broadcasts `RUN phase=PRECHECK` before lookup and reports
ownership, controller state/BE/speed, inner entity and door payload. Missing entity
is explicitly `RUN BLOCKED reason=INNER_CONTRAPTION_MISSING`; failed ownership,
preflight and pending lifecycle each have separate reasons. Previously `sendFailure`
alone could be invisible to a function command source; refusals now also use the
M42 logger/player diagnostics.

After preservation succeeds, RUN seeds the existing fixture-only native close
trial. It does not modify the production Sliding Door state machine. The native
actor remains responsible for the parent-door mutation. Yaw is not part of the
first preservation runtime check.

## Old partial state and restore blockers

The two reported occupied positions `(-47,96,67)` and `(-47,96,68)` came from a
later M22 restore attempt. The supplied log does NOT include their BlockStates or
BEs; neither their contents nor fixture ownership can honestly be inferred from
coordinates alone. They are not blamed for the earlier entity loss.

Cleanup now logs each occupied current-pose restore cell with state, BE and the
block being restored, then re-anchors the owned body to its original reserved
origin. Thus a fallen body's collision with a test platform need not be solved by
deleting the platform. No absolute blocked coordinates are hardcoded.

At the original origin, unrelated/unproven blockers are preserved with exact
diagnostics. Only exact deterministic matching fixture residue is removable;
payload BEs additionally require the complete matching four-block payload
signature and an empty chest. State/NBT snapshots are restored if native outer
disassembly does not complete. Global M22 safety is unchanged.

A legacy controller with no resolved live entity can be reset in fixture-only
cleanup, without rebuilding missing payload data. Cleanup then uses native outer
disassembly and removes only the deterministic static footprint/owned station and
glue. It re-resolves the world after removal, remains retryable, and is idempotent.
Unknown external residue is not deleted merely because it is near the fixture.
M22 recreates transferred glue from bounds rather than preserving entity tags.
After confirmed native outer disassembly, cleanup also recognizes only the exact
known fixture glue bounds; it does not delete arbitrary nearby glue. Failed
native outer disassembly ends the task with a retryable error rather than
repeatedly retrying each tick. PREPARE failures schedule safe native rollback.

## First runtime only

```mcfunction
/function sable:m42/cleanup
/function sable:m42/door
```

Wait for live preparation and:

```text
[M42] SETUP phase=READY staticOuterBlocks=7 livePayloadBlocks=17
[M42] INNER_PRE_OUTER_ASSEMBLY ... payloadPreservedCandidate=true decision=PASS
```

Click Physics Assembler once, then WAIT. Expected:

```text
SABLE_M42_NESTED_PISTON event=SNAPSHOT_CREATED ... serializer=NATIVE_CREATE
SABLE_M42_NESTED_PISTON event=DESTINATION_REGISTERED ... actorsPreserved=true storagePreserved=true
[M42] INNER_POST_OUTER_ASSEMBLY ... controllerPresent=true innerContraptionPresent=true doorActorPresent=true payloadPreserved=true decision=PASS
```

Visually confirm the mechanism still exists and STOP. Only afterward may the user
run `/function sable:m42/run`; do not test yaw before baseline mutation passes.

## Verification and artifact

PASS: spotlessApply, spotlessCheck, compileJava,
verifyM42SlidingDoorParentWorld, verifyCreateMovementBehaviourCoverage, all
retained M31-M40 gates (including M39RuntimeMixinTargets and Roller rendering),
retained rendering/mixin gates, verifySableProductionBaseline, reobfJar, jarJar,
reobfJarJar, verifyStandaloneForgeArtifact, full build and git diff --check.
Final full chain: BUILD SUCCESSFUL in 1m 19s, 278 actionable tasks
(230 executed, 48 up-to-date).

The focused verifier adds pinned native actuator/controller/MovementContext
bytecode fingerprints, live-controller/paused-READY source contracts, transaction
integration/rollback guards, native storage/actor preservation canaries, exact
seven-static/17-captured topology and translated-anchor/offset regression math.
These are static tests, not a claim of running a native Level or Minecraft.
The existing packaged mixin-target manifest still passes without modification.

Packaged Sable class comparison: 910 before, 913 after. Fifteen existing class
files changed: eight M42 fixture files (commands and nested classes/layout) and
seven ownership-transaction files (owner/TransferSession plus nested records whose
source line tables shift). Three files were added: the piston adapter, its native
snapshot holder, and fixture Residue record. No class was removed.

All five frozen Sliding Door production classes are byte-identical to the prior
artifact: SlidingDoorMovementBehaviourMixin and SableSlidingDoorTarget with its
Decision/Evidence/Geometry nested types. No rendering, Plough, Drill, Saw, Blaze
Burner, M38 or other closed actor implementation was edited.

The aggregate hash gate explicitly accounts for the new piston adapter and its
transaction integration, retaining the existing forbidden-change/removal checks.
Functional coverage still has four residual rows / three families and is OPEN.

Artifact:

```text
forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar
bytes: 12912424
SHA-256: 5EFD4149D15C86B87BDED57A9EDD1BFFC7E37421EC17C514824A5EB5B78CE6B7
```

Changed files for M42.2, relative to the prior accepted setup-repair workspace:

- common/src/main/java/dev/ryanhcode/sable/compatibility/create/contraptions/SableNestedPistonOwnershipTransfer.java
- common/src/main/java/dev/ryanhcode/sable/compatibility/create/contraptions/SableNestedBearingOwnershipTransfer.java
- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureCommands.java
- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureLayout.java
- forge/src/m42SlidingDoorTest/java/dev/ryanhcode/sable/compatibility/create/sliding_door/M42SlidingDoorGeometryTest.java
- forge/build.gradle
- M42_2_NESTED_PISTON_PRESERVATION.md

Pre-existing uncommitted M41/M42 work remains in the workspace. Minecraft was not
launched. M42.2 is IMPLEMENTED / static PASS / preservation runtime pending;
Sliding Door mutation remains runtime pending; M42 OPEN. No M43 started.
