# M40.1: native kinetic preflight and ownership repair

M40 only. M28-M39 remain closed. Basin output production source and bytecode are
frozen; the first M40 runtime never reached native dump. No M41 work.

## Proven boundary and evidence limits

The supplied runtime proves nine outer hull blocks assembled, clutch effective
speed 0, no native rotating Basin capture, and a later generic AMBIGUOUS result.
It does not report motor speed/network, bearing speed/network, or the branch that
returned AMBIGUOUS. Those values cannot honestly be reconstructed from a single
clutch renderer line. New diagnostics measure them on the next real runtime.

The source audit proves a fixture architecture error (classification G):
`NestedBearingCaptureDecision.evaluate(false, emptySteeringNetworks, ..., false)`
returns `SKIP_NO_SELECTED_STEERING_NETWORK`. M40 selected no steering wheel and
had stopped/disassembled its bearing; its two-block static payload was excluded
from the nine-block hull. The native attachment preview did not create a LIVE
rotor. Thus outer assembly could move the controller without moving its Basin or
chassis. Their normal-world rotation does not prove nested ownership.

The native kinetic NBT copy also preserves Source/network fields except BE x/y/z.
The original fixture's `generatedSpeed.setValue(16)` is unchanged from the
motor's default. Native ScrollValueBehaviour returns before its callback for
same-value writes. It cannot be relied on as a relocated network bootstrap.
This is a source-proven missing harness action, not proof of which numerical
upstream value occurred in that world. No general kinetic layer is added.

## Exact topology, unchanged layout

Origin O is the Mechanical Bearing. No shaft blocks are needed: three native
shaft sockets are directly adjacent on X.

| Offset | BlockState | BE | Ownership after outer assembly |
|---|---|---|---|
| `(-2,0,0)` | creative_motor, facing=east | CreativeMotorBlockEntity | outer hull, BE NBT copied |
| `(-1,0,0)` | clutch, axis=x, powered follows fixture stop | ClutchBlockEntity | outer hull, BE NBT copied |
| `(0,0,0)` | mechanical_bearing, facing=east | MechanicalBearingBlockEntity | outer hull, BE NBT copied |
| `(-1,1,0)` | lever, floor/east | none | outer hull |
| `(-2..0,0,-1)` | three iron blocks | none | outer hull |
| `(0,0,-2)` | iron block | none | outer hull / starting block |
| `(0,1,-2)` | physics_assembler | PhysicsAssemblerBlockEntity | outer hull |
| `(1,0,0)` | radial_chassis, axis=x, sticky_north/east | ChassisBlockEntity before capture | native inner rotor |
| `(1,1,0)` | basin, facing=down | BasinBlockEntity before capture | native inner rotor / actor NBT |
| `(-3..3,-4,-3..3)` | 49 stone platform cells | none | physical parent world |

Outer selection remains nine blocks; rotor selection is two separate native
blocks. Setup checks all three kinetic BE types, selected positions and serializable
NBT. Nested diagnostics use M35FixtureLookup's Sable-aware block/BE path, not an
assumption inferred from a count.

## Exact native lifecycle basis

Pinned Create 6.0.8 source and mapped/raw bytecode were inspected:

- SmartBlockEntity.tick calls initialize once on its first normal tick.
- KineticBlockEntity.tick calls attachKinetics when needsSpeedUpdate is true.
- attachKinetics clears that flag and calls RotationPropagator.handleAdded.
- KineticBlockEntity.read loads Speed, Source, Network unless warnOfMovement set
  the native movement flag. Sable's copy updates BE coordinates, not Source NBT.
- CreativeMotorBlockEntity.initialize and its generatedSpeed callback call
  updateGeneratedRotation. GeneratingKineticBlockEntity.applyNewSpeed creates a
  native network on a real 0 -> nonzero transition and attaches it.
- KineticBlockEntity.switchToBlockState is Create's public relocation/control
  reset: native removeSource/detach and generator reactivation, then normal state
  write. The fixture uses this on only its owned motor/clutch/bearing, without
  assigning speed, source, network, initialization flag, or editing their NBT.
- Clutch powered=true suppresses the output-side rotation modifier, not the
  input-side speed. WEST input modifier remains 1; EAST output modifier becomes
  0. Unpowered modifiers are both 1. Native reconnection propagates to the bearing.

The harness drives a real motor transition 0 -> 16, uses the public native reset
and native attachKinetics, and records effective/theoretical speed, source,
network, generated speed and initialized state. Private initialized and bearing
assembleNextTick fields are read-only bounded diagnostics, checked by ASM; they
are never written. Reflection unavailability is explicitly reported.

## Comparison with closed milestones

M28 static payload discovery is deliberately steering-network-only; a live rotor
uses the general SNAPSHOT_EXISTING path. M40 now uses that exact existing path.
M35/M39 native fixture motors undergo meaningful speed transitions and use the
existing lever/sublevel edit architecture. M40 reuses those world/BE routes and
native motor controls; it does not modify their code or global kinetic behavior.

## New state ordering

```text
BUILD / structural VALIDATE
 -> BASELINE: native drive + first native kinetic/BE tick
 -> live bearing CCE with exactly chassis+Basin, six items, near initial angle
 -> report KINETIC_BASELINE nonzero chain
 -> native powered clutch pauses rotor; NEVER_PLACE retains the live CCE
 -> READY
 -> user clicks outer Physics Assembler
 -> M28 SNAPSHOT_EXISTING transfers the live rotor with its controller
 -> automatic NESTED preflight: inspect restored chain, native reset / 0->16
 -> verify nonzero equal speeds and same native network
 -> stop/retain rotor and record verified body UUID
 -> RUN: unpower clutch, inspect BEARING_PRECONDITION
 -> zero speed/network invalid: immediate BLOCKED, no CAPTURE wait
 -> valid: CAPTURE -> OUTPUT -> native restoration -> summary
```

The baseline/native capture wait is bounded to 20 ticks. A zero network on its
first verification tick fails explicitly. Initial setup preflight failure enters
the existing native cleanup/rollback lifecycle and only reports ROLLBACK COMPLETE
after footprint verification. No unknown moving entity is directly deleted.

Diagnostics report the nonzero chain while the preflight pulse is engaged; the
waiting rotor is intentionally stopped by its clutch. RUN must re-enable and
prove speed again. It does not mistake a powered output stop for a dead motor.

Yaw is refused until M40BaselinePass is derived from actual output/restoration.
No yaw fixture polishing is part of this repair.

## Independent ownership bug

The old advance guard threw whenever lookup.fixture was null or UUID mismatched:
`throw new IllegalStateException("fixture ownership unavailable " + found.status)`.
This was the line 366 failure, not an exception from Basin output.

The old resolver had two concrete defects:

1. It declared ambiguity on a second preliminary bearing/assembler candidate
   BEFORE checking the second candidate's full M40 controller signature.
2. Even after storing the body UUID, it rediscovered raw origin from aggregate
   collectBlocks bounds every tick. A present known body with no scan match was
   collapsed into AMBIGUOUS, without preserving the prior controller origin.

The supplied generic log cannot distinguish these branches. Both are repaired,
and new transition-bounded OWNERSHIP diagnostics name the exact reason.

Lookup now enumerates actual chunk BEs, validates full controller signature and
identity BEFORE counting candidates, deduplicates the same body/controller and
persists M40RawOrigin with M40Body. Known raw controller positions are resolved
directly regardless of transient aggregate bounds. Two genuinely matching bodies
remain ambiguous; unrelated nearby bodies cannot poison a unique match. Marker
version/dimension and damaged known-body signatures remain explicit safe refusals.

Previous version-1 markers remain accepted. Cleanup can resolve their controller
through BE enumeration even before a raw origin was stored. The old static Basin/
chassis left at the original footprint are removed only after native outer
disassembly; deterministic block identity checks preserve unrelated blocks.

## Regression coverage

Executable tests cover native static-discovery rejection without a steering
network, live-rotor capture eligibility, shaft adjacency, healthy/dead/missing/
split-network gates, qualified-before-ambiguity ownership, duplicate candidates,
unrelated identities and genuine multiple matching bodies. Existing M40 output,
transform, reservation and geometry tests remain intact.

ASM checks exact native reset, attach/handleAdded, motor generation callback,
clutch modifier contract and diagnostic fields. SHA-256 canaries freeze both
production Basin source files from the previous artifact. All retained verifiers
are unchanged. Runtime measurements and output PASS still require Minecraft.

## Verification and delivery

All requested commands completed successfully using the existing Windows Gradle
workflow. Minecraft was not launched:

- spotlessApply, spotlessCheck, forge:compileJava.
- verifyM40BasinParentWorldOutput, including executable decision/geometry tests,
  pinned Create ASM canaries and frozen Basin source hashes.
- verifyM39SeatParentWorld and verifyM39RuntimeMixinTargets.
- All retained M31-M38 gates, including verifyM35RollerMovingRendering.
- verifySableProductionBaseline, reobfJar, jarJar, reobfJarJar.
- verifyStandaloneForgeArtifact and full build: PASS (275 actionable tasks).
- git diff --check: PASS.

An independent packaged-class comparison against the pre-M40 production baseline
found all 89 retained compatibility classes byte-identical. Both production Basin
source files remain byte-identical to the previous M40 artifact's source and are
enforced by SHA-256 canaries. No earlier production code or rendering was changed.

Current artifact: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`.
Size: 12,851,655 bytes.
SHA-256: `42D5F3665E8E6AD91E27E5560C946777B1BA6E34F291D94345C59680DA30BF8E`.

Files changed in this repair, relative to the preceding M40 implementation:

- forge/src/main/java/dev/ryanhcode/sable/command/M40BasinFixtureCommands.java
- forge/src/main/java/dev/ryanhcode/sable/command/M40FixturePreconditions.java (new)
- forge/src/m40BasinTest/java/dev/ryanhcode/sable/compatibility/create/basin/M40BasinOutputTest.java
- forge/build.gradle (M40 verifier only)
- M40_1_KINETIC_FIXTURE_REPAIR.md (new)
- M40_RUNTIME_TEST_COMMANDS.md
- M40_BASIN_PARENT_WORLD_OUTPUT.md (link to this current repair report)

M28-M39 remain CLOSED / PASS. M40 fixture repair is implemented and statically
verified, but nested kinetic runtime and native Basin output runtime remain
pending. M40 is OPEN. No M41 work.
