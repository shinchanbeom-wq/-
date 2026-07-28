scoreboard players set @s shc_hurt_cd 20
scoreboard players remove @s shc_body 1
scoreboard players remove @s shc_left_arm 1
scoreboard players remove @s shc_right_arm 1
scoreboard players remove @s shc_left_leg 1
scoreboard players remove @s shc_right_leg 1
tellraw @s {"text":"피해를 받아 신체 부위 내구도가 감소했습니다.","color":"dark_red"}
