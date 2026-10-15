# M42 fixture attachment validation repair

Scope: fixture setup/validation/tests only. No M43, production Sliding Door or
rendering change. Minecraft is not launched. Setup READY remains runtime pending.
Pinned Create: 6.0.8, commit `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

## Exact failure and diagnosis

The original check at lines 130-131 was:

```java
if (included == null || !included.contains(origin.offset(M42SlidingDoorFixtureLayout.LOWER))
        || !included.contains(origin.east().south())) throw new IllegalStateException("native chassis attachment incomplete " + included);
```

`included` came from `chassis.getIncludedBlockPositions(Direction.EAST, false)`.
The exception suffix was the ACTUAL included list, not the missing set.

At origin `(-43,101,67)`:

- Old expected range: lower door `(-42,102,67)` and chest `(-42,101,68)`.
- Actual range from runtime: `[(-42,101,68)]`.
- Missing under that incorrect expectation: lower door `(-42,102,67)`.
- Extra: none. The reported `origin+(1,0,1)` IS the chest, and WAS included.
- Second origin `(-46,103,67)` yields chest `(-45,103,68)` and the same result.

BUILD explicitly places the chest at `(1,0,1)` and the lower/upper door at
`(1,1,0)/(1,2,0)`. There is no later replacement of those blocks. No origin or
terrain defect is implicated. The past log does not expose every complete live
BlockState; the new diagnostic reports them rather than inventing runtime values.

Root-cause classification: **D**, wrong validation contract/expected set; more
precisely G's distinction between radial range inclusion and complete native
attachment. BUILD is not missing the chest. No chassis-face, range or door-facing
change is needed, and the two-half topology is retained.

## Exact native semantics

The source and packaged bytecode were inspected for:

- `ChassisBlockEntity.getIncludedBlockPositionsRadial`: skips axis-parallel faces,
  requires sticky sides, bounds search by `getRange()+0.5`, and skips brittle blocks
  before adding them. Packaged `isBrittle` call is at offset 257, followed by a
  branch back to the search loop at offset 263.
- `RadialChassisBlock.getGlueableSide`: for axis X, UP maps to `sticky_north`,
  SOUTH to `sticky_east`, DOWN to `sticky_south`, NORTH to `sticky_west`.
- `data/create/tags/blocks/brittle.json` includes `#minecraft:doors`; Create's
  `data/minecraft/tags/blocks/doors.json` includes the Sliding Door variants.
- `BlockMovementChecksImpl.isBlockAttachedTowardsFallback`: a DoorBlock LOWER
  attaches UP; either half attaches DOWN. Packaged DoorBlock branch: offsets
  134-174. It uses actual `DoorBlock` inheritance, not a fixture special case.
- `Contraption.moveBlock`: independently queries `isBlockAttachedTowards` at
  offset 837 and accepts that condition independently of generic brittle/sticky
  exclusion. Thus the lower door above the chassis attaches DOWN to it; the upper
  door attaches DOWN to the lower. These are native support links, not Super Glue.
- `PistonContraption.collectExtensions`: retracted piston has anchor one block
  east; captures 12 rear poles and generates a sticky head at the piston cell.
  Capture coordinates are converted back through that actual native anchor.

`SlidingDoorBlock extends DoorBlock`; it is a two-block vertical pair with
FACING/HALF/HINGE/OPEN/POWERED plus Create VISIBLE. LOWER can survive without a
solid support test; UPPER requires the same door below. `newBlockEntity` returns
null for UPPER and `SlidingDoorBlockEntity` for LOWER. Optional neighboring double
doors are not required and none is built here. No arbitrary glue is added.

## Complete relative block map

Positions below are relative to the selected origin. BUILD uses the exact state
strings below; unlisted properties retain native defaults. Lever control sets the
clutch to its stopped state during setup. No topology coordinate is changed.

| Offset | Role / BUILD state | Inner capture | Outer selection | Attachment |
|---|---|---|---|---|
| `(-12,0,0)` through `(-1,0,0)`, all 12 integer X cells | `create:piston_extension_pole[facing=east]` | Yes | Yes | Native piston pole chain |
| `(0,0,0)` | `create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted]` | Generated head at this cell, not the controller BE | Yes | Native controller/poles; outer glue |
| `(1,0,0)` | `create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]` | Yes | Yes | Native piston front |
| `(1,0,1)` | `minecraft:chest[facing=north,type=single,waterlogged=false]` | Yes | Yes | SOUTH sticky radial range |
| `(1,1,0)` | `create:andesite_door[facing=north,half=lower,hinge=left,open=true,powered=false]` | Yes | Yes | Native DoorBlock DOWN support, NOT radial range |
| `(1,2,0)` | `create:andesite_door[facing=north,half=upper,hinge=left,open=true,powered=false]` | Yes | Yes | Native DOWN link to lower |
| `(0,-2,0)` | `create:creative_motor[facing=up]` | No | Yes | Outer glue |
| `(0,-1,0)` | `create:clutch[axis=y,powered=false]` (control stops it) | No | Yes | Outer glue |
| `(0,-1,1)` | `minecraft:lever[face=wall,facing=south,powered=true]` | No | Yes | Outer glue / wall attachment |
| `(0,0,-2)` | `minecraft:iron_block` | No | Yes | Outer glue / assembler starting block |
| `(0,0,-1)` | `minecraft:iron_block` | No | Yes | Outer glue |
| `(0,1,-2)` | `simulated:physics_assembler` | No | Yes | Outer glue / assembly root |
| `(5,0,-4)` | Parent target support `minecraft:stone` | No | No | Intentionally stationary |
| `(5,1,-4)` | Parent `minecraft:iron_door[facing=north,half=lower,hinge=left,open=true,powered=false]` | No | No | Intentionally stationary |
| `(5,2,-4)` | Parent `minecraft:iron_door[facing=north,half=upper,hinge=left,open=true,powered=false]` | No | No | Intentionally stationary |

Marker: invisible/no-gravity ArmorStand, tag `sable_m42_origin`, center above the
origin cell; not a block or payload member. Existing outer SuperGlueEntity bounds:
`[0,-2,-2] -> [1,2,2]` relative to origin. Its X interval excludes the payload
column; it only joins the outer controller/support column. Native inner capture
validation now runs BEFORE that glue exists. No inner payload glue is used.

At SETUP READY the parked target remains separate. Existing later RUN relocation
is unchanged and is not exercised by this setup-only acceptance request.

## Repair and diagnostics

The existing `mechanism()` BUILD map remains authoritative. Derived views are:

1. `movingPayload()`: chassis, chest, lower and upper door from that map.
2. `chassisRangeExpected()`: the non-brittle chest, not the two door halves.
3. `capturedBlockIds()`: all 17 native captured block IDs, including native poles
   and synthetic head. No independent coordinate list is maintained by VALIDATE.

Before READY, the actual native `PistonContraption.assemble` must succeed and match
all 17 positions/IDs, no extras, both door actors, exact four BUILD payload states,
and the exact radial range set. A missing or wrong door/chest still rejects setup.
The assembler's coordinate normalization uses the actual `payload.anchor`.

Bounded `[M42] ATTACHMENT` prints origin, chassis position/state/range, complete
expected/actual capture sets, missing/extra, missing states, all four payload
states, wrong states/IDs, expected/actual radial sets, sticky-face mapping, glue
status, half count and VALID/REJECT decision. Exceptions name the differing sets.

Ordering:

```text
SELECT -> marker verified -> BUILD -> native ATTACHMENT -> VALIDATE
 -> outer glue/selection -> stationary target -> READY
```

Rollback retains the prior verified footprint scan and never prints READY after
failure. Marker/cleanup lifecycle, origin search, motion and target placement are
unchanged.

## Regression checks

The focused verifier adds exact hashes of six relevant pinned native classes and
the two door/brittle tag files. It retains all production bytecode/refmap checks.
The offline topology regression reconstructs BUILD plus the parked station, checks
the native range exclusion and support-attachment traversal contracts, recovers
the exact four payload blocks, normalizes the full 17-block capture and rejects
missing lower/upper halves. It reproduces why the old range check rejected a
valid attached lower door. It proves the parked station is not in the payload.

These are source/bytecode-backed offline tests, not a claim of a live Level/native
assembly test. Actual native assembly is the mandatory setup-time READY gate.

## First runtime acceptance

Only run:

```mcfunction
/function sable:m42/cleanup
/function sable:m42/door
```

Expected:

```text
[M42] SETUP phase=BUILD
[M42] ATTACHMENT ... missing=[] extra=[] ... chassisExpected=[chest] chassisActual=[chest] ... decision=NATIVE_CAPTURE_VALID
[M42] SETUP phase=VALIDATE
[M42] SETUP phase=READY
```

STOP at READY. Do not click Physics Assembler or run/yaw until this setup is
confirmed in Minecraft. M42 functionality is still NOT REACHED / OPEN.

## Verification and artifact

PASS: spotlessApply, spotlessCheck, compileJava,
verifyM42SlidingDoorParentWorld, verifyCreateMovementBehaviourCoverage,
verifyCreateProductionFixtureBoundary, every retained M31-M40 verifier, retained
rendering/mixin gates (including M39RuntimeMixinTargets),
verifySableProductionBaseline, reobfJar, jarJar, reobfJarJar,
verifyStandaloneForgeArtifact, full build and git diff --check. Full chain:
BUILD SUCCESSFUL in 2m 50s, 278 actionable tasks (251 executed, 27 up-to-date).
The focused topology regression and all eight new pinned attachment/tag canaries
passed. Aggregate coverage remains OPEN with the same four residual behavior rows
across Plough, Drill/Saw and Blaze Burner; none is modified here.

Before/after comparison of all 910 packaged Sable classes: only eight fixture
classes changed (M42SlidingDoorFixtureCommands and six nested/compiler-generated
classes, plus M42SlidingDoorFixtureLayout). No classes added or removed. All five
M42 production classes are byte-identical:

| Class under `dev/ryanhcode/sable/` | SHA-256 before = after |
|---|---|
| `compatibility/create/sliding_door/SableSlidingDoorTarget.class` | `E72515E5B099DF4C55798F0548953E1612938FD36CC045F7ACE978CA1904127A` |
| `compatibility/create/sliding_door/SableSlidingDoorTarget$Decision.class` | `706172B84EBC9889DF8DC9B731AA2FD293E4FAE5B73D06D6915060A31BD67D98` |
| `compatibility/create/sliding_door/SableSlidingDoorTarget$Evidence.class` | `585709A22F120156B8EA2ABB7DFE5488A14A1FD8B6826930FC9864B2FE139358` |
| `compatibility/create/sliding_door/SableSlidingDoorTarget$Geometry.class` | `78DC652C1F28095027C588B09DFF7F2BE38D9AA799AAF9C75438CB8E2974DEA1` |
| `mixin/compatibility/create/behaviour_compatibility/sliding_door/SlidingDoorMovementBehaviourMixin.class` | `2935501BFE785D1B8E73B36963138B1DBCA4A3386D6EC35647DAFA6B28C991BA` |

Artifact (replaces the original M42 report's historical artifact fingerprint):

```text
forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar
bytes: 12896355
SHA-256: CF0B7F37DC1E2F3930DECCC66CDD557BC999B2DCFEC72AC03C4C1EE7C588B239
```

Changed files for this repair, relative to the prior M42 artifact workspace:

- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureCommands.java
- forge/src/main/java/dev/ryanhcode/sable/command/M42SlidingDoorFixtureLayout.java
- forge/src/m42SlidingDoorTest/java/dev/ryanhcode/sable/compatibility/create/sliding_door/M42SlidingDoorGeometryTest.java
- forge/build.gradle
- M42_FIXTURE_ATTACHMENT_REPAIR.md

Pre-existing M41/M42 uncommitted files remain in the workspace; they are not new
production changes from this repair. No Minecraft launch or runtime PASS is
claimed. M42 setup repair is statically verified / runtime pending; M42 OPEN.
