scoreboard players set @s 붕대사용 0
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{superhardcore:{bandage:1b}}] run function superhardcore:bandage_success
execute unless items entity @s container.* minecraft:paper[minecraft:custom_data~{superhardcore:{bandage:1b}}] run tellraw @s {"text":"붕대가 필요합니다. 양털 2개로 조합하세요.","color":"red"}
