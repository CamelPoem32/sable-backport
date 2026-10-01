summon minecraft:armor_stand ~ ~ ~ {Tags:["sable_m37_origin","sable_m37_v1","sable_m37_entity"],Invisible:1b,Marker:1b,NoGravity:1b}
fill ~-12 ~ ~ ~-1 ~ ~ create:piston_extension_pole[facing=east]
setblock ~ ~ ~ create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted]
setblock ~1 ~ ~ create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]
setblock ~1 ~1 ~ create:andesite_funnel[facing=east,extracting=false,powered=false,waterlogged=false]
setblock ~1 ~ ~1 minecraft:chest[facing=north,type=single,waterlogged=false]
setblock ~ ~-2 ~ create:creative_motor[facing=up]
data merge block ~ ~-2 ~ {ScrollValue:-64}
setblock ~ ~-1 ~ create:clutch[axis=y,powered=false]
setblock ~ ~-1 ~1 minecraft:lever[face=wall,facing=south,powered=true]
setblock ~ ~ ~-2 minecraft:iron_block
setblock ~ ~ ~-1 minecraft:iron_block
setblock ~ ~1 ~-2 simulated:physics_assembler
setblock ~14 ~ ~ minecraft:stone
summon minecraft:armor_stand ~14 ~1 ~ {Tags:["sable_m37_station"],Invisible:1b,Marker:1b,NoGravity:1b}
sable_m37 validate
