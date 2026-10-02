# M38 moving Dropper and Dispenser

Target: Create 6.0.8 (`1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`), Minecraft 1.20.1, Forge 47.4.20.

## Fixture lifecycle repair (v2)

The v1 failure was dispatch ordering, not a shifted origin. Minecraft 1.20.1 `ServerFunctionManager.execute` calls `ExecutionContext.delayFunctionCall` when a function execution context already exists. The public fixture function called Java setup, which invoked a second function and immediately searched for its marker. Marker lookup and rollback therefore ran before the queued builder. The queued builder subsequently left an untyped v1 marker and base geometry. The actor placement, kind tag, native Java glue creation, and validation were never reached. The old cleanup returned CORRUPT on the absent kind tag before looking for the assembled body.

Setup now selects a loaded origin, creates/verifies its tagged ArmorStand synchronously, then places the shared 22-block Java map synchronously. Native chassis inclusion must contain actor and chest. Outer glue is constructed with Create's `SuperGlueEntity(Level,AABB)`, not guessed summon NBT, and native outer traversal must select all 22 blocks. READY identity is published only after validation. Failed setup scans the structural footprint and glue after rollback; residue retains ownership and is reported INCOMPLETE.

One lookup is shared by setup/run/yaw/cleanup: NONE, STATIC_VALID, STATIC_PARTIAL, ASSEMBLED_VALID, ASSEMBLED_RECOVERABLE, ORPHAN_MARKER, CORRUPT_AMBIGUOUS. Legacy untyped markers are cleanup-recoverable. Markerless migration is limited to the observed previous origin `(-31,101,69)` plus the deterministic controller/pole/assembler signature; multiple matching bodies are refused. Moving poles are recognized in the native PistonContraption, not interpreted as missing structure. Recovery never makes a pre-READY manual assembly runnable.

Cleanup inspects the current native restore destinations, including state, BE and intended source block. It does not assume the reported cells `(-32,96,67)` and `(-32,96,68)` contain fixture blocks. The identified test body is returned to its original reserved origin before native outer disassembly, preserving unknown ground at the fallen pose. Only duplicate deterministic M38 blocks at the original restore footprint may be snapshotted/removed; unrelated occupants refuse cleanup. On a failure while the source body survives, those snapshots are restored. No M22 disassembly or production physics safety rule is changed.

## Exact native call paths

`AbstractContraptionEntity.tickActors` visits an actor's next cell. Create's `DropperMovementBehaviour.visitNewPosition` checks the server side, obtains `MovementContext.getItemStorage()`, chooses a slot with `getSlot` (including native aggregate-storage top-off), copies that stack, obtains its `MountedDispenseBehavior`, calls `dispense(stack, context, pos)`, and writes the returned stack with `MountedItemStorage.setStackInSlot`. `DispenserMovementBehaviour` inherits this method and only overrides `getDispenseBehavior`: its registered mounted behavior is used, otherwise `DefaultMountedDispenseBehavior.INSTANCE`.

For ordinary dropped items, `DefaultMountedDispenseBehavior` computes a normal from the actor state and `context.rotation`, checks the adjacent container through `HopperBlockEntity.getContainerAt(context.world, front)`, then runs native `execute`/`spawnItem`. The item entity is constructed in `context.world`, at the cell/normal-derived mouth; its motion includes `context.motion`. For arrows, `MountedProjectileDispenseBehavior.execute` constructs the native projectile, shoots using its computed normal and `context.motion`, and adds it to `context.world`. Create owns slot choice, count changes, randomness, sounds and level events.

For other dispenser items, Create's `DispenserBehaviorConverter` may wrap a vanilla `DispenseItemBehavior` in `FallbackBehavior`. That wrapper constructs `ContraptionBlockSource(context,pos,facing)`, where `getPos` and `x/y/z` derive from `pos`, `getBlockState` substitutes the facing, `getLevel` returns the parent server level, and `getEntity` is null. It invokes the original vanilla behavior object. M38 allows only the audited flint-and-steel fallback; it does not reinterpret vanilla behaviors or add a parallel item catalog.

## Compatibility boundary

For a nested Sable actor, Create's raw actor position is in its hidden plot while `context.world` is the parent level. Passing the raw `BlockPos` to mounted behavior code can therefore query, spawn, mutate, and play events at the wrong parent-world cell. M38's `DropperMovementBehaviourMixin` activates only for a server-side native moving actor with a Sable owner. `SableMountedDispenserTarget` resolves the actor's current raw point and rotated local facing through the current logical Sable pose into a loaded parent-visible source cell and a near-cardinal physical facing. It rejects hidden plots, other Sable bodies, and unsupported orientations. A scoped `SableCreateActorWorldContext.ParentInteractionScope` temporarily presents parent-visible position, motion, relative motion, and rotation to the original Create method, then restores every raw field in `close`.

Create's original mounted behavior receives the transformed `BlockPos`. No global `BlockSource`, `Level`, entity-spawn, or block-mutation redirect exists. Ordinary world actors pass directly to Create. The per-actor visible-cell marker also permits the interaction to follow Sable translation/yaw even if its hidden plot cell did not change.

The supported envelope is level translation plus near-cardinal yaw, using the established `SablePortableInterfaceTarget.resolveGeometry` thresholds: transformed world-up dot Y must exceed `0.999`; transformed facing's squared error from a cardinal axis must not exceed `0.5`. Material pitch/roll is a safe no-op. This is intentionally not arbitrary 3-D dispensing.

## Behavior families

| Family | M38 status | Exact path/reason |
|---|---|---|
| Dropper default item emission | Implemented, runtime pending | Native `DefaultMountedDispenseBehavior.spawnItem`; parent cell and physical motion. |
| Dispenser default item | Implemented, runtime pending | Same native default mounted behavior; adjacent parent inventory remains a native decision. |
| Arrow projectile | Implemented, runtime pending | Native `MountedProjectileDispenseBehavior`; transformed normal and motion. |
| Flint-and-steel ignition | Implemented, runtime pending | Create's `FallbackBehavior` invokes the original vanilla behavior with its `ContraptionBlockSource`. |
| Spawn eggs/entity creation | Out of scope | Create has distinct registered mounted behavior; not audited for this bridge's effects. |
| Buckets, bottles, fluid pickup/place | Out of scope | Multiple block/fluid and container replacement paths need separate evidence. |
| Bonemeal, TNT, firework/fire charge | Out of scope | Distinct fallback/registered effects; not enabled by M38. |
| Armor/equipment, saddles, shears | Out of scope | Entity-interaction and null `BlockSource.getEntity` semantics need individual audit. |
| Boats/minecarts, block placement, other special behavior | Out of scope | Distinct placement and entity-space rules not demonstrated by the baseline. |

Unsupported behavior is skipped for nested Sable actors rather than falling back to the hidden plot. This does not change ordinary Create dispensing. Same-Sable mounted inventory is native; cross-Sable physical block targets are rejected. No new rendering hook is present: Dropper and Dispenser remain ordinary contraption blocks in the existing Sable CPU-visible bridge, with no plot-scale PoseStack translation.

## Runtime acceptance

The M38 fixture uses the M37-proven radial-chassis/piston/assembler topology. It places an actor above the chassis and a chest on the chassis' sticky south side; Create's native chassis inclusion is checked before `READY`. The tagged marker owns the fixture. Setup is transactional; cleanup reverses the Creative Motor for native piston retraction, disassembles the outer Sable, and removes only known fixture blocks and tagged output entities. Output entity observation is read-only and scoped to the tagged run. `yaw90` only applies to the arrow fixture and refills its restored actor inventory after native retraction.

Placement reserves the actual block map and moving/emission sweep, not a `31 x 8 x 33` air cube. A bounded, deterministic 27-candidate search tries nearby loaded origins and up to four additional blocks of height without clearing existing world blocks. Candidate inspection distinguishes unloaded chunks, occupied blocks, block entities, and existing Sable volumes. Ordinary ground at relative `y<=-3` remains untouched. No candidate means no BUILD and no partial fixture. See `M38_RUNTIME_TEST_COMMANDS.md` for the complete relative footprint.

Static M38 verification does not establish runtime success. Require an actual parent-world item, arrow, or fire change and native mounted inventory delta in Minecraft before closing each gate.
