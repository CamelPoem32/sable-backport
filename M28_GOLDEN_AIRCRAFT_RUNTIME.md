# M28 Golden Aircraft Runtime Qualification

Use a fresh M28 artifact. The only M28 commands used here are read-only:
`/sable m28 status` and `/sable m28 inspect`.

## Build and preflight

1. Build `M28_GOLDEN_AIRCRAFT_BUILD.md` in an open area or on a long runway.
2. Set both Creative Motors to 0 using the normal Create value boxes.
3. Configure each Steering Wheel angle limit to 30-45 degrees with Create's
   normal scroll-value interaction.
4. Set each Mechanical Bearing's normal Create movement mode to
   `Only Place when Anchor Destroyed` (`ROTATE_NEVER_PLACE`). Activate it once,
   confirm it captures one symmetric
   sail only, then return each surface to neutral. The payload must remain an
   assembled contraption while stopped.
5. Super Glue the static aircraft according to the glue map. Do not glue the
   three moving sails.
6. Put a named item in the chest and toggle the lever/lamp once.
7. Right-click the Physics Assembler normally. Do not use a fixture command.
8. Board by walking onto the deck. Run `/sable m28 inspect`; it should find the
   Sable through production player tracking, show three wheels, one propeller,
   static and moving aero providers, and `finite=true`.

The aircraft works without ever running an M28 command. Inspection is optional.

## Controls

Propulsion uses the normal Create Creative Motor value box. Set it to a modest
positive speed, then increase gradually. Reverse only through the same normal
motor UI if desired.

For any Steering Wheel, hold normal use on the wheel and move the mouse left or
right. Release use at the desired deflection. The wheel drives its real Create
shaft at 16 RPM until it reaches the target, then stops. Move the wheel back to
the center to neutralize that surface.

The three wheels are physically wired as:

| Wheel | Effect |
| --- | --- |
| Tail horizontal surface | Pitch up/down |
| Vertical fin surface | Yaw left/right |
| Starboard outboard surface | Roll left/right |

If a sign is opposite to the desired cockpit convention, reverse that channel
with ordinary Create shaft/gearbox orientation. Do not compensate with a
command.

## Flight

1. Stand on the aircraft while stationary and walk across the deck.
2. Start the propeller motor normally and raise RPM gradually.
3. Gain forward speed along the runway. Use a small pitch deflection to lift
   off. An elevated straight launch platform is acceptable if ground friction
   makes the frozen aerodynamic model impractical from level terrain.
4. Verify pitch up/down, yaw left/right, and roll left/right independently.
5. Perform a short climb, heading change, descent, and approach. Keep inputs
   small; no stabilization controller is present.
6. While moving moderately, open the chest and verify the named item remains.
7. Toggle the lever and observe the lamp.
8. Briefly power the drill pod with its normal Creative Motor while passing a
   safely placed expendable block. Confirm the ordinary Drill interaction, then
   stop it.
9. At low speed, place one non-critical copper block on the Sable and break it.
   If production Sable rejects safe structural edits, record that exact result
   as the M29 mutation gate; do not bypass it.

## Landing and teardown

1. Reduce propulsion through the normal motor UI.
2. Neutralize all three Steering Wheels and wait for their bearings to finish.
3. Land on clear terrain using the skids and stop propulsion completely.
4. Disassemble each bearing payload through normal Create interaction so every
   symmetric sail returns to the parent Sable.
5. Check that the Physics Assembler restoration volume is clear.
6. Activate the Physics Assembler normally.
7. Verify all blocks return exactly once, the chest inventory remains, no
   contraption entity remains, and no stale aerodynamic provider remains.

M28 does not require airborne save/reload, chunk-unload stress, or deep dynamic
mass mutation. Those are M29. The known Jade/large-AABB logging remains recorded
technical debt unless it directly blocks this continuous flight.

## Acceptance record

## M28.1 manual-body diagnostics

`/sable m28 inspect` has no fixture/session fallback. It reports
`SABLE_M28_AIRCRAFT_RESOLUTION` with the current support owner, targeted block
owner, selected production Sable UUID, and resolution method. Support ownership
has priority over the ray target.

The inspect payload distinguishes a Wooden Propeller block and block entity
from its registration in the normal `BlockEntitySubLevelActor` index. It also
reports the exact production thrust implied by current propeller state, the
last individually recorded propulsion force when available, rigid-body type,
backend membership, mass, velocities, raw selected bounds, and suspicious
terrain blocks captured by assembly. Run inspect once to enable force recording
and again after at least one physics tick for an accumulated-force sample.

At 64 RPM or higher, `propulsionProviderCount=1`,
`runtimeState=ACTIVE_PROPULSION`, and nonzero
`accumulatedPropulsionForceBodyLocal` prove that normal M26 production force
reached the manually assembled Sable. A dynamic body held at zero velocity must
then be diagnosed as a contact/mass/runway condition, not as missing propulsion.

M28 closes only after one uninterrupted manual sequence demonstrates assembly,
boarding, normal propulsion, takeoff, all three control axes, climb, turn,
descent, onboard interactions, a safe Create actor test, landing, stow, and
normal disassembly. Until then the status is
`M28.1 IMPLEMENTED / RUNTIME_REQUIRED`.

## M28.3 steering retest

Build the corrected three-channel topology from
`M28_GOLDEN_AIRCRAFT_BUILD.md`. Before assembly, verify three Steering Wheels
exist and that the pitch/yaw gearbox gap remains air. After normal assembly,
run `/sable m28 inspect`; it prints one `SABLE_M28_STEERING_WHEEL` line for
pitch, yaw, and roll with wheel, gearbox, shaft, bearing, network, speed, and
removal state.

Hold use and move the mouse on one wheel at a time. Its generated RPM, adjacent
gearbox speed, downstream shaft RPM, and bearing RPM must become nonzero and
then return to zero at the target. The other two channels must stay at zero.
No wheel or neighboring drivetrain block may be replaced. Static status is
`M28.3 IMPLEMENTED / RUNTIME_REQUIRED`.

## M28.4 steering stress retest

The Steering Wheel is the sole source for each isolated control channel. Its
frozen base capacity is `16 SU/RPM`; at its fixed 16 RPM output Create reports
256 SU of network capacity. No Creative Motor belongs in a pitch, yaw, or roll
control channel.

Run `/sable m28 inspect` before and while moving each wheel. Every channel must
report `stressConfigKey=simulated:steering_wheel`, `stressCapacity=16.0`,
`stressImpact=0.0`, `networkCapacity>0`, and
`networkOverstressed=false`. While moving, generated, gearbox, shaft, and
bearing RPM are nonzero. At the target, generated and bearing RPM return to
zero. `networkMembers` must contain only that channel's Steering Wheel,
required gearbox/shaft transmission, and one Mechanical Bearing.

Static status is `M28.4 IMPLEMENTED / RUNTIME_REQUIRED`.

## M28.5 held control and bearing hold retest

Create 6.0.8 uses the bearing's movement mode to decide what zero speed means.
`ROTATE_PLACE` places and discards the contraption when the Steering Wheel
reaches its target; `ROTATE_NEVER_PLACE` retains the same entity at its current
angle. The Steering Wheel's frozen `TURN_ANGLE` context limits travel but does
not override this placement setting. Configure all three control bearings to
`Only Place when Anchor Destroyed` through their normal Create UI. Value 0 is
`Always Place when Stopped`; the holding mode is value 2.

Aim at one wheel, hold RMB, and move the mouse across its gearbox and the
Physics Assembler without releasing. The captured `{Sable UUID, local wheel
position, hand, session token}` remains authoritative, so the aircraft must not
disassemble. At the target, inspect must report `generatedRpm=0`,
`bearingMovementMode=ROTATE_NEVER_PLACE`, `contraptionPresent=true`,
`controlSailPresent=true`, `controlLifecycleState=HOLDING_TARGET`, and no new
remove/create cycle. Release RMB, then verify an intentional Physics Assembler
click still follows normal M22 behavior. Repeat for pitch, yaw, and roll.

Static status is `M28.5c IMPLEMENTED / VISUAL_RUNTIME_REQUIRED`.

## M28.5b control-network preflight

The latest full log proves Steering Wheel RPM reached one Mechanical Bearing;
disconnected power alone is not the root diagnosis. The same assembly showed
`movementMode=ROTATE_PLACE`, repeated contraption create/remove cycles, and a
captured `create:white_sail` instead of `simulated:white_symmetric_sail`.
One Steering Wheel was also unexpectedly replaced by air outside M22 transfer.
Its initiating owner must come from the new bounded Create destruction trace;
the log does not yet prove which Create branch, support event, or player action
initiated that removal.

Run `/sable m28 validate_controls` while standing on or targeting the manually
assembled Sable. The read-only command inventories actual wheel local positions
and compares each canonical route, effective assembled bearing mode, captured
payload, and Create 6.0.8 static connectivity. `overallStatus=PASS` is required
before controlling a wheel. A `WRONG_BEARING_MOVEMENT_MODE` result must be
corrected on the actual assembled bearing to `Only Place when Anchor Destroyed`.
`WRONG_CONTROL_PAYLOAD` requires the one-block bearing payload to be
`simulated:white_symmetric_sail`, not an ordinary Create white sail.

If preflight passes, operate one wheel and inspect the server's
`SABLE_M28_KINETIC_CONFLICT` record if anything breaks. It reports the initiating
Create method, old/new source speeds, network members, generators, and exact
destroyed local position. Compare an identical isolated channel first in the
static world and then in a tiny manually assembled Sable before attributing a
failure to Sable/Create integration.

## M28.5c assembled-sail render batch

Oculus 1.8.0 replaces Minecraft's entity source with a segmented
`FullyBufferedMultiBufferSource`. The old Forge `AFTER_ENTITIES` bridge wrote
the assembled Create sail into that source after Oculus's matching collection
and draw boundary, so valid moving vertices remained unflushed. The bridge now
uses one Sable-owned vanilla source for all manually dispatched contained
contraptions and flushes it once at the end of the stage.

Retest both axis-Y and axis-Z symmetric sails with Oculus and shaders enabled.
The compact `SABLE_M28_ENTITY_BATCH` trace must end in the same frame with
`flushOwner=SABLE flushed=true unflushedRanges=0`. Player-visible assembled
rotation and shader/material correctness remain required before M28 closes.

## M28.5d Embeddium vertex and entity-phase comparison

ImmediatelyFast is no longer installed and its removal did not change the
assembled-sail symptom. Embeddium's `SodiumBufferBuilder` delegates to a vanilla
backing `BufferBuilder`; the new bounded trace reads the actual stored position
bytes after each sampled Catnip vertex call. It also recognizes Oculus
`BufferSegment` draws by those bytes, since that path bypasses vanilla
`BufferSource.endBatch(RenderType)`.

Run the same axis-Z sail first in default scoped mode, then from a fresh launch
with `-Dsable.m28.entityPhaseAB=true`. In both modes collect the Sodium write,
comparison, finalization, pre-draw, and draw-provenance markers. Enable
`-Dsable.m28.normalCreateAB=true` for one ordinary-world bearing control. The
entity-phase mode is diagnostic until player-visible rotation and shader
compatibility select it. Static status is
`M28.5d IMPLEMENTED / VISUAL_RUNTIME_REQUIRED`.

## M28.6 framebuffer ownership A/B

The moving controlled-contraption vertices are proven through finalization and
the same-frame GPU draw. Use the M28.6 flags one at a time on the same assembled
control sail:

1. Baseline: `-Dsable.m28.visualOwnershipTrace=true
   -Dsable.m28.framebufferProbe=true`.
2. Dynamic-owner suppression: add
   `-Dsable.m28.suppressDynamicContraption=true`. If the stationary sail remains,
   another draw owns the visible image; if it disappears, the dynamic draw is
   the visible owner.
3. Static rebuild A/B: use
   `-Dsable.m28.forceCapturedStaticInvalidate=true` without suppression.
4. Entity-phase A/B: use `-Dsable.m28.entityPhaseAB=true` without suppression.

Expected markers are `SABLE_M28_STATIC_CACHE_LIFECYCLE`,
`SABLE_M28_STATIC_DRAW_CONTENT`, `SABLE_M28_VISIBLE_OWNER`,
`SABLE_M28_SCREEN_BOUNDS`, `SABLE_M28_FRAMEBUFFER_PROBE`, and
`SABLE_M28_ENTITY_PHASE_AB`. M28 mechanics remain PASS. Visual status remains
FAIL/PARTIAL until the player-visible assembled sail rotates.

## M28.7 unidentified visual-owner trace

The M28.6 suppression run proved that neither Sable's known dynamic Create draw
nor the current immediate static Sable snapshot owns the stationary image. Use a
fresh launch for each bounded M28.7 run.

Run A JVM properties:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.suppressDynamicContraption=true
```

Rotate one assembled sail through at least 90 degrees. Preserve all
`SABLE_M28_ENTITY_DISPATCH`, `SABLE_M28_CONTRAPTION_RENDER_GLOBAL`,
`SABLE_M28_ENTITY_RENDER_COUNT`, `SABLE_M28_ENTITY_REGISTRY_OWNERSHIP`,
`SABLE_M28_INCLUSIVE_ENTITY_QUERY`, and `SABLE_M28_SAIL_MODEL_DRAW` lines.

If the stationary sail remains and a `VANILLA_LEVEL_ENTITY_PASS` dispatcher
owner is present, repeat as Run B with:

```text
-Dsable.m28.suppressVanillaTargetContraption=true
```

Run B suppresses only that vanilla-pass controlled-contraption call. It does not
change mechanics, block ownership, or the known Sable bridge.

## M28.8 Flywheel visual ownership gate

M28.7 runtime proved that Create/Flywheel independently builds a `ContraptionVisual`
containing the captured symmetric sail. M28.8 traces the exact structure model and the
matrix written to its `VisualEmbedding`. It also fixes the diagnostic worker-thread GL
access and provides one narrow ownership A/B:

```text
-Dsable.m28.visualOwnershipTrace=true
-Dsable.m28.suppressDynamicContraption=true
-Dsable.m28.suppressVanillaTargetContraption=true
-Dsable.m28.suppressFlywheelTargetContraption=true
```

Use a fresh launch with shaders off and rotate one assembled sail through at least 90
degrees. If the stationary sail disappears, Flywheel's `ContraptionVisual` is the proven
visible owner. If it remains, do not select a production renderer fix yet. Static status
is `M28.8 IMPLEMENTED / VISUAL_RUNTIME_REQUIRED`; no Minecraft launch was performed by
the implementation pass.
## M28.9 visual identity runtime gate

Use `sable.m28.visualOwnershipTrace=true`, scoped Flywheel target suppression, CPU target tint,
projected overlay, static symmetric-sail suppression, and final-presentation tracing together. Rotate
one assembled sail through at least 90 degrees and classify the result using the M28.9 audit. This is
diagnostic only; M28 visual status remains runtime-required.
