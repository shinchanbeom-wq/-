# Keep the challenge rules active even if operators change them temporarily.
scoreboard players add #global shc_timer 1
execute if score #global shc_timer matches 200.. run function superhardcore:rules
execute if score #global shc_timer matches 200.. run scoreboard players set #global shc_timer 0

# Initialize each survival/adventure player once.
execute as @a[tag=!shc_initialized,gamemode=!spectator,gamemode=!creative] run function superhardcore:init_player

# Allow player-facing trigger commands and item right-click actions.
scoreboard players enable @a 신체
scoreboard players enable @a 하트강화
scoreboard players enable @a 붕대사용
scoreboard players enable @a 메뉴
execute as @a[scores={신체=1..}] run function superhardcore:body_status
execute as @a[scores={하트강화=1..}] run function superhardcore:use_heart_essence
execute as @a[scores={붕대사용=1..}] run function superhardcore:use_bandage
execute as @a[scores={메뉴=1..}] run function superhardcore:menu
execute as @a[scores={shc_heart_used=1..}] run function superhardcore:use_heart_essence
execute as @a[scores={shc_menu_used=1..}] at @s if entity @s[nbt={Pose:"CROUCHING"}] run function superhardcore:menu
scoreboard players set @a[scores={shc_heart_used=1..}] shc_heart_used 0
scoreboard players set @a[scores={shc_menu_used=1..}] shc_menu_used 0

# One-life rule: after a death, the player becomes a spectator.
execute as @a[scores={shc_deaths=1..}] run function superhardcore:death

# Recompute inventory weight and penalties.
execute as @a[tag=shc_initialized,gamemode=!spectator,gamemode=!creative] run function superhardcore:injury
execute as @a[tag=shc_initialized,gamemode=!spectator,gamemode=!creative] run function superhardcore:weight
execute as @a[tag=shc_initialized,gamemode=!spectator,gamemode=!creative] run function superhardcore:limb_effects

# Upgrade hostile mobs as they appear.
function superhardcore:mobs/tick
