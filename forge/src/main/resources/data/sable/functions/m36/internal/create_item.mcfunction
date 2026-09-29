summon minecraft:armor_stand ~ ~ ~ {Tags:["sable_m36_origin","sable_m36_v1","sable_m36_item"],Invisible:1b,Marker:1b,NoGravity:1b}
fill ~-12 ~ ~ ~-1 ~ ~ create:piston_extension_pole[facing=east]
setblock ~ ~ ~ create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted]
setblock ~1 ~ ~ create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]
setblock ~1 ~1 ~ create:portable_storage_interface[facing=east]
setblock ~1 ~ ~1 minecraft:chest[facing=north,type=single,waterlogged=false]
item replace block ~1 ~ ~1 container.0 with minecraft:cobblestone 16
setblock ~ ~-2 ~ create:creative_motor[facing=up]
data merge block ~ ~-2 ~ {ScrollValue:-64}
setblock ~ ~-1 ~ create:clutch[axis=y,powered=false]
setblock ~ ~-1 ~1 minecraft:lever[face=wall,facing=south,powered=true]
setblock ~ ~ ~-2 minecraft:iron_block
setblock ~ ~ ~-1 minecraft:iron_block
setblock ~ ~1 ~-2 simulated:physics_assembler
setblock ~15 ~1 ~ create:portable_storage_interface[facing=west]
setblock ~15 ~ ~ minecraft:hopper[facing=south,enabled=true]
setblock ~15 ~ ~1 minecraft:chest[facing=north,type=single,waterlogged=false]
summon minecraft:armor_stand ~15 ~1 ~ {Tags:["sable_m36_station"],Invisible:1b,Marker:1b,NoGravity:1b}
sable_m36 validate
