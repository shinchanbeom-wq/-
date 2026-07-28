clear @s minecraft:warped_fungus_on_a_stick[minecraft:custom_data~{superhardcore:{heart_essence:1b}}] 1
scoreboard players add @s shc_hp 2
function superhardcore:health_sync
effect give @s minecraft:instant_health 1 1 true
tellraw @s [{"text":"하트의 정수를 사용했습니다. 현재 최대 체력: ","color":"red"},{"score":{"name":"@s","objective":"shc_hp"}},{"text":" / 40","color":"red"}]
