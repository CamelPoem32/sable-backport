# M28 Steering Source Flap Audit

## Authority

- Frozen Simulated: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.
- Target: Create 6.0.8 mapped development runtime in the Forge build.

## Failure

The target wheel printed the newly requested angle as `targetAngle`, but its
`inUse` countdown belonged to the older active target. On the expiration tick,
the server integrated toward that old target, decremented `inUse` to zero, and
called `stopMove()` without examining the new request. `getGeneratedSpeed()`
then returned zero. The next tick's `else if` finally began a new move. Thus
`27.824955 - 27.344967 = 0.479988` degrees could be printed beside zero RPM:
the two values were not the same target snapshot used for the stop decision.

Exact Create 6.0.8 `GeneratingKineticBlockEntity.applyNewSpeed` detaches
kinetics and clears the network when a generator changes to zero. When it
changes back to a nonzero speed, it creates a network ID and attaches. The
wheel's needless stop therefore directly caused the observed `16 -> 0 / none
-> 16` network cycle. This was not a stress-capacity failure. In
`ROTATE_NEVER_PLACE`, the bearing keeps its existing contraption when stopped,
so the later log showed the same entity while the source still flapped.

## Adaptation

Each server tick integrates the previous real kinetic motion, then evaluates
the latest requested angle and current wheel angle once. A new target replaces
the old travel instruction before its countdown can stop the generator. If a
countdown expires with actual angular error still outside the existing 0.001
degree tolerance, it resumes instead of declaring hold. The `TURN_ANGLE`
context and 16 RPM sign/facing conversion remain in `beginMove`.

Create 6.0.8 copies a sequence context downstream on `setSource` and its
Mechanical Bearing consumes that context in `onSpeedChanged`. A same-speed
`updateGeneratedRotation` does not propagate a new context, although it keeps
the network attached. The wheel therefore extends only its own downstream
bearing's remaining sequenced travel by the requested target delta scaled by
the actual bearing/wheel RPM ratio. The existing accessor exposes the exact
Create 6.0.8 `sequencedAngleLimit` field for this wheel-specific refresh; no
generic bearing tick or kinetic-network method is changed. The refresh is
skipped if the network/source chain changed, since Create's normal reattachment
then owns the new context. A real target reach still stops the generator.

No new hysteresis threshold was added: the failure was a stale-target
decision, not tolerance chatter. The existing 0.001 degree threshold is used
for both control input and settling. A stopped bearing with error outside
that threshold is now logged as `AWAITING_KINETIC_SOURCE`, not
`HOLDING_TARGET`.

The Java-only production-decision test covers continuous changing targets,
the reported 0.479988 degree case, a reached target, resumption, and reversal.
It cannot prove in-game network membership, bearing motion, or contraption
retention; M28 remains `IMPLEMENTED / RUNTIME_REQUIRED` until those are
observed in a fresh manual runtime test.
