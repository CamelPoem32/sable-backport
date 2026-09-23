# M33 Runtime Test Commands

These commands are valid for Minecraft 1.20.1 with Create 6.0.8 and cheats enabled. Registry IDs
and Deployer blockstate properties are verified by `verifyM33DeployerParentWorldInteraction`.

## One-time inventory setup

Paste every command:

```mcfunction
/give @s create:deployer 3
/give @s create:wrench 1
/give @s minecraft:cobblestone 64
/give @s minecraft:iron_pickaxe 1
/give @s minecraft:chest 3
/give @s minecraft:stone 64
/give @s minecraft:lever 4
/give @s create:sticky_mechanical_piston 1
/give @s create:piston_extension_pole 4
/give @s create:radial_chassis 4
/give @s create:creative_motor 1
/give @s create:shaft 4
/give @s create:super_glue 1
/give @s simulated:physics_assembler 1
```

## Shared carriage and orientation

Reuse the runtime-validated M31/M32 moving piston carriage. Build and configure it before assembling
the outer Sable body.

```text
WEST                                                             EAST

[motor/shaft][sticky piston]====[radial chassis][Deployer] ---> [target]
                                               working hand --->
                              [chest glued to the moving chassis]
```

Exact manual steps:

1. Stop and disassemble the inner piston contraption.
2. Place the Deployer at the carriage nose with its working hand facing EAST. Glue its WEST/back
   side to the Radial Chassis.
3. Glue the chest to the same moving chassis. The chest must move with the Deployer.
4. The Sticky Mechanical Piston must move the chassis and Deployer EAST.
5. The interaction target is the cell exactly two blocks EAST of the Deployer's resting block.
6. Reassemble the inner piston. For Sable tests, then assemble the complete body with the Physics
   Assembler.

To place a precisely oriented Deployer, stand in its block cell before assembly and paste:

```mcfunction
/setblock ~ ~ ~ create:deployer[axis_along_first=false,facing=east]
```

To create or clear its mounted chest, stand in the chest block cell and paste one of:

```mcfunction
/setblock ~ ~ ~ minecraft:chest[facing=north,type=single,waterlogged=false]
/setblock ~ ~ ~ minecraft:air
```

## Fixture A: block placement

Use a fresh Deployer in default USE mode. Stand in the mounted chest cell:

### SETUP COMMANDS

```mcfunction
/setblock ~ ~ ~ minecraft:chest[facing=north,type=single,waterlogged=false]
/item replace block ~ ~ ~ container.0 with minecraft:cobblestone 64
```

Stand in the physical parent-world target cell exactly two blocks in front of the resting Deployer:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:stone
```

### MINIMAL MANUAL STEPS

1. Confirm the Deployer hand faces EAST and is still in default USE mode.
2. Assemble/cycle the inner piston so the Deployer reaches the target.
3. For the Sable gate, assemble the outer body first, then cycle the same inner piston.

### EXPECTED PASS

- Cobblestone appears in the visible target cell above the stone support.
- The chest stack decreases through native Create mounted-storage bookkeeping.
- After translating or rotating Sable, reset the fixture at the new physical hand destination and
  the new visible target is used.
- No cobblestone is placed in hidden plot storage.

### RESET COMMANDS

Stand in the target cell:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:stone
```

Stand in the mounted chest cell:

```mcfunction
/item replace block ~ ~ ~ container.0 with minecraft:cobblestone 64
```

### CLEANUP COMMANDS

Stand in the target cell:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:air
```

Stand in the mounted chest cell:

```mcfunction
/setblock ~ ~ ~ minecraft:air
```

## Fixture B: right-click block interaction

Use a fresh Deployer in default USE mode and leave its moving chest empty. Stand in the target cell:

### SETUP COMMANDS

```mcfunction
/setblock ~ ~-1 ~ minecraft:stone
/setblock ~ ~ ~ minecraft:lever[face=floor,facing=north,powered=false]
```

If a mounted chest remains, stand in its cell and clear its first slot:

```mcfunction
/item replace block ~ ~ ~ container.0 with minecraft:air
```

### MINIMAL MANUAL STEPS

1. Confirm the fresh Deployer is in default USE mode and its hand faces EAST.
2. Cycle the inner piston once so the hand reaches the lever.

### EXPECTED PASS

- The visible lever changes from `powered=false` to `powered=true` exactly as with a normal Create
  Deployer.
- Stationary, translated, and 90-degree-rotated Sable tests act on the lever at the physical hand
  destination.
- Hidden plot storage does not change.

### RESET COMMANDS

Stand in the target cell:

```mcfunction
/setblock ~ ~-1 ~ minecraft:stone
/setblock ~ ~ ~ minecraft:lever[face=floor,facing=north,powered=false]
```

### CLEANUP COMMANDS

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:air
```

## Fixture C: PUNCH block breaking

Use a fresh Deployer. Before inner or outer assembly, hold the Create Wrench and right-click the
protruding hand/front half once. The goggles/tooltip mode indication must change from Using to Punching.
This is the only manual mode-setting action.

Stand in the mounted chest cell:

### SETUP COMMANDS

```mcfunction
/setblock ~ ~ ~ minecraft:chest[facing=north,type=single,waterlogged=false]
/item replace block ~ ~ ~ container.0 with minecraft:iron_pickaxe 1
```

Stand in the physical parent-world target cell:

```mcfunction
/setblock ~ ~ ~ minecraft:stone
```

### MINIMAL MANUAL STEPS

1. Wrench the fresh Deployer's front half once into Punching mode before assembly.
2. Confirm its working hand faces EAST.
3. Cycle the inner piston into the stone and allow native Create breaking progress to finish.

### EXPECTED PASS

- The visible stone breaks through native Create PUNCH behavior.
- Native progress/stall, drops, tool durability, and mounted-storage behavior are preserved.
- Translation and rotation move the physical target; no hidden plot block is broken.

### RESET COMMANDS

Stand in the target cell:

```mcfunction
/setblock ~ ~ ~ minecraft:stone
```

Stand in the mounted chest cell:

```mcfunction
/item replace block ~ ~ ~ container.0 with minecraft:iron_pickaxe 1
```

### CLEANUP COMMANDS

Stand in the target cell, then in the mounted chest cell:

```mcfunction
/setblock ~ ~ ~ minecraft:air
```

## Normal-world regression and Sable sequence

Run each fixture in this order:

1. Leave the outer body disassembled and cycle the inner carriage. This is the normal-world control.
2. Reset the exact target with the fixture commands.
3. Assemble the outer Sable body and repeat while stationary.
4. Translate the body. Stand at the new physical hand destination, paste the RESET commands, and
   repeat.
5. Rotate the body 90 degrees. Stand at the new physical hand destination, paste RESET, and repeat.
6. Disassemble the outer body and repeat the normal-world control.

PASS requires native item consumption/state change/breaking behavior at the visible parent target in
every supported pose. Contact with another Sable body is intentionally unsupported and must be a
safe no-op.
