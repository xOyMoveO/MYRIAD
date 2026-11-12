package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class WarBladeSkill extends AbilitySkill {
 public WarBladeSkill(Definition definition){super(definition);}

@Override public void release(Cast c){c.shield(c.player,c.value(230,70,.7f),120);var m=c.memory();m.until=c.now()+120;m.count=3;c.buff(getId()+"_ready",120);}
@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.until<=c.now()||m.count<=0||c.now()-m.last<4)return;m.last=c.now();m.count--;c.damage(h.target(),c.value(45,20,.3f),PHYSICAL,false);AbilityVisuals.arc(c,c.player.position(),3,80);if(m.count==0)c.buff(getId()+"_ready",0);}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),2,16);}
}

