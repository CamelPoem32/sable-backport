# M28 Golden Aircraft Runtime Qualification

Use a fresh M28 artifact. The only M28 commands used here are read-only:
`/sable m28 status` and `/sable m28 inspect`.

## Build and preflight

1. Build `M28_GOLDEN_AIRCRAFT_BUILD.md` in an open area or on a long runway.
2. Set both Creative Motors to 0 using the normal Create value boxes.
3. Configure each Steering Wheel angle limit to 30-45 degrees with Create's
   normal scroll-value interaction.
4. Activate each Mechanical Bearing once. Confirm it captures one symmetric
   sail only, then return each surface to neutral.
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

M28 closes only after one uninterrupted manual sequence demonstrates assembly,
boarding, normal propulsion, takeoff, all three control axes, climb, turn,
descent, onboard interactions, a safe Create actor test, landing, stow, and
normal disassembly. Until then the status is
`M28 IMPLEMENTED / RUNTIME_REQUIRED`.

