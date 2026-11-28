package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class HeartPillArraySkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 80, 25.3f, .35f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

 public HeartPillArraySkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4;}
@Override public void selectionPose(Cast c,boolean active){StaffHandCues.select(c,active);}
 @Override public void release(Cast c){StaffHandCues.release(c);Vec3 at=c.point;pulse(c,at);c.repeat(80,20,i->pulse(c,at));}
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,4,80);}
 private static void pulse(Cast c,Vec3 at){
     for(var ally:c.allies(at,4))c.shield(ally,SHIELD.value(c),25);
     AbilityVisuals.burst(c,at,3.8);
 }
}

