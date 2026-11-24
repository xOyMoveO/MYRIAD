package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The owner's hand poses use the existing presentation channel. */
public final class InkCastCues {
    private InkCastCues(){}

    static void select(Cast c,boolean active){
        emit(c,active?InkCastAnimation.AIM:InkCastAnimation.STOP,0,InkCastAnimation.REFRESH_LIFE);
    }
    static void charge(Cast c,boolean active){
        if(!active||c.windupProgress()==0||c.now()%2==0)
            emit(c,active?InkCastAnimation.WINDUP:InkCastAnimation.STOP,c.windupProgress()*16,
                    InkCastAnimation.REFRESH_LIFE);
    }
    static void release(Cast c){release(c,false);}
    static void release(Cast c,boolean self){
        emit(c,self?InkCastAnimation.SELF:InkCastAnimation.RELEASE,0,InkCastAnimation.CAST_LIFE);
    }
    static void activate(Cast c){
        var pos=c.player.position();
        AbilityVisuals.emit(c,AbilityVfxGeometry.CHARGE,pos,pos,0,InkCastAnimation.ACTIVATE,6,true);
    }
    public static void basicBullet(ServerPlayer player, ItemStack held, Vec3 from, Vec3 direction){
        if(!held.is(ModItems.ZHENQI.get()))return;
        Packets.sendToPlayer(player,new AbilityVfxPayload(player.getId(),-1,3,0,AbilityVfxGeometry.CHARGE,
                from,from.add(direction),0,InkCastAnimation.BASIC,10,player.level().getGameTime(),true));
    }
    private static void emit(Cast c,int kind,double progress,int life){
        var pos=c.player.position();
        AbilityVisuals.emit(c,AbilityVfxGeometry.CHARGE,pos,pos,progress,kind,life,true);
    }
}
