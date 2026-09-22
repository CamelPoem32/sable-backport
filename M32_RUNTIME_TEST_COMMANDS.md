# M32 Runtime Test Commands

These commands are for Minecraft 1.20.1 with cheats enabled. They use registry IDs and state
properties verified from Create 6.0.8 and the target Minecraft classes/resources.

## One-time inventory setup

Paste every command. This supplies all actors and the same moving-carriage parts used by the M31
Mechanical Drill fixture.

```mcfunction
/give @s create:mechanical_saw 1
/give @s create:mechanical_harvester 1
/give @s create:mechanical_plough 1
/give @s create:sticky_mechanical_piston 1
/give @s create:piston_extension_pole 4
/give @s create:radial_chassis 4
/give @s create:creative_motor 1
/give @s create:shaft 4
/give @s create:super_glue 1
/give @s simulated:physics_assembler 1
/give @s minecraft:lever 4
```

### Minimum manual assembly

Reuse the runtime-validated M31 moving piston carriage. Before assembling the outer Sable body:

1. Stop and disassemble the inner piston contraption.
2. Replace the Mechanical Drill at the carriage nose with the actor under test.
3. Point the actor's working face EAST and glue its back to the Radial Chassis.
4. The Sticky Mechanical Piston must move the chassis and actor EAST into the target cell.
5. Reassemble the inner piston, then assemble the complete body with the Physics Assembler.

```text
WEST                                                        EAST

[motor/shaft] [sticky piston]====[radial chassis][actor]  ->  [target]
                                                    working face ->
```

If you prefer command placement before assembly, stand in the desired actor block cell and use the
actor-specific `/setblock` command below. Commands cannot insert a block into an already assembled
moving Sable sublevel; place or replace the actor before outer assembly.

## Mechanical Saw

### Actor placement

Stand in the Saw block cell before assembly:

```mcfunction
/setblock ~ ~ ~ create:mechanical_saw[axis_along_first=false,facing=east,flipped=false]
```

The blade faces EAST. Glue the WEST/back side to the moving chassis. The carriage moves EAST.

```text
[moving chassis][Saw] ->  [oak log]
                  EAST ->
```

### Target setup

Stand in the parent-world cell the Saw should enter. This creates a three-log vertical target:

```mcfunction
/fill ~ ~ ~ ~ ~2 ~ minecraft:oak_log[axis=y]
```

### Target reset

```mcfunction
/fill ~ ~ ~ ~ ~2 ~ minecraft:oak_log[axis=y] replace
```

### PASS

- In normal world, native Create Saw behavior cuts the visible log/tree target.
- In assembled Sable, the same visible parent-world logs are cut.
- After translating or rotating the body, place the target at the new physical blade destination;
  only that target is cut.
- Drops enter mounted storage or appear near the visible cut, never near hidden plot coordinates.

### Cleanup

Stand at the same target origin:

```mcfunction
/fill ~ ~ ~ ~ ~2 ~ minecraft:air replace
```

## Mechanical Harvester

### Actor placement

Stand in the Harvester block cell before assembly:

```mcfunction
/setblock ~ ~ ~ create:mechanical_harvester[facing=east,waterlogged=false]
```

The blades face EAST. Glue the WEST/back side to the moving chassis. The carriage moves EAST through
the crop cell.

```text
[moving chassis][Harvester] -> [wheat age=7]
                         EAST -> [farmland]
```

### Crop setup

Stand in the parent-world crop cell the Harvester should enter:

```mcfunction
/setblock ~ ~-1 ~ minecraft:farmland[moisture=7]
/setblock ~ ~ ~ minecraft:wheat[age=7]
/setblock ~2 ~-1 ~ minecraft:water[level=0]
```

The water is two blocks away and keeps the command-created farmland hydrated.

### Target reset

```mcfunction
/setblock ~ ~-1 ~ minecraft:farmland[moisture=7]
/setblock ~ ~ ~ minecraft:wheat[age=7]
```

### PASS

- The fully grown visible wheat is harvested with native Create drops.
- With Create's default replant setting, wheat remains at its replanted low age; if that server
  setting is disabled, Create's normal non-replant result is accepted instead.
- Translating or rotating Sable changes the physical crop cell that is processed.
- No crop in hidden plot storage is treated as external terrain.

### Cleanup

Stand at the same crop origin:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:air
/setblock ~2 ~-1 ~ minecraft:air
```

## Mechanical Plough

### Actor placement

Stand in the Plough block cell before assembly:

```mcfunction
/setblock ~ ~ ~ create:mechanical_plough[facing=east,waterlogged=false]
```

The working face points EAST. Glue the WEST/back side to the moving chassis. The Plough's active air
cell passes directly above the dirt.

```text
[moving chassis][Plough] -> [air active cell]
                       EAST -> [dirt below]
```

### Terrain setup

Stand in the parent-world air cell the Plough should enter:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:dirt
```

### Target reset

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:dirt
```

### PASS

- Native Create Plough logic applies its diamond-hoe interaction to the visible dirt below, producing
  farmland when vanilla rules allow it.
- Blocks/entities in the active cell retain native Create Plough behavior.
- Translating or rotating Sable changes the physical active cell; an old target is not mutated later.

### Cleanup

Stand at the same terrain origin:

```mcfunction
/setblock ~ ~ ~ minecraft:air
/setblock ~ ~-1 ~ minecraft:air
```

## Rotation, translation, and moving-body sequence

Run this sequence separately for Saw, Harvester, and Plough:

1. Leave the outer body disassembled and run the inner carriage into the command-created target.
   This is the normal-world control.
2. Reset the target, assemble the outer body, and repeat while the body is stationary.
3. Translate the body, stand at the actor's new physical destination, and paste the actor's TARGET
   RESET commands there.
4. Rotate the body 90 degrees, again stand at the new physical destination, and paste TARGET RESET.
5. Move the body slowly while cycling the inner carriage. Place targets only in the visible parent
   world.
6. Disassemble the outer body and repeat the normal-world control.

For every gate, PASS means the visible target changes and the hidden plot representation does not.
Contact with another Sable body is intentionally unsupported in M32 and must be a safe no-op.

