# M33.1 Deployer Mounted-Storage Test

Use Minecraft 1.20.1 / Create 6.0.8 with cheats enabled. The registry IDs below are retained from the verified M33 fixture. Run the diagnostic once with `-Dsable.m31.traceCreateActors=true`; no other Sable flag is needed.

## Give the complete fixture

```mcfunction
/give @s create:deployer 1
/give @s minecraft:chest 1
/give @s minecraft:cobblestone 64
/give @s create:super_glue 1
/give @s create:sticky_mechanical_piston 1
/give @s create:piston_extension_pole 16
/give @s create:radial_chassis 4
/give @s create:creative_motor 1
/give @s create:shaft 4
/give @s create:wrench 1
/give @s simulated:physics_assembler 1
```

## Build the inner carriage

Stop and disassemble the inner Create piston first. Arrange its moving section as follows. The piston moves EAST; the Deployer works NORTH, across the target lane. The chest is on the moving chassis, not on the stationary piston base or the outer Sable hull.

```text
                                   NORTH: target lane, eight empty cells
                                         ^ ^ ^ ^ ^ ^ ^ ^
WEST  [motor][sticky piston]====[moving radial chassis][Deployer]   EAST motion ->
                                  [Chest glued to moving chassis]
```

1. Glue the Deployer's back and the chest to the **same moving radial chassis**. The chest and Deployer must move as one inner Create contraption. Do not glue the chest only to the stationary Sable hull.
2. Leave the Deployer hand empty and in default USING mode. Place 16 or more cobblestone in the chest **before inner Create assembly**.
3. Extend the piston through at least eight block positions. Keep the target lane clear of the moving chassis.
4. At inner Create assembly, the chest's former static block cell should become air and the chest should visibly travel with the Deployer. A chest that remains stationary is not mounted storage for this actor.

Stand in the Deployer block cell before assembly to force its working face NORTH:

```mcfunction
/setblock ~ ~ ~ create:deployer[axis_along_first=false,facing=north]
```

Stand in the chest cell before assembly to create and fill it:

```mcfunction
/setblock ~ ~ ~ minecraft:chest[facing=north,type=single,waterlogged=false]
/item replace block ~ ~ ~ container.0 with minecraft:cobblestone 16
```

## Eight-placement lane

Stand in the **first parent-world target cell**, two blocks NORTH of the Deployer's first working position and at the same height. These commands clear eight cells eastward and put solid support beneath them. The target cells must remain in the ordinary visible world, not inside the Sable body.

```mcfunction
/fill ~ ~ ~ ~7 ~ ~ minecraft:air
/fill ~ ~-1 ~ ~7 ~-1 ~ minecraft:stone
```

Run the piston through the eight target positions. Expected result: eight visible cobblestone blocks, eight native hand refills, and exactly eight cobblestone consumed from the mounted chest. A fresh fixture with 16 cobblestone should have eight left. If the Deployer does not place on the first pass, align its first working cell with the first cleared target and verify the working face points NORTH; do not add an item directly to its hand.

Before testing Sable, verify the same inner piston/chest/Deployer works in the normal world. Then assemble the Sable body and repeat with a freshly filled mounted chest. The M28 transfer path is Mechanical-Bearing-only; this procedure does **not** assume an already assembled piston CCE is migrated across outer assembly. Reassemble the inner Create piston after each outer Sable transition if it was disassembled by that transition.

For the inventory-accounting check, inspect the chest's stack before inner assembly and after disassembling the inner Create piston. Do not run `/item replace block` against the chest's old position while it is a moving contraption: that position is intentionally air.

## Reset and cleanup

Stand in the first parent-world target cell:

```mcfunction
/fill ~ ~ ~ ~7 ~ ~ minecraft:air
/fill ~ ~-1 ~ ~7 ~-1 ~ minecraft:stone
```

After disassembling the inner piston, stand in the chest cell:

```mcfunction
/item replace block ~ ~ ~ container.0 with minecraft:cobblestone 16
```

When finished, stand in the first target cell:

```mcfunction
/fill ~ ~ ~ ~7 ~ ~ minecraft:air
/fill ~ ~-1 ~ ~7 ~-1 ~ minecraft:air
```

The diagnostic's `capturedChestCount`, `capturedChestBECount`, `mountedStorageCount`, and `matchingItemCount` identify the first failing ownership stage. `capturedChestCount=0` means the chest was never in the inner Create contraption; adding items to a stationary outer-Sable chest cannot refill it.
