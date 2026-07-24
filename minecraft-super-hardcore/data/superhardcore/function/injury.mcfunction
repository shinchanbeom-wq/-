execute if score @s shc_hurt_cd matches 1.. run scoreboard players remove @s shc_hurt_cd 1
execute if data entity @s {HurtTime:10s} unless score @s shc_hurt_cd matches 1.. run function superhardcore:injury_apply
