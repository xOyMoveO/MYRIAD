package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.*;
public final class WuShengSkill extends AbilitySkill {
    public static final double MARK_DASH_RANGE = 12, DRAWN_DASH_RANGE = 6, DRAWN_CUT_RANGE = 5;
    public static final int WINDUP_TICKS = 14, MARK_DASH_TICKS = 7, MARK_DELAY = 8, DRAWN_DASH_TICKS = 6, DRAWN_STUN_TICKS = 12;
    public static final float MISSING_HP_SCALE = .25f;
    private static final AbilityDamageFormula DRAWN = AbilityDamageFormula.physicalFromSpell(240, 92, 1.20f, .80f)
            .segment("skill.myriad.damage.empty_break", 1);
    private static final AbilityDamageFormula SHEATHED = AbilityDamageFormula.physicalFromSpell(320, 125, 1.60f, .80f)
            .segment("skill.myriad.damage.delayed_cut", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DRAWN, SHEATHED);
    }

    public WuShengSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.FLASH,8);
            c.actionDash(c.forward(DRAWN_DASH_RANGE),DRAWN_DASH_TICKS,1.0,(t,first)->{},()->{
                if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0)return;
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_DOWN,8);
                ShuoVisuals.slash(c,DRAWN_CUT_RANGE,120,2);
                ShuoCombat.cone(c,DRAWN_CUT_RANGE,120,DRAWN.value(c),DRAWN_STUN_TICKS);
            },false,false);ShuoVisuals.trackMotion(c,true);return;
        }
        ShuoCombat.windup(c,WINDUP_TICKS,()->{
        float damage=SHEATHED.value(c)+ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.FLASH,10);
        var targets=new HashSet<java.util.UUID>();
        var line=new ShuoSilentLineVisuals(c);
        c.actionDash(c.forward(MARK_DASH_RANGE),MARK_DASH_TICKS,1.3,(t,first)->{targets.add(t.getUUID());ShuoVisuals.mark(c,t);},()->{
            if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0){line.stop();ShuoVisuals.cancel(c.player,index());return;}
            line.hold();
            ShuoCombat.sheathe(c.player);
            c.actionWhile(MARK_DELAY,age->{
                if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0){line.stop();ShuoVisuals.cancel(c.player,index());return false;}
                if(age<MARK_DELAY)return true;
                ShuoVisuals.cancel(c.player,index());
                line.cut();
                for(var id:targets)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t&&c.enemy(t)){
                    if(ShuoCombat.physicalHit(c,t,damage*executionScale(t.getHealth(),t.getMaxHealth())))ShuoVisuals.contact(c.player,index(),t,1.1f);
                }
                return false;
            });
        },false,false);
        line.start();
        ShuoVisuals.trackMotion(c,false);
    });}
    /** A capped finisher bonus, never target-max-health damage or an instant-kill threshold. */
    public static float executionScale(float health,float maximum){
        return maximum<=0||!Float.isFinite(health)||!Float.isFinite(maximum)?1:1+MISSING_HP_SCALE*(1-Math.clamp(health/maximum,0,1));
    }
}
