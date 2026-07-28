clear @s minecraft:paper[minecraft:custom_data~{superhardcore:{bandage:1b}}] 1
scoreboard players add @s shc_body 15
scoreboard players add @s shc_left_arm 15
scoreboard players add @s shc_right_arm 15
scoreboard players add @s shc_left_leg 15
scoreboard players add @s shc_right_leg 15
execute if score @s shc_body matches 101.. run scoreboard players set @s shc_body 100
execute if score @s shc_left_arm matches 101.. run scoreboard players set @s shc_left_arm 100
execute if score @s shc_right_arm matches 101.. run scoreboard players set @s shc_right_arm 100
execute if score @s shc_left_leg matches 101.. run scoreboard players set @s shc_left_leg 100
execute if score @s shc_right_leg matches 101.. run scoreboard players set @s shc_right_leg 100
tellraw @s {"text":"붕대를 사용해 모든 신체 부위 내구도를 15 회복했습니다.","color":"green"}
