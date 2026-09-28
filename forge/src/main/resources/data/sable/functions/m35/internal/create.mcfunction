summon minecraft:armor_stand ~ ~ ~ {Tags:["sable_m35_origin","sable_m35_v5"],Invisible:1b,Marker:1b,NoGravity:1b}
fill ~-12 ~ ~ ~-1 ~ ~ create:piston_extension_pole[facing=east]
setblock ~ ~ ~ create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted]
setblock ~1 ~ ~ create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]
setblock ~1 ~1 ~ create:mechanical_roller[facing=east,waterlogged=false]
setblock ~1 ~ ~1 minecraft:chest[facing=north,type=single,waterlogged=false]
item replace block ~1 ~ ~1 container.0 with minecraft:cobblestone 16
data merge block ~1 ~1 ~ {Filter:{id:"minecraft:cobblestone",Count:1b},ScrollValue:0}
setblock ~ ~-2 ~ create:creative_motor[facing=up]
data merge block ~ ~-2 ~ {ScrollValue:-64}
setblock ~ ~-1 ~ create:clutch[axis=y,powered=false]
setblock ~ ~-1 ~1 minecraft:lever[face=wall,facing=south,powered=true]
setblock ~ ~ ~-2 minecraft:iron_block
setblock ~ ~ ~-1 minecraft:iron_block
setblock ~ ~1 ~-2 simulated:physics_assembler
fill ~4 ~-1 ~ ~14 ~1 ~ minecraft:air
fill ~4 ~-2 ~ ~14 ~-2 ~ minecraft:stone
fill ~6 ~ ~ ~8 ~ ~ minecraft:stone
fill ~10 ~-2 ~ ~11 ~-2 ~ minecraft:air
fill ~10 ~-3 ~ ~11 ~-3 ~ minecraft:stone
sable_m35 validate
