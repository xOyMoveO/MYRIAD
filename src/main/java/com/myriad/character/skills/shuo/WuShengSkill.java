package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.*;
public final class WuShengSkill extends AbilitySkill {
    public WuShengSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.FLASH,8);
            c.dash(c.forward(6),6,1.0,(t,first)->{},()->{
                if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0)return;
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_DOWN,8);
                ShuoVisuals.slash(c,5,120,2);
                ShuoCombat.cone(c,5,120,c.value(240,80,1.2f),12);
            },false,false);ShuoVisuals.trackMotion(c,true);return;
        }
        ShuoCombat.windup(c,14,()->{
        float damage=c.value(320,100,1.6f)+ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.FLASH,10);
        var targets=new HashSet<java.util.UUID>();
        c.dash(c.forward(12),7,1.3,(t,first)->{targets.add(t.getUUID());ShuoVisuals.mark(c,t);},()->{
            if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0){ShuoVisuals.cancel(c.player,index());return;}
            ShuoCombat.sheathe(c.player);
            c.whileActive(8,age->{
                if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0){ShuoVisuals.cancel(c.player,index());return false;}
                if(age<8)return true;
                ShuoVisuals.cancel(c.player,index());
                for(var id:targets)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t&&c.enemy(t)){
                    if(ShuoCombat.physicalHit(c,t,damage*executionScale(t.getHealth(),t.getMaxHealth())))ShuoVisuals.contact(c.player,index(),t,1.1f);
                }
                return false;
            });
        },false,false);
        ShuoVisuals.trackMotion(c,false);
    });}
    /** A capped finisher bonus, never target-max-health damage or an instant-kill threshold. */
    public static float executionScale(float health,float maximum){
        return maximum<=0||!Float.isFinite(health)||!Float.isFinite(maximum)?1:1+.25f*(1-Math.clamp(health/maximum,0,1));
    }
}
