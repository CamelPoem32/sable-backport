# M36 Portable Interfaces

Target: Create 6.0.8 at `1a1a9a2819b4f89f78caec41b55ed8cb222fa24b` on Minecraft 1.20.1. The compatibility boundary is `PortableStorageInterfaceMovement`, which serves both Portable Storage Interface (PSI) and Portable Fluid Interface (PFI).

## Create handshake

`AbstractContraptionEntity.tickActors` computes the actor's active point from `getActiveAreaOffset` (local facing times 1.85), applies the inner contraption transform, and calls `visitNewPosition`/`tick`. `PortableStorageInterfaceMovement.visitNewPosition` calls `findInterface`. That method validates the actor facing with `getCurrentFacingIfValid`, searches the active block and one block farther along that facing with `findStationaryInterface`, and uses `getStationaryInterfaceAt` to require a stationary block entity of the same interface type, opposite facing, and no redstone power. The server stores `WorkingPos`, then calls `PortableStorageInterfaceBlockEntity.startTransferringTo`.

The movement behaviour's server `tick` uses `WorkingPos` to revalidate the stationary block entity and sets its `keepAlive` to 2. The stationary block entity's tick ends a connection when keepalive expires; `stopTransferring` removes the exposed capability and runs Create's native disconnect animation. `reset` clears the movement actor's target and animation state. Connection distance, stall, timer, and animation are still Create's.

For items, `PortableItemInterfaceBlockEntity.startTransferringTo` exposes `InterfaceItemHandler` around the connected contraption's `getStorage().getAllItems()`; for fluids, `PortableFluidInterfaceBlockEntity` exposes `InterfaceFluidHandler` around `getStorage().getFluids()`. Both invalidate their capability on disconnect. This code does not copy items or fluids and does not create a Sable inventory proxy.

## Coordinate boundary

| Value | Space |
| --- | --- |
| Actor block and facing in captured contraption | `INTERFACE_LOCAL` |
| Native inner-Create active point and facing | `SABLE_RAW` |
| Outer logical pose applied to both | `PARENT_VISIBLE` |
| Two candidate block cells for parent `getBlockEntity` | `PARENT_BLOCK` |

The Sable-only movement hook transforms the active point and facing before invoking Create's original `visitNewPosition` and `tick`. It temporarily presents parent-visible `MovementContext.position`, motion, and rotation, restoring them in `try`-with-resources. It re-searches when the physical candidate changes (even without inner piston movement) or when `WorkingPos` is absent. It does not repeatedly call `startTransferringTo` for an unchanged connected cell. When a pose becomes unsupported or the parent chunk unloads, it calls native `reset`; the stationary side then expires through native keepalive. It never falls back to hidden plot coordinates.

Create's own facing acceptance is the squared vector error `<= 0.5` against the nearest cardinal direction. M36 also requires transformed world-up dot global up `> 0.999`, so materially pitched or rolled Sable bodies do not dock through an accidental cardinal projection. Translation and level cardinal yaw are supported. Both parent candidate chunks must be loaded, and candidates belonging to another Sable body or hidden plot are rejected.

## Topology

| Case | Status | Reason |
| --- | --- | --- |
| P1: moving PSI in Sable, stationary parent PSI | Runtime transfer PASS | Native item capability remains authoritative. |
| P2: moving PFI in Sable, stationary parent PFI | Runtime transfer PASS | Same handshake, distinct native fluid capability. |
| P3: stationary interface on Sable hull, parent counterpart | New architecture required | No native moving actor drives the hull interface handshake. |
| P4: both interfaces in the same Sable | Out of scope | Local sublevel interface pairing is not the physical parent pair. |
| P5: interfaces on two Sable bodies | Safe no-op | Cross-body capability ownership is not modeled. |
| P6: ordinary Create PSI/PFI | Already native | Hook returns directly to original Create methods. |

## Rendering

`PortableStorageInterfaceMovement.disableBlockEntityRendering` is true. Create chooses `PSIActorVisual` under Flywheel or its native `PortableStorageInterfaceRenderer.renderInContraption` CPU path otherwise. The client-only render hook selects that native CPU path only for Sable-contained Create contraptions, using the established small-coordinate contraption bridge. It does not disable Flywheel globally, widen culling, or add hidden-plot PoseStack translations.

## Diagnostics

`-Dsable.m31.traceCreateActors=true` enables transition-only `SABLE_M36_PSI` events for candidate, connection and rejection decisions. No M36 diagnostic string or world scan is constructed per tick when the flag is off. The weak-key context maps hold only the last physical target and optional last trace; ordinary Create contexts are never inserted.

## Fixture teardown

The M36 command fixture is independent of production PSI/PFI compatibility. Setup and cleanup use one marker-based lookup: a unique fixture can be active in the parent world, assembled in Sable, partially restored after an interrupted disassembly, or reduced to an orphan marker. Ambiguous or unknown footprints are refused. Cleanup powers only its tagged stationary interface with a temporary redstone block and calls Create's native `neighbourChanged()`. Create then stops the capability and its moving actor clears `WorkingPos` and `stall` through native `reset`. Only then does the fixture reverse its motor for native piston retraction. The temporary power block is removed with the known fixture footprint.

The 240-tick cleanup bound remains. Expiration removes the in-memory task, not the marker or blocks; another cleanup command re-reads the current world and resumes from the current state. Outer disassembly is checked again after any exception because the sublevel can disappear during the call. An exception is reported with state in chat and a stack trace in `latest.log`; it is not automatically treated as success. The earlier class-only `IllegalStateException` report did not retain enough detail to identify its exact throw site retroactively.

## Source canaries and runtime limits

`verifyM36PortableInterfaces` checks the exact Create 6.0.8 method/class names and bytecode constants, Sable-only mixin registration, coordinate geometry, cleanup policy, and one-command fixture packaging. The fixture uses a native hopper/chest for items and a native Mechanical Pump/tank for water. Item and fluid transfer are runtime proven; repeat cleanup, disconnect and yaw remain runtime gates before M36 can be marked PASS. See `M36_RUNTIME_TEST_COMMANDS.md`.
