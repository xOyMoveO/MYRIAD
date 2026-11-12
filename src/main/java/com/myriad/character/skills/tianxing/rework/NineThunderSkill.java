package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class NineThunderSkill extends AbilitySkill {
    public NineThunderSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,5,8);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return 5;}
 @Override public void release(Cast c){ThunderAudio.play(c,ThunderAudio.Cue.CHARGE);ThunderSkillPresentation.play(c,com.myriad.network.ThunderSkillViewPayload.NINE_FIRE,10);Vec3 at=c.point;c.later(8,false,()->com.myriad.character.ability.AbilityVisuals.field(c,at,5,51));for(int hit=0;hit<6;hit++){final int i=hit;c.later(8+hit*10,false,()->{
 ThunderAudio.play(c,i==5?ThunderAudio.Cue.JUDGMENT:ThunderAudio.Cue.PULSE,at);
 com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.PILLAR,at.add(0,.05,0),at.add(0,6,0),5,i==5?1:0,9,false);c.area(at,5,c.value(45,18,.25f),MAGIC);if(i==5)for(var t:c.enemies(at,5))c.root(t,16,false);});}}
}
