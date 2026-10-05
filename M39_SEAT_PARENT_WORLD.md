# M39 Seat / passenger boundary

Baseline: Minecraft 1.20.1, Forge 47.4.20, Create 6.0.8 commit
`1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`, Flywheel 1.0.5.
M28-M38 remain frozen. M39 is implemented, not Minecraft-runtime accepted.

## Exact native paths

- Stationary: `SeatBlock.use` checks occupied `SeatEntity` in `new AABB(pos)`;
  `SeatBlock.sitDown` creates it at `(x+.5,y,z+.5)`, then native
  `Entity.startRiding(seat,true)`. Seat has no block entity.
  `SeatEntity.positionRider` retains species offsets; its own renderer draws nothing.
- Capture: `Contraption.moveBlock -> moveSeat` adds the local seat and any initial
  passenger; `onEntityCreated -> addSittingPassenger` transfers initial riders.
- Moving mount: `ContraptionHandlerClient.rightClickingOnContraptionsGetsHandledLocally`
  checks bounds, `rayTraceContraption` inverses the ray into inner-local coordinates,
  then `handlePlayerInteraction` sends `ContraptionInteractionPacket` with entity ID,
  local block position, face and hand. Server resolves that ID in sender.level,
  checks reach and delegates `handlePlayerInteraction -> addSittingPassenger ->
  Entity.startRiding(this,true)`, mapping UUID to seat index.
- Tick: `Entity.rideTick` zeros delta movement, ticks passenger, invokes
  `vehicle.positionRider`. Controlled CCE also calls it when moved on the client.
  `AbstractContraptionEntity.getPassengerPosition` constructs local
  `seat.lowerCorner + (.5,myRidingOffset+ySize-.15,.5)`, applies `toGlobalVector`,
  adds `(.5,.5,.5)` and subtracts `(.5,ySize,.5)`. `positionRider` then adds
  `SeatEntity.getCustomEntitySeatOffset - .125` along world-up.
- Dismount: native `LivingEntity.stopRiding/removeVehicle ->
  AbstractContraptionEntity.removePassenger` captures the position before removing
  mapping, writes `ContraptionDismountLocation`, sends mapping update. Its
  `getDismountLocationForPassenger` consumes this location. M39 then searches
  loaded parent cells using native `DismountHelper`, rejecting inner payload bounds
  and existing Sable collision shapes. With no safe floor, native-style physical
  current-position fallback is logged, not represented as a safe-floor PASS.
- Native disassembly otherwise restores `SeatBlock.sitDown` at raw restored seats.
  M39 detaches first, so parent riders cannot be re-seated into hidden plots.
- `SeatMovementBehaviour.startMoving` records `SeatIndex`; `visitNewPosition`
  checks the visited block for occlusion/bottom slab and, if occupied, stops the
  passenger and teleports above that block. The scoped movement mixin transforms
  only this solid-cell lookup and uses the physical safe-dismount result instead
  of native raw-cell teleport coordinates. Ordinary Seat behavior calls originals.

The exact mapped jar was inspected with javap; verifier checks target bytecode
members and call sites. Upstream sources:
[AbstractContraptionEntity](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java),
[SeatBlock](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/contraptions/actors/seat/SeatBlock.java).

The forced-dismount source is
[SeatMovementBehaviour](https://github.com/Creators-of-Create/Create/blob/1a1a9a2819b4f89f78caec41b55ed8cb222fa24b/src/main/java/com/simibubi/create/content/contraptions/actors/seat/SeatMovementBehaviour.java).

## Ownership and topology

Sable is not a separate Minecraft dimension/Level for these entities. The player,
pig, inner CCE and outer body container use the same parent ServerLevel (ClientLevel
on the client). The CCE controller/position is raw plot-indexed; SubLevel ownership
is semantic via `SableCreateContraptionContext`. Vanilla `startRiding` checks cycle,
Forge mount permission, capacity/canRide; no Level transfer occurs. M39 additionally
requires identical level, LivingEntity, shouldKick and no raw plot ownership.

| Case | Classification | Boundary |
|---|---|---|
| S1 parent player -> nested controlled Create Seat | SUPPORTED | Native relation, physical coordinates |
| S2 parent living mob -> same Seat | SUPPORTED | Same native species offsets; fixture uses pig |
| S3 Seat directly on outer hull | OUT_OF_SCOPE | Stationary SeatEntity is not this controlled-CCE bridge |
| S4 raw/logical plot passenger -> same Seat | SAFE_NO_OP | Not a parent passenger; mount rejected |
| S5 raw Sable B entity -> A Seat | SAFE_NO_OP | Ownership rejection, physical interaction required |
| S6 ordinary stationary/moving Seat | ALREADY_NATIVE | Null owner returns original methods |

Trains/oriented contraptions are excluded. Parent-visible mobs that are not raw
plot entities have no persistent cross-body ownership; native physical interaction
is the criterion, never hidden plot proximity.

## Transform and narrow hooks

`SEAT_LOCAL -> INNER_CONTRAPTION -> SABLE_RAW -> outer pose -> PARENT_VISIBLE`.
RETURN injection changes the native passenger result BEFORE Entity.MoveFunction
sets a passenger position: `outer(nativeResult + ySize*UP) - ySize*UP`.
Client uses renderPose(partialTicks), server logicalPose. No raw setPos, no giant
PoseStack translation/cancellation. Native body-height/species offsets remain.
No custom renderer, velocity impulse, look-yaw assignment, culling or Flywheel change.

SeatInteractionClientMixin transforms the eight-corner candidate bounds and ray
inputs; existing native local shape hit chooses the Seat. Packet mixin changes reach
distance only for a verified local seat. Normal-world code calls originals.
Existing local-player mount/dismount look conversion and ServerPlayer teleport
conversion bypass this already-physical rider. Tracking exemption prevents a
second outer inheritance and raw-coordinate network serialization. Existing
ServerEntity passenger suppression is bypassed for Seat CCEs so vanilla
ClientboundSetPassengersPacket plus Create ContraptionSeatMappingPacket remain
authoritative. No new network packet is introduced. CCE has no controlling player
for vehicle movement input; player cannot steer it. Create's canCollideWith rejects
riders already on a vehicle; noPhysics/collision is not globally disabled.

## Teardown and lifecycle

Seat CCE disassemble/remove and outer SimAssemblyHelper ownership-transfer/removal
detach only eligible Seat passengers via stopRiding. Forge veto remains a hard
teardown refusal, not silent passenger loss. Death/logout/dimension events invoke
native stopRiding for this scope only. Ordinary death/logout handling stays native.
M39.1 replaces the invalid self-mixin with a direct SimAssemblyHelper helper call
after occupied-restore-space refusal and before ownership transfer. Its exact
packaged bytecode and the List/Collection crash are documented in
[M39.1 repair](M39_1_RUNTIME_MIXIN_REPAIR.md).
SaveWithoutId must not gather parent riders into the vehicle's raw x/z. Loaded
fixtures recover from persistent origin/body UUID even when Java tasks were lost.
World-reload native packet/order correctness still needs Minecraft acceptance.

Fixture cleanup: detach passengers -> stop -> native +64 retract -> inner restored
-> return only test body's pose to reserved origin -> native outer disassembly
-> inspect/remove known deterministic static footprint -> marker/stabilizer removal.
Unknown restore occupants refuse without deletion; timeout drops transient task,
preserving persistent identity so cleanup can retry. No active CCE is deleted directly.

## Fixture geometry

Origin is controller. Reuses proven M38 native topology (does not modify it):
12 east poles behind piston; chassis (1,0,0) sticky_north/east; chest (1,0,1);
red Seat (1,1,0); motor (0,-2,0), clutch (0,-1,0), lever (0,-1,1);
iron (0,0,-1/-2), assembler (0,1,-2). Native chassis and outer selection
are validated before READY; selected outer blocks must be 22.

Parent platform: x=-14..15,z=-15..6,y=-4; boarding steps x=-1..3,
(y,z)=(0,3),(-1,4),(-2,5),(-3,6). Air gap keeps platform outside hull.
Bounded loaded-air reservation chooses 27 deterministic nearby candidates, starting
six blocks above the invoking player's feet (the owned floor remains above the player).
Marker is synchronously verified before mutation; failed builds roll back only
the actually placed footprint, with post-scan before discarding ownership.

Run assembles native CCE, stops for boarding, then extends to >=11 of 12 available
blocks, holds 160 ticks and naturally retracts. This avoids native endpoint
disassembly at 12 before testing return travel. Run measures every mapped rider
sample: position error <=.25 blocks, no hidden coordinates, finite velocity <8
blocks/tick, and safe parent dismount. A trace/summary alone is not runtime acceptance.
Debug diagnostics: KEEP_DEBUG_GATED under sable.m31.traceCreateActors; fixture
phase feedback is bounded and always available. Actual visual/network acceptance
must include continuous ride, yaw, normal sneak, and cleanup while occupied.

M39.2 uses native MOVE_NEVER_PLACE while waiting for human boarding: the old
default MOVE_PLACE disassembled the announced vehicle when its clutch stopped.
After a real mount it resumes drive with never-place protection. It switches to
MOVE_PLACE_RETURNED before retraction from full extension or explicit cleanup.
One semantic fixture resolver tracks the captured UUID across run phases; manual
BOARDING has no short timeout. Production Seat code remains unchanged.
See [M39.2 fixture repair](M39_2_SEAT_FIXTURE_BOARDING_REPAIR.md) for the exact
line-419 audit, ownership evidence, diagnostics, tests and latest artifact.

## Static verification and artifact

PASS: spotlessApply, spotlessCheck, compileJava, verifyM39SeatParentWorld,
verifyM39RuntimeMixinTargets (15 packaged hooks and the direct teardown call),
all retained M31-M38 focused gates (including M33.1 and M35 rendering),
verifySableProductionBaseline, reobfJar, jarJar, reobfJarJar,
verifyStandaloneForgeArtifact, full build and git diff --check.
The M39.2 final full chain completed in 4m16s with 274 actionable tasks,
249 executed and 25 up-to-date.
Existing compiler deprecation/Mixin warnings remain; no compilation errors.

The M39 gate executes deterministic transform/layout tests and native bytecode
canaries, plus fixture UUID/ownership, human boarding timeout and native
movement-mode ordering tests. The ASM gate verifies packaged descriptors, injection points/refmap
and direct teardown order, and rejects the actual preserved crashing jar.
This is structural verification, not full Mixin execution or an in-Minecraft test.
Final jar inspection confirms six packaged functions, Seat hook classes and
production getBlockState/teleportTo mappings in sable.refmap.json.

Artifact: forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar
Size: 12,806,548 bytes
SHA-256: E24040B9C322DA6C1AB00483D96CFCE6ED27CE80A1336F28802611F15CC1FC7F

All six production Seat helper/mixin ZIP entries are byte-for-byte unchanged
against the preserved pre-repair M39 artifact.

Minecraft was not launched. Player/mob motion, client/server agreement, sneak,
reload and occupied teardown remain runtime acceptance gates.
The latest user runtime proves M39 boot, fixture setup and native Seat capture.
Player mounting was NOT REACHED. M39.2 boarding repair is statically verified,
runtime pending; M39 overall remains OPEN. M28-M38 remain closed.

## Changed files

Paths relative to sable-backport:

```text
M39_RUNTIME_TEST_COMMANDS.md
M39_SEAT_PARENT_WORLD.md
M39_1_RUNTIME_MIXIN_REPAIR.md
M39_2_SEAT_FIXTURE_BOARDING_REPAIR.md
buildSrc/src/main/java/M39RuntimeMixinTargetVerifier.java
common/src/main/java/dev/ryanhcode/sable/compatibility/create/seat/SableSeatPassengers.java
common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/seat/SeatContraptionEntityMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/seat/SeatInteractionClientMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/seat/SeatInteractionPacketMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/seat/SeatMovementBehaviourMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/create/seat/SeatOuterRemovalMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_rotations_and_riding/LocalPlayerMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_rotations_and_riding/ServerEntityMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_rotations_and_riding/ServerPlayerMixin.java
common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_sublevel_collision/EntityMixin.java
common/src/main/java/dev/ryanhcode/sable/mixinhelpers/camera/camera_rotation/EntitySubLevelRotationHelper.java
forge/build.gradle
forge/src/m39SeatTest/java/dev/ryanhcode/sable/compatibility/create/seat/M39SeatTransformTest.java
forge/src/m39SeatTest/java/dev/ryanhcode/sable/command/M39SeatFixtureVehicleTest.java
forge/src/main/java/dev/ryanhcode/sable/command/M39SeatFixtureCommands.java
forge/src/main/java/dev/ryanhcode/sable/command/M39SeatFixtureLayout.java
forge/src/main/java/dev/ryanhcode/sable/command/M39SeatFixtureVehicleIdentity.java
forge/src/main/java/dev/ryanhcode/sable/forge/M39SeatLifecycleEvents.java
forge/src/main/java/dev/ryanhcode/sable/forge/SableForge.java
forge/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java
forge/src/main/resources/data/sable/functions/m39/cleanup.mcfunction
forge/src/main/resources/data/sable/functions/m39/dismount.mcfunction
forge/src/main/resources/data/sable/functions/m39/mob.mcfunction
forge/src/main/resources/data/sable/functions/m39/player.mcfunction
forge/src/main/resources/data/sable/functions/m39/run.mcfunction
forge/src/main/resources/data/sable/functions/m39/yaw90.mcfunction
forge/src/main/resources/sable-common-forge.mixins.json
```

M39.1 removed `forge/src/main/java/dev/ryanhcode/sable/mixin/m39/SeatOuterDisassemblyMixin.java`.
See the repair report for the repair-only delta, separate from the initial M39 work.
