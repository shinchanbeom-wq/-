# Super Hardcore datapack initialization
scoreboard objectives add shc_deaths deathCount
scoreboard objectives add shc_timer dummy
scoreboard objectives add shc_hp dummy
scoreboard objectives add shc_weight dummy
scoreboard objectives add shc_hurt_cd dummy
scoreboard objectives add 신체 trigger
scoreboard objectives add 하트강화 trigger
scoreboard objectives add 붕대사용 trigger
scoreboard objectives add shc_body dummy
scoreboard objectives add shc_left_arm dummy
scoreboard objectives add shc_right_arm dummy
scoreboard objectives add shc_left_leg dummy
scoreboard objectives add shc_right_leg dummy
scoreboard players set #global shc_timer 0
function superhardcore:rules
tellraw @a {"text":"[Super Hardcore] enabled: 3-heart start, body durability, weight penalties, craftable heart essence and bandages.","color":"dark_red","bold":true}
