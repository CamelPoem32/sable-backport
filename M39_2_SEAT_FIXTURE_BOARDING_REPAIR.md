# M39.2 native Seat boarding hold and vehicle identity

Scope: fixture/runtime harness only. No M40. Production Seat passenger transforms,
interaction, dismount, teardown, networking and rendering are unchanged.
Minecraft was not launched. The user's runtime proves boot, setup and Seat capture,
but has not reached player mounting or riding.

## Exact original failure

Original `M39SeatFixtureCommands.java:419`:

```java
if (vehicle == null) throw new IllegalStateException("boarding vehicle vanished");
```

`vehicle` was assigned at the start of advance by `inner(fixture)`. The original
capture path also used that SAME method. Capture printed the UUID but stored no
vehicle UUID/reference in Task; Task only acquired a passenger UUID after mounting.
Therefore the proposed capture-inclusive / boarding-parent-getEntity mismatch
does not exist in the original source. Parent getEntity(UUID) was used later for
the passenger, not for resolving the vehicle in BOARDING.

Original inner discovery:

1. Read the fixture's piston through M35FixtureLookup.blockEntity, using plot
   block-entity storage when an outer Sable exists.
2. Return piston.movedContraption if non-null, not removed and a PistonContraption.
3. Otherwise enumerate fixture.level.getAllEntities(), requiring an unremoved
   PistonContraption, matching raw controller position and containing Sable.

Original ASSEMBLING called `drive(fixture, -64, true)` before entering BOARDING.
That powers the clutch-control lever, removes piston drive and changes speed to
zero. The fixture never configured the native piston movement mode, leaving
Create's default MOVE_PLACE. The raw Create 6.0.8 bytecode establishes the chain:

```text
LinearActuatorBlockEntity.onSpeedChanged(F)V:
  assembleNextTick = true; waitingForSpeedChange = false
LinearActuatorBlockEntity.tick()V:
  running && assembleNextTick && speed == 0 -> tryDisassemble()
LinearActuatorBlockEntity.tryDisassemble()V:
  MOVE_NEVER_PLACE -> waitingForSpeedChange=true; return
  MOVE_PLACE_RETURNED away from initial offset -> wait; return
  otherwise -> disassemble()
MechanicalPistonBlockEntity.disassemble()V:
  movedContraption.disassemble(); running=false; movedContraption=null
```

The hold could destroy the vehicle it had just announced. This is a native
controller lifecycle/configuration bug in the harness, not a UUID lookup bug or
evidence against Seat production compatibility. Both original discovery paths
reject a removed vehicle, and a cleared controller reference leaves nothing to find.

The supplied log does not include an authoritative manager/removal snapshot, so
the exact historical removal reason is not recoverable from that log alone.
The source-backed stop/disassembly path is deterministic; new diagnostics will
also establish actual runtime ownership and removal status instead of guessing.
One switch branch runs per advance call; capture does not fall through into
BOARDING in that same invocation. New gameTime fields disambiguate close log lines.

The later M13 red-seat placement is native block restoration, not a preceding
lookup cause. The failure handler removes the Java task and reports failure; it
does not call drive/retract or perform a Seat block repair. Restoration after the
failure cannot retroactively explain the earlier lookup miss.

Exact upstream actuator source:
[Create 6.0.8 LinearActuatorBlockEntity](https://raw.githubusercontent.com/Creators-of-Create/Create/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/contraptions/piston/LinearActuatorBlockEntity.java).
Both LinearActuatorBlockEntity and MechanicalPistonBlockEntity were also audited
with javap against target_modpack/mods/create-1.20.1-6.0.8.jar.

## Level and registration ownership

ServerSubLevel is NOT a Minecraft Level. SubLevel.getLevel explicitly returns its
parent Level. ServerLevelPlot constructs LevelChunk with that parent Level and
addresses subLevel.getLevel().entityManager (PersistentEntitySectionManager).
Create creates the piston CCE using the controller's Level, assigns
movedContraption, then calls Level.addFreshEntity synchronously in assemble.
No alternate sublevel entity manager or UUID getter is required for this topology.

The new discovery trace prints the actual entity Level class/identity, comparisons
against the parent and body.getLevel, alive/removed/reason, owner UUID, discovery
path and gameTime. Follow-up lookup reports parent getter, body-parent getter,
controller reference and the combined semantic lookup, including same-instance.
This is instrumentation for runtime proof; this document does not invent an
isAlive/removalReason value for the previous Minecraft entity UUID.

No registration wait was added: the old discovery was already through a live
controller reference after native assembly. There is no evidence of a temporary
registration gap. Retrying the old zero-speed/MOVE_PLACE mistake would hide it.

Physics body recreation in finalizeExistingSubLevelStorage replaces/rebuilds a
physics handle, not this PistonContraption entity. Native piston disassembly and
later reassembly create a new vehicle; that is not a mid-ride identity continuation.
The existing explicit bearing ownership-transfer recreation path is bearing-only,
not a reason to adopt a new Seat piston CCE. A different UUID during this run is
reported ENTITY_REPLACED, not silently treated as the captured vehicle.

## Authoritative fixture resolver

All fixture discovery, BOARDING, RIDING, yaw, dismount and cleanup use
resolveVehicle(fixture, expectedUuid), directly or through inner(fixture).
The selection policy is shared with executable fixture tests.

Candidates come only from the known controller reference, expected UUID getters,
and parent-manager entities with the exact known controller position. Validity
requires all of:

- ControlledContraptionEntity with native PistonContraption;
- alive and not removed;
- same parent Level and current fixture Sable;
- exact raw controller identity;
- exactly the intended captured red Seat, at native toLocalPos(expectedSeat);
- physical Seat pose within two blocks of the expected current piston-offset
  path after the real outer transform (allows native interpolation, not plot fallback).

Capture stores UUID, last entity ID, Level description and gameTime. No Entity
reference is retained in Task. Repeated getters/controller candidates are deduped
by UUID. A removed vehicle, unrelated controller/body/Seat, replacement or ambiguity
cannot be adopted. Cleanup after restart has no transient Task identity and uses
the persistent marker/body/controller signature to resolve the currently owned
native Seat vehicle. The valid fixture setup geometry is unchanged.

Known persisted outer ownership remains recoverable even if the moving Seat
disappears, so the run can print VEHICLE_LOST and cleanup still finds the fixture.
This does not weaken candidate vehicle identity or permit unrelated CCE adoption.

## Native hold and human interaction

```text
RUN
-> native movement-mode control = MOVE_NEVER_PLACE
-> engage native piston
-> discover/capture expected Seat vehicle
-> stop clutch, retaining native assembled CCE
-> BOARDING_WAIT_FOR_PLAYER (no manual-input timeout)
-> actual native Forge mount attempt / Create Seat mapping
-> confirmed native parent-world passenger relation
-> engage travel
-> extension/hold remain MOVE_NEVER_PLACE (protects zero-speed kinetic reattachment)
-> before native retraction from full extension: MOVE_PLACE_RETURNED
```

The option is changed using the fixture piston's public ScrollOptionBehaviour
setValue API and checked by get(), not by poking running, waiting or entity fields.
MOVE_NEVER_PLACE preserves the extension hold. MOVE_PLACE_RETURNED is enabled only
after proven native extension, immediately before retraction, and allows natural
disassembly on return. A real offset guard prevents a return-mode handoff at zero
while drive reattachment is pending. Dismount and cleanup select the native
returned-placement mode before driving return, including recovery of the previous
failed fixture and cleanup while boarding. No fake movement or mounting is added.

BOARDING has no short human-input timeout. The 1200-tick bound remains for native
assembly/motion/cleanup. A real vehicle loss fails explicitly, with exact UUID,
last ID/Level, current lookups, last-known versus current removal status,
ticksSinceCapture and gameTime. No fabricated position/Entity reference is used.

Read-only fixture EntityMountEvent observation emits MOUNT_ATTEMPT only for the
current run's captured UUID, same Level and BOARDING phase. It does not cancel,
set fields, call startRiding or add passengers. MOUNT_SUCCESS still requires an
actual native rider/mapping. Production Seat code is unchanged.

## Static regression coverage

verifyM39SeatParentWorld now executes fixture identity tests covering next-tick
UUID resolution, controller availability independent of parent UUID lookup,
duplicate observations, removed/replaced/ambiguous vehicles, unrelated candidate
rejection, 100000 ticks of human boarding, bounded machine timeouts, normal block
restoration not being identity replacement, and fresh cleanup identity selection.

Source gates require all phases to share the resolver, never-place before boarding
stop, real passenger before travel, native return mode, no registration retry
band-aid and no raw entity reference stored for tracking. Exact Create ASM canaries
check speed-change assembly scheduling, tick tryDisassemble, and never-place's
wait flag/return. Mount observation is verified read-only and UUID scoped.

These are executable policy/geometry tests plus source/native-bytecode checks,
not a full in-Minecraft riding test. A nonexistent transient-registration scenario
is not simulated as a supported runtime lifecycle.

## Next runtime ONLY

Keep -Dsable.m31.traceCreateActors=true.

```mcfunction
/function sable:m39/cleanup
/function sable:m39/player
```

Wait for READY, click the generated Physics Assembler once, then:

```mcfunction
/function sable:m39/run
```

Expected bounded trace:

```text
[M39] RUN phase=WAIT_FOR_NATIVE_SEAT_CONTRAPTION
[M39] VEHICLE_DISCOVERY uuid=... discoveryPath=PISTON_CONTROLLER_REFERENCE decision=SEMANTIC_CONTROLLER gameTime=...
[M39] SEAT_CAPTURED seats=1 vehicle=...
[M39] RUN phase=BOARDING_WAIT_FOR_PLAYER movementMode=MOVE_NEVER_PLACE
[M39] VEHICLE_LOOKUP ... inclusiveLookup=<captured UUID> ... isRemoved=false
```

Do NOTHING for 5-10 seconds. The same Seat/CCE must remain available. Then
right-click the visible Seat, requiring real MOUNT_ATTEMPT and MOUNT_SUCCESS.
Only after confirmed mount may travel engage. Do not test yaw/dismount yet.
Cleanup remains the same single command for the existing failed fixture.

## Repair-only changed files

```text
forge/src/main/java/dev/ryanhcode/sable/command/M39SeatFixtureCommands.java
forge/src/main/java/dev/ryanhcode/sable/command/M39SeatFixtureVehicleIdentity.java (new)
forge/src/m39SeatTest/java/dev/ryanhcode/sable/command/M39SeatFixtureVehicleTest.java (new)
forge/build.gradle
M39_2_SEAT_FIXTURE_BOARDING_REPAIR.md (new)
M39_RUNTIME_TEST_COMMANDS.md
M39_SEAT_PARENT_WORLD.md
```

No production Seat hook, teardown helper, network/render code, M38 file or fixture
layout/function resource is changed by this repair.

## Final verification and artifact

PASS: separate spotlessApply, then spotlessCheck, forge:compileJava,
verifyM39SeatParentWorld, verifyM39RuntimeMixinTargets, all retained M31-M38
focused gates (including M33.1 and M35 rendering), verifySableProductionBaseline,
reobfJar, jarJar, reobfJarJar, verifyStandaloneForgeArtifact, full build and
git diff --check. The final full chain completed in 4m16s with 274 actionable
tasks: 249 executed and 25 up-to-date. Existing compiler/Mixin deprecation
warnings remain; no compilation or verification error occurred.

Artifact: forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar

Size: 12,806,548 bytes

SHA-256: E24040B9C322DA6C1AB00483D96CFCE6ED27CE80A1336F28802611F15CC1FC7F

ZIP-entry SHA-256 comparison against the preserved pre-repair M39 jar confirms
all six production Seat helper/mixin classes are byte-for-byte unchanged.
No Minecraft client/server was launched. Policy tests and ASM canaries are static
verification, not new runtime proof of mounting or riding.

Status: M28-M38 remain closed. M39 boot/setup/native capture are runtime proven.
Player mount is NOT REACHED; fixture boarding repair requires runtime acceptance.
M39 overall OPEN. No M40.
