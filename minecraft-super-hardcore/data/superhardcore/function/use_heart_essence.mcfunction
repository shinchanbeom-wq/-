scoreboard players set @s 하트강화 0
execute unless score @s shc_hp matches ..39 run tellraw @s {"text":"이미 최대 체력(하트 20칸)입니다.","color":"red"}
execute if score @s shc_hp matches ..39 if items entity @s container.* minecraft:heart_of_the_sea[minecraft:custom_data~{superhardcore:{heart_essence:1b}}] run function superhardcore:heart_success
execute if score @s shc_hp matches ..39 unless items entity @s container.* minecraft:heart_of_the_sea[minecraft:custom_data~{superhardcore:{heart_essence:1b}}] run tellraw @s {"text":"하트의 정수가 필요합니다. 빨간색 염료와 금 조각으로 조합하세요.","color":"red"}
