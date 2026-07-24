clear @s minecraft:warped_fungus_on_a_stick[minecraft:custom_data~{superhardcore:{heart_essence:1b}}] 1
scoreboard players add @s shc_hp 2
execute if score @s shc_hp matches 40.. run scoreboard players set @s shc_hp 40
execute if score @s shc_hp matches 8 run attribute @s minecraft:max_health base set 8
execute if score @s shc_hp matches 10 run attribute @s minecraft:max_health base set 10
execute if score @s shc_hp matches 12 run attribute @s minecraft:max_health base set 12
execute if score @s shc_hp matches 14 run attribute @s minecraft:max_health base set 14
execute if score @s shc_hp matches 16 run attribute @s minecraft:max_health base set 16
execute if score @s shc_hp matches 18 run attribute @s minecraft:max_health base set 18
execute if score @s shc_hp matches 20 run attribute @s minecraft:max_health base set 20
execute if score @s shc_hp matches 22 run attribute @s minecraft:max_health base set 22
execute if score @s shc_hp matches 24 run attribute @s minecraft:max_health base set 24
execute if score @s shc_hp matches 26 run attribute @s minecraft:max_health base set 26
execute if score @s shc_hp matches 28 run attribute @s minecraft:max_health base set 28
execute if score @s shc_hp matches 30 run attribute @s minecraft:max_health base set 30
execute if score @s shc_hp matches 32 run attribute @s minecraft:max_health base set 32
execute if score @s shc_hp matches 34 run attribute @s minecraft:max_health base set 34
execute if score @s shc_hp matches 36 run attribute @s minecraft:max_health base set 36
execute if score @s shc_hp matches 38 run attribute @s minecraft:max_health base set 38
execute if score @s shc_hp matches 40 run attribute @s minecraft:max_health base set 40
effect give @s minecraft:instant_health 1 1 true
tellraw @s [{"text":"하트의 정수를 사용했습니다. 현재 최대 체력: ","color":"red"},{"score":{"name":"@s","objective":"shc_hp"}},{"text":" / 40","color":"red"}]
