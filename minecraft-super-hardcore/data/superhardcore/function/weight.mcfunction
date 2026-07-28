scoreboard players set @s shc_weight 0
execute if items entity @s container.* #superhardcore:light_weight run scoreboard players add @s shc_weight 1
execute if items entity @s container.* #superhardcore:medium_weight run scoreboard players add @s shc_weight 2
execute if items entity @s container.* #superhardcore:heavy_weight run scoreboard players add @s shc_weight 4
execute if score @s shc_weight matches 1..2 run effect give @s minecraft:slowness 2 0 true
execute if score @s shc_weight matches 3..5 run effect give @s minecraft:slowness 2 1 true
execute if score @s shc_weight matches 6.. run effect give @s minecraft:slowness 2 2 true
