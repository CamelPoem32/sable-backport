# M34 Runtime Test Commands

Minecraft 1.20.1 / Create 6.0.8, cheats enabled. Registry IDs and the Contact blockstate properties are checked by `verifyM34MovementBehaviourCoverage`. These are the only two newly modified actors. Use the existing runtime-proven inner moving piston carriage and outer Sable Physics Assembler. All coordinates below are relative to where **you** stand when pasting a command.

## Shared moving carriage

```mcfunction
/give @s create:sticky_mechanical_piston 1
/give @s create:piston_extension_pole 4
/give @s create:radial_chassis 4
/give @s create:creative_motor 1
/give @s create:shaft 4
/give @s create:super_glue 1
/give @s simulated:physics_assembler 1
```

```text
WEST                                                    EAST
[motor/shaft][sticky piston]====[radial chassis][ACTOR] ---> [target]
                                         glued back face     physical parent world
```

Stop and disassemble the inner piston, attach the actor's WEST/back side to the Radial Chassis with Super Glue, and make the piston move it EAST. Reassemble the inner Create contraption, then assemble the outer Sable body with the Physics Assembler. For rotated-body checks, rotate the outer Sable body and place the target at the actor's **new visible** front, not at the old raw plot position. Do not glue the target to the carriage.

## Redstone Contact

The moving Contact faces EAST. The stationary target Contact faces WEST. Stand in the exact parent-world cell the moving Contact will touch.

### SETUP COMMANDS

```mcfunction
/give @s create:redstone_contact 2
/give @s create:wrench 1
/setblock ~ ~ ~ create:redstone_contact[facing=west,powered=false]
```

For the moving Contact, stand in its carriage-nose cell before inner assembly and paste:

```mcfunction
/setblock ~ ~ ~ create:redstone_contact[facing=east,powered=false]
```

### EXPECTED PASS

Advance the carriage until the two faces meet. At the stationary target cell, paste:

```mcfunction
/execute if block ~ ~ ~ create:redstone_contact[facing=west,powered=true] run say M34_CONTACT_POWERED_PASS
```

Move the carriage away. The stationary Contact should return to its native unpowered state. Translate or rotate Sable, rebuild the stationary target at the new physical contact point, and repeat. A normal-world moving Contact after outer disassembly must retain Create behaviour.

### RESET COMMANDS

Stand in the target cell:

```mcfunction
/setblock ~ ~ ~ create:redstone_contact[facing=west,powered=false]
```

### CLEANUP COMMANDS

Stand in the target cell, then in the moving actor cell after disassembly:

```mcfunction
/setblock ~ ~ ~ minecraft:air
```

## Haunted Bell

The moving Haunted Bell must pass near the target mob. Stand at a clear visible parent-world test cell near the carriage path. The bell's native pulse uses the parent chunk and should reveal nearby mobs according to Create's normal Haunted Bell effect.

### SETUP COMMANDS

```mcfunction
/give @s create:haunted_bell 1
/summon minecraft:zombie ~ ~ ~ {Tags:["sable_m34_fixture"],NoAI:1b,PersistenceRequired:1b}
```

Manually glue the Haunted Bell to the EAST/front end of the moving Radial Chassis. It has no facing blockstate to configure. Reassemble the inner carriage and outer Sable body, then move the bell through the mob's visible vicinity.

### EXPECTED PASS

The Haunted Bell pulse appears around the visible parent-world bell/mob area, not at the hidden plot. Translate/rotate the outer body and repeat near a new tagged mob. After outer disassembly, an ordinary moving Create Haunted Bell behaves unchanged.

### RESET COMMANDS

```mcfunction
/kill @e[tag=sable_m34_fixture]
/summon minecraft:zombie ~ ~ ~ {Tags:["sable_m34_fixture"],NoAI:1b,PersistenceRequired:1b}
```

### CLEANUP COMMANDS

```mcfunction
/kill @e[tag=sable_m34_fixture]
```
