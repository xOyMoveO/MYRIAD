package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import java.util.List;
public final class DuanLiuSkill extends AbilitySkill {
    private static final AbilityDamageFormula SHEATHED = AbilityDamageFormula.physicalFromSpell(110, 22, .85f, .50f);
    private static final AbilityDamageFormula DOUBLE_SLASH = AbilityDamageFormula.physicalFromSpell(45, 10, .35f, .25f)
            .segment("skill.myriad.damage.double_slash", 2);
    private static final AbilityDamageFormula OPENING = SHEATHED.scaled(.60f)
            .segment("skill.myriad.damage.first_cut", 1);
    private static final AbilityDamageFormula RETURN = SHEATHED.scaled(.40f)
            .segment("skill.myriad.damage.second_cut", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DOUBLE_SLASH, OPENING, RETURN);
    }

    public DuanLiuSkill(Definition d){super(d);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        if(ShuoCombat.drawn(c.player)){
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_LEFT,5);
            ShuoVisuals.slash(c,3.5,100,0);
            var first=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),3.5,100,DOUBLE_SLASH.value(c),0,0,0);
            ShuoCombat.follow(c,4,()->{
                ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,5);
                ShuoVisuals.slash(c,3.5,100,1);
                var second=ShuoCombat.coneAt(c,c.player.position(),c.forward(1),3.5,100,DOUBLE_SLASH.value(c),0,0,0);
                for(var id:second)if(first.contains(id)&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,6,false);
            });return;
        }
        ShuoCombat.windup(c,9,()->{
        float bonus=ShuoCombat.draw(c.player,com.myriad.network.ShuoViewPayload.CUT_LEFT,7);
        var origin=c.player.position();var facing=c.forward(1);
        ShuoVisuals.rift(c,origin,facing,7,1.2);
        var first=ShuoCombat.line(c,origin,facing,7,1.2,OPENING.value(c)+bonus);
        for(var id:first)if(c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.slow(t,.35f,20);
        ShuoCombat.follow(c,6,()->{
            ShuoCombat.cut(c,com.myriad.network.ShuoViewPayload.CUT_RIGHT,5);
            ShuoVisuals.rift(c,origin,facing,7,1.2);
            var second=ShuoCombat.line(c,origin,facing,7,1.2,RETURN.value(c));
            for(var id:second)if(first.contains(id)&&c.player.level().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity t)c.root(t,8,false);
        });
    });}
}
