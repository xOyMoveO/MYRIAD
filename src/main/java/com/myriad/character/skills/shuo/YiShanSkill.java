package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class YiShanSkill extends AbilitySkill {
    private static final AbilityDamageFormula SHEATHED = AbilityDamageFormula.physicalFromSpell(95, 20, .80f, .50f);
    private static final AbilityDamageFormula TAILWIND = AbilityDamageFormula.physicalFromSpell(65, 15, .55f, .50f)
            .segment("skill.myriad.damage.tailwind", 1);
    private static final AbilityDamageFormula DASH = SHEATHED.scaled(.60f)
            .segment("skill.myriad.damage.dash_hit", 1);
    private static final AbilityDamageFormula WOUND = SHEATHED.scaled(.40f)
            .segment("skill.myriad.damage.wound", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(TAILWIND, DASH, WOUND);
    }

    public YiShanSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.FLASH,7);
            float amount=TAILWIND.value(c);
            c.actionDash(c.forward(4),5,1.0,(t,first)->{if(ShuoCombat.physicalHit(c,t,amount)){ShuoVisuals.hit(c,t);c.slow(t,.25f,20);}},()->{},false,false);
            ShuoVisuals.trackMotion(c,true);
            return;
        }
        ShuoCombat.windup(c,10,()->{
            float opening=DASH.value(c), wound=WOUND.value(c);
            float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.FLASH,9);
            var marked=new java.util.HashSet<java.util.UUID>();
            c.actionDash(c.forward(7),6,1.1,(t,first)->{
                if(ShuoCombat.physicalHit(c,t,opening+bonus)){marked.add(t.getUUID());ShuoVisuals.hit(c,t);ShuoVisuals.mark(c,t);}
            },()->{
                if(AbilityRuntime.controlled(c.player)){ShuoVisuals.cancel(c.player,index());return;}
                // Already-applied blade wounds are delayed damage, not another interruptible cast.
                c.later(6,false,()->{
                    ShuoVisuals.cancel(c.player,index());
                    for(var id:marked)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t&&c.enemy(t)
                            &&t.distanceToSqr(c.player)<=24*24&&ShuoCombat.physicalHit(c,t,wound)){
                        c.slow(t,.35f,20);ShuoVisuals.contact(c.player,index(),t,.85f);
                    }
                });
            },false,false);
            ShuoVisuals.trackMotion(c,false);
        });
    }
}
