function sable:m35/internal/lane
item replace block ~1 ~ ~1 container.0 with minecraft:cobblestone 16
data merge block ~1 ~1 ~ {Filter:{id:"minecraft:cobblestone",Count:1b},ScrollValue:0}
setblock ~ ~-1 ~1 minecraft:lever[face=wall,facing=south,powered=true]
tellraw @a[distance=..32] {"text":"[M35] RESET: lane rebuilt, chest 16 cobblestone, Roller mode 0, lever ON. Static fixture only.","color":"green"}
