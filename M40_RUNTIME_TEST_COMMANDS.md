# M40 runtime commands

Static implementation only until this test is performed in Minecraft. Do not
start M41. The previous M28-M39 acceptance remains closed.

Install the newly built standalone Forge `-all.jar`; keep the exact supported
Create/Forge/Flywheel versions. Optional native output diagnostics reuse:

```text
-Dsable.m31.traceCreateActors=true
```

## First output

```mcfunction
/function sable:m40/cleanup
/function sable:m40/basin
```

Wait for BUILD -> VALIDATE -> ATTACHMENT -> KINETIC_BASELINE -> READY. The baseline
must report source/clutch/bearing speed 16 and networkValid=true during its native
test pulse, then liveRotorRetained=true. It pauses the rotor before READY.

Click the generated Physics Assembler **once**. Wait for automatic diagnostics:

```text
[M40] OWNERSHIP ... decision=ASSEMBLED ambiguityReason=NONE
[M40] KINETIC_NESTED_BEFORE_RECONNECT ...
[M40] KINETIC_RECONNECT scope=OWNED_M40_FIXTURE nativeRelocationReset=true motorTransition=0->16
[M40] KINETIC_NESTED ... sourceSpeed=16.0 clutchSpeed=16.0 bearingSpeed=16.0 networkValid=true
[M40] KINETIC_NESTED preflight=PASS liveRotorRetained=true waitingForRun=true clutchPowered=true bearingPaused=true
```

The speed report is taken with the drive enabled. Afterwards the powered clutch
intentionally holds the live rotor: source/input clutch speed stays nonzero;
bearing output is zero until RUN. This is not a dead network. Then:

```mcfunction
/function sable:m40/run
```

The horizontal native bearing turns the Basin upside down. Expect:

```text
[M40] BEARING_PRECONDITION ... speed=16.0 running=true assembleNextTick=<native state>
[M40] BASIN_CAPTURED ... inputOutputBefore=6 mountedBasinStorage=NONE_NATIVE_BE_NBT
[M40] NATIVE_OUTPUT_ATTEMPT awaiting native inversion while bearing turns
SABLE_M40_BASIN event=OUTPUT_ATTEMPT ... decision=NATIVE_INVERSION
SABLE_M40_BASIN event=ITEM_SPAWN ... decision=NATIVE_CREATE_DUMP
[M40] BASIN_OUTPUT ... nativeOutputOffset=0 ... parentWorldEntity=true hiddenPlotSpawn=false
[M40] ITEM_SPAWN ... count=4 ...
[M40] ITEM_SPAWN ... count=2 ...
[M40] OUTPUT_CONFIRMED nativeDump=true before=6 after=0 emittedStacks=2 innerAngle=<nonzero>
[M40] DISASSEMBLY innerRestored=true
[M40] nativeOutput=true parentWorldEntity=true physicalSpawnValid=true hiddenPlotSpawn=false noDuplicate=true restoredContents=0 PASS
```

Order of the two slot/entity lines is unspecified; input/output handlers are in
a native map. Actual UUIDs, coordinates, angles and velocity vectors vary. The
payload is exactly four cobblestone and two andesite. Observe these two dropped
stacks at the physical Basin, falling toward the parent-world platform. Do not
manually repair glue, place items or activate processing blocks.

## Deferred level yaw regression

Do not test yaw in the first M40.1 runtime. The helper refuses until a machine-
derived baseline M40 PASS has been recorded. Only after baseline acceptance:

```mcfunction
/function sable:m40/yaw90
```

Wait for `YAW90 COMPLETE source=6`, then:

```mcfunction
/function sable:m40/run
```

Expect another two native outputs, this time at the newly yawed physical Basin,
the same zero hidden-plot flags, and PASS. The helper replenishes only the owned
test Basin; no production inventory behavior is replaced.

## Cleanup

```mcfunction
/function sable:m40/cleanup
```

Expect native inner restoration, outer disassembly, any recreated native rotor
restoration, then `CLEANUP COMPLETE footprintClear=true stabilizerRemoved=true`.
Repeated cleanup reports `CLEANUP already clean`. Cleanup can also be used before
or during a run. If an unrelated restore cell is obstructed, diagnostics preserve
the blocker and ownership; cleanup remains retryable rather than force-deleting.

No runtime PASS is established by build/verifier success alone.
