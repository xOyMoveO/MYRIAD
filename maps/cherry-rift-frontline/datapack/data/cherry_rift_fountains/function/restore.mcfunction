execute in minecraft:overworld positioned -152.5 81 39.5 run effect give @a[distance=..6,gamemode=!adventure,gamemode=!spectator] minecraft:instant_health 1 0 true
execute in minecraft:overworld positioned 153.5 81 -38.5 run effect give @a[distance=..6,gamemode=!adventure,gamemode=!spectator] minecraft:instant_health 1 0 true
schedule function cherry_rift_fountains:restore 2s replace
