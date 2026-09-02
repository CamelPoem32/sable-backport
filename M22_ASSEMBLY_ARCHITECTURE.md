# M22 Simulated Assembly Architecture

Frozen upstream: `Creators-of-Aeronautics/Simulated-Project`, Simulated `1.3.0`,
commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Canonical Upstream Path

Registry ID: `simulated:physics_assembler`.

Upstream block class:
`simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlock.java`.

Upstream block entity class:
`simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity.java`.

User interaction:
`PhysicsAssemblerBlock.useWithoutItem` starts a client hold interaction for a
local player. `PhysicsAssemblerGUIHandler.release` sends `AssemblePacket` when
the lever reaches the threshold. A Create deployer fake player calls
`assembleOrDisassemble` directly server-side.

Server call graph:

```text
PhysicsAssemblerGUIHandler.release
  -> AssemblePacket(blockPos)
  -> PhysicsAssemblerBlockEntity.assembleOrDisassemble
     -> if assembler BE is not inside a Sable sublevel:
          toAssemble = assemblerPos.relative(PhysicsAssemblerBlock.getStickyFacing(state))
          SimAssemblyHelper.assembleFromSingleBlock(level, assemblerPos, toAssemble, true, true)
            -> SimAssemblyContraption.searchMovedStructure(level, toAssemble)
               -> 18-neighbor traversal
               -> Create BlockMovementChecks
               -> Super Glue and Honey Glue adjacency
               -> chassis, piston, gantry, bogey, cart-assembler special cases
            -> SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchor, blocks, bounds)
               -> allocate ServerSubLevel
               -> move BlockStates and BlockEntity NBT into hidden plot storage
               -> build mass/collision/body through Sable
               -> set visible logical pose
     -> if assembler BE is inside a ServerSubLevel:
          validate disassembly
          align to nearest 90-degree yaw with a temporary free constraint
          SimAssemblyHelper.disassembleSubLevel(level, subLevel, rawAssemblerPos, visibleGoal, rotation, true)
            -> collect non-air blocks from plot
            -> SubLevelAssemblyHelper.moveBlocks(parentLevel, transform, blocks)
            -> move tracking points
```

## Target M22 Adaptation

The target keeps `simulated:physics_assembler` as the canonical assembly block.
The Forge 1.20.1 backport does not port the client hold GUI/Veil packet path in
M22. Instead, ordinary server-side right-click on the registered Physics
Assembler directly invokes `PhysicsAssemblerBlockEntity.assembleOrDisassemble`.
This preserves the canonical block and lifecycle action without adding Veil
network/bootstrap dependencies.

Assembly starts at the block below the assembler. The M22 fixture therefore
places the assembler on top of the payload, matching the upstream mounted
assembler intent while avoiding the not-yet-ported face-attached state model.

Sable ownership is not copied into Simulated. M22 calls the existing backported
`SubLevelAssemblyHelper.assembleBlocks`, which owns allocation, hidden plot
coordinates, BlockEntity NBT transfer, mass tracker creation, collision upload,
physics body registration, tracking points, and pose initialization.

Disassembly computes:

```text
rawAssemblerPos = assembler block position inside the hidden plot
visibleGoal = floor(subLevel.logicalPose.transformPosition(center(rawAssemblerPos)))
transform = new AssemblyTransform(rawAssemblerPos, visibleGoal, 0, Rotation.NONE, parentLevel)
```

This means restored blocks are placed at the current visible transform, not the
original assembly position and not the hidden plot around `~20,000,000`.

## M22 Boundaries

Implemented now:

- `physics_assembler` block right-click lifecycle
- upstream-shaped structure collection
- ordinary BlockState and BlockEntity NBT movement through Sable
- guarded occupied-space disassembly
- test-only nudge through `RigidBodyHandle.teleport`
- fixture and inspect diagnostics

Deferred:

- client lever overlay and Veil packets
- Honey Glue entity mechanics
- Simulated-specific future block movement checks
- Create moving contraption glue/entity transfer
- alignment free-constraint disassembly motors
- spring, torsion, swivel, rope, docking, sensors, Aeronautics
- multiplayer qualification

## Diagnostics

Assembly logs:

```text
SABLE_M22_ASSEMBLY_TRANSFORM parentAnchor=... localAnchor=... rawAnchor=... visibleOrigin=... visibleDeltaAfterAssembly=...
```

`/sable m22 inspect <selector>` reports parent-world assembler state or
assembled Sable state, including block count, BlockEntity count, raw bounds,
mass, center of mass, body registration, collision geometry, and serialization
pointer presence.
