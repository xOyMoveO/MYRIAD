package com.myriad.combat;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.game.ModState;
import com.myriad.game.VisionSystem;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;
import java.util.WeakHashMap;

/** Vanilla handles picking, swing and immediate attack. Only validate game rules
 * and retain pre-reset strength for the existing mod-attribute damage pipeline. */
public final class VanillaMeleeAttack {
    private record Snapshot(UUID target,long tick,float strength){}
    private static final WeakHashMap<ServerPlayer,Snapshot> STRENGTH=new WeakHashMap<>();
    private VanillaMeleeAttack(){}
    /** Gameplay attack routing, independent of any custom animation system. */
    public static boolean usesVanilla(CharacterType role){
        return role != null && switch(role){
            case XUANCHEN, HANZE, QINGLUO, YUNSHENG -> false;
            default -> true;
        };
    }

    public static boolean prepare(ServerPlayer player,Entity raw){
        var data=PlayerDataProvider.get(player);
        if(!ModState.isEnabled()||!usesVanilla(CharacterType.byId(data.getCharacterId()))
                ||!BasicAttackSystem.canAttackNow(player,data)||AbilityRuntime.channeling(player)
                ||player.getAttackStrengthScale(.5f)<1
                ||data.getBuff("g1_stun")>0||!PlayerDataProvider.isInitialWeapon(player.getMainHandItem())
                ||!(raw instanceof LivingEntity target)||!target.isAlive()||target==player
                ||player.level()!=target.level()||SkillTargets.ally(player,target)
                ||target instanceof net.minecraft.world.entity.player.Player victim&&(victim.isCreative()||victim.isSpectator())
                ||!player.isWithinEntityInteractionRange(target,0)||!player.hasLineOfSight(target)
                ||!VisionSystem.canSee(player,target)||target.isInvisibleTo(player))return false;
        // Player.onAttack() resets strength BEFORE hurtOrSimulate in Minecraft 26.2.
        STRENGTH.put(player,new Snapshot(target.getUUID(),player.level().getGameTime(),player.getAttackStrengthScale(.5f)));
        com.myriad.character.skills.shuo.ShuoCombat.basic(player);
        com.myriad.character.skills.keheng.KehengBasic.swing(player);
        com.myriad.character.skills.yingxiao.rework.NightBasicVisuals.swing(player);
        return true;
    }
    public static float consumeStrength(ServerPlayer player,LivingEntity target){
        Snapshot snapshot=STRENGTH.remove(player);
        return snapshot!=null&&snapshot.tick==player.level().getGameTime()&&snapshot.target.equals(target.getUUID())
                ?snapshot.strength:player.getAttackStrengthScale(.5f);
    }
    /** Only fully charged clicks are accepted; successful attacks never use a weak multiplier. */
    public static float damageScale(float strength){
        return 1;
    }
    public static void clear(UUID player){STRENGTH.keySet().removeIf(p->p.getUUID().equals(player));
        com.myriad.character.skills.yingxiao.rework.NightBasicVisuals.clear(player);}
}
