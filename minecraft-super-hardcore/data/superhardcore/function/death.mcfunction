scoreboard players set @s shc_deaths 0
gamemode spectator @s
title @s title {"text":"GAME OVER","color":"dark_red","bold":true}
title @s subtitle {"text":"Super Hardcore에서는 부활할 수 없습니다.","color":"red"}
tellraw @a [{"selector":"@s","color":"red"},{"text":" 님이 Super Hardcore에서 탈락했습니다.","color":"dark_red"}]
