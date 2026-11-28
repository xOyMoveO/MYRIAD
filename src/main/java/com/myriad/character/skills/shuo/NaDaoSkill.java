package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class NaDaoSkill extends AbilitySkill {
    public static final double RETREAT_RANGE = 3, CUT_RANGE = 3;
    public static final int RETREAT_TICKS = 6, RECOVERY_TICKS = 8, RETURN_TICKS = 7, SLOW_TICKS = 20, HASTE_TICKS = 30;
    public static final float SLOW = .30f, HASTE = .20f;
    private static final AbilityDamageFormula RETREAT_CUT = AbilityDamageFormula.physicalFromSpell(55, 17.5f, .40f, .50f);
    private static final AbilityDamageFormula OPENING = RETREAT_CUT.scaled(.60f)
            .segment("skill.myriad.damage.opening_slash", 1);
    private static final AbilityDamageFormula RETURN = RETREAT_CUT.scaled(.40f)
            .segment("skill.myriad.damage.return_slash", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(OPENING, RETURN);
    }

    public NaDaoSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return c.data.getBuff("g1_root")==0;}
    @Override public void release(AbilityRuntime.Cast c){
        boolean exposed=ShuoCombat.drawn(c.player);
        ShuoCombat.motion(c.player,RECOVERY_TICKS);
        if(exposed){
            var origin=c.player.position();var facing=c.forward(1);
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,6);
            ShuoVisuals.slash(c,CUT_RANGE,90,1);
            ShuoCombat.cone(c,CUT_RANGE,90,OPENING.value(c),0);
            // The after-cut stays where the retreat began; moving out is real counterplay.
            c.actionWhile(RETURN_TICKS,age->{
                if(AbilityRuntime.controlled(c.player)){ShuoVisuals.cancel(c.player,index());return false;}
                if(age<RETURN_TICKS)return true;
                ShuoVisuals.slashAt(c,origin,facing,CUT_RANGE,90,0);
                ShuoCombat.coneAt(c,origin,facing,CUT_RANGE,90,RETURN.value(c),0,SLOW,SLOW_TICKS);return false;
            });
        }
        var forward=c.forward(1);var side=new net.minecraft.world.phys.Vec3(forward.z,0,-forward.x);
        var delta=forward.scale(-.9).add(side.scale(.4358899)).normalize().scale(RETREAT_RANGE);
        c.actionDash(delta,RETREAT_TICKS,.2,(t,first)->{},()->{if(exposed)ShuoCombat.sheathe(c.player);else if(!AbilityRuntime.controlled(c.player))c.haste(HASTE,HASTE_TICKS);},false,false);
        ShuoVisuals.trackMotion(c,exposed);
    }
}
