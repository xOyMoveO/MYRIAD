package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class PaperCraneSkill extends AbilitySkill {
 public PaperCraneSkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    LivingEntity enemy=c.target(14,false),friend=c.target(14,true);
    final LivingEntity target=enemy!=null?enemy:friend!=null?friend:c.player;
    final boolean hostile=enemy!=null;Vec3 start=c.player.getEyePosition();final Vec3[] last={start};
    double bend=c.player.getRandom().nextBoolean()?1:-1;int visual=c.newVisualInstance();
    c.whileActive(14,age->{
        if(!target.isAlive()||target.level()!=c.player.level()||target.distanceToSqr(c.player)>256||!c.player.hasLineOfSight(target)){
            AbilityVisuals.finishFlight(c,visual);return false;
        }
        double u=Math.min(1,age/12.0);
        Vec3 end=target.position().add(0,target.getBbHeight()*.6,0),f=end.subtract(start).normalize(),side=AbilityMeshBuilder.side(f);
        Vec3 at=start.lerp(end,u).add(side.scale(bend*Math.sin(Math.PI*u)*1.5)).add(0,Math.sin(Math.PI*u)*1.1,0);
        var block=c.player.level().clip(new net.minecraft.world.level.ClipContext(last[0],at,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,c.player));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS||com.myriad.character.skills.sulan.FengQiangSkill.firstEnemyHit(c.player,last[0],at)!=null){
            AbilityVisuals.finishFlight(c,visual);AbilityVisuals.dissipate(c,at);return false;
        }
        AbilityVisuals.flight(c,last[0],at,age,false,visual);last[0]=at;if(u<1)return true;
        AbilityVisuals.finishFlight(c,visual);
        if(hostile){c.damage(target,c.value(55,18,.45f),MAGIC);c.slow(target,.25f,20);}
        else {c.heal(target,c.value(40,14,.3f));if(target instanceof net.minecraft.server.level.ServerPlayer ally){
            var d=com.myriad.player.PlayerDataProvider.get(ally);d.setMana(Math.min((float)ally.getAttributeValue(com.myriad.registry.ModAttributes.MAX_MANA),d.getMana()+20));}}
        AbilityVisuals.burst(c,target.position(),.8);return false;
    });
}
@Override public void present(Cast c){AbilityVisuals.seal(c,c.player.position(),.6,false);}
}

