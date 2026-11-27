package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class PaperCraneSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount("heal", 40, 17.5f, .3f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 55, 24.3f, .45f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public PaperCraneSkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    LivingEntity enemy=c.target(14,false),friend=c.target(14,true);
    final LivingEntity target=enemy!=null?enemy:friend!=null?friend:c.player;
    final boolean hostile=enemy!=null;Vec3 start=c.player.getEyePosition();final Vec3[] last={start};
    double bend=c.player.getRandom().nextBoolean()?1:-1;int visual=c.newVisualInstance();
    var flight=java.util.UUID.randomUUID();var progress=new ActionTime.Progress(c.now());
    Runnable finish=()->{ActionTime.clearTarget(flight);AbilityVisuals.finishFlight(c,visual);};
    double duration=12/com.myriad.combat.HeroProjectileTuning.SPEED;
    int timeout=(int)Math.ceil(4*duration);
    c.whileActive(timeout,age->{
        if(!target.isAlive()||target.level()!=c.player.level()||target.distanceToSqr(c.player)>256||!c.player.hasLineOfSight(target)){
            finish.run();return false;
        }
        progress.advance(c.now(),AbilityRuntime.projectileRate(c.player,flight,last[0],c.now()),duration);
        double u=Math.min(1,progress.completed()/duration);
        Vec3 end=target.position().add(0,target.getBbHeight()*.6,0),f=end.subtract(start).normalize(),side=AbilityMeshBuilder.side(f);
        Vec3 at=start.lerp(end,u).add(side.scale(bend*Math.sin(Math.PI*u)*1.5)).add(0,Math.sin(Math.PI*u)*1.1,0);
        var block=c.player.level().clip(new net.minecraft.world.level.ClipContext(last[0],at,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,c.player));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS||com.myriad.combat.WindWallSystem.interceptProjectile(c.player,last[0],at)!=null){
            finish.run();AbilityVisuals.dissipate(c,at);return false;
        }
        AbilityVisuals.flight(c,last[0],at,age,false,visual);last[0]=at;if(u<1){if(age>=timeout){finish.run();AbilityVisuals.dissipate(c,at);return false;}return true;}
        finish.run();
        if(hostile){c.damage(target,DAMAGE.value(c),DAMAGE.kind());c.slow(target,.25f,20);}
        else {c.heal(target,HEAL.value(c));if(target instanceof net.minecraft.server.level.ServerPlayer ally){
            var d=com.myriad.player.PlayerDataProvider.get(ally);d.setMana(Math.min((float)ally.getAttributeValue(com.myriad.registry.ModAttributes.MAX_MANA),d.getMana()+20));}}
        AbilityVisuals.emit(c,AbilityVfxGeometry.BURST,at,at.add(f),.8,hostile?2:3,9,false);return false;
    },finish);
}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.seal(c,c.player.position(),.6,false);}
}

