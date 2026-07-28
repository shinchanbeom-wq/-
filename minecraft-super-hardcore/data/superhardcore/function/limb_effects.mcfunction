# Body damage weakens natural healing; destroyed body blocks natural recovery with hunger.
execute if score @s shc_body matches 1..39 run effect give @s minecraft:hunger 2 0 true
execute if score @s shc_body matches ..0 run effect give @s minecraft:hunger 2 4 true
execute if score @s shc_body matches ..0 run effect give @s minecraft:weakness 2 0 true

# Damaged or destroyed arms reduce mining speed and combat performance.
execute if score @s shc_left_arm matches 1..49 run effect give @s minecraft:mining_fatigue 2 0 true
execute if score @s shc_right_arm matches 1..49 run effect give @s minecraft:mining_fatigue 2 0 true
execute if score @s shc_left_arm matches ..0 run effect give @s minecraft:mining_fatigue 2 3 true
execute if score @s shc_right_arm matches ..0 run effect give @s minecraft:mining_fatigue 2 3 true
execute if score @s shc_left_arm matches ..0 run effect give @s minecraft:weakness 2 1 true
execute if score @s shc_right_arm matches ..0 run effect give @s minecraft:weakness 2 1 true
execute if score @s shc_left_arm matches ..0 run item replace entity @s weapon.offhand with minecraft:air

# Damaged legs reduce speed and jump; each destroyed leg adds roughly -50% movement through Slowness IV.
execute if score @s shc_left_leg matches 1..49 run effect give @s minecraft:slowness 2 0 true
execute if score @s shc_right_leg matches 1..49 run effect give @s minecraft:slowness 2 0 true
execute if score @s shc_left_leg matches 1..49 run effect give @s minecraft:jump_boost 2 128 true
execute if score @s shc_right_leg matches 1..49 run effect give @s minecraft:jump_boost 2 128 true
execute if score @s shc_left_leg matches ..0 run effect give @s minecraft:slowness 2 3 true
execute if score @s shc_right_leg matches ..0 run effect give @s minecraft:slowness 2 3 true
execute if score @s shc_left_leg matches ..0 run effect give @s minecraft:jump_boost 2 250 true
execute if score @s shc_right_leg matches ..0 run effect give @s minecraft:jump_boost 2 250 true
