# M40: Basin parent-world dropped items

Status before Minecraft acceptance: IMPLEMENTED / runtime pending. M28-M39 remain
CLOSED / PASS by the accepted project runtime history. No M41 work is included.

This is the original M40 output audit and historical build report. The current
fixture lifecycle, kinetic repair, verification results and artifact fingerprint
are in [M40.1 kinetic fixture repair](M40_1_KINETIC_FIXTURE_REPAIR.md).

## Exact target and audit

Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8, Flywheel 1.0.5.
Create source commit: `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.
The verifier inspects the original installed Create jar with ASM, not a newer
branch or inferred source strings. It also checks the final reobfuscated Mixin,
refmap, configuration and function entries in the standalone artifact.

Primary source:
[BasinMovementBehaviour](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/processing/basin/BasinMovementBehaviour.java).

`AllBlocks.BASIN` registers `new BasinMovementBehaviour()`.

```text
AbstractContraptionEntity.tickActors
  -> compute actor center using toGlobalVector / applyRotation
  -> assign MovementContext.position and rotation
  -> BasinMovementBehaviour.tick
     -> MovementBehaviour.super.tick
     -> if temporaryData is null or true:
        rotate local UP; native Direction.getNearest must be DOWN
        -> dump
           -> getOrReadInventory: InputItems / OutputItems from captured BE NBT
           -> lambda$dump$1, each nonempty slot
              -> new ItemEntity(context.world, context.position.x/y/z, native stack)
              -> setDeltaMovement(rotatedUp.scale(0.05))
              -> context.world.addFreshEntity(item)
              -> clear that native handler slot
              -> serialize handler back into context.blockEntityData
           -> update client Basin BE read-only item data, if present
           -> temporaryData = false
```

This is a one-shot **inversion dump of both input and output inventory**, not
Mixer/Press processing. `startMoving`, `visitNewPosition` and `stopMoving` do not
perform Basin dumps. A translating upright piston cannot exercise this callback's
dump branch, so the test uses a native horizontal Mechanical Bearing.

The synthetic static output lambda has descriptor:

```text
(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;
 Lnet/minecraft/world/phys/Vec3;Ljava/lang/String;
 Lnet/minecraftforge/items/ItemStackHandler;)V
```

The exact spawn operation is `Level.addFreshEntity(Entity):boolean`, runtime
SRG `m_7967_`. Its result and all native inventory writes are preserved.

## Revalidated M34 gap

M34's dropped-output positioning diagnosis was correct but incomplete. The
Minecraft Level backing a Sable-hosted Create actor is already the parent Level;
`SubLevel` is not a replacement Minecraft `Level`. Its actor position remains in
the hidden plot, and its rotation includes only the inner Create rotation.
Thus the original item would spawn in the parent Level at a hidden coordinate;
the original dump test/velocity would also omit the outer body rotation.

## Narrow compatibility boundary

Only `BasinMovementBehaviour.tick` is wrapped. The existing
`SableCreateActorWorldContext.owner` identifies the actual owning Sable from the
contraption anchor. No owner means the original callback is invoked unchanged.
With an owner, null/non-finite positions, incompatible Level identity, and a
visible destination inside any hidden plot are rejected without a raw fallback.

The existing auto-closing parent-interaction scope supplies:

```text
BASIN_LOCAL
 -> native inner toGlobalVector (SABLE_RAW)
 -> current owning Sable logicalPose.transformPosition (PARENT_VISIBLE)
 -> native ItemEntity position (PARENT_ENTITY_SPACE)

physical rotation(v) = outer.logicalPose.transformNormal(inner.rotation(v))
native initial velocity = physical rotation(UP) * 0.05
```

Position has zero output offset and no randomized spawn displacement. The native
constructor's initial random velocity is overwritten by Create's own velocity
setter. Native `Direction.getNearest(...)=DOWN` remains authoritative; M40 adds
no new cardinal quantization or arbitrary orientation threshold. Outer pitch/roll
is composed rather than discarded, because this behavior outputs a continuous
position/vector and does not inspect a directional block grid.

The scope restores raw position, motion and rotation after the callback even on
failure. Native NBT writes and the one-shot flag are not restored or fabricated.
The output callsite wrapper invokes the original operation first, then records
read-only evidence. It does not create entities, copy stacks or change counts.
The scope and all prior M31-M39 production code remain unchanged.

## Inventory and fluids

Basin BE item capability is `CombinedInvWrapper(inputInventory, outputInventory)`.
Create's fallback mounted storage accepts the exact `ItemStackHandler` class, not
this combined wrapper. Basin movement uses captured `InputItems`/`OutputItems`
NBT and its own lazily read handlers, not a parallel mounted Sable inventory.

Each nonempty slot becomes one ItemEntity carrying the whole stack. The native
slot clear and NBT serialization ensure inner disassembly restores an emptied
Basin; no replacement items are generated during restoration. Cleanup before
dump preserves native return-to-home/disassembly and deletes only the fixture's
explicitly seeded test contents before removing the static test Basin.

Fluid classification: **NO_MOVEMENT_OUTPUT**. The movement callback does not read
or spill fluid tanks. Tank NBT remains captured/restored natively. No M40 fluid
spilling, processing, stationary break drops or recipe buffer changes are added.

## Fixture geometry and ownership

Relative to controller origin O, all cells are placed synchronously by Java:

| Offset | Block / role |
|---|---|
| `(0,0,0)` | Mechanical Bearing, facing EAST |
| `(-2,0,0)` | Creative Motor, facing EAST, native speed 16 |
| `(-1,0,0)` | Clutch, axis X |
| `(-1,1,0)` | Lever, initially stops clutch |
| `(-2..0,0,-1)` | Three iron outer-hull cells |
| `(0,0,-2)` | Iron assembler starting block |
| `(0,1,-2)` | Physics Assembler |
| `(1,0,0)` | Radial chassis, axis X, sticky north/east |
| `(1,1,0)` | Basin, native attached rotor payload |
| `(-3..3,-4,-3..3)` | 49-cell parent-world catch platform |

The two-block inner rotor contains exactly chassis + Basin. Outer native glue
uses `SuperGlueEntity(Level,AABB)` and covers only the nine stationary controller/
hull cells, excluding the rotor and parent platform. No guessed glue summon NBT.
Setup evaluates native chassis inclusion, a native BearingContraption assembly
preview, and Simulated outer selection before READY.

Origin candidates reuse the deterministic M38 nearby search, elevated five blocks
above the player block. Exact build cells, two rotor planes and fall corridors
are reserved, under 150 cells in total, in loaded chunks only. Existing flat
terrain below the catch platform is not reserved. No broad air fills or terrain
overwrites are used. Marker creation and relocated-origin verification happen
before BUILD. Failure clears actually placed blocks and verifies the footprint;
an incomplete rollback retains ownership for cleanup.

One persistent tagged marker, origin/version/dimension/body identity and one
lookup govern setup, run, yaw, cleanup and the fixture-only physics hold. Lookup
statuses: NONE, STATIC, ASSEMBLED, PARTIAL, ORPHAN, AMBIGUOUS. The hold applies
only to the uniquely owned READY M40 body. Normal Sable physics is unchanged.

## Lifecycle and evidence

RUN: CAPTURE -> OUTPUT -> STOP -> RETURN -> native inner disassembly -> summary.
The native bearing uses NEVER_PLACE and stops after the first dump; the fixture
returns it toward its initial angle before native disassembly. The output must
occur at a nonzero angle greater than 45 degrees. The fixture never sets an
angle, constructs an ItemEntity or calls a custom dump.

Payload: input 4 cobblestone + output 2 andesite; expected two native entity
UUIDs totaling six items, native counts 6 -> 0, one-shot flag false, position
error <= 1e-6 blocks and velocity error <= 1e-9. PASS also requires restored
inventories empty, proving the emitted contents are not restored a second time.

Cleanup: STOP -> RETURN -> OUTER_DISASSEMBLE -> re-resolve/STOP/RETURN any native
rotor recreated by outer ownership transfer -> REMOVE -> verified footprint.
No moving Create entity is directly deleted. Unrelated restore/static blockers
are preserved and reported. Failed/time-limited cleanup drops only asynchronous
task state; persistent ownership remains retryable after restart.

Output evidence is weak-context-keyed and bounded to 32 entries. Fixture-owned
output UUIDs receive ownership tags only after the real native output occurs.
Cleanup removes only that marker's tagged items/glue. It does not kill arbitrary
nearby entities. Two native empty-after-output handlers prevent re-output during
normal inner/outer restoration.

Yaw helper returns the rotor natively, validates the new physical corridor,
sets a level +90-degree outer pose and refills only the owned static TEST Basin.
If pose/refill fails, prior yaw and Basin NBT are restored. The symmetric platform
stays in the parent world. A second run exercises transformed native output.

## Rendering, safety and verification

No rendering changes. All transforms are vector math; M40 introduces no PoseStack
or giant translation/cancellation. Normal-world callback and spawn operation
remain native. Cross-Sable targeting never searches nearby plots: the actual
owning body is used, and hidden physical destinations are rejected.

Diagnostics use `-Dsable.m31.traceCreateActors=true`: OUTPUT_ATTEMPT, ITEM_SPAWN,
OUTPUT_COMMITTED. They are one-shot native events, not per-frame/tick spam.
Fixture chat records capture, exact output points/velocities, confirmation and
the final machine-derived PASS/INCOMPLETE result.

`verifyM40BasinParentWorldOutput` checks exact original bytecode, stack mutation
order, one-shot flag, registration, final mixin/refmap/function packaging,
normal-world bypass and no custom output implementation. Executable deterministic
tests cover local/inner/outer composition, yaw, inversion, velocity, null/NaN,
glue exclusion, footprint size and rotor/platform clearance. Previous gates are
retained. Runtime acceptance still requires real Minecraft output and cleanup.

## Completed build verification

All requested static/release gates passed on 2026-10-05:

- `spotlessApply`, `spotlessCheck`, `:forge:compileJava`.
- `:forge:verifyM40BasinParentWorldOutput`.
- M39 Seat + runtime mixin target gates; M38, M37, M36 gates.
- Both M35 mechanics and moving rendering gates; M34 coverage.
- M33 and M33.1, M32, M31, production baseline.
- `reobfJar`, `jarJar`, `reobfJarJar`, `verifyStandaloneForgeArtifact`.
- Full `build`: successful, 275 tasks (248 executed, 27 up-to-date).
- `git diff --check`: passed (existing Windows line-ending notices only).

An additional final standalone-artifact invocation passed after full build.
All 89 pre-existing packaged Create compatibility/mixin classes retained their
previous SHA-256 hashes. Only three M40 classes were added in those packages.
Minecraft was not launched; no M40 runtime PASS is claimed.

Artifact: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`.
Size: 12,843,175 bytes.
SHA-256: `0537645B7027C1C5AECFEB075A4DD9436BC5F249B4956EB55185A5ECE8C1336D`.

## Changed files

- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/basin/SableBasinOutput.java` (new).
- `common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/basin/BasinMovementBehaviourMixin.java` (new).
- `forge/src/main/java/dev/ryanhcode/sable/command/M40BasinFixtureCommands.java` (new).
- `forge/src/main/java/dev/ryanhcode/sable/command/M40BasinFixtureLayout.java` (new).
- `forge/src/m40BasinTest/java/dev/ryanhcode/sable/compatibility/create/basin/M40BasinOutputTest.java` (new).
- `forge/src/main/resources/data/sable/functions/m40/basin.mcfunction` (new).
- `forge/src/main/resources/data/sable/functions/m40/run.mcfunction` (new).
- `forge/src/main/resources/data/sable/functions/m40/yaw90.mcfunction` (new).
- `forge/src/main/resources/data/sable/functions/m40/cleanup.mcfunction` (new).
- `forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java` (M40 registration only).
- `forge/src/main/resources/sable-common-forge.mixins.json` (M40 config entry only).
- `forge/build.gradle` (M40 gate only; retained gates unchanged).
- `M40_BASIN_PARENT_WORLD_OUTPUT.md` (new).
- `M40_RUNTIME_TEST_COMMANDS.md` (new).
