execute as @e[type=minecraft:zombie,tag=!shc_upgraded] run function superhardcore:mobs/zombie_setup
execute as @e[type=minecraft:zombie] at @s if entity @a[distance=..8,gamemode=!creative,gamemode=!spectator] run function superhardcore:mobs/zombie_break
execute as @e[type=minecraft:skeleton,tag=!shc_upgraded] run tag @s add shc_upgraded
execute as @e[type=minecraft:skeleton] at @s as @e[type=minecraft:arrow,distance=..2,tag=!shc_shotgun,limit=1,sort=nearest] run function superhardcore:mobs/skeleton_shotgun
execute as @e[type=minecraft:creeper,tag=!shc_upgraded] run function superhardcore:mobs/creeper_setup
execute as @e[type=minecraft:spider,tag=!shc_upgraded] run tag @s add shc_upgraded
execute as @e[type=minecraft:spider] at @s run effect give @a[distance=..4,gamemode=!creative,gamemode=!spectator] minecraft:slowness 2 0 true
execute as @a[scores={shc_spider_kills=1..}] at @s run function superhardcore:mobs/spider_death_spawn
scoreboard players set @a[scores={shc_spider_kills=1..}] shc_spider_kills 0
