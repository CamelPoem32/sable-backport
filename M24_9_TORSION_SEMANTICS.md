# M24.9 Frozen Torsion Spring Semantics

Authority: Simulated commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Production ownership

- `TorsionSpringBlock` is a directional Create kinetic block. Its input shaft is on the face opposite `FACING` and its rotation axis is the facing axis.
- `TorsionSpringBlockEntity` is a `KineticBlockEntity`. Its nested `Output` is a `GeneratingKineticBlockEntity` exposed through Simulated's `ExtraKinetics` two-port extension.
- It does not create a Sable Rotary, Fixed, Rope, or other rigid-body constraint and does not directly couple two Sable bodies.
- Create input speed selects a signed target angle. The configured angle range is 1 through 360 degrees and defaults to 90 degrees.
- While input runs, the output travels toward `angleLimit * sign(inputSpeed)` using Create's angular-speed conversion. Reversing input selects the opposite target.
- When input stops while unpowered, the output returns to zero. When redstone-powered, it holds rather than rewinding.
- A sequenced gearshift `TURN_ANGLE` instruction can cap angular travel.
- Horizontal comparator output reports the signed fraction of current angle over configured angle on the clockwise or counter-clockwise side.
- `TorsionSpringRenderer` and `TorsionSpringVisual` render the current interpolated spring/output angle.

## 1.20.1 target qualification

M24.10 replaces the generic two-Sable harness with one assembled Sable carrying a dedicated Torsion BE, a real Creative Motor input, and a real shaft on the virtual output side. The target ports the frozen `ExtraKinetics` identity, propagation, persistence, independent generated-output network, signed target/current angle state, unpowered return, powered hold, comparator state, and animated BER path.

Manual M24.12 acceptance proves the input, generated output, return/hold
behavior, signed angle motion, and dynamic spring rendering. Torsion Spring is
`RUNTIME_PROVEN`; it deliberately creates no Sable backend joint.

## Fixture sequence

1. Spawn `/sable m24 fixture torsion basic`.
2. Assemble the sole body A and inspect `/sable m24 bodies`; `expectedBodies=1` and body B is `not_required`.
3. Set `/sable m24 torsion angle_limit 45`.
4. Run `/sable m24 torsion rpm 32`, then `0`, then `-32`.
5. Use `/sable m24 torsion power true|false` to qualify the real redstone hold/release path.
6. Inspect `/sable m24 inspect torsion` after each transition.

The fixture motor is a real Create Creative Motor. Commands alter only its normal Create speed behavior; they never rotate a Sable transform.
