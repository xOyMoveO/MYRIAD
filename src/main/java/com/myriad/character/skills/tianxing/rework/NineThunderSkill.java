package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class NineThunderSkill extends AbilitySkill {
    public static final int WARNING_TICKS = 8, STRIKE_INTERVAL = 10, STRIKES = 6, ROOT_TICKS = 16;
    public static final double RANGE = 12, RADIUS = 5;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 20.7f, .25f).segment("skill.myriad.damage.segment.pulse", STRIKES);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public NineThunderSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.warning(c,c.point,RADIUS,WARNING_TICKS);}
    @Override public boolean needsPoint(){return true;} @Override public double previewRadius(Cast c){return RADIUS;}
    @Override public double pointRange(Cast c){return RANGE;}
 @Override public void release(Cast c){ThunderAudio.play(c,ThunderAudio.Cue.CHARGE);ThunderSkillPresentation.play(c,com.myriad.network.ThunderSkillViewPayload.NINE_FIRE,10);Vec3 at=c.point;c.later(WARNING_TICKS,false,()->com.myriad.character.ability.AbilityVisuals.field(c,at,RADIUS,51));for(int hit=0;hit<STRIKES;hit++){final int i=hit;c.later(WARNING_TICKS+hit*STRIKE_INTERVAL,false,()->{
 ThunderAudio.play(c,i==STRIKES-1?ThunderAudio.Cue.JUDGMENT:ThunderAudio.Cue.PULSE,at);
 com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.PILLAR,at.add(0,.05,0),at.add(0,6,0),5,i==STRIKES-1?1:0,9,false);c.area(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());if(i==STRIKES-1)for(var t:c.enemies(at,RADIUS))c.root(t,ROOT_TICKS,false);});}}
}
