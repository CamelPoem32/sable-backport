# M24.10 Winch Tick Audit

Authority: frozen Simulated commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Frozen ownership

- `RopeWinchBlockEntity` extends Create `KineticBlockEntity`.
- Its ordinary `tick()` calls `super.tick()` and, on the server while it owns a rope, calls `updateRopeStrandExtension`.
- `getMovementSpeed()` is `clamp(KineticBlockEntity.convertToLinear(getSpeed()), -0.49, 0.49)`.
- The mutation owner is the Winch BE tick: it updates the first segment extension and adds/removes unit rope segments as the extension crosses segment boundaries.
- It is not a lazy-tick operation and is not gated by redstone or a generic component-enabled flag.

## Target defect

M24.9 registered Rope Winch as the generic `M24PhysicalBlockEntity`. Its only length update was inside generic constraint maintenance and was gated by generic `enabled`. Neighbor updates from the working Create motor could set that unrelated flag false. Thus RPM reached the BE while the frozen production Winch tick never owned an update, matching the absence of `SABLE_M24_WINCH_TICK` and `configurationUpdateCount=0`.

## M24.10 adaptation

`M24WinchBlockEntity` is now the registered Winch BE and overrides the normal Create `tick()`. It invokes the frozen Winch mutation contract once per BE tick. Constraint maintenance only maintains the current rope target; it no longer advances Winch length. Diagnostics distinguish geometric polyline length, total logical length, first-segment extension, fixed-segment count, backend current length, and backend target length.

The observed zero-RPM `6.618331` versus `6.374998` pair compared geometric polyline length with logical segment length. M24.10 does not reinterpret this diagnostic difference as a backend rest-length defect and does not alter Rope physics.
