tag @s add shc_initialized
scoreboard players set @s shc_deaths 0
scoreboard players set @s shc_hp 6
scoreboard players set @s shc_body 100
scoreboard players set @s shc_left_arm 100
scoreboard players set @s shc_right_arm 100
scoreboard players set @s shc_left_leg 100
scoreboard players set @s shc_right_leg 100
scoreboard players set @s shc_hurt_cd 0
attribute @s minecraft:max_health base set 6
effect give @s minecraft:resistance 5 4 true
effect give @s minecraft:saturation 1 0 true
tellraw @s {"text":"Super Hardcore: 기본 체력은 하트 3칸입니다. /trigger 신체 로 신체 상태를 확인하세요.","color":"gold"}
