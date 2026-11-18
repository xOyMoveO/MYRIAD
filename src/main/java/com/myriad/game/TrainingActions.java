package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** All training panel requests are validated and applied on the server. */
public final class TrainingActions {
    private TrainingActions(){}
    public static void apply(ServerPlayer player,String action,boolean value){
        boolean global=action.equals("enabled")||action.equals("nocooldown")||action.equals("nomana")||action.equals("nogold");
        if(global&&!player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)){
            player.sendSystemMessage(Component.translatable("message.myriad.no_permission"));return;
        }
        if(!ModState.isEnabled()){
            player.sendSystemMessage(Component.translatable("screen.myriad.train.require_mod"));return;
        }
        if(action.equals("enabled")){
            if(value&&!TrainingMode.isEnabled()&&GameManager.get()!=null&&GameManager.get().getPhase()==GameManager.Phase.PLAYING){
                player.sendSystemMessage(Component.translatable("screen.myriad.train.match_running"));return;
            }
            TrainingMode.setEnabled(value);syncAll(player);return;
        }
        if(!TrainingMode.isEnabled()){
            player.sendSystemMessage(Component.translatable("screen.myriad.train.require_training"));return;
        }
        var data=PlayerDataProvider.get(player);
        if(data==null)return;
        if(action.startsWith("level:")){
            int level;try{level=Integer.parseInt(action.substring(6));}catch(NumberFormatException e){return;}
            if(level<1||level>LevelSystem.maxLevel(data)||!FirstGroupSkills.active(data.getCharacterId()))return;
            float health=player.getHealth(),mana=data.getMana();
            if(level<data.getLevel()){
                AbilityRuntime.clear(player);data.setLevel(level);data.refundAllSkillPointsForTraining();data.clearSkillCooldowns();
            }else{
                data.setLevel(level);
                int spent=data.getSkillLevels().values().stream().mapToInt(rank->Math.max(0,rank)).sum();
                data.setSkillPoints(Math.max(0,level-spent));
            }
            data.setXp(0);LevelSystem.applyLevelAttributes(player,level);PlayerHealthSystem.recalculate(player);
            player.setHealth(Math.min(health,player.getMaxHealth()));
            data.setMana((float)Math.min(mana,player.getAttributeValue(ModAttributes.MAX_MANA)));
        }else switch(action){
            case "level" -> LevelSystem.addXp(player,data,LevelSystem.xpToNext(data.getLevel()));
            case "gold" -> data.addGold(value?10000:1000);
            case "refresh" -> data.clearSkillCooldowns();
            case "restore" -> {player.setHealth(player.getMaxHealth());data.setMana((float)player.getAttributeValue(ModAttributes.MAX_MANA));}
            case "faction_guardian" -> PlayerDataProvider.assignTeam(player,Faction.GUARDIAN);
            case "faction_destruction" -> PlayerDataProvider.assignTeam(player,Faction.DESTRUCTION);
            case "nocooldown" -> {
                TrainingMode.setNoCooldown(value);
                if(value){data.clearSkillCooldowns();for(var p:player.level().getServer().getPlayerList().getPlayers())PlayerDataProvider.get(p).clearSkillCooldowns();}
            }
            case "nomana" -> {TrainingMode.setNoMana(value);if(value)data.setMana((float)player.getAttributeValue(ModAttributes.MAX_MANA));}
            case "nogold" -> TrainingMode.setNoGoldCost(value);
            default -> {return;}
        }
        if(global)syncAll(player);else PlayerDataProvider.syncToClient(player);
    }
    private static void syncAll(ServerPlayer source){
        for(var player:source.level().getServer().getPlayerList().getPlayers())PlayerDataProvider.syncToClient(player);
        if(!source.level().getServer().getPlayerList().getPlayers().contains(source))PlayerDataProvider.syncToClient(source);
    }
}
