# M27.1 Sail Provider Audit

## Frozen Authority

Sable commit `b7226222caf4eace63a708bdcd73ef36c971137d` contains
`neoforge/.../sails_providing_lift/SailBlockMixin`. It targets Create's
`com.simibubi.create.content.contraptions.bearing.SailBlock` and implements
`BlockSubLevelLiftProvider` plus `BlockSubLevelCustomCenterOfMass`. Its normal
is `BlockStateProperties.FACING.getOpposite()` and its center of mass is the
block center.

Create 6.0.8 exposes that exact `SailBlock` class and inherits the directional
`FACING` property from `WrenchableDirectionalBlock`. The target adaptation is
an interface-only mixin; it has no optional method injector and no `require=0`.

## Registration And Invocation

`SubLevelAssemblyHelper` moves the transformed sail state into the plot and
notifies the plot of the block change. `ServerLevelPlot.onBlockChange` detects
the mixed-in `BlockSubLevelLiftProvider` interface and stores a
`LiftProviderContext` containing position, state, and frozen normal.
`ServerSubLevel.prePhysicsTick` consumes that context and invokes
`sable$contributeLiftAndDrag` before applying the accumulated impulse to the
existing rigid body. Providers are reconstructed from block state changes;
they are not separately persisted.

## M27 Failure And Fix

The M27 mixin implementation class and the common source config both existed,
but Forge excludes the common config and packages the curated
`sable-common-forge.mixins.json`. That runtime config omitted
`compatibility.create.sails_providing_lift.SailBlockMixin`, so Create sails
were never transformed and could not enter plot provider discovery.

M27.1 adds the exact mixin to the curated runtime config and verifies the
compiled interface contract, exact Create 6.0.8 target class, generated mixin
config, and packaged artifact. For `create:white_sail[facing=down]`, the frozen
normal is `UP`.

## Fixture Ownership

The former M27 resolver waited until a command, then required exact equality
with the original fixture block fingerprint. A bearing can legitimately move
its sail payload out of static Sable storage, invalidating that equality before
the body UUID was captured.

M27.1 registers the pending session by exact parent Physics Assembler position.
`PhysicsAssemblerBlockEntity#assemble` passes the successful assembly result,
including its exact `ServerSubLevel` and assembly offset, directly to M27. The
session stores that UUID and assembler-relative component positions before any
bearing command runs. Subsequent commands resolve only by UUID; a moving sail
is classified through Create contraption provider ownership rather than static
block-count equality.

Final status after M27.2 manual qualification: `M27 CLOSED / RUNTIME_PROVEN`.
