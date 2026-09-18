# M28.5 Steering TURN_ANGLE And Held-Use Audit

## Authority

- Simulated: `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`
- Frozen Create: 6.0.10 for Minecraft 1.21.1
- Target Create: 6.0.8 for Minecraft 1.20.1

## Frozen Steering Wheel

`SteeringWheelHandler` captures one block position when hold begins. Mouse yaw
changes `targetAngleToUpdate`; `SteeringWheelPacket` carries that same position
and a finite target. `SteeringWheelBlockEntity.updateTargetAngle` clamps the
target, generates signed 16 RPM, and publishes a
`SequencedGearshiftBlockEntity.SequenceContext` whose instruction is
`TURN_ANGLE`. `sequencedAngleLimit` prevents overshoot. When `inUse` reaches
zero, the wheel clears generated speed and the Create network stops.

`TURN_ANGLE` is travel ownership, not a Mechanical Bearing placement-mode
override. Frozen Simulated does not call `MechanicalBearingBlockEntity`, set its
movement mode, or retain a nonzero idle RPM.

## Target Create 6.0.8

The exact target bytecode has `MechanicalBearingBlockEntity.syncSequenceContext`
returning true. `onSpeedChanged` copies a `TURN_ANGLE` effective value into
`sequencedAngleLimit`, and `tick` clamps angular travel to that limit. On the
next zero-speed update, `tick` independently evaluates the bearing's normal
`RotationMode`:

- `ROTATE_PLACE`: stop and disassemble immediately.
- `ROTATE_PLACE_RETURNED`: disassemble at the initial angle.
- `ROTATE_NEVER_PLACE`: keep the same `ControlledContraptionEntity` assembled
  and stationary.

Consequently, a Golden control bearing left in `ROTATE_PLACE` visibly replaces
its sail with world blocks at every target, then assembles a new entity for the
next command. This is the observed flash and `DISCARDED` lifecycle. It is not a
stress failure and does not justify a generic Create mixin.

The parity configuration is Create's `ROTATE_NEVER_PLACE` mode, displayed in
Create 6.0.8 as `Only Place when Anchor Destroyed`. The M28 build and runtime
procedures now require it for each control bearing. The wheel continues to emit
exact frozen `TURN_ANGLE`, and generated RPM still returns to zero at the
target.

The `ScrollOptionBehaviour` values follow enum declaration order: value 0 is
`ROTATE_PLACE` (`Always Place when Stopped`), value 1 is
`ROTATE_PLACE_RETURNED` (`Only Place near Initial Angle`), and value 2 is
`ROTATE_NEVER_PLACE` (`Only Place when Anchor Destroyed`).

## Held-use capture

The earlier client port retained only the raw plot `BlockPos`. Every client tick
looked that raw position up directly; if the client briefly failed to expose the
block entity while the Sable moved, the session ended even though RMB remained
down. The shared Sable ray refresher could then place a different onboard block,
including the Physics Assembler, under normal repeated-use processing.

M28.5 captures `{Sable UUID, Sable-local wheel position, hand, session token,
dimension}` at the initial wheel use. The wheel is resolved by UUID plus local
position thereafter. The server validates that same identity and never consults
the current crosshair. If the captured owner becomes invalid, ordinary use stays
suppressed until physical RMB release. Release emits one stop packet and clears
ownership; an intentional later Physics Assembler click remains unchanged.

## Removal diagnostics

`onRemove(..., movedByPiston=true)` is the known M22 assembly/disassembly block
transfer path. It is now logged as `phase=BLOCK_TRANSFER
owner=M22_ASSEMBLY_DISASSEMBLY cause=PISTON_MOVE`. Only removal outside that
transfer is labeled `UNEXPECTED_BLOCK_REMOVED`.

## M28.5a Forge accessor ownership

`MechanicalBearingBlockEntityAccessor` is a read-only diagnostic accessor for
the protected Create 6.0.8 field `movementMode`. Its erased JVM descriptor is
`ScrollOptionBehaviour`; its generic signature is
`ScrollOptionBehaviour<IControlContraption.RotationMode>`. It does not select or
change a bearing mode and is not part of Steering Wheel control calculations.

M28.5 registered the accessor in upstream `common/src/main/resources/sable.mixins.json`.
The Forge backport deliberately excludes that resource and packages the curated
`forge/src/main/resources/sable-common-forge.mixins.json`, so the accessor class
was present without being applied. M28.5a moves the registration to the owning
Forge config's common `mixins` list and verifies source, generated resources,
exact Create bytecode, and the final reobfuscated jar together.
